# 上传到 GitHub

> 本文记录本项目的仓库初始化、推送与更新流程。仓库已排除虚拟环境、依赖、数据库和运行产物。

## 1. 上传前检查

```bash
cd yuanyu

# 不应出现 .env、密钥、真实人员信息
find . -name ".env" -o -name "*.pem" -o -name "*.key"

# 不应被 Git 跟踪的目录
git status --short | grep -E "node_modules|\.venv|target/|backend/data|snapshots" || true
```

默认配置中的 JWT 密钥、内部 Token、MySQL 密码均为开发占位值，生产环境必须通过环境变量覆盖。

## 2. 在 GitHub 创建空仓库

打开 <https://github.com/new>：

- Repository name：`yuanyu-safety`（可自定义）
- 不要勾选 “Add a README file / .gitignore / license”
- 建议先设为 **Private**，答辩结束后再决定是否公开

## 3. 初始化并推送

```bash
cd yuanyu

git init -b main
git config core.quotepath false

git add .
git status --short

git commit -m "feat: 基于 YOLO 的工地安全行为实时检测预警系统 v1.0"

git remote add origin https://github.com/<你的用户名>/yuanyu-safety.git
git push -u origin main
```

使用 SSH：

```bash
git remote add origin git@github.com:<你的用户名>/yuanyu-safety.git
git push -u origin main
```

HTTPS 推送时，密码处填写 **Personal Access Token**（GitHub 已不支持账号密码直推）。

## 4. 仓库内容说明

会提交：

- `backend/`、`frontend/`、`ai-service/` 源码
- `deploy/`、`docs/`、`README.md`
- `ai-service/weights/ppe_best.pt`（6 MB）、`yolov8n.pt`（6 MB）
- `ai-service/videos/demo_site.mp4`（4 MB，人脸已模糊）
- `docs/screenshots/*.png`（16 张，约 5 MB）

不会提交（见 `.gitignore`）：

- `ai-service/.venv/`、`frontend/node_modules/`、`backend/target/`
- `backend/data/`（H2 数据库）
- `ai-service/snapshots/`（告警截图）
- `ai-service/weights/safety.pt`（自训练权重，按需自行分发）
- `.env`、日志、IDE 配置

仓库跟踪文件约 22 MB，低于 GitHub 单文件 100 MB 限制，无需 Git LFS。

## 5. 后续更新

```bash
git add .
git commit -m "feat: 描述本次改动"
git push
```

## 6. 常见问题

**push 被拒绝（non-fast-forward）**

```bash
git pull --rebase origin main
git push
```

**误提交了密钥**

1. 立即在对应平台撤销/轮换密钥
2. 从 Git 历史中移除（`git filter-repo` 或 BFG）
3. 强制推送并通知协作者重新克隆

**想改用 Git LFS 管理权重/视频**

```bash
git lfs install
git lfs track "ai-service/weights/*.pt"
git lfs track "ai-service/videos/*.mp4"
git add .gitattributes
```

**公开仓库的素材版权**

`ai-service/videos/demo_site.mp4` 由 SHWD 公开数据集示例图拼接，仅用于教学演示；
若仓库公开传播，建议替换为自己拍摄或已获授权的素材。
