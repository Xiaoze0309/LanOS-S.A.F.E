-- LanOS S.A.F.E 数据库初始化（Debian 13 / MariaDB 11.8）
-- 用法：mariadb -uroot < scripts/bootstrap.sql

CREATE DATABASE IF NOT EXISTS lanos_safe
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- root 走 TCP 且密码为空（与 .env 里 mysql+asyncmy://root:@127.0.0.1:3306/lanos_safe 对应）
ALTER USER 'root'@'localhost' IDENTIFIED VIA mysql_native_password USING PASSWORD('');
CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED VIA mysql_native_password USING PASSWORD('');
GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
FLUSH PRIVILEGES;

-- 可选：更安全的做法是建专用账号，然后把 .env 里的 root 换掉
-- CREATE USER IF NOT EXISTS 'lanos'@'127.0.0.1' IDENTIFIED BY '换成强密码';
-- GRANT ALL PRIVILEGES ON lanos_safe.* TO 'lanos'@'127.0.0.1';

SELECT VERSION() AS mariadb_version, CURRENT_USER() AS current_account;
