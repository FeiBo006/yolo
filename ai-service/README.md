# 算法服务说明（ai-service）

Python + FastAPI + Ultralytics YOLOv8，负责视频拉流、抽帧推理、ByteTrack 跟踪、规则判定、
标注画面输出与告警上报。Web 前端通过 MJPEG 直接消费标注画面，因此画面与检测框天然对齐。

## 1. 运行模式

| 模式 | 触发条件 | 行为 |
| --- | --- | --- |
| `mock` | 没有 `weights/safety.pt` 或 `DETECTOR_MODE=mock` | 生成合成工地画面与模拟检测结果，演示全链路 |
| `yolo` | `weights/safety.pt` 存在，或显式 `DETECTOR_MODE=yolo` | 真实 YOLOv8 推理 + ByteTrack（PPE + COCO 双模型） |
| `auto`（默认） | 启动时检测权重 | 有 `safety.pt` 走 yolo，否则 mock |

切换示例：

```bash
MODEL_PATH=weights/ppe_best.pt DETECTOR_MODE=yolo DEVICE=mps ./run.sh
```

仓库自带：

| 文件 | 说明 |
| --- | --- |
| `weights/ppe_best.pt` | 第三方 PPE 模型（hardhat / no-hardhat / safety vest / no-safety vest） |
| `weights/yolov8n.pt` | COCO 人物检测，用于人数统计与入侵判定 |
| `videos/demo_site.mp4` | 真实工地画面演示视频（人脸已模糊），配合「AI 实景演示」摄像头使用 |

当 `PERSON_MODEL_PATH` 指向的文件存在时，检测流程为：

1. PPE 模型 `track()` 输出 `helmet / no_helmet / vest / no_vest`
2. COCO 模型 `track(classes=[0])` 输出 `person`，trackId 加 100000 偏移避免冲突
3. 合并结果：PPE 模型的 person 被 COCO 结果替换
4. 规则引擎据此做未戴安全帽、未穿反光衣、区域入侵判定

DEMO 类型摄像头始终走合成检测，不消耗模型算力。

## 2. 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/ai/health` | 模式、设备、模型路径、权重是否存在 |
| GET | `/ai/streams` | 当前运行的视频流与 FPS |
| POST | `/ai/streams/sync` | 立即从后端同步摄像头配置 |
| GET | `/ai/streams/{cameraId}/mjpeg` | 标注后的 MJPEG 视频流 |
| GET | `/ai/streams/{cameraId}/snapshot` | 原始画面截图（用于绘制 ROI） |
| POST | `/ai/detect` | 单张图片检测（multipart，字段名 `file`） |
| POST | `/ai/test-source` | 探测 RTSP/文件/USB 视频源 |
| GET | `/ai/snapshots/{filename}` | 告警截图 |
| GET | `/ai/models` | 本地权重列表 |

FastAPI 交互文档：<http://localhost:8000/docs>

## 3. 配置

复制 `.env.example` 为 `.env`（当前实现直接读取环境变量，可在启动前 `export`）：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `BACKEND_BASE_URL` | `http://127.0.0.1:8080` | 业务后端 |
| `INTERNAL_TOKEN` | `yuanyu-internal-2026` | 内部接口凭证，需与后端一致 |
| `SYNC_INTERVAL` | 20 | 配置同步间隔（秒） |
| `HEARTBEAT_INTERVAL` | 30 | 摄像头心跳间隔（秒） |
| `MODEL_PATH` | `weights/safety.pt` | 权重路径 |
| `DETECTOR_MODE` | `auto` | `auto` / `yolo` / `mock` |
| `DEVICE` | `auto` | `auto` / `mps` / `cuda` / `cpu` |
| `CONF_THRESHOLD` | 0.35 | PPE 模型置信度阈值（规则阈值另算） |
| `PERSON_MODEL_PATH` | `weights/yolov8n.pt` | COCO 人物检测权重，置空可关闭 |
| `PERSON_CONF` | 0.35 | 人物检测置信度 |
| `IOU_THRESHOLD` | 0.5 | NMS IoU |
| `IMGSZ` | 640 | 推理尺寸 |
| `MAX_WIDTH` | 960 | 输出画面宽度 |
| `JPEG_QUALITY` | 72 | MJPEG 质量 |
| `TARGET_FPS` | 16 | DEMO / 文件流输出帧率 |
| `INFER_EVERY` | 2 | 每 N 帧推理一次 |
| `PUBLISH_MODE` | `http` | `http` 直投后端 / `rabbit` 走 MQ |
| `RABBITMQ_URL` | `amqp://guest:guest@127.0.0.1:5672/%2F` | MQ 地址 |

