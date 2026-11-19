# 架构说明

## 1. 分层

```
┌──────────────────────────── Android (Kotlin/Compose) ────────────────────────────┐
│  ui/screens   Home · Domain · Waf · Settings · Auth                              │
│  ui/design    Tokens(配色/圆角/弹簧) + Miuix(卡片/开关/分段/对话框/进场动画)      │
│  data         LanosApi：HttpURLConnection + org.json（无 Retrofit/Ktor）          │
│  MainActivity 认证门禁（token 为空 → AuthScreen）+ NavHost + 主题持久化           │
└───────────────────────────────────┬──────────────────────────────────────────────┘
                                    │ HTTPS/HTTP JSON + Bearer
┌───────────────────────────────────▼──────────────────────────────────────────────┐
│                         FastAPI (Uvicorn, async)                                 │
│  api/v1/auth     注册/验证码/登录/刷新/2FA                                        │
│  api/v1/domains  域名 CRUD + 归属校验 + 流量区间查询                              │
│  api/v1/waf      规则 CRUD + 事件 + 统计 + 试跑 + Nginx 导出                      │
│  api/v1/system   health + demo seed + ingest（Nginx 上报）                        │
├──────────────────────────────────────────────────────────────────────────────────┤
│  services  waf(规则引擎) · buckets(时间桶) · verify(DNS/HTTP) · ratelimit         │
│  core      config(pydantic-settings) · security(PBKDF2/JWT/TOTP) · mailer(SMTP)  │
│  db        models(SQLAlchemy 2.0 async) · session(asyncmy) · seed                │
└───────────────────────────────────┬──────────────────────────────────────────────┘
                                    │
                              MariaDB 11.8
```

## 2. 数据模型

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `users` | email(unique), password_hash, is_active, totp_secret, totp_enabled, backup_codes | `is_active=false` 表示邮箱未验证 |
| `email_codes` | email, purpose(register/login), code_hash, attempts, expires_at, consumed_at | 只存 HMAC，不存明文；5 次错误作废 |
| `refresh_tokens` | user_id, token_hash(unique), expires_at, revoked_at | 刷新即轮换（旧 token 立即吊销） |
| `sites` | user_id, domain(unique), status, verify_token | domain 全局唯一，同一域名不允许两个账号抢占 |
| `traffic_points` | site_id, ts(分钟), requests, bytes, blocked | 唯一键 (site_id, ts)，上报时同分钟累加 |
| `waf_rules` | name, pattern, target, action, severity, enabled, builtin | builtin 只能停用不能删 |
| `waf_events` | site_id, ip, method, path, ua, action, rule_name, score | `site_id` 可为空 = 全局事件 |

时间统一存 **UTC naive**，避免容器时区（Asia/Shanghai）导致统计漂移。

## 3. 接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | 邮箱+密码，发验证码（DEV_MODE 回传 dev_code） |
| POST | `/api/v1/auth/register/verify` | 校验码 → 激活 + 发令牌 |
| POST | `/api/v1/auth/login` | 密码 → 令牌 或 `mfa_required` |
| POST | `/api/v1/auth/login/totp` | Authenticator 6 位码 / 备份码 |
| POST | `/api/v1/auth/login/email-code` | 发送登录邮箱码 |
| POST | `/api/v1/auth/login/email-code/verify` | 校验登录邮箱码 |
| POST | `/api/v1/auth/refresh` | 轮换刷新令牌 |
| POST | `/api/v1/auth/logout` | 吊销全部刷新令牌 |
| GET | `/api/v1/auth/me` | 当前用户 + 2FA 状态 |
| POST | `/api/v1/auth/2fa/setup` | 生成 secret + otpauth_uri + 备份码 |
| POST | `/api/v1/auth/2fa/enable` / `disable` | 开启（校验 6 位码）/ 关闭（校验密码） |
| GET/POST | `/api/v1/domains` | 列表 / 添加（幂等） |
| POST | `/api/v1/domains/{id}/verify` | DNS TXT 或 HTTP 文件校验 |
| DELETE | `/api/v1/domains/{id}` | 删除（级联流量与事件） |
| GET | `/api/v1/domains/{id}/traffic?range=` | 10 档区间聚合 |
| GET/POST/PATCH/DELETE | `/api/v1/waf/rules` | 规则管理（写入前编译正则校验） |
| GET | `/api/v1/waf/events?limit=` | 攻击事件 |
| GET | `/api/v1/waf/stats?range=` | 扫描量/拦截量/TOP 攻击/TOP IP |
| POST | `/api/v1/waf/inspect` | 规则试跑（不落库） |
| GET | `/api/v1/waf/edge/nginx` | 导出 Nginx 片段 |
| GET | `/api/v1/system/health` | 健康检查（含 DB） |
| POST | `/api/v1/system/demo/seed` | 一年份演示数据（仅 DEV_MODE） |
| POST | `/api/v1/ingest` | 日志上报（X-Ingest-Key） |

## 4. 关键设计决策

**为什么密码/JWT/TOTP 都手写而不用 passlib/pyjwt/pyotp？**
Debian on ARM 的很多轮子要现编（你之前 MySQL 已经踩过这个坑）。PBKDF2 用 `hashlib`、JWT 用 `hmac`、TOTP 用 `hmac+struct`，全部标准库，装依赖时不会卡在编译；而且这些原语本身很短，配了单测（含 RFC 6238 官方向量）。

**为什么 token 表里只存哈希？**
刷新令牌等同于密码，泄露即接管账号。存 SHA-256，校验时对比哈希；轮换时旧行立即 `revoked_at`。

**为什么流量表按分钟存、查询时再聚合？**
10 档区间跨度从 30 分钟到 1 年，预聚合要维护多套表；分钟级原始点 + SQL `FLOOR(UNIX_TIMESTAMP(ts)/bucket)` 一次查询就够，代价是 1 年档会扫较多行。数据量大了再加「小时表 + 天表」两级汇总（Phase 2）。

**为什么 WAF 有「试跑」接口？**
规则是正则，写错会误杀。`POST /waf/inspect` 让 App/curl 在不影响线上流量的情况下验证判定结果，也方便你调正则。

**为什么控制面拦截不等于网站被保护？**
请求先到你的服务器，FastAPI 才可能看到它；真实站点的入口是 Nginx。所以本项目输出的是「规则 + 观测 + 下发物」，落地拦截在边缘。这是架构上的诚实边界，不是没做完。

## 5. 威胁模型（当前实现能挡什么）

| 风险 | 现状 |
| --- | --- |
| 撞库/爆破 | 单邮箱 10 次/10 分钟限流 + 恒定时间密码校验（不存在也跑一次 PBKDF2） |
| 邮箱枚举 | 登录/注册错误信息不区分「用户不存在」与「密码错误」 |
| 验证码爆破 | 6 位码、10 分钟过期、单码 5 次尝试上限、发送限流 |
| 令牌泄露 | access 1 小时、refresh 可吊销可轮换、只存哈希 |
| 2FA 丢失 | 8 个一次性备份码（HMAC 存储，用后删除） |
| 域名抢占 | 添加前检查全局唯一；校验需控制 DNS 或站点根目录 |
| 注入/XSS/扫描器 | 规则引擎判定 + 事件留痕（真正拦截在边缘） |
| CC 攻击 | 单 IP 60 秒窗口计数（单进程内存，多 worker 需换 Redis） |

未覆盖：CSRF（纯 Bearer 无 cookie，风险低）、SSRF（校验服务会请求用户填的域名，内网地址未做黑名单——Phase 2 补）、审计日志、密钥轮换。
