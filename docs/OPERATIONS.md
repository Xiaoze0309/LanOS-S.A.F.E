# 部署与运维

## 1. 后端

### Alpine / Podroid

```sh
cd backend
cp .env.example .env
sh scripts/mariadb.sh start          # 没有 OpenRC 也能用
sh scripts/start-backend-alpine.sh   # 首次：apk + venv + pip
sh scripts/run.sh                    # 之后：直接起 uvicorn
```

MariaDB 起不来的四个常见原因：以 root 直接跑（缺 `--user=mysql`）、`/run/mysqld` 不存在、
musl 下 InnoDB AIO（加 `--innodb-use-native-aio=0`）、数据目录未初始化（`mariadb-install-db`）。
`mariadb.sh` 已处理前三项，并会打印最后 25 行日志。

有 OpenRC 时也可以用服务：

```sh
rc-service mariadb start
cp deploy/openrc/lanos-backend /etc/init.d/lanos && chmod +x /etc/init.d/lanos
rc-update add lanos default
```

### Debian

```sh
cd backend && bash scripts/start-backend.sh
```

### Docker

```sh
docker compose up -d --build     # 自带 MariaDB，Win/Mac/Linux 通用
```

### .env 关键项

| 键 | 说明 |
| --- | --- |
| `MYSQL_URL` | `mysql+asyncmy://user:pass@127.0.0.1:3306/lanos_safe` |
| `SECRET_KEY` | 令牌签名密钥，正式环境必须换随机值 |
| `DEV_MODE` | `true` 时验证码回传客户端（仅本地）；对外必须 `false` |
| `SMTP_*` | 真实发信，见第 5 节 |
| `MS_CLIENT_ID` | 启用 Microsoft 登录 |
| `INGEST_KEY` | 日志上报接口的共享密钥 |

建议为每个项目建独立数据库与账号：

```sql
CREATE DATABASE lanos_safe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'lanos'@'127.0.0.1' IDENTIFIED BY '强密码';
GRANT ALL PRIVILEGES ON lanos_safe.* TO 'lanos'@'127.0.0.1';
```

## 2. 域名接入

添加域名后二选一：

| 方式 | 做法 |
| --- | --- |
| DNS TXT | 在 `_lanos-verify.<域名>` 加 TXT，值为 App 显示的 token |
| 文件 | `https://<域名>/.well-known/lanos-verify.txt` 内容为 token |

注意：

- TXT 名称**带下划线**，输入时不要带引号（Cloudflare 会自动加）
- 文件方式要求域名有 A/CNAME 且能对外提供 HTTP
- 用 Cloudflare 代理时，校验文件路径建议绕缓存或加白

## 3. Cloudflare 接入（一键）

App → 域名卡片 → Cloudflare 监控 → 填 **Zone ID + API Token**。

Token 权限（按需勾选，资源只勾该域名）：

| 权限 | 用途 |
| --- | --- |
| `Zone.Analytics:Read` | 拉小时级流量（必需） |
| `Zone.Zone:Read` | 校验 Zone ID（必需） |
| `Zone.WAF:Edit` | 下发自定义规则 |
| `Zone Settings:Edit` | 切换 Under Attack |

接入后后端每 30 分钟同步一次；也可在弹窗里手动同步。免费版自定义规则上限 **5 条**，
`body` 类规则 CF 看不到会被跳过。

## 4. 流量数据来源

| 来源 | 能看到 | 接入方式 |
| --- | --- | --- |
| 演示数据 | 假数据 | 首页空态按钮 |
| Cloudflare | 边缘请求数/流量 | 第 3 节，自动同步 |
| Nginx | 每条请求的 IP/UA/路径 | `deploy/nginx/lanos.conf` + `deploy/shipper/lanos_shipper.py` |
| 任何网关 | 任意 | `POST /api/v1/ingest`（单条）、`/api/v1/traffic/aggregate`（批量） |

Cloudflare 回源时源站看到的是 CF 节点 IP，必须配 `real_ip_header CF-Connecting-IP`，
否则按 IP 的限速会误伤。

## 5. 邮件

```sh
cd backend && . .venv/bin/activate
python scripts/test_mail.py 你的邮箱@example.com
```

| 服务 | 配置 |
| --- | --- |
| Resend | `smtp.resend.com:465 ssl`，用户 `resend`，密码 API Key；需验证域名 |
| QQ | `smtp.qq.com:465 ssl`，密码是授权码 |
| Brevo | `smtp-relay.brevo.com:587 starttls`，用户名形如 `xxx@smtp-brevo.com` |
| Gmail | `smtp.gmail.com:587 starttls`，应用专用密码 |

`DEV_MODE=false` 且 SMTP 配置错误时，注册接口直接返回 502，不会静默降级。

## 6. Microsoft 登录

1. Azure/Entra 门户 → App registrations → New registration
2. 账户类型选「任何组织目录 + 个人 Microsoft 帐户」
3. Authentication → **Allow public client flows = Yes**（设备码流程，无需 redirect URI）
4. 复制 Application (client) ID → `MS_CLIENT_ID`
5. 重启后端，登录页出现「使用 Microsoft 账号登录」

## 7. 并发与阻塞

| 操作 | 处理 |
| --- | --- |
| SMTP（smtplib） | 专用线程池 4 线程 + 信号量 3 并发 + 25s 超时 |
| PBKDF2 密码哈希 | `hash_password_async` → 线程池 |
| 域名校验 / CF / 微软 OAuth | `asyncio.to_thread` |
| 数据库 | asyncmy + SQLAlchemy async |

不要开 `uvicorn --workers N`：CF 同步循环会重复跑，进程内限流计数不共享。

## 8. WAF 能力边界

| 攻击 | 现在 |
| --- | --- |
| SQLi/XSS/穿越/扫描器 | 规则判定 + 事件；可导出 Nginx 片段、可下发 CF |
| 单 IP CC | ingest 侧 60s 窗口（180 次/分），建议配合 Nginx `limit_req` |
| 分布式 CC | 需 Cloudflare Rate Limiting / Bot Management |
| 大规模 DDoS | 应用层挡不住，只能靠边缘清洗 |

自测：`python scripts/waf_selftest.py`（需要 `TOKEN`）。

## 9. 构建与发布

- Termux：`bash scripts/termux-build.sh`（使用 Termux 仓库的 aapt2，产物进 `Lanos4_byDSH/app/`）
- Debian：`bash scripts/build_apk.sh`
- 签名：`./gradlew :app:assembleRelease` 后 `bash scripts/sign_release.sh release.jks`
- CI：push 触发 `Build Android APK`；`Build desktop backend` 用 PyInstaller 出三平台后端

桌面 **GUI** 尚未实现，需要 Compose Multiplatform 重构，不在当前范围。

## 10. 常见故障

| 现象 | 排查 |
| --- | --- |
| `2003 Can't connect ... 3306` | MariaDB 没跑；`sh scripts/mariadb.sh status` |
| 日志显示 `port: 0` | 配置里有 `skip-networking`，或启动参数缺 `--port` |
| App「网络异常」 | 后端与 App 网络不通；QEMU 环境用 cloudflared |
| 域名验证失败 | `dig +short TXT _lanos-verify.<域名>`；确认装了 `dnspython` |
| MIUI 安装被取消 | 关闭纯净模式、允许「安装未知应用」、关安装扫描 |
