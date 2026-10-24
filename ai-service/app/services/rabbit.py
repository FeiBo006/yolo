"""RabbitMQ 告警事件发布器（可选）。

默认走 HTTP 直投后端；设置 PUBLISH_MODE=rabbit 时优先走 MQ，
发布失败自动回退 HTTP，保证告警不丢。
"""
from __future__ import annotations

import json
import logging
import threading
from typing import Any

from ..config import settings

log = logging.getLogger(__name__)

EXCHANGE = "yuanyu.alarm.exchange"
QUEUE = "yuanyu.alarm.queue"
ROUTING_KEY = "alarm.event"


class RabbitPublisher:
    def __init__(self) -> None:
        self.connection = None
        self.channel = None
        self.lock = threading.Lock()

    def _ensure(self) -> None:
        if self.channel is not None and getattr(self.connection, "is_open", False):
            return
        import pika

        params = pika.URLParameters(settings.rabbitmq_url)
        self.connection = pika.BlockingConnection(params)
        self.channel = self.connection.channel()
        self.channel.exchange_declare(exchange=EXCHANGE, exchange_type="topic", durable=True)
        self.channel.queue_declare(queue=QUEUE, durable=True)
        self.channel.queue_bind(queue=QUEUE, exchange=EXCHANGE, routing_key=ROUTING_KEY)
        log.info("RabbitMQ 发布通道已建立：%s", settings.rabbitmq_url)

    def publish(self, payload: dict[str, Any]) -> bool:
        with self.lock:
            try:
                import pika

                self._ensure()
                self.channel.basic_publish(
                    exchange=EXCHANGE,
                    routing_key=ROUTING_KEY,
                    body=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                    properties=pika.BasicProperties(content_type="application/json", delivery_mode=2),
                )
                return True
            except Exception as exc:
                log.warning("MQ 发布失败，回退 HTTP：%s", exc)
                self._close()
                return False

    def _close(self) -> None:
        try:
            if self.connection is not None and self.connection.is_open:
                self.connection.close()
        except Exception:
            pass
        self.connection = None
        self.channel = None
