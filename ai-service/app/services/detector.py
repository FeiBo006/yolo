"""YOLO 检测器与类别归一化。

- 有 weights/safety.pt 时使用真实 YOLOv8 + ByteTrack；
- 没有权重时使用 DEMO 合成源（见 demo.py），保证系统可以完整演示。
"""
from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import numpy as np

from ..config import settings

log = logging.getLogger(__name__)

NAME_MAP = {
    "person": "person",
    "worker": "person",
    "people": "person",
    "helmet": "helmet",
    "hardhat": "helmet",
    "hard hat": "helmet",
    "safety helmet": "helmet",
    "no_helmet": "no_helmet",
    "no-helmet": "no_helmet",
    "nohelmet": "no_helmet",
    "no hardhat": "no_helmet",
    "no-hardhat": "no_helmet",
    "without helmet": "no_helmet",
    "vest": "vest",
    "safety vest": "vest",
    "reflective vest": "vest",
    "no_vest": "no_vest",
    "no-vest": "no_vest",
    "novest": "no_vest",
    "no safety vest": "no_vest",
    "no-safety vest": "no_vest",
}


@dataclass
class Detection:
    class_name: str
    confidence: float
    bbox: tuple[float, float, float, float]
    track_id: Optional[int] = None

    def foot_point(self) -> tuple[float, float]:
        x1, _, x2, y2 = self.bbox
        return (x1 + x2) / 2.0, y2


class BaseDetector:
    mode = "base"
    device = "cpu"
    version = "unknown"

    def detect(self, frame: np.ndarray, track: bool = True) -> list[Detection]:
        raise NotImplementedError


