"""单路视频工作线程：拉流、推理、跟踪、规则判定、MJPEG 输出、告警上报。"""
from __future__ import annotations

import json
import logging
import threading
import time
from collections import deque
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

import cv2
import numpy as np

from ..config import BASE_DIR, settings
from .backend import BackendClient
from .demo import CLASS_COLORS, render_demo_frame
from .detector import BaseDetector, Detection
from .rules import AreaDef, RuleDef, RuleEngine

log = logging.getLogger(__name__)

BOUNDARY = "frame"
LABELS = {
    "person": "PERSON",
    "helmet": "HELMET",
    "no_helmet": "NO-HELMET",
    "vest": "VEST",
    "no_vest": "NO-VEST",
}


class CameraWorker(threading.Thread):
    def __init__(self, camera: dict[str, Any], areas: list[dict], rules: list[dict],
                 detector: Optional[BaseDetector], client: BackendClient, publisher=None):
        super().__init__(daemon=True, name=f"camera-{camera.get('id')}")
        self.client = client
        self.publisher = publisher
        self.detector = detector
        self.camera_id = int(camera["id"])
        self.camera_name = camera.get("cameraName") or f"摄像头{self.camera_id}"
        self.stream_type = (camera.get("streamType") or "DEMO").upper()
        self.stream_url = camera.get("streamUrl") or ""
        self.seed = self.camera_id

        self.rule_engine = RuleEngine()
        self.areas: list[AreaDef] = []
        self.rules: list[RuleDef] = []
        self.update_config(camera, areas, rules)

        self._stop = threading.Event()
        self._lock = threading.Lock()
        self._condition = threading.Condition(self._lock)
        self._jpeg: Optional[bytes] = None
        self._raw_jpeg: Optional[bytes] = None
        self._frame_seq = 0
        self._capture: Optional[cv2.VideoCapture] = None
        self._started_at = time.time()

        self.fps = 0.0
        self.inference_ms = 0.0
        self.last_frame_at: Optional[float] = None
        self.last_error: Optional[str] = None
        self.status = "STARTING"
        self._frame_times: deque[float] = deque(maxlen=60)
        self._last_report = 0.0
        self._last_heartbeat = 0.0
        self._last_detections: list[Detection] = []

    # ---------------- 配置 ----------------
    def update_config(self, camera: dict[str, Any], areas: list[dict], rules: list[dict]) -> None:
        self.camera_name = camera.get("cameraName") or self.camera_name
        self.stream_type = (camera.get("streamType") or self.stream_type or "DEMO").upper()
        self.stream_url = camera.get("streamUrl") or ""
        parsed_areas = [parse_area(a) for a in areas if a.get("cameraId") == self.camera_id]
        parsed_rules = [parse_rule(r) for r in rules
                        if r.get("cameraId") in (None, self.camera_id)]
        self.areas = [a for a in parsed_areas if a.area_type == "DANGER"]
        self.rules = parsed_rules
        self.rule_engine.update(self.rules, self.areas)

    # ---------------- 生命周期 ----------------
    def run(self) -> None:
        log.info("启动视频线程 camera=%s type=%s", self.camera_id, self.stream_type)
        backoff = 1.0
        while not self._stop.is_set():
            try:
                self._loop()
                backoff = 1.0
            except Exception as exc:  # pragma: no cover
                self.status = "ERROR"
                self.last_error = str(exc)
                log.warning("摄像头 %s 处理异常：%s，%.1fs 后重连", self.camera_id, exc, backoff)
                self._release_capture()
                self._stop.wait(backoff)
                backoff = min(backoff * 2, 15.0)
        self._release_capture()
        self.status = "STOPPED"
        log.info("视频线程停止 camera=%s", self.camera_id)

    def stop(self) -> None:
        self._stop.set()

    def _loop(self) -> None:
        # DEMO 源始终使用合成检测结果，避免在 YOLO 模式下浪费算力
        if self.stream_type == "DEMO":
            self._demo_loop()
            return
        self._open_capture()
        self.status = "RUNNING"
        frame_interval = 1.0 / max(1, settings.target_fps)
        while not self._stop.is_set():
            started = time.time()
            ok, frame = self._capture.read()
            if not ok or frame is None:
                if self.stream_type == "VIDEO":
                    # 本地视频循环播放
                    self._capture.set(cv2.CAP_PROP_POS_FRAMES, 0)
                    ok, frame = self._capture.read()
                if not ok or frame is None:
                    raise RuntimeError("视频帧读取失败")
            self._process(frame, time.time() - self._started_at)
            elapsed = time.time() - started
            if elapsed < frame_interval:
                time.sleep(frame_interval - elapsed)

    def _demo_loop(self) -> None:
        self.status = "RUNNING"
        frame_interval = 1.0 / max(1, settings.target_fps)
        while not self._stop.is_set():
            started = time.time()
            t = time.time() - self._started_at
            frame, detections = render_demo_frame(t, self.seed, settings.max_width, int(settings.max_width * 9 / 16))
            self._process(frame, t, preset_detections=detections)
            elapsed = time.time() - started
            if elapsed < frame_interval:
                time.sleep(frame_interval - elapsed)

    # ---------------- 核心处理 ----------------
    def _process(self, frame: np.ndarray, t: float, preset_detections: Optional[list[Detection]] = None) -> None:
        frame = self._resize(frame)
        h, w = frame.shape[:2]
        now = datetime.now()
        if self._frame_seq % 15 == 0:
            raw = self._encode(frame)
            with self._lock:
                self._raw_jpeg = raw

        if preset_detections is not None:
            detections = preset_detections
        elif self.detector is not None:
            if self._frame_seq % max(1, settings.infer_every) == 0:
                started = time.time()
                detections = self.detector.detect(frame)
                self.inference_ms = (time.time() - started) * 1000
                self._last_detections = detections
            else:
                detections = self._last_detections
        else:
            detections = []

        alarms = self.rule_engine.evaluate(detections, now)
        annotated = self._draw(frame, detections, t)
        with self._lock:
            self._frame_seq += 1
            self._jpeg = self._encode(annotated)
            self._condition.notify_all()

        self._frame_times.append(time.time())
        self.fps = self._calc_fps()
        if alarms:
            self._emit_alarms(alarms, annotated, now, w, h)

        self._report_periodically(detections, now)
        self.last_frame_at = time.time()

    def _emit_alarms(self, alarms, annotated: np.ndarray, now: datetime, w: int, h: int) -> None:
        timestamp = int(now.timestamp() * 1000)
        for candidate in alarms:
            x1, y1, x2, y2 = candidate.bbox
            bbox = f"{x1:.3f},{y1:.3f},{x2:.3f},{y2:.3f}"
            track = candidate.track_id if candidate.track_id is not None else -1
            alarm_no = f"{self.camera_id}-{timestamp}-{track}-{candidate.behavior_type}"[:40]
            filename = f"{self.camera_id}_{int(now.timestamp())}_{track}_{candidate.behavior_type}.jpg"
            path = settings.snapshot_dir / filename
            try:
                cv2.imwrite(str(path), annotated)
            except Exception as exc:  # pragma: no cover
                log.warning("截图保存失败：%s", exc)
            event = {
                "alarmNo": alarm_no,
                "cameraId": self.camera_id,
                "behaviorType": candidate.behavior_type,
                "level": candidate.level,
                "confidence": candidate.confidence,
                "trackId": candidate.track_id,
                "bbox": bbox,
                "snapshotUrl": f"/ai/snapshots/{filename}" if path.exists() else None,
                "alarmTime": now.strftime("%Y-%m-%d %H:%M:%S"),
            }
            log.info("生成告警 %s %s conf=%.2f", self.camera_name, candidate.behavior_type, candidate.confidence)
            published = False
            if self.publisher is not None:
                published = self.publisher.publish(event)
            if not published:
                self.client.report_alarm(event)

    def _report_periodically(self, detections: list[Detection], now: datetime) -> None:
        if time.time() - self._last_report < settings.report_interval:
            return
        self._last_report = time.time()
        counts: dict[str, int] = {}
        for det in detections:
            counts[det.class_name] = counts.get(det.class_name, 0) + 1
        payload = {
            "cameraId": self.camera_id,
            "recordTime": now.strftime("%Y-%m-%d %H:%M:%S"),
            "personCount": counts.get("person", 0),
            "helmetCount": counts.get("helmet", 0),
            "noHelmetCount": counts.get("no_helmet", 0),
            "vestCount": counts.get("vest", 0),
            "noVestCount": counts.get("no_vest", 0),
            "fps": round(self.fps, 2),
            "inferenceMs": round(self.inference_ms, 2),
            "modelVersion": self.detector.version if self.detector else "mock-v1",
        }
        self.client.report_detection(payload)
        if time.time() - self._last_heartbeat >= settings.heartbeat_interval:
            self._last_heartbeat = time.time()
            self.client.heartbeat(self.camera_id)

    # ---------------- 绘制 ----------------
    def _draw(self, frame: np.ndarray, detections: list[Detection], t: float) -> np.ndarray:
        canvas = frame.copy()
        h, w = canvas.shape[:2]
        # ROI
        for area in self.areas:
            points = np.array([[int(x * w), int(y * h)] for x, y in area.points], dtype=np.int32)
            if len(points) >= 3:
                overlay = canvas.copy()
                cv2.fillPoly(overlay, [points], (0, 60, 220))
                canvas = cv2.addWeighted(overlay, 0.12, canvas, 0.88, 0)
                cv2.polylines(canvas, [points], True, (0, 220, 255), 2)
                cv2.putText(canvas, "DANGER ZONE", (points[0][0] + 6, points[0][1] + 22),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 220, 255), 2)
        # 检测框
        for det in detections:
            x1, y1, x2, y2 = det.bbox
            p1 = (int(x1 * w), int(y1 * h))
            p2 = (int(x2 * w), int(y2 * h))
            color = CLASS_COLORS.get(det.class_name, (200, 200, 200))
            cv2.rectangle(canvas, p1, p2, color, 2)
            label = f"{LABELS.get(det.class_name, det.class_name)} {det.confidence:.2f}"
            (tw, th), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
            cv2.rectangle(canvas, (p1[0], max(0, p1[1] - th - 8)), (p1[0] + tw + 6, p1[1]), color, -1)
            cv2.putText(canvas, label, (p1[0] + 3, max(12, p1[1] - 5)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.5, (20, 20, 20), 1)
        # HUD
        mode = "demo" if self.stream_type == "DEMO" else (self.detector.mode if self.detector else "mock")
        cv2.putText(canvas, f"CAM-{self.camera_id:02d}  {mode.upper()}", (16, h - 44),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)
        cv2.putText(canvas, f"FPS {self.fps:4.1f}   {datetime.now():%Y-%m-%d %H:%M:%S}",
                    (16, h - 16), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (230, 230, 230), 1)
        return canvas

    def _encode(self, frame: np.ndarray) -> bytes:
        ok, buffer = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), settings.jpeg_quality])
        if not ok:
            return b""
        return buffer.tobytes()

    def _resize(self, frame: np.ndarray) -> np.ndarray:
        h, w = frame.shape[:2]
        if w <= settings.max_width:
            return frame
        scale = settings.max_width / w
        return cv2.resize(frame, (settings.max_width, int(h * scale)), interpolation=cv2.INTER_AREA)

    def _calc_fps(self) -> float:
        if len(self._frame_times) < 2:
            return 0.0
        span = self._frame_times[-1] - self._frame_times[0]
        if span <= 0:
            return 0.0
        return (len(self._frame_times) - 1) / span

    # ---------------- 视频源 ----------------
    def _open_capture(self) -> None:
        self._release_capture()
        source: Any = self.stream_url
        if self.stream_type == "USB":
            source = int(self.stream_url) if str(self.stream_url).isdigit() else 0
        elif self.stream_type == "VIDEO":
            path = Path(self.stream_url)
            if not path.is_absolute():
                path = BASE_DIR / path
            source = str(path)
        capture = cv2.VideoCapture(source)
        if not capture.isOpened():
            raise RuntimeError(f"无法打开视频源：{self.stream_type} {self.stream_url}")
        self._capture = capture
        log.info("视频源已打开 camera=%s", self.camera_id)

    def _release_capture(self) -> None:
        if self._capture is not None:
            try:
                self._capture.release()
            finally:
                self._capture = None

    # ---------------- 对外输出 ----------------
    def mjpeg(self):
        last_seq = -1
        while not self._stop.is_set():
            with self._condition:
                if self._frame_seq == last_seq:
                    self._condition.wait(timeout=2.0)
                seq = self._frame_seq
                data = self._jpeg
            if data and seq != last_seq:
                last_seq = seq
                yield (b"--" + BOUNDARY.encode() + b"\r\nContent-Type: image/jpeg\r\nContent-Length: "
                       + str(len(data)).encode() + b"\r\n\r\n" + data + b"\r\n")

    def snapshot(self) -> Optional[bytes]:
        with self._lock:
            return self._raw_jpeg or self._jpeg

    def status_dict(self) -> dict[str, Any]:
        return {
            "cameraId": self.camera_id,
            "cameraName": self.camera_name,
            "streamType": self.stream_type,
            "status": self.status,
            "mode": "demo" if self.stream_type == "DEMO" else (self.detector.mode if self.detector else "mock"),
            "fps": round(self.fps, 2),
            "inferenceMs": round(self.inference_ms, 2),
            "lastFrameAt": datetime.fromtimestamp(self.last_frame_at).strftime("%Y-%m-%d %H:%M:%S") if self.last_frame_at else None,
            "lastError": self.last_error,
        }


