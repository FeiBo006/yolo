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

### 1.1 隐私与敏感信息复核（2026-10-25）

对当前 HEAD 与本地目录做了检查：

| 项目 | 结果 |
| --- | --- |
| 真实姓名 / 住址 / 身份证 / 银行卡 | 未发现 |
| 真实手机号 | 未发现；演示数据为 `1380000000x` 虚构号码 |
| 工地地址 | 代码和页面均已脱敏，示例地址为虚构的「示例市/示例区」 |
| API Key / 私钥 / `.env` | 未发现；`.env` 未被跟踪 |
| 数据库 / 告警截图 / 虚拟环境 | 未被 Git 跟踪（见 `.gitignore`） |
| 演示视频人脸 | 当前版本已模糊处理 |
| Git 作者邮箱 | 提交记录使用本机个人邮箱，push 后会公开 |
| 默认开发凭据 | JWT 密钥、内部 Token、MySQL/RabbitMQ 默认密码仍在代码中 |

**历史提交需要特别注意**：

当前 `HEAD` 是脱敏后的版本，但 Git 历史中仍保留旧版演示视频和旧版截图（未模糊人脸）。
即使当前文件已替换，检出旧提交仍能看到这些内容。计划公开仓库时，建议在首次 push 前压缩历史。

审计命令：

```bash
# 查看提交作者邮箱
git log --format='%h %an <%ae>' | sort -u

# 查看历史中的大文件
git rev-list --objects --all \
  | git cat-file --batch-check='%(objecttype) %(objectname) %(objectsize) %(rest)' \
  | awk '$1=="blob" && $3>1000000'

# 查看当前被忽略但仍在本地目录中的内容
git status --ignored --short
```

如果确认要清理历史（**仅在尚未 push 时执行**）：

```bash
# 1. 先在仓库外备份一份完整历史
git bundle create ../yuanyu-history.bundle --all

# 2. 配置 GitHub noreply 身份
git config user.name "your-github-name"
git config user.email "your-github-name@users.noreply.github.com"

# 3. 用当前干净文件创建新的初始提交
git checkout --orphan clean-main
git add -A
git commit -m "feat: 基于 YOLO 的工地安全行为实时检测预警系统 v1.0"
git branch -D main
git branch -m main

# 4. 本地清理旧对象（确认新提交无误后再执行）
git reflog expire --expire=now --all
git gc --prune=now --aggressive
```

> 备份 bundle 中包含旧版素材，仅保存在本地，不要上传到 GitHub。
> 压缩历史会改变所有提交哈希；如果已经 push，需要 force push 并通知协作者。

本地忽略目录中还有：

- `backend/data/`：约 1.9 MB，H2 演示数据库（含虚拟账号与操作日志）
- `ai-service/snapshots/`：约 91 MB、上千张告警截图
- `.vscode/`：仅一条 Java 编译设置
- `ai-service/.venv/`、`frontend/node_modules/`：依赖目录

这些不会进入 Git；但**不要直接压缩整个项目目录上传**，否则会连同数据库、告警截图一起泄露。

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
