"""把常见标注数据整理成 YOLO 目录结构，并生成 data.yaml。

用法：
  python scripts/prepare_dataset.py --source /path/to/raw --output datasets/safety \
      --classes person helmet no_helmet vest no_vest
数据目录约定（二选一）：
  raw/images/*.jpg + raw/labels/*.txt
  raw/*.jpg + raw/*.txt
"""
from __future__ import annotations

import argparse
import random
import shutil
from pathlib import Path

IMAGE_SUFFIX = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def collect_pairs(source: Path) -> list[tuple[Path, Path]]:
    images_dir = source / "images"
    labels_dir = source / "labels"
    pairs: list[tuple[Path, Path]] = []
    if images_dir.exists():
        for image in sorted(images_dir.rglob("*")):
            if image.suffix.lower() not in IMAGE_SUFFIX:
                continue
            label = labels_dir / f"{image.stem}.txt"
            if label.exists():
                pairs.append((image, label))
    else:
        for image in sorted(source.rglob("*")):
            if image.suffix.lower() not in IMAGE_SUFFIX:
                continue
            label = image.with_suffix(".txt")
            if label.exists():
                pairs.append((image, label))
    return pairs


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", default=Path("datasets/safety"), type=Path)
    parser.add_argument("--classes", nargs="+", default=["person", "helmet", "no_helmet", "vest", "no_vest"])
    parser.add_argument("--train", type=float, default=0.8)
    parser.add_argument("--val", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    pairs = collect_pairs(args.source)
    if not pairs:
        raise SystemExit(f"未在 {args.source} 找到图片+标注，请检查目录结构")
    random.Random(args.seed).shuffle(pairs)

    total = len(pairs)
    train_end = int(total * args.train)
    val_end = train_end + int(total * args.val)
    splits = {
        "train": pairs[:train_end],
        "val": pairs[train_end:val_end],
        "test": pairs[val_end:],
    }

    output = args.output.resolve()
    for split, items in splits.items():
        image_dir = output / "images" / split
        label_dir = output / "labels" / split
        image_dir.mkdir(parents=True, exist_ok=True)
        label_dir.mkdir(parents=True, exist_ok=True)
        for image, label in items:
            shutil.copy2(image, image_dir / image.name)
            shutil.copy2(label, label_dir / f"{image.stem}.txt")
        print(f"{split:5s}: {len(items)} 张")

    data_yaml = output / "data.yaml"
    lines = [
        f"path: {output}",
        "train: images/train",
        "val: images/val",
        "test: images/test",
        f"nc: {len(args.classes)}",
        "names:",
    ]
    lines += [f"  {i}: {name}" for i, name in enumerate(args.classes)]
    data_yaml.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"\n共 {total} 张，data.yaml 已写入 {data_yaml}")


if __name__ == "__main__":
    main()