def parse_area(raw: dict) -> AreaDef:
    points: list[tuple[float, float]] = []
    try:
        value = json.loads(raw.get("points") or "[]")
        for item in value:
            if isinstance(item, (list, tuple)) and len(item) >= 2:
                points.append((float(item[0]), float(item[1])))
            elif isinstance(item, dict):
                points.append((float(item["x"]), float(item["y"])))
    except Exception:
        points = []
    return AreaDef(
        id=int(raw.get("id", 0)),
        camera_id=int(raw.get("cameraId", 0)),
        area_name=raw.get("areaName") or "危险区域",
        area_type=(raw.get("areaType") or "DANGER").upper(),
        points=points,
    )


def parse_rule(raw: dict) -> RuleDef:
    return RuleDef(
        id=int(raw.get("id", 0)),
        behavior_type=raw.get("behaviorType") or "",
        camera_id=raw.get("cameraId"),
        min_confidence=float(raw.get("minConfidence") or 0.45),
        consecutive_frames=int(raw.get("consecutiveFrames") or 3),
        cooldown_seconds=int(raw.get("cooldownSeconds") or 60),
        level=raw.get("level") or "MEDIUM",
        start_time=raw.get("startTime"),
        end_time=raw.get("endTime"),
        enabled=bool(raw.get("enabled", 1)),
    )
