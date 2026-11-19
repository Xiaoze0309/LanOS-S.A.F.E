#!/bin/sh
# Alpine / Podroid 版：一条命令起 MariaDB + FastAPI
# 用法： sh scripts/start-backend-alpine.sh
set -eu

cd "$(dirname "$0")/.."
say() { printf '\n==> %s\n' "$*"; }

say "安装依赖（apk）"
apk add --no-cache python3 py3-pip py3-virtualenv mariadb mariadb-client \
  build-base python3-dev libffi-dev openssl-dev rust cargo curl

say "准备 MariaDB 目录"
mkdir -p /run/mysqld /var/log/mysql
chown -R mysql:mysql /run/mysqld /var/log/mysql /var/lib/mysql 2>/dev/null || true

if [ ! -d /var/lib/mysql/mysql ]; then
  say "初始化数据目录"
  mariadb-install-db --user=mysql --datadir=/var/lib/mysql \
    --auth-root-authentication-method=normal --skip-test-db
fi

if ! mariadb-admin ping >/dev/null 2>&1; then
  say "启动 MariaDB"
  if command -v rc-service >/dev/null 2>&1 && [ -x /etc/init.d/mariadb ]; then
    # Alpine 自带 OpenRC：用服务脚本，不用手写参数
    rc-update add mariadb default >/dev/null 2>&1 || true
    rc-service mariadb start || true
  else
    # 没有 OpenRC 时退回直接拉进程
    setsid mariadbd --user=mysql --datadir=/var/lib/mysql \
      --socket=/run/mysqld/mysqld.sock --pid-file=/run/mysqld/mysqld.pid \
      --bind-address=127.0.0.1 --port=3306 --skip-networking=0 \
      --log-error=/var/log/mysql/error.log \
      </dev/null >/var/log/mysql/stderr.log 2>&1 &
  fi
  # 上次非正常关闭时 InnoDB 要做崩溃恢复，5 秒常常不够，最多等 60 秒
  i=0
  while [ "$i" -lt 60 ]; do
    mariadb-admin ping >/dev/null 2>&1 && break
    sleep 1
    i=$((i + 1))
  done
fi

if ! mariadb-admin ping >/dev/null 2>&1; then
  echo "MariaDB 没起来。排查："
  echo "  进程： $(pgrep -a mariadbd 2>/dev/null || echo '没在跑')"
  echo "  端口： $(netstat -lnt 2>/dev/null | grep 3306 || echo '3306 没监听')"
  echo "  目录： $(ls -ld /var/lib/mysql /run/mysqld 2>&1)"
  echo "--- 最后 30 行日志 ---"
  tail -30 /var/log/mysql/error.log 2>/dev/null || tail -30 /var/log/mysql/stderr.log
  exit 1
fi

say "建库 + 授权 root 空密码走 127.0.0.1"
mariadb -uroot < scripts/bootstrap.sql

say "Python 环境"
[ -f .env ] || cp .env.example .env
python3 -m venv .venv 2>/dev/null || virtualenv .venv
. .venv/bin/activate
pip install -q --upgrade pip
say "装依赖（musl 上没有预编译 wheel 时会现场编译，第一次可能 5-15 分钟）"
pip install -q -r requirements.txt

say "启动后端 http://0.0.0.0:8000"
exec uvicorn app.main:app --host 0.0.0.0 --port 8000
