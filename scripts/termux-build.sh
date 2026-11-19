#!/usr/bin/env bash
# 在 Termux 里直接编出签名 APK（已实测通过，Android 16 / aarch64）
# 用法： bash scripts/termux-build.sh
set -euo pipefail

SDK="$HOME/android-sdk-termux"
GRADLE_VER="8.7"
GRADLE_MIRROR="https://mirrors.cloud.tencent.com/gradle/gradle-${GRADLE_VER}-bin.zip"
PLATFORM_ZIP_URL="https://dl.google.com/android/repository/platform-35_r02.zip"
AAPT2_DEB_VER="16.0.0.4-4"

log() { printf '\n==> %s\n' "$*"; }
have() { command -v "$1" >/dev/null 2>&1; }
cd "$(dirname "$0")/.."

# 1) JDK 17 ---------------------------------------------------------------------------------
if [ ! -x "$PREFIX/lib/jvm/java-17-openjdk/bin/java" ]; then
  log "安装 OpenJDK 17（Termux 的 deb 是按 /data/data/com.termux 打包的，这里手动解包到当前前缀）"
  tmp="$(mktemp -d)" && cd "$tmp"
  apt-get download openjdk-17 libandroid-shmem >/dev/null
  for d in *.deb; do dpkg-deb -x "$d" x; done
  mkdir -p "$PREFIX/lib/jvm"
  cp -a x/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk "$PREFIX/lib/jvm/"
  cp -a x/data/data/com.termux/files/usr/lib/libandroid-shmem.so "$PREFIX/lib/jvm/java-17-openjdk/lib/server/" || true
  for b in java javac keytool jarsigner; do ln -sf "$PREFIX/lib/jvm/java-17-openjdk/bin/$b" "$PREFIX/bin/$b"; done
  cd - >/dev/null && rm -rf "$tmp"
fi
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"

# 2) 信任库（JDK 的 cacerts 指向 com.termux 断链）---------------------------------------------
if [ ! -f "$HOME/.lanos-truststore.p12" ]; then
  log "生成 PKCS12 信任库"
  mkdir -p "$HOME/tools" && cd "$HOME/tools"
  [ -f PemToJks.java ] || cat > PemToJks.java <<'JAVA'
import java.io.*; import java.security.*; import java.util.*;
public class PemToJks {
  public static void main(String[] a) throws Exception {
    java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
    Collection<? extends java.security.cert.Certificate> cs;
    try (InputStream in = new FileInputStream(a[0])) { cs = cf.generateCertificates(in); }
    KeyStore ks = KeyStore.getInstance("PKCS12"); ks.load(null, null);
    int i = 0; for (java.security.cert.Certificate c : cs) ks.setCertificateEntry("ca" + (i++), c);
    try (OutputStream out = new FileOutputStream(a[1])) { ks.store(out, a[2].toCharArray()); }
    System.out.println("imported " + i + " certs");
  }
}
JAVA
  "$JAVA_HOME/bin/javac" PemToJks.java
  "$JAVA_HOME/bin/java" -Duser.home="$HOME" PemToJks "$PREFIX/etc/tls/cert.pem" "$HOME/.lanos-truststore.p12" changeit
  cd - >/dev/null
fi

# 3) Gradle（services.gradle.org 在国内常连不上，用腾讯镜像）----------------------------------
if [ ! -x "$HOME/gradle-$GRADLE_VER/bin/gradle" ]; then
  log "下载 Gradle $GRADLE_VER"
  curl -fL --retry 5 --retry-all-errors -o "$HOME/gradle-$GRADLE_VER-bin.zip" "$GRADLE_MIRROR"
  ( cd "$HOME" && unzip -q -o "gradle-$GRADLE_VER-bin.zip" )
  chmod +x "$HOME/gradle-$GRADLE_VER/bin/gradle"
fi

# 4) Android SDK：platform 35 + build-tools 目录 + Termux 的 aapt2 ------------------------------
mkdir -p "$SDK/platforms" "$SDK/build-tools/34.0.0" "$SDK/licenses"
if [ ! -f "$SDK/platforms/android-35/android.jar" ]; then
  log "下载 android.jar (platform 35)"
  tmp="$(mktemp -d)"
  curl -fL --retry 3 -o "$tmp/p.zip" "$PLATFORM_ZIP_URL"
  unzip -q -o "$tmp/p.zip" -d "$tmp"
  mv "$tmp/android-35" "$SDK/platforms/android-35"
  rm -rf "$tmp"
fi

if [ ! -x "$PREFIX/bin/aapt2" ]; then
  log "安装 aapt2（Termux 仓库里的 Android 16 构建）"
  tmp="$(mktemp -d)" && cd "$tmp"
  apt-get download aapt2 >/dev/null
  dpkg-deb -x aapt2_*.deb a
  cp -f a/data/data/com.termux/files/usr/bin/aapt2 "$PREFIX/bin/aapt2"
  chmod +x "$PREFIX/bin/aapt2"
  cd - >/dev/null && rm -rf "$tmp"
fi
cp -f "$PREFIX/bin/aapt2" "$SDK/build-tools/34.0.0/aapt2"
printf 'Pkg.Revision=34.0.0\nPkg.Desc=Android SDK Build-Tools\n' > "$SDK/build-tools/34.0.0/source.properties"
echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$SDK/licenses/android-sdk-license"
echo "sdk.dir=$SDK" > local.properties

# 5) 环境变量 --------------------------------------------------------------------------------
mkdir -p "$HOME/tmp"
export PATH="$JAVA_HOME/bin:$HOME/gradle-$GRADLE_VER/bin:$PREFIX/bin:/system/bin:$PATH"
export LD_LIBRARY_PATH="$JAVA_HOME/lib/server:$JAVA_HOME/lib:$PREFIX/lib"
export GRADLE_USER_HOME="$HOME/.gradle"
export TMPDIR="$HOME/tmp"
export JAVA_TOOL_OPTIONS="-Duser.home=$HOME -Djava.io.tmpdir=$HOME/tmp -Djavax.net.ssl.trustStore=$HOME/.lanos-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=PKCS12"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"

log "编译（首次会下载依赖，约 10-20 分钟）"
gradle :app:assembleDebug --no-daemon --console=plain --max-workers=2 \
  -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2" \
  -Pandroid.suppressUnsupportedCompileSdk=35

APK="app/build/outputs/apk/debug/app-debug.apk"
log "完成"
ls -lh "$APK"
echo "sha256: $(sha256sum "$APK" | cut -d' ' -f1)"
OUT_DIR="/storage/emulated/0/Download/Lanos4_byDSH/app"
if mkdir -p "$OUT_DIR" 2>/dev/null && [ -w "$OUT_DIR" ]; then
  cp -f "$APK" "$OUT_DIR/app-debug.apk"
  echo "已复制： $OUT_DIR/app-debug.apk"
fi
