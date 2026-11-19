#!/usr/bin/env bash
# 生成 release keystore，并把 unsigned APK 对齐 + 签名
# 用法：
#   1) ./gradlew :app:assembleRelease        # 产出 app-release-unsigned.apk
#   2) bash scripts/sign_release.sh /path/to/keystore.jks
set -euo pipefail

KS="${1:-release.jks}"
ALIAS="${2:-lanos}"
STOREPASS="${3:-$(openssl rand -base64 12 2>/dev/null || echo 'lanos-'$RANDOM)}"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
BT="35.0.0"

cd "$(dirname "$0")/.."

if [ ! -f "$KS" ]; then
  echo "==> 生成 keystore: $KS （密码：$STOREPASS，请立刻保存到安全位置）"
  keytool -genkeypair -v \
    -keystore "$KS" -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STOREPASS" -keypass "$STOREPASS" \
    -dname "CN=LanOS S.A.F.E, OU=Dev, O=LanOS, L=Local, C=CN"
fi

IN="app/build/outputs/apk/release/app-release-unsigned.apk"
OUT="app/build/outputs/apk/release/app-release-signed.apk"
[ -f "$IN" ] || { echo "先跑 ./gradlew :app:assembleRelease"; exit 1; }

"$SDK/build-tools/$BT/zipalign" -p -f 4 "$IN" /tmp/lanos-aligned.apk
"$SDK/build-tools/$BT/apksigner" sign \
  --ks "$KS" --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$STOREPASS" --key-pass "pass:$STOREPASS" \
  --out "$OUT" /tmp/lanos-aligned.apk
rm -f /tmp/lanos-aligned.apk

"$SDK/build-tools/$BT/apksigner" verify --print-certs "$OUT" | head -6
ls -lh "$OUT"