## 4. 类别映射

`app/services/detector.py` 会把常见数据集类别归一化：

| 原始类别 | 归一化 |
| --- | --- |
| person / worker | person |
| helmet / hardhat / safety helmet | helmet |
| no_helmet / no-hardhat / without helmet | no_helmet |
| vest / safety vest / reflective vest | vest |
| no_vest / no-safety vest | no_vest |

若模型只有正类（helmet、vest），会用「人员框头部/躯干区域是否与正类框重叠」推导
`no_helmet` / `no_vest`，对应论文中的「几何规则补全」思路。

## 5. 数据集准备

目录约定（二选一）：

```text
raw/
├── images/xxx.jpg
└── labels/xxx.txt

# 或
raw/
├── xxx.jpg
└── xxx.txt
```

标注格式为 YOLO：`class_id cx cy w h`（归一化）。整理并划分 8:1:1：

```bash
python scripts/prepare_dataset.py \
  --source /path/to/raw \
  --output datasets/safety \
  --classes person helmet no_helmet vest no_vest
```

生成 `datasets/safety/data.yaml`。

## 6. 训练

```bash
source .venv/bin/activate
python scripts/train.py \
  --data datasets/safety/data.yaml \
  --model yolov8n.pt \
  --epochs 100 --imgsz 640 --batch 16
```

- Apple Silicon 自动使用 MPS；NVIDIA 用 `--device 0`
- 训练结束自动复制 `best.pt` 到 `weights/safety.pt`
- 指标写入 `runs/train/safety/metrics.json`

## 7. 评估

```bash
python scripts/evaluate.py \
  --weights weights/safety.pt \
  --data datasets/safety/data.yaml \
  --output runs/eval/report.json
```

输出 `mAP@0.5`、`mAP@0.5:0.95`、`Precision`、`Recall`、`F1`、单张推理耗时，
以及混淆矩阵、PR 曲线路径，可直接用于论文实验章节。

## 8. 性能建议

| 场景 | 建议 |
| --- | --- |
| 无 GPU 演示 | `DETECTOR_MODE=mock`，或 `yolov8n + IMGSZ=640 + INFER_EVERY=3` |
| Apple Silicon | `DEVICE=mps`，`yolov8n` 可到 15–30 FPS |
| NVIDIA | `yolov8s` + 半精度，或导出 TensorRT |
| 多路并发 | 每路独立线程 + 抽帧；超过 4 路建议多进程/多实例 |
| 远距离小目标 | 提高输入分辨率或增加小目标检测头 |

## 9. 常见问题

- **`cv2` 导入失败**：确认已安装 `opencv-python`；Linux 容器需 `libgl1`、`libglib2.0-0`
- **首次 track 报 `lap` 缺失**：`pip install lap>=0.5.12`（已写入 `requirements-yolo.txt`）
- **RTSP 打不开**：先用 `ffplay` / VLC 验证地址与账号密码，确认网络可达
- **MPS 报错**：设置 `DEVICE=cpu` 回退
- **告警重复**：检查规则中的冷却时间与连续帧；`alarmNo` 幂等只在后端生效，算法侧还有 track 级冷却
- **画面中文乱码**：OpenCV `putText` 不支持中文，画面 HUD 使用英文，中文在 Web 界面展示

## 10. 权重与许可

- 训练脚本、服务代码：本仓库
- 预训练 PPE 权重：[baskarmother/yolov8-ppe-construction](https://huggingface.co/baskarmother/yolov8-ppe-construction)（MIT License）
- COCO 人物权重：[Ultralytics YOLOv8n](https://github.com/ultralytics/assets/releases)（AGPL-3.0）
- 演示视频素材：SHWD 数据集示例图（仅用于教学演示）
- YOLOv8：[Ultralytics](https://github.com/ultralytics/ultralytics)（AGPL-3.0，毕业设计使用请注明）
- 数据集：SHWD、Roboflow Construction Site Safety，使用前请确认各自许可
