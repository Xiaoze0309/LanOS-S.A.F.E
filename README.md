# LanOS S.A.F.E

面向多站点的流量与安全控制台 —— **Android 客户端**。

在手机上查看站点流量、管理域名、调整 WAF 规则、接入 Cloudflare，不必打开电脑。

![icon](app/src/main/res/drawable-nodpi/ic_logo.png)

## 特性

- **设计语言**：Miuix（HyperOS 风）与 Material 3 实时切换，弹簧动效、悬浮胶囊导航、可拖动的平滑流量图
- **账号**：邮箱验证码注册、JWT 登录、忘记密码、双因素（Authenticator / 邮箱验证码）
- **Microsoft 登录**：可选，设备码流程，免去密码
- **域名**：DNS TXT 或 `/.well-known/` 文件校验归属
- **流量**：30m / 1h / 3h / 6h / 12h / 24h / 1m / 3m / 6m / 1y 十档区间
- **Cloudflare**：一键接入 Zone、Under Attack 开关、下发 WAF 规则
- **WAF**：规则启停、自定义正则、攻击事件流
- **合规**：内置服务协议与隐私政策

## 安装

从 **Releases** 下载 `app-release-signed.apk`（v2 + v3 签名）。

```
包名      com.lanos.safe
minSdk    24
targetSdk 35
```

首次打开后：**设置 → 服务端** 填入后端地址（如 `https://safe.example.com`）。

> 这是客户端，需要配套服务端。部署方式见 [docs/OPERATIONS.md](docs/OPERATIONS.md)。

## 构建

```bash
./gradlew :app:assembleDebug
# 产物 app/build/outputs/apk/debug/app-debug.apk
```

Termux 里可直接出包（使用 Termux 仓库的 aapt2）：

```bash
bash scripts/termux-build.sh
```

## 技术栈

Kotlin 2.0 · Jetpack Compose · Material 3 · Coroutines
（配套服务端为 FastAPI + MariaDB，见 `docs/`）

## 文档

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 端到端架构、数据模型、接口清单
- [docs/OPERATIONS.md](docs/OPERATIONS.md) — 服务端部署、Cloudflare / 邮件 / 域名配置

## License

[MIT](LICENSE) © 2026 Xiaoze0309
