# LanOS S.A.F.E v2

多站点流量与安全网关：**Kotlin(Compose) + FastAPI + MySQL/MariaDB + Uvicorn**。
名字是你取的，架构也是——这个仓库按它重写了一遍。

- Android 端：Miuix（HyperOS 风）设计系统 + Material 3 双外观实时切换，弹性动效、悬浮胶囊导航、指纹式流量图表
- 后端：JWT 登录、邮箱验证码注册、邮箱 + Microsoft Authenticator(TOTP) 双因素、域名归属校验、10 档流量区间、WAF 规则引擎与攻击事件
- 数据库：只有 MySQL/MariaDB（`mysql+asyncmy`），**没有 SQLite 分支**

> 图标取自你给的 XZ logo，`tools/make_icon.py` 用纯标准库裁掉白边生成 512×512 自适应图标。

---

## 1. 目录

```
LanOS-SAFE-v2/
├─ app/                      Android（Compose，单模块）
│  └─ src/main/java/com/lanos/safe/
│     ├─ MainActivity.kt      认证门禁 + 四个 Tab + 主题状态
│     ├─ data/LanosApi.kt     网络层（HttpURLConnection + org.json，零额外依赖）
│     ├─ ui/design/           Tokens.kt（配色/动效/圆角）、Miuix.kt（组件库）
│     ├─ ui/components/       悬浮导航、流量面积图
│     └─ ui/screens/          Home / Domain / Waf / Settings / Auth
├─ backend/
│  ├─ app/core/               config、security（PBKDF2+JWT+TOTP，全标准库）、mailer
│  ├─ app/db/                 models.py、session.py、seed.py（内置规则）
│  ├─ app/services/           waf.py（规则引擎）、buckets.py（时间聚合）、verify.py、ratelimit.py
│  ├─ app/api/v1/             auth / domains / waf / system
│  ├─ scripts/bootstrap.sql   建库 + root 空密码走 TCP
│  └─ run.sh                 一键：venv + 依赖 + uvicorn --reload
└─ tools/make_icon.py         图标生成（无 Pillow 环境也能跑）
```

## 2. 后端：三步跑起来（Debian 13 / Podroid）

```bash
# ① MariaDB（只装一次；容器没有 systemd）
apt-get update && apt-get install -y --no-install-recommends mariadb-server
rm -f /usr/sbin/policy-rc.d
mkdir -p /run/mysqld && chown -R mysql:mysql /run/mysqld
[ -d /var/lib/mysql/mysql ] || mariadb-install-db --user=mysql --datadir=/var/lib/mysql \
  --auth-root-authentication-method=normal --skip-test-db
/etc/init.d/mariadb start

# ② 建库 + 授权（root 空密码走 127.0.0.1）
cd backend && mariadb -uroot < scripts/bootstrap.sql

# ③ 起服务（自动建 venv、装依赖、建表、写内置 WAF 规则）
./run.sh
```

启动日志里出现 `数据库就绪：127.0.0.1:3306/lanos_safe` 即成功。
数据库没起来时不会直接抛 2003，而是重试 10 次并打印排查提示。

自检：

```bash
curl -s localhost:8000/api/v1/system/health
# {"ok":true,"service":"lanos-safe","version":"2.0.0","database":"up",...}
```

### Alpine / Podroid（musl）

Podroid 的容器是 **Alpine**，不是 Debian，`apt-get` 那套脚本不能用。用这个：

```sh
cd backend && sh scripts/start-backend-alpine.sh
```

它做的事和 Debian 版一样，只是换成 `apk`，并且直接拉 `mariadbd` 进程（Alpine 没有 systemd）。

Alpine 上 MariaDB 起不来，基本是这四个原因：

