#!/bin/sh
# 최신 실행 파일(jar)을 GitHub Release 에서 내려받아 바꿔 끼우고 재시작합니다.
#   sudo /opt/kakao-news-bot/update.sh
# 매일 새벽 자동으로도 실행됩니다 (kakao-news-bot-update.timer).
set -eu
REPO="${REPO:-lshxxn/SH_TEST}"
APP_DIR="${APP_DIR:-/opt/kakao-news-bot}"
APP_USER="${APP_USER:-newsbot}"
JAR_URL="https://github.com/$REPO/releases/download/latest/kakao-news-bot.jar"

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
curl -fsSL --retry 3 -o "$TMP" "$JAR_URL"
EXPECTED="$(curl -fsSL --retry 3 "$JAR_URL.sha256" | cut -d' ' -f1)"
ACTUAL="$(sha256sum "$TMP" | cut -d' ' -f1)"
if [ -z "$EXPECTED" ] || [ "$EXPECTED" != "$ACTUAL" ]; then
  echo "내려받은 파일의 체크섬이 맞지 않습니다. 잠시 후 다시 시도하세요." >&2
  exit 1
fi
if [ -f "$APP_DIR/app.jar" ] && [ "$(sha256sum "$APP_DIR/app.jar" | cut -d' ' -f1)" = "$ACTUAL" ]; then
  echo "이미 최신 버전입니다."
  exit 0
fi
install -o "$APP_USER" -g "$APP_USER" -m 644 "$TMP" "$APP_DIR/app.jar"
systemctl restart kakao-news-bot
echo "업데이트 완료: 새 버전으로 재시작했습니다."
