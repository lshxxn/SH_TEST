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
if [ -f "$APP_DIR/pmset-changed" ]; then
  echo "잠자기 설정을 원래대로 되돌립니다. 관리자 비밀번호를 입력하세요."
  sudo pmset -a disablesleep 0 && sudo pmset -c sleep 1 && rm -f "$APP_DIR/pmset-changed" \
    && echo "되돌렸습니다. 이제 뚜껑을 닫으면 평소처럼 잠듭니다." \
    || echo "되돌리지 못했습니다. 나중에 터미널에서: sudo pmset -a disablesleep 0"
fi
if [ "${1:-}" = "--purge" ]; then
  rm -rf "$APP_DIR" "$LOG_DIR"
  echo "설치 폴더와 로그도 지웠습니다."
else
  echo "키 파일과 로그는 남아 있습니다: $APP_DIR, $LOG_DIR"
fi