| 现象 | 原因 / 处理 |
| --- | --- |
| `Please consult the Knowledge Base to find out how to run mysqld as root` | 直接以 root 跑 `mariadbd` 被拒 → 加 `--user=mysql` |
| `Can't create/write to file '/run/mysqld/mysqld.pid'` | 没建目录 → `mkdir -p /run/mysqld && chown mysql:mysql /run/mysqld` |
| `io_setup() failed` / `Cannot initialize AIO` | musl 下 InnoDB 原生 AIO 不可用 → 加 `--innodb-use-native-aio=0` |
| `Table 'mysql.user' doesn't exist` | 数据目录没初始化 → `mariadb-install-db --user=mysql --datadir=/var/lib/mysql` |

Python 侧：musl 没有 manylinux wheel，`pydantic-core` / `asyncmy` 可能会现场编译，脚本里已经装了
`build-base python3-dev rust cargo`，第一次 `pip install` 慢是正常的。

**APK 构建不要在 Alpine 里做**：Google 的 aapt2 是 glibc 二进制，musl 跑不了。
APK 继续在 Termux 里用 `scripts/termux-build.sh` 编（那边有 Termux 仓库的 aapt2）。

## 3. 账号流程（邮箱验证 + 2FA）

### 先把后端跑起来

```bash
cd backend && bash scripts/start-backend.sh
# 自动装 MariaDB / Python 依赖、建库授权、起 uvicorn
```

验证：`curl -s localhost:8000/api/v1/system/health` 返回 `"database":"up"`。

### App 里注册 / 登录

1. 「设置 → 服务端」填 `http://127.0.0.1:8000`（同局域网就填电脑 IP），点「测试连接」
2. 注册：邮箱随便填一个格式正确的（如 `me@lanos.local`），密码 ≥ 8 位
3. 后端 `DEV_MODE=true` 时，**验证码会直接显示在页面的黄色标签里**（`本地调试码：123456`），不需要真邮箱
4. 填进去就进首页；首页空态点「先生成一份演示数据」，立刻有一年流量和攻击事件

要真发邮件，在 `backend/.env` 填 `SMTP_HOST / SMTP_PORT / SMTP_USER / SMTP_PASSWORD`（QQ 邮箱用授权码，465/SSL）。
2FA 在「设置 → 双因素认证」里开启，用 Microsoft Authenticator 扫 secret 或手输。

常见失败：

| 现象 | 原因/处理 |
| --- | --- |
| 登录页提示「网络异常」 | 后端没起，或 App 里的地址不对；先 `curl` health |
| 提示「邮箱尚未验证」 | 注册后没走验证码步骤，回注册页重来 |
| 收不到验证码 | 没配 SMTP 也没关系，看页面上的「本地调试码」 |
| 提示「邮箱或密码错误」 | 密码 < 8 位被前端拦过，或注册时邮箱带了空格 |



1. App 注册 → 后端写 `email_codes` 并调用 SMTP 发 6 位码
2. `DEV_MODE=true` 时接口会额外返回 `dev_code`，App 直接用 `PillTag` 显示，**不用配邮箱也能自测**
3. 验证通过 → 拿到 `access_token`（1 小时）+ `refresh_token`（30 天）
4. 设置页「双因素认证」→ 生成密钥与备份码 → 用 **Microsoft Authenticator** 扫码或手输 secret → 输入 6 位码开启
5. 之后登录：密码通过后返回 `mfa_required`，App 进入第二步，可切「Authenticator 验证码」或「邮箱验证码」

想真发邮件，在 `backend/.env` 填 `SMTP_HOST/PORT/USER/PASSWORD`（QQ 邮箱用授权码 + 465/SSL）。

## 4. 构建与分发

### Android（APK）

