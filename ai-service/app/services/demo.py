"""DEMO 合成视频源：生成可用于演示的工地画面与对应检测结果。

真实部署时把摄像头类型换成 RTSP/VIDEO 即可，算法链路完全一致。
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import cv2
import numpy as np

CLASS_COLORS = {
    "person": (80, 200, 120),
    "helmet": (0, 215, 255),
    "no_helmet": (60, 60, 235),
    "vest": (0, 150, 255),
    "no_vest": (220, 80, 220),
}


@dataclass
class DemoDetection:
    class_name: str
    confidence: float
    bbox: tuple[float, float, float, float]  # 归一化 x1,y1,x2,y2
    track_id: int

    def foot_point(self) -> tuple[float, float]:
        x1, _, x2, y2 = self.bbox
        return (x1 + x2) / 2.0, y2


def _seed_offset(seed: int) -> float:
    return (seed % 7) * 0.37


def worker_states(t: float, seed: int = 1) -> list[dict]:
    """返回 4 个工人的位置与穿戴状态，位置随时间平滑移动。"""
    off = _seed_offset(seed)
    workers = []
    specs = [
        {"phase": 0.0, "speed": 0.16, "y": 0.72, "helmet": False, "vest": True},
        {"phase": 2.1, "speed": 0.11, "y": 0.80, "helmet": True, "vest": True},
        {"phase": 4.0, "speed": 0.09, "y": 0.62, "helmet": True, "vest": False},
        {"phase": 1.2, "speed": 0.13, "y": 0.66, "helmet": True, "vest": True},
    ]
    for i, spec in enumerate(specs):
        x = 0.22 + 0.30 * math.sin(t * spec["speed"] + spec["phase"] + off) + i * 0.12
        x = min(max(x, 0.10), 0.90)
        workers.append({
            "id": i + 1,
            "x": x,
            "y": spec["y"],
            "helmet": spec["helmet"],
            "vest": spec["vest"],
            "w": 0.055,
            "h": 0.20,
        })
    return workers


def _draw_background(frame: np.ndarray, seed: int) -> None:
    h, w = frame.shape[:2]
    # 天空渐变
    for y in range(int(h * 0.62)):
        ratio = y / max(1, int(h * 0.62))
        color = (int(190 - 40 * ratio), int(150 - 30 * ratio), int(110 - 20 * ratio))
        cv2.line(frame, (0, y), (w, y), color, 1)
    # 地面
    cv2.rectangle(frame, (0, int(h * 0.62)), (w, h), (95, 105, 110), -1)
    # 远处楼体
    cv2.rectangle(frame, (int(w * 0.06), int(h * 0.20)), (int(w * 0.28), int(h * 0.62)), (120, 125, 132), -1)
    cv2.rectangle(frame, (int(w * 0.30), int(h * 0.10)), (int(w * 0.50), int(h * 0.62)), (138, 140, 148), -1)
    for r in range(4):
        for c in range(3):
            x = int(w * 0.08) + c * int(w * 0.06)
            y = int(h * 0.26) + r * int(h * 0.08)
            cv2.rectangle(frame, (x, y), (x + int(w * 0.035), y + int(h * 0.05)), (90, 100, 105), -1)
    # 塔吊
    cv2.line(frame, (int(w * 0.72), int(h * 0.62)), (int(w * 0.72), int(h * 0.08)), (60, 60, 60), 5)
    cv2.line(frame, (int(w * 0.55), int(h * 0.12)), (int(w * 0.90), int(h * 0.12)), (60, 60, 60), 5)
    cv2.line(frame, (int(w * 0.90), int(h * 0.12)), (int(w * 0.90), int(h * 0.30)), (70, 70, 70), 2)
    # 围挡
    cv2.rectangle(frame, (0, int(h * 0.58)), (w, int(h * 0.62)), (40, 110, 190), -1)
    for x in range(0, w, 60):
        cv2.line(frame, (x, int(h * 0.58)), (x, int(h * 0.62)), (230, 230, 230), 2)
    # 水印
    cv2.putText(frame, "DEMO SOURCE", (18, 32), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
    cv2.putText(frame, f"CAM-{seed:02d}", (w - 120, 32), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)


def _draw_worker(frame: np.ndarray, worker: dict) -> None:
    h, w = frame.shape[:2]
    cx = int(worker["x"] * w)
    feet = int(worker["y"] * h)
    bw = int(worker["w"] * w)
    bh = int(worker["h"] * h)
    top = feet - bh
    # 腿
    cv2.line(frame, (cx - bw // 4, feet), (cx - bw // 5, feet - bh // 2), (30, 60, 120), 6)
    cv2.line(frame, (cx + bw // 4, feet), (cx + bw // 5, feet - bh // 2), (30, 60, 120), 6)
    # 身体 / 反光衣
    body_color = (0, 120, 220) if worker["vest"] else (90, 90, 100)
    cv2.rectangle(frame, (cx - bw // 2, top + bh // 4), (cx + bw // 2, feet - bh // 3), body_color, -1)
    if worker["vest"]:
        cv2.line(frame, (cx - bw // 3, top + bh // 3), (cx - bw // 3, feet - bh // 2), (200, 240, 255), 3)
        cv2.line(frame, (cx + bw // 3, top + bh // 3), (cx + bw // 3, feet - bh // 2), (200, 240, 255), 3)
    # 头
    head_r = max(6, bw // 3)
    cv2.circle(frame, (cx, top + head_r), head_r, (120, 170, 210), -1)
    if worker["helmet"]:
        cv2.ellipse(frame, (cx, top + head_r - 2), (head_r + 3, head_r // 2 + 2), 0, 180, 360, (0, 210, 255), -1)


def render_demo_frame(t: float, seed: int, width: int = 960, height: int = 540) -> tuple[np.ndarray, list[DemoDetection]]:
    frame = np.zeros((height, width, 3), dtype=np.uint8)
    _draw_background(frame, seed)
    detections: list[DemoDetection] = []
    for worker in worker_states(t, seed):
        _draw_worker(frame, worker)
        x1 = worker["x"] - worker["w"] / 2
        x2 = worker["x"] + worker["w"] / 2
        y1 = worker["y"] - worker["h"]
        y2 = worker["y"]
        conf = 0.86 + (worker["id"] % 3) * 0.03
        detections.append(DemoDetection("person", round(conf, 3), (x1, y1, x2, y2), worker["id"]))
        if worker["helmet"]:
            detections.append(DemoDetection("helmet", 0.92, (x1 + 0.008, y1 - 0.005, x2 - 0.008, y1 + 0.05), worker["id"]))
        else:
            detections.append(DemoDetection("no_helmet", 0.90, (x1 + 0.008, y1 - 0.005, x2 - 0.008, y1 + 0.05), worker["id"]))
        if worker["vest"]:
            detections.append(DemoDetection("vest", 0.88, (x1 + 0.006, y1 + 0.06, x2 - 0.006, y1 + 0.13), worker["id"]))
        else:
            detections.append(DemoDetection("no_vest", 0.87, (x1 + 0.006, y1 + 0.06, x2 - 0.006, y1 + 0.13), worker["id"]))
    return frame, detections
