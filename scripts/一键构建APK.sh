#!/usr/bin/env bash
# 放在「下载」目录也能直接跑：自动找（或解压）工程，然后调用 build_apk.sh
# 用法： bash 一键构建APK.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

PROJ=""
for d in "$HERE/LanOS-SAFE-v2" "$HERE" "$HERE/LanOS-SAFE-v2"*; do
  if [ -f "$d/settings.gradle.kts" ]; then PROJ="$d"; break; fi
done

if [ -z "$PROJ" ] && [ -f "$HERE/LanOS-SAFE-v2.zip" ]; then
  echo "==> 解压 LanOS-SAFE-v2.zip 到 $HERE"
  unzip -q -o "$HERE/LanOS-SAFE-v2.zip" -d "$HERE"
  PROJ="$HERE/LanOS-SAFE-v2"
fi

if [ -z "$PROJ" ]; then
  echo "没找到工程：请把 LanOS-SAFE-v2.zip（或解压后的文件夹）和本脚本放在同一目录"
  exit 1
fi

echo "==> 工程目录：$PROJ"
exec bash "$PROJ/scripts/build_apk.sh"
