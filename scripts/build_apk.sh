#!/usr/bin/env bash
# 在 Debian 13 / Podroid 容器里一条命令出 APK（含签名校验）
#
# 用法（脚本放在工程里或「下载」目录都能跑）：
#   bash build_apk.sh
# 依赖：root 或 sudo 用来装 openjdk-17-jdk；磁盘建议 ≥ 4 GB
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
BUILD_TOOLS="35.0.0"
PLATFORM="android-35"
CMDLINE_ZIP="commandlinetools-linux-11076708_latest.zip"
CMDLINE_URL="https://dl.google.com/android/repository/$CMDLINE_ZIP"

log() { printf '\n==> %s\n' "$*"; }
have() { command -v "$1" >/dev/null 2>&1; }
as_root() { if [ "$(id -u)" = "0" ]; then "$@"; else sudo "$@"; fi; }

# ---- 找工程：本目录 / 上一级 / 同级 LanOS-SAFE-v2 -----------------------------------------
PROJ=""
for d in "$HERE" "$HERE/.." "$HERE/LanOS-SAFE-v2" "$HERE/../LanOS-SAFE-v2"; do
  if [ -f "$d/settings.gradle.kts" ]; then PROJ="$(cd "$d" && pwd)"; break; fi
done

if [ -z "$PROJ" ] && [ -f "$HERE/LanOS-SAFE-v2.zip" ]; then
  have unzip || as_root apt-get install -y --no-install-recommends unzip
  log "解压 LanOS-SAFE-v2.zip"
  unzip -q -o "$HERE/LanOS-SAFE-v2.zip" -d "$HERE"
  PROJ="$HERE/LanOS-SAFE-v2"
fi

if [ -z "$PROJ" ]; then
  echo "找不到工程（没有 settings.gradle.kts）。请把 build_apk.sh 放进工程根目录，"
  echo "或把 LanOS-SAFE-v2.zip 和它放在同一目录。"
  exit 1
fi
cd "$PROJ"
log "工程目录：$PROJ"

# ---- JDK ---------------------------------------------------------------------------------
if ! have java; then
  log "安装 OpenJDK 17"
  as_root apt-get update
  as_root apt-get install -y --no-install-recommends openjdk-17-jdk unzip curl
fi
log "Java 版本"
java -version

# ---- Android SDK -------------------------------------------------------------------------
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "安装 Android cmdline-tools 到 $SDK"
  mkdir -p "$SDK/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fL --retry 3 --retry-all-errors -o "$tmp/tools.zip" "$CMDLINE_URL"
  unzip -q "$tmp/tools.zip" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

log "接受 SDK 许可"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true

log "安装 platform / build-tools"
"$SDK/cmdline-tools/latest/bin/sdkmanager" --install \
  "platform-tools" "platforms;$PLATFORM" "build-tools;$BUILD_TOOLS"

echo "sdk.dir=$SDK" > local.properties
chmod +x gradlew

log "开始编译（首次会下载 Gradle 与依赖，约 5-15 分钟）"
./gradlew :app:assembleDebug --no-daemon

APK="app/build/outputs/apk/debug/app-debug.apk"
log "校验签名"
"$SDK/build-tools/$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | head -6

log "完成"
ls -lh "$APK"
echo "安装： adb install -r $APK"
echo "拷贝到下载目录： cp $APK /storage/emulated/0/Download/  （Android 上）"