```bash
./gradlew :app:assembleDebug     # 需要 JDK 17 + Android SDK 35
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

没有本地工具链也没关系：push 到 `main`（或在 Actions 页手动 Run workflow），
`.github/workflows/build.yml` 会出包并上传名为 `app-debug-apk` 的 Artifact。

### 在手机容器里出包（Podroid / Debian）

```bash
bash scripts/build_apk.sh     # 装 JDK17 + cmdline-tools + platform 35，直接产出并校验签名
```

debug APK 由 AGP 自动用 `~/.android/debug.keystore` 签名，可以直接 `adb install`。
要出正式版：`./gradlew :app:assembleRelease` 后 `bash scripts/sign_release.sh release.jks`。

### 在 Termux 里直接出 APK（已实测通过）

```bash
bash scripts/termux-build.sh
# 产物：app/build/outputs/apk/debug/app-debug.apk（debug 证书已签名），并自动复制到 /sdcard/Download
```

关键点：**Termux 仓库里有 `aapt2`**（`16.0.0.4-4`，Android 16 构建，PIE + bionic），
用 `-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2` 让 AGP 用它，就绕开了 Maven 里那个
只在 glibc x86-64 上能跑的官方 aapt2。

脚本会自动处理这几个坑（每个都是实测踩出来的）：

| 坑 | 处理 |
| --- | --- |
| `pkg install openjdk-17` 失败（deb 内路径写死 `/data/data/com.termux`） | `apt-get download` + `dpkg-deb -x`，手动搬到当前前缀 |
| JDK 的 `lib/security/cacerts` 是断链 → `trustAnchors must be non-empty` | 用 `$PREFIX/etc/tls/cert.pem` 生成 PKCS12 信任库 |
| `services.gradle.org` 连不上 | Gradle 8.7 走腾讯镜像 |
| `java.io.tmpdir` 指向不存在的 `com.termux/usr/tmp` | `-Djava.io.tmpdir=$HOME/tmp` |
| 手机内存小 | `-Xmx1536m`、`--max-workers=2`、Kotlin in-process |

### Windows / Linux / macOS（后端）

后端是 Python，**不能从 Android 交叉编译**，但三条路都能拿到可运行产物：

| 方式 | 命令 / 操作 | 适用 |
| --- | --- | --- |
| Docker（推荐） | `docker compose up -d --build` | Win/Mac/Linux 完全一致，自带 MariaDB |
| 单文件可执行程序 | Actions → `Build desktop backend` → 下载 `lanos-backend-{linux-x64,windows-x64,macos-x64,macos-arm64}` | 不想装 Python 的机器 |
| 源码直跑 | `cd backend && ./run.sh` | 已装 Python 3.11+ |

说明：

- 单文件版首次运行会在同目录自动生成 `.env`（DEV_MODE=true），数据库地址默认 `127.0.0.1:3306`
- macOS/Linux 下载后先 `chmod +x lanos-backend`；Windows 直接双击 `lanos-backend.exe`
- Docker 方式里数据库容器名是 `db`，后端已用 `mysql+asyncmy://root:@db:3306/lanos_safe` 连接，不用改配置
- 桌面 **GUI** 目前没有：Android 端是 Compose（Android-only API），要做桌面窗口需要引入 Compose Multiplatform（Phase 2）。桌面端现在提供的是后端服务 + `/docs` 交互文档

## 5. 域名接入与流量

添加域名后拿到两种校验方式，任选其一：

| 方式 | 内容 |
| --- | --- |
| DNS TXT | `_lanos-verify.<你的域名>` → 值就是 App 里那串 token |
| 文件 | `http://<域名>/.well-known/lanos-verify.txt` → 文件内容为 token |

点「立即验证」，后端 `app/services/verify.py` 会并发查 DNS(TXT) 与 HTTP 文件，任一通过即 `verified`。

流量区间：`30m / 1h / 3h / 6h / 12h / 24h / 1m / 3m / 6m / 1y`，后端按区间选桶宽（1 分钟 → 5/10/15/30 分钟 → 1 天 → 30 天），SQL 里 `FLOOR(UNIX_TIMESTAMP(ts)/bucket)` 聚合后补齐空桶，前端画平滑面积图。

真实流量通过 `POST /api/v1/ingest` 上报（`X-Ingest-Key` 与 `.env` 的 `INGEST_KEY` 一致）：

