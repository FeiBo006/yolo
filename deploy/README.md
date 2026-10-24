# 部署说明（Docker Compose）

## 1. 前置条件

- Docker Engine 24+ / Docker Desktop
- 可用内存建议 ≥ 6 GB（MySQL + RabbitMQ + 算法服务）
- 80、8080、8000 端口未被占用

## 2. 一键启动

```bash
cd deploy
docker compose up -d --build
```

首次构建会拉取 Maven / Node / Python 依赖，耗时约 5–15 分钟。

查看状态：

```bash
docker compose ps
docker compose logs -f backend
```

访问：

| 地址 | 说明 |
| --- | --- |
| <http://localhost> | 前端（Nginx） |
| <http://localhost:8080/api/health> | 后端健康检查 |
| <http://localhost:8000/ai/health> | 算法服务 |
| <http://localhost:15672> | RabbitMQ 管理台（guest/guest） |
| localhost:3306 | MySQL（root / `MYSQL_ROOT_PASSWORD`） |

默认账号：`admin / 123456`。

## 3. 服务组成

| 服务 | 镜像/构建 | 端口 | 说明 |
| --- | --- | --- | --- |
| mysql | mysql:8.0 | 3306 | 首次启动执行 `sql/init.sql` |
| redis | redis:7-alpine | 6379 | Token 黑名单 |
| rabbitmq | rabbitmq:3.13-management | 5672/15672 | 告警事件 |
| backend | `../backend` | 8080 | Profile：`mysql,redis,mq` |
| ai | `../ai-service` | 8000 | 默认 mock；挂载 `../ai-service/weights` |
| web | `../frontend` | 80 | Nginx + 静态资源 + 反向代理 |

## 4. 启用真实 YOLO

1. 将 `safety.pt` 或 `ppe_best.pt` 放入 `ai-service/weights/`（compose 只读挂载）。
2. 构建带 YOLO 依赖的算法镜像（默认镜像只装核心依赖，体积小）：

   ```yaml
   ai:
     build:
       context: ../ai-service
       args:
         INSTALL_YOLO: "true"
   ```

3. 修改 `docker-compose.yml` 中 ai 服务：

   ```yaml
   environment:
     DETECTOR_MODE: yolo
     MODEL_PATH: /app/weights/safety.pt
     DEVICE: cpu        # 有 NVIDIA 时改为 cuda 并配置 runtime
   ```

4. 重新创建容器：

   ```bash
   docker compose up -d --force-recreate ai
   ```

> CPU 容器内 yolov8n 单路约 2–5 FPS，建议 `INFER_EVERY=3`、`MAX_WIDTH=640`。
> 生产环境如需 GPU，请为 ai 服务配置 `deploy.resources.reservations.devices` 与 NVIDIA Container Toolkit。

## 5. 配置覆盖

在 `deploy` 目录创建 `.env`：

```env
MYSQL_ROOT_PASSWORD=change-me
INTERNAL_TOKEN=change-me-too
```

`docker compose` 会自动读取。生产环境同时应修改后端的 `JWT_SECRET`：

```yaml
environment:
  JWT_SECRET: ${JWT_SECRET}
```

## 6. 日志与排障

```bash
docker compose logs -f backend
docker compose logs -f ai
docker compose logs -f web
```

常见问题：

- **backend 启动时连不上 MySQL**：等待 mysql healthcheck 通过；`docker compose ps` 查看状态
- **MQ 连接失败**：确认 rabbitmq 健康；后端会重试，不影响 HTTP 直投路径
- **监控无画面**：确认 ai 容器健康、摄像头 `enabled=1`，Nginx `/ai/` 已关闭 `proxy_buffering`
- **告警截图 404**：确认 `ai-snapshots` 卷已挂载，且截图 URL 走 `/ai/snapshots/...`

## 7. 数据重置

```bash
docker compose down -v   # 删除 MySQL / Redis / 截图卷
docker compose up -d --build
```

## 8. 生产建议

- 修改所有默认密码与 `INTERNAL_TOKEN`
- 前端启用 HTTPS（Nginx 配置证书）
- MySQL 使用独立账号而非 root
- Redis 设置密码，RabbitMQ 使用非 guest 账号
- 算法服务与业务服务分别扩容；AI 实例无状态，可按摄像头分片
- 截图迁移到 MinIO/OSS，数据库只存 URL
