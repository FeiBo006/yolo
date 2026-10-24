"""模型评估脚本：输出 mAP、P、R、F1、FPS 与曲线图路径。"""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

from ultralytics import YOLO


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--weights", default="weights/safety.pt")
    parser.add_argument("--data", required=True)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--device", default="auto")
    parser.add_argument("--output", default="runs/eval/report.json")
    args = parser.parse_args()

    device = None if args.device == "auto" else args.device
    model = YOLO(args.weights)
    started = time.time()
    metrics = model.val(data=args.data, imgsz=args.imgsz, device=device)
    elapsed = time.time() - started

    result = {
        "weights": args.weights,
        "data": args.data,
        "map50": float(metrics.box.map50),
        "map50_95": float(metrics.box.map),
        "precision": float(metrics.box.mp),
        "recall": float(metrics.box.mr),
        "f1": float(2 * metrics.box.mp * metrics.box.mr / (metrics.box.mp + metrics.box.mr + 1e-9)),
        "speed_ms_per_image": float(metrics.speed.get("inference", 0)),
        "val_seconds": round(elapsed, 2),
        "save_dir": str(metrics.save_dir),
        "curves": {
            "confusion_matrix": str(Path(metrics.save_dir) / "confusion_matrix.png"),
            "pr_curve": str(Path(metrics.save_dir) / "PR_curve.png"),
        },
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
