"""多路视频流管理器：从后端同步配置，自动启停 CameraWorker。"""
from __future__ import annotations

import logging
import threading
import time
from typing import Any, Optional

from ..config import settings
from .backend import BackendClient
from .detector import BaseDetector
from .stream import CameraWorker

log = logging.getLogger(__name__)


class StreamManager:
    def __init__(self, detector: Optional[BaseDetector], client: BackendClient, publisher=None) -> None:
        self.detector = detector
        self.client = client
        self.publisher = publisher
        self.workers: dict[int, CameraWorker] = {}
        self._lock = threading.RLock()
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self.started_at = time.time()
        self.last_sync_at: Optional[float] = None
        self.last_sync_error: Optional[str] = None

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._loop, name="stream-manager", daemon=True)
        self._thread.start()
        log.info("流管理器已启动，同步间隔 %ss", settings.sync_interval)

    def stop(self) -> None:
        self._stop.set()
        with self._lock:
            for worker in self.workers.values():
                worker.stop()
            for worker in self.workers.values():
                worker.join(timeout=2)
            self.workers.clear()
        log.info("流管理器已停止")

    def _loop(self) -> None:
        while not self._stop.is_set():
            try:
                self.sync_once()
            except Exception as exc:  # pragma: no cover
                log.warning("同步失败：%s", exc)
            self._stop.wait(max(5, settings.sync_interval))

    def sync_once(self) -> bool:
        config = self.client.fetch_config()
        if config is None:
            self.last_sync_error = "后端不可用"
            return False
        cameras = config.get("cameras") or []
        areas = config.get("areas") or []
        rules = config.get("rules") or []
        with self._lock:
            active_ids: set[int] = set()
            for camera in cameras:
                if not camera.get("enabled", 1):
                    continue
                camera_id = int(camera["id"])
                stream_type = (camera.get("streamType") or "DEMO").upper()
                stream_url = camera.get("streamUrl") or ""
                if stream_type != "DEMO" and not stream_url:
                    log.warning("摄像头 %s 缺少视频地址，跳过", camera_id)
                    continue
                active_ids.add(camera_id)
                worker = self.workers.get(camera_id)
                if worker is None:
                    worker = CameraWorker(camera, areas, rules, self.detector, self.client, self.publisher)
                    self.workers[camera_id] = worker
                    worker.start()
                elif (worker.stream_type, worker.stream_url) != (stream_type, stream_url):
                    log.info("摄像头 %s 视频源变更，重启线程", camera_id)
                    worker.stop()
                    worker.join(timeout=2)
                    worker = CameraWorker(camera, areas, rules, self.detector, self.client, self.publisher)
                    self.workers[camera_id] = worker
                    worker.start()
                else:
                    worker.update_config(camera, areas, rules)
            for camera_id in list(self.workers.keys()):
                if camera_id not in active_ids:
                    log.info("摄像头 %s 已停用，停止线程", camera_id)
                    worker = self.workers.pop(camera_id)
                    worker.stop()
                    worker.join(timeout=2)
        self.last_sync_at = time.time()
        self.last_sync_error = None
        return True

    def get(self, camera_id: int) -> Optional[CameraWorker]:
        with self._lock:
            return self.workers.get(camera_id)

    def status(self) -> list[dict[str, Any]]:
        with self._lock:
            return [worker.status_dict() for worker in sorted(self.workers.values(), key=lambda w: w.camera_id)]

    def info(self) -> dict[str, Any]:
        with self._lock:
            return {
                "mode": self.detector.mode if self.detector else "mock",
                "device": self.detector.device if self.detector else "cpu",
                "modelPath": settings.model_path,
                "modelVersion": self.detector.version if self.detector else "mock-v1",
                "personModel": bool(self.detector.person_model) if self.detector else False,
                "personModelPath": settings.person_model_path,
                "streams": len(self.workers),
                "syncInterval": settings.sync_interval,
                "lastSyncAt": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(self.last_sync_at)) if self.last_sync_at else None,
                "lastSyncError": self.last_sync_error,
                "uptimeSeconds": int(time.time() - self.started_at),
            }
