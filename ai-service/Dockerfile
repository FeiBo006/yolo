FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai

RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
COPY requirements-yolo.txt .
ARG INSTALL_YOLO=false
RUN pip install --no-cache-dir -r requirements.txt \
    && if [ "$INSTALL_YOLO" = "true" ]; then pip install --no-cache-dir -r requirements-yolo.txt; fi

COPY app ./app
COPY videos ./videos

RUN mkdir -p weights snapshots
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
