"""算法服务配置。"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent


def _env(key: str, default: str) -> str:
    return os.getenv(key, default)


@dataclass
class Settings:
    backend_base_url: str = field(default_factory=lambda: _env("BACKEND_BASE_URL", "http://127.0.0.1:8080"))
    internal_token: str = field(default_factory=lambda: _env("INTERNAL_TOKEN", "yuanyu-internal-2026"))
    sync_interval: int = field(default_factory=lambda: int(_env("SYNC_INTERVAL", "20")))
    heartbeat_interval: int = field(default_factory=lambda: int(_env("HEARTBEAT_INTERVAL", "30")))

    model_path: str = field(default_factory=lambda: _env("MODEL_PATH", str(BASE_DIR / "weights" / "safety.pt")))
    person_model_path: str = field(default_factory=lambda: _env("PERSON_MODEL_PATH", str(BASE_DIR / "weights" / "yolov8n.pt")))
    person_conf: float = field(default_factory=lambda: float(_env("PERSON_CONF", "0.35")))
    detector_mode: str = field(default_factory=lambda: _env("DETECTOR_MODE", "auto"))  # auto / yolo / mock
    device: str = field(default_factory=lambda: _env("DEVICE", "auto"))
    conf_threshold: float = field(default_factory=lambda: float(_env("CONF_THRESHOLD", "0.35")))
    iou_threshold: float = field(default_factory=lambda: float(_env("IOU_THRESHOLD", "0.5")))
    imgsz: int = field(default_factory=lambda: int(_env("IMGSZ", "640")))

    max_width: int = field(default_factory=lambda: int(_env("MAX_WIDTH", "960")))
    jpeg_quality: int = field(default_factory=lambda: int(_env("JPEG_QUALITY", "72")))
    infer_every: int = field(default_factory=lambda: int(_env("INFER_EVERY", "2")))
    target_fps: int = field(default_factory=lambda: int(_env("TARGET_FPS", "16")))

    snapshot_dir: Path = field(default_factory=lambda: Path(_env("SNAPSHOT_DIR", str(BASE_DIR / "snapshots"))))
    report_interval: int = field(default_factory=lambda: int(_env("REPORT_INTERVAL", "10")))
    publish_mode: str = field(default_factory=lambda: _env("PUBLISH_MODE", "http").lower())
    rabbitmq_url: str = field(default_factory=lambda: _env("RABBITMQ_URL", "amqp://guest:guest@127.0.0.1:5672/%2F"))

    def __post_init__(self) -> None:
        self.snapshot_dir.mkdir(parents=True, exist_ok=True)
        if self.detector_mode == "auto":
            self.detector_mode = "yolo" if Path(self.model_path).exists() else "mock"


settings = Settings()
