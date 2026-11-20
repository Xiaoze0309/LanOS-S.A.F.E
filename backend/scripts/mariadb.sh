#!/bin/sh
# Alpine 下管理 MariaDB（不需要 OpenRC）
#   sh scripts/mariadb.sh start|stop|restart|status
set -eu

SOCK=/run/mysqld/mysqld.sock
PID=/run/mysqld/mysqld.pid
DATADIR=/var/lib/mysql
LOG=/var/log/mysql/error.log

log() { printf '%s\n' "$*"; }

is_up() { mariadb-admin ping >/dev/null 2>&1; }

prepare() {
  mkdir -p /run/mysqld /var/log/mysql
  chown -R mysql:mysql /run/mysqld /var/log/mysql "$DATADIR" 2>/dev/null || true
  if [ ! -d "$DATADIR/mysql" ]; then
    log "初始化数据目录…"
    mariadb-install-db --user=mysql --datadir="$DATADIR" \
      --auth-root-authentication-method=normal --skip-test-db
  fi
}

start() {
  if is_up; then
    log "MariaDB 已在运行"
    return 0
  fi
  prepare

  # 清理上次留下的 pid，避免误判
  [ -f "$PID" ] && kill -0 "$(cat "$PID")" 2>/dev/null || rm -f "$PID"

  log "启动 mariadbd…"
  setsid mariadbd --user=mysql --datadir="$DATADIR" \
    --socket="$SOCK" --pid-file="$PID" \
    --bind-address=127.0.0.1 --port=3306 --skip-networking=0 \
    --log-error="$LOG" </dev/null >/var/log/mysql/stderr.log 2>&1 &

  i=0
  while [ "$i" -lt 60 ]; do
    is_up && break
    sleep 1
    i=$((i + 1))
  done

  if is_up; then
    log "已启动（等待 ${i}s）"
    log "socket: $SOCK"
    netstat -lnt 2>/dev/null | grep 3306 || log "警告：3306 还没监听"
  else
    log "启动失败，最后 25 行日志："
    tail -25 "$LOG" 2>/dev/null || tail -25 /var/log/mysql/stderr.log
    exit 1
  fi
}

stop() {
  if ! is_up; then
    log "MariaDB 没在运行"
    return 0
  fi
  log "优雅关闭…"
  mariadb-admin -uroot shutdown >/dev/null 2>&1 || true
  i=0
  while [ "$i" -lt 30 ]; do
    is_up || break
    sleep 1
    i=$((i + 1))
  done
  if is_up; then
    log "优雅关闭超时，发送 TERM"
    [ -f "$PID" ] && kill "$(cat "$PID")" 2>/dev/null || pkill -f mariadbd 2>/dev/null || true
    sleep 3
  fi
  is_up && { log "还在运行"; exit 1; }
  log "已停止"
}

status() {
  if is_up; then
    log "运行中"
    mariadb-admin -uroot status 2>/dev/null || true
    netstat -lnt 2>/dev/null | grep 3306 || log "（3306 未监听）"
  else
    log "未运行"
    [ -f "$PID" ] && log "存在残留 pid 文件：$PID"
  fi
}

case "${1:-start}" in
  start)   start ;;
  stop)    stop ;;
  restart) stop; start ;;
  status)  status ;;
  *) echo "用法: sh scripts/mariadb.sh start|stop|restart|status"; exit 2 ;;
esac