class YoloDetector(BaseDetector):
    mode = "yolo"

    def __init__(self, model_path: str):
        from ultralytics import YOLO

        if not Path(model_path).exists():
            raise FileNotFoundError(f"模型权重不存在：{model_path}")
        self.model_path = model_path
        self.model = YOLO(model_path)
        self.device = self._pick_device()
        self.lock = threading.Lock()
        names = self.model.names
        self.class_names = list(names.values()) if isinstance(names, dict) else list(names)
        self.version = Path(model_path).stem

        # PPE 数据集往往缺少可靠的人体框，额外加载 COCO 模型只检测 person，用于入侵判定与人数统计
        self.person_model = None
        person_path = Path(settings.person_model_path)
        if person_path.exists() and person_path.resolve() != Path(model_path).resolve():
            try:
                self.person_model = YOLO(str(person_path))
                self.version = f"{self.version}+coco"
            except Exception as exc:  # pragma: no cover
                log.warning("人体检测模型加载失败：%s", exc)
        log.info("YOLO 模型加载完成：%s device=%s personModel=%s classes=%s",
                 model_path, self.device, bool(self.person_model), self.class_names)

    @staticmethod
    def _pick_device() -> str:
        if settings.device and settings.device != "auto":
            return settings.device
        try:
            import torch

            if torch.cuda.is_available():
                return "cuda"
            if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
                return "mps"
        except Exception:  # pragma: no cover
            pass
        return "cpu"

    def detect(self, frame: np.ndarray, track: bool = True) -> list[Detection]:
        h, w = frame.shape[:2]
        with self.lock:
            if track:
                results = self.model.track(
                    frame, persist=True, tracker="bytetrack.yaml",
                    conf=settings.conf_threshold, iou=settings.iou_threshold,
                    imgsz=settings.imgsz, device=self.device, verbose=False,
                )
            else:
                results = self.model.predict(
                    frame, conf=settings.conf_threshold, iou=settings.iou_threshold,
                    imgsz=settings.imgsz, device=self.device, verbose=False,
                )
            person_results = None
            if self.person_model is not None:
                if track:
                    person_results = self.person_model.track(
                        frame, persist=True, tracker="bytetrack.yaml", classes=[0],
                        conf=settings.person_conf, iou=settings.iou_threshold,
                        imgsz=settings.imgsz, device=self.device, verbose=False,
                    )
                else:
                    person_results = self.person_model.predict(
                        frame, classes=[0], conf=settings.person_conf,
                        iou=settings.iou_threshold, imgsz=settings.imgsz,
                        device=self.device, verbose=False,
                    )

        detections = self._extract(results[0], w, h) if results else []
        if person_results:
            persons = self._extract(person_results[0], w, h, id_offset=100000)
            if persons:
                # COCO 人体框更可靠，替换 PPE 模型输出的 person，避免重复
                detections = [d for d in detections if d.class_name != "person"] + persons
        return self._infer_missing(detections)

    @staticmethod
    def _extract(result, w: int, h: int, id_offset: int = 0) -> list[Detection]:
        boxes = result.boxes
        if boxes is None or len(boxes) == 0:
            return []
        names = result.names
        detections: list[Detection] = []
        for i in range(len(boxes)):
            box = boxes[i]
            xyxy = box.xyxy[0].tolist()
            conf = float(box.conf[0])
            cls = int(box.cls[0])
            raw_name = names.get(cls, str(cls)) if isinstance(names, dict) else names[cls]
            name = NAME_MAP.get(str(raw_name).strip().lower(), str(raw_name))
            tid = None
            if box.id is not None:
                try:
                    tid = int(box.id[0]) + id_offset
                except Exception:
                    tid = None
            detections.append(Detection(
                class_name=name,
                confidence=conf,
                bbox=(xyxy[0] / w, xyxy[1] / h, xyxy[2] / w, xyxy[3] / h),
                track_id=tid,
            ))
        return detections

    @staticmethod
    def _infer_missing(detections: list[Detection]) -> list[Detection]:
        """若模型只标注正类（helmet/vest），用几何关系推导 no_helmet / no_vest。"""
        class_set = {d.class_name for d in detections}
        persons = [d for d in detections if d.class_name == "person"]
        helmets = [d for d in detections if d.class_name == "helmet"]
        vests = [d for d in detections if d.class_name == "vest"]
        extra: list[Detection] = []
        if "no_helmet" not in class_set and helmets:
            for person in persons:
                x1, y1, x2, y2 = person.bbox
                head = (x1, y1, x2, y1 + (y2 - y1) * 0.35)
                if not any(_overlap(head, h.bbox) > 0.05 for h in helmets):
                    extra.append(Detection("no_helmet", round(person.confidence * 0.9, 3), head, person.track_id))
        if "no_vest" not in class_set and vests:
            for person in persons:
                x1, y1, x2, y2 = person.bbox
                torso = (x1, y1 + (y2 - y1) * 0.2, x2, y1 + (y2 - y1) * 0.75)
                if not any(_overlap(torso, v.bbox) > 0.05 for v in vests):
                    extra.append(Detection("no_vest", round(person.confidence * 0.85, 3), torso, person.track_id))
        return detections + extra


def _overlap(a: tuple[float, float, float, float], b: tuple[float, float, float, float]) -> float:
    x1, y1 = max(a[0], b[0]), max(a[1], b[1])
    x2, y2 = min(a[2], b[2]), min(a[3], b[3])
    if x2 <= x1 or y2 <= y1:
        return 0.0
    inter = (x2 - x1) * (y2 - y1)
    area_a = max(1e-6, (a[2] - a[0]) * (a[3] - a[1]))
    return inter / area_a


def create_detector() -> Optional[BaseDetector]:
    if settings.detector_mode != "yolo":
        log.warning("当前为 DEMO/mock 模式，未加载 YOLO 权重；使用合成视频源演示完整链路")
        return None
    try:
        return YoloDetector(settings.model_path)
    except Exception as exc:  # pragma: no cover
        log.error("YOLO 加载失败，回退 DEMO 模式：%s", exc)
        return None