```bash
curl -X POST localhost:8000/api/v1/ingest \
  -H 'Content-Type: application/json' -H 'X-Ingest-Key: change-me-ingest-key' \
  -d '{"domain":"example.com","path":"/login","method":"POST","ua":"Mozilla/5.0","ip":"1.2.3.4","bytes":812}'
# {"verdict":"allow","score":0,...}
```

Nginx 侧可以在 `log_by_lua_block`（OpenResty）或 `access_log` + 文件采集器里批量转发到该接口。

## 6. WAF

- 内置 8 条规则：SQL 注入、XSS、路径穿越、命令注入、敏感文件探测、扫描器 UA、恶意爬虫、命令行工具；首次启动写入 `waf_rules`
- 判定：命中 `block` > `challenge` > `log`；分数按严重度累加；空 UA、异常方法、畸形路径有内建启发式
- 单 IP 滑动窗口（默认 180 次/分钟）识别 CC，超限直接 block 并落事件
- App 里可启停规则、加自定义正则、看实时事件与 TOP 攻击/TOP IP
- `GET /api/v1/waf/edge/nginx` 导出 Nginx 片段：控制面在这里，**真正的拦截点在边缘**

### 能力矩阵（说实话版）

| 攻击类型 | 本项目现在能做到 | 够不够 |
| --- | --- | --- |
| SQLi / XSS / 路径穿越 / 命令注入 / 扫描器 | 规则命中 → 判定 + 落事件；可导出 Nginx 片段 | 作为「判定 + 留痕 + 规则库」够用；真正拦截要贴到 Nginx |
| 敏感文件探测（`/.env`、`/wp-login.php`） | 同上 | 同上 |
| 单 IP 高频 CC | ingest 侧 60 秒窗口计数（默认 180 次/分） | 只对「上报到本服务」的流量有效；更稳的是 Nginx `limit_req`（见 `deploy/nginx/lanos-ratelimit.conf`） |
| 分布式 CC / 代理池 | 无（每个 IP 独立计数） | 需要 Cloudflare Rate Limiting / Bot Management |
| 大规模 DDoS（L3/L4、反射放大） | 无 | **任何应用层代码都挡不住**，只能靠 CDN/清洗中心把流量截在边缘 |
| 慢速攻击 / 连接耗尽 | 无 | Nginx `limit_conn` + 短超时（已给配置） |
| 0day / 编码绕过 | 无 | 正则规则是人工维护，绕过是常态 |

### 一条能真正生效的链路

```
浏览器 → Cloudflare（挡 DDoS/机器人，Under Attack 模式）
        → Nginx（limit_req/limit_conn + 本项目导出的 if 规则 + real_ip 还原）
        → 源站
                        ↑
                 LanOS：规则库 + 判定 + 统计 + 事件（不参与转发）
```

`python3 scripts/waf_selftest.py` 可以拿 10 条样本直接验证判定（需要 `TOKEN`）。

**必须明确的一点**：App/后端本身不能替你挡住打到域名的流量。要做到「防护该网页」，得把域名解析到一台装了 Nginx/OpenResty 的机器（或 Cloudflare 之类的边缘），把这份规则下发过去；本项目的定位是**控制面 + 规则引擎 + 观测**。这也是路线图里 Phase 3 的内容。

## 7. 监控数据从哪来（Nginx / Cloudflare）

App 里的曲线不是自己“探测”出来的，必须有数据源推进来。四种来源：

| 数据源 | 能看到什么 | 怎么接 |
| --- | --- | --- |
| 演示数据 | 假数据，只为看 UI | 首页空态点「先生成一份演示数据」 |
| Nginx（源站） | 每个请求的真实路径/UA/IP/字节 | [`deploy/nginx/lanos.conf`](deploy/nginx/lanos.conf) + [`deploy/shipper/lanos_shipper.py`](deploy/shipper/lanos_shipper.py) |
| Cloudflare（边缘） | 边缘请求数、流量（小时级） | [`deploy/cloudflare/pull_cloudflare.py`](deploy/cloudflare/pull_cloudflare.py)，建议每小时 cron |
| 任何自建网关 | 任意 | `POST /api/v1/ingest`（单条）或 `/api/v1/traffic/aggregate`（批量） |

