#!/bin/sh
# 자동 실행을 끄고 설정을 지웁니다. 키 파일(secrets.properties)과 로그는 남겨 둡니다.
# 전부 지우려면: sh deploy/mac/uninstall.sh --purge
set -u
AGENTS="$HOME/Library/LaunchAgents"
APP_DIR="$HOME/Library/Application Support/kakao-news-bot"
LOG_DIR="$HOME/Library/Logs/kakao-news-bot"
UID_NUM="$(id -u)"
for label in com.kakaonewsbot.app com.kakaonewsbot.ngrok; do
  launchctl bootout "gui/$UID_NUM" "$AGENTS/$label.plist" 2>/dev/null || true
  rm -f "$AGENTS/$label.plist"
done
echo "자동 실행을 껐습니다."
if [ "${1:-}" = "--purge" ]; then
  rm -rf "$APP_DIR" "$LOG_DIR"
  echo "설치 폴더와 로그도 지웠습니다."
else
  echo "키 파일과 로그는 남아 있습니다: $APP_DIR, $LOG_DIR"
fi
