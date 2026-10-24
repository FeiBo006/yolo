"""告警规则引擎：ROI + 连续帧 + 冷却 + 置信度。"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from datetime import datetime, time as dtime
from typing import Optional

from .detector import Detection


@dataclass
class RuleDef:
    id: int
    behavior_type: str
    camera_id: Optional[int]
    min_confidence: float
    consecutive_frames: int
    cooldown_seconds: int
    level: str
    start_time: Optional[str] = None
    end_time: Optional[str] = None
    enabled: bool = True


@dataclass
class AreaDef:
    id: int
    camera_id: int
    area_name: str
    area_type: str
    points: list[tuple[float, float]] = field(default_factory=list)


@dataclass
class AlarmCandidate:
    behavior_type: str
    level: str
    confidence: float
    bbox: tuple[float, float, float, float]
    track_id: Optional[int]
    area_name: Optional[str] = None


class RuleEngine:
    def __init__(self) -> None:
        self.rules: list[RuleDef] = []
        self.areas: list[AreaDef] = []
        self.hits: dict[tuple[str, int], int] = {}
        self.last_alarm: dict[tuple[str, int], float] = {}
        self.last_behavior_alarm: dict[str, float] = {}

    def update(self, rules: list[RuleDef], areas: list[AreaDef]) -> None:
        self.rules = rules
        self.areas = areas

    def evaluate(self, detections: list[Detection], now: datetime) -> list[AlarmCandidate]:
        candidates: list[tuple[str, Detection, Optional[str], float]] = []

        no_helmet = [d for d in detections if d.class_name == "no_helmet"]
        no_vest = [d for d in detections if d.class_name == "no_vest"]
        persons = [d for d in detections if d.class_name == "person"]

        for det in no_helmet:
            candidates.append(("NO_HELMET", det, None, det.confidence))
        for det in no_vest:
            candidates.append(("NO_VEST", det, None, det.confidence))
        for person in persons:
            fx, fy = person.foot_point()
            for area in self.areas:
                if area.area_type != "DANGER" or len(area.points) < 3:
                    continue
                if point_in_polygon(fx, fy, area.points):
                    candidates.append(("INTRUSION", person, area.area_name, person.confidence))
                    break

        results: list[AlarmCandidate] = []
        for behavior, det, area_name, confidence in candidates:
            rule = self._match_rule(behavior, confidence, now)
            if rule is None:
                continue
            key = (behavior, det.track_id if det.track_id is not None else -1)
            self.hits[key] = self.hits.get(key, 0) + 1
            if self.hits[key] < max(1, rule.consecutive_frames):
                continue
            last = self.last_alarm.get(key, 0.0)
            behavior_last = self.last_behavior_alarm.get(behavior, 0.0)
            if now.timestamp() - last < max(0, rule.cooldown_seconds):
                continue
            # 同一路视频同类告警至少间隔 30 秒，避免多人同时触发时刷屏
            if now.timestamp() - behavior_last < 30:
                continue
            self.last_alarm[key] = now.timestamp()
            self.last_behavior_alarm[behavior] = now.timestamp()
            self.hits[key] = 0
            results.append(AlarmCandidate(
                behavior_type=behavior,
                level=rule.level,
                confidence=round(confidence, 3),
                bbox=det.bbox,
                track_id=det.track_id,
                area_name=area_name,
            ))
        return results

    def _match_rule(self, behavior: str, confidence: float, now: datetime) -> Optional[RuleDef]:
        matched = [r for r in self.rules if r.enabled and r.behavior_type == behavior
                   and confidence >= r.min_confidence and in_time_window(r, now)]
        if not matched:
            return None
        matched.sort(key=lambda r: (r.camera_id is None, r.id))
        return matched[0]


def in_time_window(rule: RuleDef, now: datetime) -> bool:
    if not rule.start_time or not rule.end_time:
        return True
    try:
        start = dtime.fromisoformat(rule.start_time)
        end = dtime.fromisoformat(rule.end_time)
    except ValueError:
        return True
    current = now.time()
    if start <= end:
        return start <= current <= end
    return current >= start or current <= end


def point_in_polygon(x: float, y: float, polygon: list[tuple[float, float]]) -> bool:
    inside = False
    n = len(polygon)
    j = n - 1
    for i in range(n):
        xi, yi = polygon[i]
        xj, yj = polygon[j]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi):
            inside = not inside
        j = i
    return inside
