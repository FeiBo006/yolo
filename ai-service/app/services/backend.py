"""与 Spring Boot 后端通信：拉取配置、上报告警/指标、心跳。"""
from __future__ import annotations

import logging
import time
from typing import Any

import requests

from ..config import settings

log = logging.getLogger(__name__)


class BackendClient:
    def __init__(self) -> None:
        self.base = settings.backend_base_url.rstrip("/")
        self.session = requests.Session()
        self.session.headers.update({"X-Internal-Token": settings.internal_token})
        self.timeout = 3

    def fetch_config(self) -> dict[str, Any] | None:
        for attempt in range(2):
            try:
                resp = self.session.get(f"{self.base}/api/internal/config", timeout=self.timeout)
                resp.raise_for_status()
                body = resp.json()
                return body.get("data") or {}
            except Exception as exc:
                if attempt == 1:
                    log.warning("拉取后端配置失败：%s", exc)
                else:
                    time.sleep(1)
        return None

    def report_alarm(self, payload: dict[str, Any]) -> bool:
        for attempt in range(3):
            try:
                resp = self.session.post(
                    f"{self.base}/api/internal/alarms", json=payload, timeout=self.timeout
                )
                resp.raise_for_status()
                return True
            except Exception as exc:
                if attempt == 2:
                    log.error("告警上报失败：%s payload=%s", exc, payload.get("alarmNo"))
                else:
                    time.sleep(0.3 * (attempt + 1))
        return False

    def report_detection(self, payload: dict[str, Any]) -> None:
        try:
            self.session.post(
                f"{self.base}/api/internal/detections", json=payload, timeout=self.timeout
            )
        except Exception as exc:
            log.debug("检测指标上报失败：%s", exc)

    def heartbeat(self, camera_id: int) -> None:
        try:
            self.session.post(
                f"{self.base}/api/internal/heartbeat",
                params={"cameraId": camera_id},
                timeout=self.timeout,
            )
        except Exception as exc:
            log.debug("心跳上报失败：%s", exc)
