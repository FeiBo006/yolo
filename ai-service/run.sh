#!/usr/bin/env bash
# 启动算法服务（DEMO 模式只需核心依赖，速度快）
set -e
cd "$(dirname "$0")"

PYTHON_BIN="${PYTHON:-python3.12}"
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  PYTHON_BIN="python3"
fi

if [ ! -d .venv ]; then
  echo "创建虚拟环境：$PYTHON_BIN"
  "$PYTHON_BIN" -m venv .venv
fi

source .venv/bin/activate
pip install -q --upgrade pip
pip install -q -r requirements.txt

echo "算法服务启动：http://127.0.0.1:${PORT:-8000}/docs"
exec uvicorn app.main:app --host 0.0.0.0 --port "${PORT:-8000}"