Nginx 接入三步：

```bash
# 1) 把 lanos.conf 里的 log_format / real_ip / access_log 贴进 nginx.conf
# 2) 先还原 Cloudflare 的真实 IP，否则 CC 规则会按 CF 节点 IP 误伤
curl -s https://www.cloudflare.com/ips-v4 | sed 's/^/set_real_ip_from /; s/$/;/' > /etc/nginx/conf.d/lanos-realip.conf
nginx -t && nginx -s reload

# 3) 起日志转发（无第三方依赖）
cd deploy/shipper
LANOS_URL=http://127.0.0.1:8000 INGEST_KEY=你的key   nohup python3 lanos_shipper.py /var/log/nginx/lanos_access.log &
```

Cloudflare 接入：

```bash
export CF_TOKEN=xxx CF_ZONE_ID=xxx DOMAIN=example.com
export LANOS_URL=http://127.0.0.1:8000 INGEST_KEY=你的key
python3 deploy/cloudflare/pull_cloudflare.py 24
```

**边界（别误会）**：

- 现在**不会**自动改你的 Nginx / Cloudflare 配置，规则是「导出片段 + 你手动贴」；API 写入属于 Phase 3
- 没有 uptime/拨测、没有证书到期监控
- Cloudflare 的「被拦截数」需要另外的 `firewallEventsAdaptive` 数据集，目前只拉了请求数与流量
- 域名校验走 DNS TXT 最稳（TXT 不经代理）；用 HTTP 文件方式时，记得在 CF 里给 `/.well-known/lanos-verify.txt` 加白/绕缓存

## 7.5 只用 Cloudflare（无源站 / 免费域名）

DNS 全托管在 Cloudflare、没有自己的 Nginx（比如 DigitalPlat 免费域名），走这条：

1. 域名 NS 指向 Cloudflare，等状态 **Active**
2. App 添加域名 → 复制 `_lanos-verify.<域名>` 的 TXT 值
3. CF DNS 加这条 TXT → 回 App 点「立即验证」
4. CF 建 API Token（只勾这个域名）：
   - `Zone.Analytics:Read`（拉流量，必需）
   - `Zone.Zone:Read`（校验 Zone ID，必需）
   - `Zone.WAF:Edit`（下发规则才需要）
   - `Zone Settings:Edit`（Under Attack 才需要）
5. App → 域名卡片 →「Cloudflare 监控」→ 填 Zone ID + Token → 验证并接入

接上之后：

- 曲线来自 **CF 边缘统计**，每 30 分钟自动同步，不用跑脚本
- 「Under Attack」一键切安全级别；「下发 WAF 规则」把启用的 block 规则写进 CF
- 免费版自定义规则上限 **5 条**，`body` 类规则 CF 看不到会跳过
- 这种模式下 `deploy/nginx/*` 完全用不上

## 8. 用 Resend 发验证码（推荐）

Resend 免费版 100 封/天、3000 封/月，和 Cloudflare DNS 配合最省事。

1. resend.com 注册 → **Domains** → 添加你的域名（建议 `send.你的域名`）
2. 它给的记录全部加到 **Cloudflare DNS**：
   - `MX` / `TXT` 必须 **DNS only（灰云）**，开橙云会一直 Pending
   - 名称照抄（如 `resend._domainkey`、`send`）
3. 回 Resend 点 **Verify**，状态变绿
4. **API Keys** → 建 key（`re_` 开头，Sending 权限）
5. `backend/.env`：

```ini
SMTP_HOST=smtp.resend.com
SMTP_PORT=465
SMTP_SECURITY=ssl
SMTP_USER=resend
SMTP_PASSWORD=re_你的key
SMTP_FROM=no-reply@你的域名
```

