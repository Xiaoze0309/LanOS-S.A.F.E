#!/usr/bin/env bash
# 启动后端：首次运行会建虚拟环境并装依赖
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d .venv ]; then
  python3 -m venv .venv
fi
# shellcheck disable=SC1091
. .venv/bin/activate

pip install -q --upgrade pip
pip install -q -r requirements.txt

if [ ! -f .env ]; then
  cp .env.example .env
  echo "已生成 backend/.env（DEV_MODE=true，验证码会回传给 App）"
fi

exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
