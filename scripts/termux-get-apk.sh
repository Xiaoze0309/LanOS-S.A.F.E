#!/usr/bin/env bash
# 纯 Termux 可用：把工程推到 GitHub，用 Actions 编译，再把签名好的 APK 拉回手机。
#
#   export GITHUB_TOKEN=ghp_xxx      # 新建的 token，必须勾 repo + workflow 两个权限
#   bash termux-get-apk.sh
#
# 默认推一个 v* 标签触发构建，不动 main；想提交到 main 用 LANOS_PUSH_MODE=main。
set -euo pipefail

API="https://api.github.com"
OWNER="${LANOS_OWNER:-Xiaoze0309}"
REPO="${LANOS_REPO:-Lanos-S.A.F.E}"
PUSH_MODE="${LANOS_PUSH_MODE:-tag}"     # tag | main
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${TMPDIR:-$HOME}/lanos-ci-build"
DEST="${LANOS_DEST:-/storage/emulated/0/Download}"

say() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[31m%s\033[0m\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

[ -n "${GITHUB_TOKEN:-}" ] || die "先设置 token：export GITHUB_TOKEN=ghp_xxx（别再用手上那个泄露的）"

say "检查依赖"
for t in git curl unzip python3; do
  if ! have "$t"; then
    echo "安装缺失的 $t"
    pkg install -y "$t" >/dev/null 2>&1 || die "pkg install $t 失败，请手动安装"
  fi
done

# ---- 1. 准备源码 --------------------------------------------------------------
say "准备源码"
rm -rf "$WORK"
mkdir -p "$WORK/src"
if [ -f "$HERE/LanOS-SAFE-v2.zip" ]; then
  unzip -q "$HERE/LanOS-SAFE-v2.zip" -d "$WORK" || die "解压 LanOS-SAFE-v2.zip 失败"
  SRC="$WORK/LanOS-SAFE-v2"
elif [ -f "$HERE/app/build.gradle.kts" ]; then
  SRC="$HERE"
else
  die "找不到工程：把 LanOS-SAFE-v2.zip 和本脚本放同一目录"
fi
echo "源码：$SRC"

# ---- 2. 克隆仓库并覆盖 --------------------------------------------------------
say "克隆 $OWNER/$REPO"
git -c http.extraheader="AUTHORIZATION: bearer $GITHUB_TOKEN" \
    clone --depth 1 "https://github.com/$OWNER/$REPO.git" "$WORK/repo" >/dev/null 2>&1 \
  || die "克隆失败：token 权限不足，或仓库不存在/已被改名"

# 注意：提交里包含 .github/workflows/*，经典 PAT 必须勾选 workflow 权限，否则 push 会被拒

# 用工程内容覆盖（保留 .git 与 .github）
( cd "$SRC" && tar -cf - . ) | ( cd "$WORK/repo" && tar -xf - )

cd "$WORK/repo"
git -c user.name="lanos-bot" -c user.email="lanos-bot@users.noreply.github.com" add -A
if git diff --cached --quiet; then
  say "内容与远端一致，直接复用已有的最新构建"
else
  git -c user.name="lanos-bot" -c user.email="lanos-bot@users.noreply.github.com" \
      commit -q -m "v2: 重新构建 $(date +%F_%H%M)"
fi
SHA="$(git rev-parse HEAD)"
echo "commit: $SHA"

# ---- 3. 推送触发构建 ----------------------------------------------------------
if [ "$PUSH_MODE" = "main" ]; then
  say "推送到 main"
  git -c http.extraheader="AUTHORIZATION: bearer $GITHUB_TOKEN" push -q origin "HEAD:main"
else
  TAG="v2-ci-$(date +%Y%m%d-%H%M%S)"
  say "推送标签 $TAG（不修改 main）"
  git -c http.extraheader="AUTHORIZATION: bearer $GITHUB_TOKEN" push -q origin "HEAD:refs/tags/$TAG"
fi

# ---- 4. 等 workflow -----------------------------------------------------------
say "等待 Actions 启动（约 5-10 分钟，首次会下载 Gradle 与依赖）"
RUN_ID=""
for _ in $(seq 1 40); do
  sleep 6
  RUN_ID="$(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
    "$API/repos/$OWNER/$REPO/actions/runs?head_sha=$SHA&per_page=5" |
    python3 -c 'import json,sys
d=json.load(sys.stdin)
runs=[r for r in d.get("workflow_runs",[]) if r.get("name")=="Build Android APK"]
print(runs[0]["id"] if runs else "")' )"
  [ -n "$RUN_ID" ] && break
  printf '.'
done
[ -n "$RUN_ID" ] || die "没等到 workflow。手动看一眼：https://github.com/$OWNER/$REPO/actions"

echo " run id: $RUN_ID"
for _ in $(seq 1 240); do
  read -r STATUS CONCL < <(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
      "$API/repos/$OWNER/$REPO/actions/runs/$RUN_ID" |
      python3 -c 'import json,sys
d=json.load(sys.stdin); print(d.get("status",""), d.get("conclusion") or "-")')
  printf '\r  状态: %-12s %s' "$STATUS" "$CONCL"
  [ "$STATUS" = "completed" ] && break
  sleep 5
done
echo

if [ "$CONCL" != "success" ]; then
  die "构建失败（$CONCL）。日志：https://github.com/$OWNER/$REPO/actions/runs/$RUN_ID"
fi

# ---- 5. 下载 artifact ---------------------------------------------------------
say "下载 APK"
ART_URL="$(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
  "$API/repos/$OWNER/$REPO/actions/runs/$RUN_ID/artifacts" |
  python3 -c 'import json,sys
d=json.load(sys.stdin)
for a in d.get("artifacts",[]):
    if a["name"]=="app-debug-apk":
        print(a["archive_download_url"]); break')"
[ -n "$ART_URL" ] || die "这次 run 没有 app-debug-apk 产物"

REDIRECT="$(curl -s -o /dev/null -w '%{redirect_url}' -H "Authorization: Bearer $GITHUB_TOKEN" "$ART_URL")"
[ -n "$REDIRECT" ] || die "拿不到下载地址（artifact 可能已过期）"
curl -fL --retry 3 -o "$WORK/apk.zip" "$REDIRECT"
unzip -o -q "$WORK/apk.zip" -d "$WORK/apk"

APK="$(find "$WORK/apk" -name '*.apk' | head -1)"
[ -n "$APK" ] || die "压缩包里没有 .apk"

mkdir -p "$DEST" 2>/dev/null || true
if [ -d "$DEST" ] && [ -w "$DEST" ]; then
  cp -f "$APK" "$DEST/app-debug.apk"
  OUT="$DEST/app-debug.apk"
else
  OUT="$HERE/app-debug.apk"
  cp -f "$APK" "$OUT"
fi

say "完成"
ls -lh "$OUT"
echo "sha256: $(sha256sum "$OUT" | cut -d' ' -f1)"
echo "安装： adb install -r \"$OUT\""
