"""算法服务 HTTP 接口。"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import FileResponse, JSONResponse, Response, StreamingResponse
from pydantic import BaseModel

from ..config import BASE_DIR, settings
from ..services.runtime import manager
from ..services.stream import BOUNDARY, parse_rule  # noqa: F401

log = logging.getLogger(__name__)
router = APIRouter(prefix="/ai", tags=["ai"])


class SourceTestRequest(BaseModel):
    streamType: str = "DEMO"
    streamUrl: str | None = None


@router.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "UP",
        "service": "yuanyu-ai",
        "mode": manager.detector.mode if manager.detector else "mock",
        "device": manager.detector.device if manager.detector else "cpu",
        "modelPath": settings.model_path,
        "modelVersion": manager.detector.version if manager.detector else "mock-v1",
        "weightsExists": Path(settings.model_path).exists(),
        "personModel": bool(manager.detector.person_model) if manager.detector else False,
        "personModelPath": settings.person_model_path,
    }


@router.get("/streams")
def streams() -> dict[str, Any]:
    return {"info": manager.info(), "streams": manager.status()}


@router.post("/streams/sync")
def sync_streams() -> dict[str, Any]:
    ok = manager.sync_once()
    return {"ok": ok, "info": manager.info(), "streams": manager.status()}


@router.get("/streams/{camera_id}/mjpeg")
def mjpeg(camera_id: int) -> StreamingResponse:
    worker = manager.get(camera_id)
    if worker is None:
        raise HTTPException(status_code=404, detail="该摄像头未在算法服务中运行")
    return StreamingResponse(
        worker.mjpeg(),
        media_type=f"multipart/x-mixed-replace; boundary={BOUNDARY}",
        headers={"Cache-Control": "no-cache, no-store, must-revalidate", "Pragma": "no-cache"},
    )


@router.get("/streams/{camera_id}/snapshot")
def snapshot(camera_id: int) -> Response:
    worker = manager.get(camera_id)
    if worker is None:
        raise HTTPException(status_code=404, detail="该摄像头未运行")
    data = worker.snapshot()
    if not data:
        raise HTTPException(status_code=404, detail="暂无画面")
    return Response(content=data, media_type="image/jpeg")


@router.post("/detect")
async def detect(file: UploadFile = File(...)) -> JSONResponse:
    if manager.detector is None:
        return JSONResponse(status_code=400, content={
            "ok": False,
            "message": "当前为 DEMO/mock 模式，未加载 YOLO 权重；请先训练模型或配置 MODEL_PATH",
        })
    raw = await file.read()
    frame = cv2.imdecode(np.frombuffer(raw, np.uint8), cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(status_code=400, detail="图片解析失败")
    detections = manager.detector.detect(frame, track=False)
    return JSONResponse(content={
        "ok": True,
        "width": frame.shape[1],
        "height": frame.shape[0],
        "detections": [
            {
                "class": d.class_name,
                "confidence": round(d.confidence, 3),
                "bbox": [round(v, 4) for v in d.bbox],
            }
            for d in detections
        ],
    })


@router.post("/test-source")
def test_source(request: SourceTestRequest) -> dict[str, Any]:
    stream_type = (request.streamType or "DEMO").upper()
    url = request.streamUrl or ""
    if stream_type == "DEMO":
        return {"ok": True, "message": "DEMO 合成源可用（无需真实摄像头）", "width": 960, "height": 540}
    if not url:
        return {"ok": False, "message": "视频地址为空"}
    source: Any = url
    if stream_type == "USB":
        source = int(url) if url.isdigit() else 0
    elif stream_type == "VIDEO":
        path = Path(url)
        if not path.is_absolute():
            path = BASE_DIR / path
        source = str(path)
        if not path.exists():
            return {"ok": False, "message": f"本地视频不存在：{path}"}
    try:
        capture = cv2.VideoCapture(source)
        ok = capture.isOpened()
        width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)) if ok else 0
        height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT)) if ok else 0
        capture.release()
        if not ok:
            return {"ok": False, "message": "视频源无法打开，请检查地址/网络/账号密码"}
        return {"ok": True, "message": "视频源可用", "width": width, "height": height}
    except Exception as exc:  # pragma: no cover
        return {"ok": False, "message": f"探测失败：{exc}"}


@router.get("/snapshots/{filename}")
def snapshot_file(filename: str) -> FileResponse:
    if "/" in filename or "\\" in filename or filename in (".", ".."):
        raise HTTPException(status_code=400, detail="非法文件名")
    path = settings.snapshot_dir / filename
    if not path.exists():
        raise HTTPException(status_code=404, detail="截图不存在")
    return FileResponse(path, media_type="image/jpeg")


@router.get("/models")
def models() -> dict[str, Any]:
    weights_dir = BASE_DIR / "weights"
    items = []
    if weights_dir.exists():
        for path in sorted(weights_dir.glob("*")):
            if path.suffix.lower() in (".pt", ".onnx", ".engine"):
                items.append({
                    "name": path.name,
                    "size": path.stat().st_size,
                    "active": str(path) == settings.model_path,
                })
    return {
        "mode": manager.detector.mode if manager.detector else "mock",
        "activePath": settings.model_path,
        "weights": items,
    }
