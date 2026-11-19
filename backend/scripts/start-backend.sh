#!/usr/bin/env bash
# 在 Debian 13 / Podroid 里一条命令启动后端（MariaDB + FastAPI）
# 用法： bash scripts/start-backend.sh
set -euo pipefail

cd "$(dirname "$0")/.."
log() { printf '\n==> %s\n' "$*"; }
as_root() { if [ "$(id -u)" = "0" ]; then "$@"; else sudo "$@"; fi; }

log "安装依赖（MariaDB / Python / 编译工具）"
as_root apt-get update
as_root apt-get install -y --no-install-recommends \
  mariadb-server mariadb-client python3 python3-venv python3-dev build-essential curl

# 容器里常见：这个钩子会让 service/invoke-rc.d 拒绝启动
as_root rm -f /usr/sbin/policy-rc.d

log "准备 MariaDB"
as_root mkdir -p /run/mysqld /var/log/mysql
as_root chown -R mysql:mysql /run/mysqld /var/log/mysql /var/lib/mysql || true
if [ ! -d /var/lib/mysql/mysql ]; then
  as_root mariadb-install-db --user=mysql --datadir=/var/lib/mysql \
    --auth-root-authentication-method=normal --skip-test-db
fi

if ! mariadb-admin ping >/dev/null 2>&1; then
  log "启动 MariaDB"
  as_root /etc/init.d/mariadb start >/dev/null 2>&1 || true
  sleep 3
fi
if ! mariadb-admin ping >/dev/null 2>&1; then
  log "init 脚本没起来，直接拉起 mariadbd"
  as_root nohup mariadbd --user=root --datadir=/var/lib/mysql \
    --socket=/run/mysqld/mysqld.sock --pid-file=/run/mysqld/mysqld.pid \
    --bind-address=127.0.0.1 --port=3306 --innodb-buffer-pool-size=64M \
    --log-error=/var/log/mysql/error.log >>/var/log/mysql/stderr.log 2>&1 &
  sleep 5
fi
mariadb-admin ping >/dev/null 2>&1 || {
  echo "MariaDB 还是没起来，看 /var/log/mysql/error.log"; exit 1; }

log "建库 + 授权 root 空密码走 127.0.0.1"
mariadb -uroot < scripts/bootstrap.sql

log "Python 依赖"
[ -f .env ] || cp .env.example .env
python3 -m venv .venv
# shellcheck disable=SC1091
. .venv/bin/activate
pip install -q --upgrade pip
pip install -q -r requirements.txt

log "启动后端： http://0.0.0.0:8000  （文档 /docs）"
echo "App 里「设置 → 服务端」填 http://127.0.0.1:8000，点测试连接"
exec uvicorn app.main:app --host 0.0.0.0 --port 8000
