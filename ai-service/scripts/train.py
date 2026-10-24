"""YOLOv8 训练脚本，训练完成后复制 best.pt 到 weights/safety.pt。

示例：
  python scripts/train.py --data datasets/safety/data.yaml --model yolov8n.pt \
      --epochs 100 --imgsz 640 --batch 16
"""
from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

from ultralytics import YOLO


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", required=True)
    parser.add_argument("--model", default="yolov8n.pt")
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--batch", type=int, default=16)
    parser.add_argument("--device", default="auto", help="auto / cpu / mps / 0")
    parser.add_argument("--project", default="runs/train")
    parser.add_argument("--name", default="safety")
    parser.add_argument("--output", default="weights/safety.pt")
    args = parser.parse_args()

    device = None if args.device == "auto" else args.device
    model = YOLO(args.model)
    results = model.train(
        data=args.data,
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        device=device,
        project=args.project,
        name=args.name,
        exist_ok=True,
        pretrained=True,
        mosaic=1.0,
        mixup=0.1,
        hsv_h=0.015,
        hsv_s=0.7,
        hsv_v=0.4,
        fliplr=0.5,
        patience=30,
    )

    save_dir = Path(results.save_dir)
    best = save_dir / "weights" / "best.pt"
    metrics = {}
    try:
        metrics = {k: float(v) for k, v in results.results_dict.items() if isinstance(v, (int, float))}
    except Exception:
        pass
    metrics["save_dir"] = str(save_dir)
    (save_dir / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

    if best.exists():
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(best, output)
        print(f"best.pt 已复制到 {output}")
    print(f"训练完成，结果目录：{save_dir}")
    print(json.dumps(metrics, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
