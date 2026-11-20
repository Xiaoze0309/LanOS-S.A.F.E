#!/bin/sh
# 日常启动：依赖已装好时用这个，秒起（不做 apk/pip 安装）
# 用法： sh scripts/run.sh
set -eu

cd "$(dirname "$0")/.."

if [ ! -x .venv/bin/uvicorn ]; then
  echo "还没建好虚拟环境，先跑一次： sh scripts/start-backend-alpine.sh"
  exit 1
fi

# shellcheck disable=SC1091
. .venv/bin/activate

if [ ! -f .env ]; then
  echo "缺少 backend/.env，先 cp .env.example .env 并填 SMTP/密钥"
  exit 1
fi

echo "==> LanOS backend: http://0.0.0.0:8000  (docs /docs)"
exec uvicorn app.main:app --host 0.0.0.0 --port 8000
