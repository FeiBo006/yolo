"""运行时单例：检测器 + 后端客户端 + 流管理器。"""
from __future__ import annotations

from ..config import settings
from .backend import BackendClient
from .detector import create_detector
from .manager import StreamManager
from .rabbit import RabbitPublisher

detector = create_detector()
client = BackendClient()
publisher = RabbitPublisher() if settings.publish_mode == "rabbit" else None
manager = StreamManager(detector, client, publisher)