6. 验证，再把 `DEV_MODE` 改 false：

```bash
cd backend && . .venv/bin/activate
python scripts/test_mail.py 你的邮箱@example.com
```

| 现象 | 原因 |
| --- | --- |
| `550 The from address is not verified` | `SMTP_FROM` 的域名还没验证通过 |
| `535 Authentication failed` | 用户名必须是 `resend`，密码是 API Key |
| 记录一直 Pending | Cloudflare 里开成橙云了，MX/TXT 要灰云 |

## 8.5 并发与阻塞（为什么不会卡死）

后端是单事件循环的异步服务，任何同步阻塞调用都会拖住所有请求。当前的处理：

| 操作 | 方式 |
| --- | --- |
| SMTP 发信（smtplib） | **专用线程池**（4 线程）+ 信号量（最多 3 并发）+ 25 秒超时；不在事件循环里跑 |
| PBKDF2 密码哈希（26 万轮，CPU 密集） | `hash_password_async` / `verify_password_async` → 线程池 |
| 域名校验（DNS/HTTP） | `asyncio.to_thread`，带 4–6 秒超时 |
| Cloudflare / 微软 OAuth | `asyncio.to_thread` |
| 数据库 | asyncmy + SQLAlchemy async，全程 await |

为什么不用 aiosmtplib：它确实更原生，但会多一个依赖，而且照样要限并发。现在这套
（smtplib + 专用线程池 + Semaphore + wait_for）效果等价，且能把「SMTP 服务器卡住」
的影响限制在 25 秒、3 个并发之内，其余请求正常处理。

> 不要开 `--workers` 多进程：Cloudflare 同步循环会重复跑，进程内限流计数也不共享。
> 真要扩容，先把限流/同步状态挪到 Redis。

## 9. 已经验证 / 还没验证

已验证（在本机用纯 Python 跑了单测）：

- PBKDF2 密码哈希、JWT 签发/过期/篡改/换密钥拒绝
- TOTP 对齐 RFC 6238 官方测试向量（`94287082`、`07081804`），可直接被 Authenticator 识别
- WAF：SQLi 拦截、正常请求放行、扫描器 UA 拦截、空 UA 质询、坏正则不炸、限速窗口
- 时间桶聚合：30 分钟 31 点、3 小时 37 点、1 年 13 点，数值累加正确
- 后端全量 `compileall` 通过

还没验证（当前环境没有 JDK/Android SDK，也没有 MariaDB）：

- Kotlin 未实际编译；Compose 代码按 BOM 2024.09.00 的稳定 API 写，若 IDE 报个别符号找不到，多半是 import 少了 `androidx.compose.foundation.*` 里的某个扩展
- `asyncmy` 与 MariaDB 11.8 的实连
- Nginx 片段只做了文本生成，未在真机 Nginx 上 `nginx -t`

## 10. 安全提醒（重要）

1. **你贴在对话里的 GitHub token 已经泄露**，请立刻去 GitHub → Settings → Developer settings → Personal access tokens 撤销并重建。我没有用它推送任何东西，只读了一次仓库。
2. `root` 空密码只适合本机开发；对外提供服务前，用 `scripts/bootstrap.sql` 末尾注释里的 `lanos` 专用账号 + 强密码，并把 `DEV_MODE` 改成 `false`。
3. `SECRET_KEY` 换随机值：`python3 -c "import secrets;print(secrets.token_urlsafe(48))"`。
4. 手机/容器里的 `.env` 不要提交进仓库（`.gitignore` 已包含 `.env`）。

## 11. 路线图

- Phase 1（本次）：认证 + 2FA + 域名校验 + 流量 10 档 + WAF 控制面
- Phase 2：WebSocket 实时推送、告警（邮件/Telegram）、规则命中测试器、流量留存策略
- Phase 3：OpenResty 下发、Cloudflare/Fail2Ban 联动、GeoIP 封禁、CC 指纹（JA3）
- Phase 4：多租户、审计日志、计费
