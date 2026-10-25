#!/bin/sh
# 맥에서 뉴스 챗봇 서버와 ngrok 을 "항상 켜진 상태"로 만듭니다.
#   - 로그인하면 자동으로 켜지고, 꺼지면 10초 뒤 다시 켜집니다 (launchd)
#   - 전원이 연결돼 있으면 맥이 잠들지 않게 막습니다 (caffeinate)
# 실행:  sh deploy/mac/install.sh [ngrok주소]
# 다시 실행하면 새 버전으로 교체됩니다. 끄려면 sh deploy/mac/uninstall.sh
set -eu

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP_DIR="$HOME/Library/Application Support/kakao-news-bot"
LOG_DIR="$HOME/Library/Logs/kakao-news-bot"
AGENTS="$HOME/Library/LaunchAgents"
APP_LABEL="com.kakaonewsbot.app"
NGROK_LABEL="com.kakaonewsbot.ngrok"
PORT="${PORT:-8080}"
DRY_RUN="${DRY_RUN:-}"   # 테스트용: 설정 파일만 만들고 실제로 켜지는 않음

say() { printf '%s\n' "$*"; }
fail() { printf '\n문제: %s\n' "$*" >&2; exit 1; }
xml() { printf '%s' "$1" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g'; }

# 1. Java 21 이상
if [ -n "$DRY_RUN" ]; then
  JAVA="${DRY_RUN_JAVA:-/usr/bin/java}"
else
  JAVA_HOME_DIR="$(/usr/libexec/java_home -v 21+ 2>/dev/null || true)"
  [ -n "$JAVA_HOME_DIR" ] || fail "Java 21 이상이 없습니다. https://adoptium.net 에서 Temurin 21 을 설치한 뒤 다시 실행하세요."
  JAVA="$JAVA_HOME_DIR/bin/java"
fi

# 2. ngrok
NGROK="${DRY_RUN_NGROK:-$(command -v ngrok 2>/dev/null || true)}"
for c in /opt/homebrew/bin/ngrok /usr/local/bin/ngrok "$HOME/bin/ngrok"; do
  [ -z "$NGROK" ] && [ -x "$c" ] && NGROK="$c"
done
[ -n "$NGROK" ] || fail "ngrok 을 찾을 수 없습니다. https://ngrok.com/download 에서 설치하고 'ngrok config add-authtoken <토큰>' 을 먼저 실행하세요."
if [ -z "$DRY_RUN" ] && ! "$NGROK" config check >/dev/null 2>&1; then
  say "주의: ngrok 인증 토큰이 없는 것 같습니다. 'ngrok config add-authtoken <토큰>' 을 먼저 실행하세요."
fi
if "$NGROK" http --help 2>&1 | grep -q -- '--url'; then URL_FLAG="--url=https://__DOMAIN__"; else URL_FLAG="--domain=__DOMAIN__"; fi

# 2-1. 터미널에서 수동으로 켜 둔 서버/ngrok 이 있으면 먼저 끄게 한다 (포트와 ngrok 세션이 겹치면 계속 재시작만 반복한다)
if [ -z "$DRY_RUN" ]; then
  if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1 && ! launchctl print "gui/$(id -u)/$APP_LABEL" >/dev/null 2>&1; then
    fail "포트 $PORT 를 이미 다른 프로그램이 쓰고 있습니다. 터미널에서 켜 둔 스프링 서버 창을 Ctrl+C 로 끄고 다시 실행하세요."
  fi
  if pgrep -x ngrok >/dev/null 2>&1 && ! launchctl print "gui/$(id -u)/$NGROK_LABEL" >/dev/null 2>&1; then
    fail "ngrok 이 이미 실행 중입니다. 터미널의 ngrok 창을 Ctrl+C 로 끄고 다시 실행하세요. (무료 계정은 ngrok 을 하나만 띄울 수 있습니다)"
  fi
fi

# 3. ngrok 고정 주소
DOMAIN_FILE="$APP_DIR/ngrok-domain"
NGROK_DOMAIN="${1:-}"
if [ -z "$NGROK_DOMAIN" ] && [ -f "$DOMAIN_FILE" ]; then NGROK_DOMAIN="$(cat "$DOMAIN_FILE")"; fi
if [ -z "$NGROK_DOMAIN" ]; then
  printf 'ngrok 고정 주소를 입력하세요 (예: prelaunch-singing-placard.ngrok-free.dev): '
  read -r NGROK_DOMAIN
fi
NGROK_DOMAIN="$(printf '%s' "$NGROK_DOMAIN" | sed -E 's#^https?://##; s#/.*$##; s/[[:space:]]//g')"
[ -n "$NGROK_DOMAIN" ] || fail "ngrok 주소가 비어 있습니다."
URL_FLAG="$(printf '%s' "$URL_FLAG" | sed "s/__DOMAIN__/$NGROK_DOMAIN/")"

# 4. 빌드
JAR="$(ls "$ROOT"/target/kakao-news-bot-*.jar 2>/dev/null | head -1 || true)"
if [ -z "$DRY_RUN" ] || [ -z "$JAR" ]; then
  say "프로그램을 빌드합니다 (처음에는 몇 분 걸립니다)..."
  (cd "$ROOT" && sh ./mvnw -q -B -DskipTests package)
  JAR="$(ls "$ROOT"/target/kakao-news-bot-*.jar | head -1)"
fi

# 5. 설치 폴더
mkdir -p "$APP_DIR" "$LOG_DIR" "$AGENTS"
cp "$JAR" "$APP_DIR/app.jar"
if [ -f "$ROOT/secrets.properties" ]; then
  cp "$ROOT/secrets.properties" "$APP_DIR/secrets.properties"
  chmod 600 "$APP_DIR/secrets.properties"
fi
[ -f "$APP_DIR/secrets.properties" ] || say "주의: secrets.properties 가 없어 AI 대화는 꺼집니다. 키를 넣은 파일을 $ROOT 에 두고 다시 실행하세요."
printf '%s\n' "$NGROK_DOMAIN" > "$DOMAIN_FILE"

# 6. launchd 설정 파일
APP_PLIST="$AGENTS/$APP_LABEL.plist"
NGROK_PLIST="$AGENTS/$NGROK_LABEL.plist"
cat > "$APP_PLIST" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$APP_LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>/usr/bin/caffeinate</string>
    <string>-i</string>
    <string>-s</string>
    <string>$(xml "$JAVA")</string>
    <string>-jar</string>
    <string>$(xml "$APP_DIR/app.jar")</string>
    <string>--server.port=$PORT</string>
  </array>
  <key>WorkingDirectory</key><string>$(xml "$APP_DIR")</string>
  <key>EnvironmentVariables</key>
  <dict>
    <key>LANG</key><string>ko_KR.UTF-8</string>
  </dict>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ThrottleInterval</key><integer>10</integer>
  <key>StandardOutPath</key><string>$(xml "$LOG_DIR/app.log")</string>
  <key>StandardErrorPath</key><string>$(xml "$LOG_DIR/app.log")</string>
</dict>
</plist>
PLIST
cat > "$NGROK_PLIST" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$NGROK_LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>$(xml "$NGROK")</string>
    <string>http</string>
    <string>$(xml "$URL_FLAG")</string>
    <string>--log=stdout</string>
    <string>$PORT</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ThrottleInterval</key><integer>10</integer>
  <key>StandardOutPath</key><string>$(xml "$LOG_DIR/ngrok.log")</string>
  <key>StandardErrorPath</key><string>$(xml "$LOG_DIR/ngrok.log")</string>
</dict>
</plist>
PLIST

if [ -n "$DRY_RUN" ]; then
  say "DRY_RUN: 설정 파일만 만들었습니다 → $APP_PLIST, $NGROK_PLIST"
  exit 0
fi

# 7. 켜기 (이미 켜져 있으면 내렸다가 다시)
UID_NUM="$(id -u)"
launchctl bootout "gui/$UID_NUM" "$APP_PLIST" 2>/dev/null || true
launchctl bootout "gui/$UID_NUM" "$NGROK_PLIST" 2>/dev/null || true
sleep 1
launchctl bootstrap "gui/$UID_NUM" "$APP_PLIST"
launchctl bootstrap "gui/$UID_NUM" "$NGROK_PLIST"

# 8. 확인
say "서버가 켜지기를 기다립니다..."
OK=""
i=0
while [ $i -lt 45 ]; do
  if curl -fsS "http://localhost:$PORT/kakao/skill/news" >/dev/null 2>&1; then OK=1; break; fi
  i=$((i + 1)); sleep 2
done
TUNNEL="$(curl -fsS http://localhost:4040/api/tunnels 2>/dev/null | grep -o '"public_url":"[^"]*"' | head -1 | cut -d'"' -f4 || true)"
say ""
say "============================================================"
if [ -n "$OK" ]; then say "  설치 완료! 서버가 켜졌습니다."; else say "  설치는 됐지만 서버가 아직 응답하지 않습니다. 아래 로그를 확인하세요."; fi
say "  - 뉴스 스킬     https://$NGROK_DOMAIN/kakao/skill/news"
say "  - AI 대화 스킬  https://$NGROK_DOMAIN/kakao/skill/chat"
if [ -n "$TUNNEL" ]; then say "  - ngrok 터널    연결됨 ($TUNNEL)"; else say "  - ngrok 터널    아직 확인 안 됨 → $LOG_DIR/ngrok.log"; fi
say "  - 로그 보기     tail -f \"$LOG_DIR/app.log\""
say "  - 끄기          sh deploy/mac/uninstall.sh"
say "  주의: 맥이 잠들면 챗봇도 멈춥니다. 전원을 연결해 두고 뚜껑을 닫지 마세요."
say "============================================================"
