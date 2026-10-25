#!/bin/sh
# 오라클 클라우드 평생 무료 서버(Ubuntu)에 뉴스 챗봇을 설치합니다. 한 번만 실행하면 됩니다.
#
#   curl -fsSLo install.sh https://raw.githubusercontent.com/lshxxn/SH_TEST/claude/kakao-news-bot/deploy/oracle/install.sh
#   sudo sh install.sh
#
# 하는 일: Java 21, ngrok 설치 → 실행 파일 내려받기 → 서비스 등록(자동 시작·재시작)
#         → 매일 새벽 자동 업데이트 → (선택) 유휴 회수 방지 작업
# 다시 실행해도 안전합니다. 값을 묻는 단계에서 그냥 Enter 를 치면 이전 값을 유지합니다.
set -eu

REPO="${REPO:-lshxxn/SH_TEST}"
BRANCH="${BRANCH:-claude/kakao-news-bot}"
APP_USER="newsbot"
PORT="${PORT:-8080}"
DRY_RUN="${DRY_RUN:-}"                 # 테스트용: 설치 없이 설정 파일만 ROOTFS 아래에 만든다
ROOTFS="${DRY_RUN_ROOT:-}"
APP_DIR="$ROOTFS/opt/kakao-news-bot"
UNIT_DIR="$ROOTFS/etc/systemd/system"
JAR_URL="https://github.com/$REPO/releases/download/latest/kakao-news-bot.jar"
RAW_URL="https://raw.githubusercontent.com/$REPO/$BRANCH/deploy/oracle"

say() { printf '%s\n' "$*"; }
fail() { printf '\n문제: %s\n' "$*" >&2; exit 1; }
ask() {            # ask 변수명 "질문" [hidden]
  _var="$1"; _q="$2"; _hidden="${3:-}"
  printf '%s: ' "$_q"
  if [ -n "$_hidden" ] && [ -t 0 ]; then stty -echo; fi
  read -r _val || _val=""
  if [ -n "$_hidden" ] && [ -t 0 ]; then stty echo; printf '\n'; fi
  eval "$_var=\$_val"
}

if [ -z "$DRY_RUN" ] && [ "$(id -u)" -ne 0 ]; then
  fail "관리자 권한이 필요합니다. 'sudo sh install.sh' 로 실행하세요."
fi

# ── 1. 패키지 설치 ──────────────────────────────────────────────────────────────
if [ -z "$DRY_RUN" ]; then
  export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a
  say "[1/6] Java 21 과 ngrok 을 설치합니다..."
  apt-get update -qq
  apt-get install -y -qq ca-certificates curl gnupg openjdk-21-jre-headless >/dev/null
  if ! command -v ngrok >/dev/null 2>&1; then
    install -d -m 755 /etc/apt/keyrings
    if curl -fsSL https://ngrok-agent.s3.amazonaws.com/ngrok.asc | gpg --dearmor -o /etc/apt/keyrings/ngrok.gpg 2>/dev/null \
       && echo "deb [signed-by=/etc/apt/keyrings/ngrok.gpg] https://ngrok-agent.s3.amazonaws.com bookworm main" > /etc/apt/sources.list.d/ngrok.list \
       && apt-get update -qq && apt-get install -y -qq ngrok >/dev/null; then
      :
    else
      say "apt 로 설치하지 못해 ngrok 을 직접 내려받습니다..."
      case "$(uname -m)" in x86_64) ARCH=amd64 ;; aarch64|arm64) ARCH=arm64 ;; *) fail "지원하지 않는 CPU: $(uname -m)" ;; esac
      curl -fsSL "https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-linux-$ARCH.tgz" | tar -xz -C /usr/local/bin ngrok
    fi
  fi
  command -v timedatectl >/dev/null 2>&1 && timedatectl set-timezone Asia/Seoul || true
  # 메모리 1GB 서버용 스왑 (Java 가 메모리 부족으로 죽는 것을 막는다)
  if [ "$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)" -lt 1500 ] && ! swapon --show | grep -q .; then
    say "메모리가 작아 스왑 1GB 를 만듭니다..."
    fallocate -l 1G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  fi
  NGROK="$(command -v ngrok)"
  JAVA="$(command -v java)"
else
  NGROK="${DRY_RUN_NGROK:-/usr/bin/ngrok}"
  JAVA="/usr/bin/java"
fi
if "$NGROK" http --help 2>&1 | grep -q -- '--url'; then URL_FLAG="--url=https://__DOMAIN__"; else URL_FLAG="--domain=__DOMAIN__"; fi

# ── 2. 사용자와 폴더 ────────────────────────────────────────────────────────────
say "[2/6] 전용 사용자와 설치 폴더를 준비합니다..."
if [ -z "$DRY_RUN" ]; then
  id "$APP_USER" >/dev/null 2>&1 || useradd -r -m -d /var/lib/newsbot -s /usr/sbin/nologin "$APP_USER"
fi
mkdir -p "$APP_DIR" "$UNIT_DIR"

# ── 3. 값 입력 (ngrok 토큰·주소, 제미나이 키) ─────────────────────────────────
say "[3/6] 설정 값을 입력합니다. 이전에 입력한 값이 있으면 Enter 로 유지합니다."
NGROK_AUTHTOKEN="${NGROK_AUTHTOKEN:-}"
if [ -z "$NGROK_AUTHTOKEN" ]; then
  if [ -f "$APP_DIR/ngrok.yml" ]; then ask NGROK_AUTHTOKEN "ngrok 인증 토큰 (Enter = 유지)" hidden
  else ask NGROK_AUTHTOKEN "ngrok 인증 토큰 (dashboard.ngrok.com > Your Authtoken)" hidden; fi
fi
[ -n "$NGROK_AUTHTOKEN" ] || [ -f "$APP_DIR/ngrok.yml" ] || fail "ngrok 인증 토큰이 필요합니다."

NGROK_DOMAIN="${NGROK_DOMAIN:-}"
SAVED_DOMAIN=""; [ -f "$APP_DIR/ngrok-domain" ] && SAVED_DOMAIN="$(cat "$APP_DIR/ngrok-domain")"
if [ -z "$NGROK_DOMAIN" ]; then
  ask NGROK_DOMAIN "ngrok 고정 주소${SAVED_DOMAIN:+ (Enter = $SAVED_DOMAIN)} (예: xxxx.ngrok-free.dev)"
  [ -n "$NGROK_DOMAIN" ] || NGROK_DOMAIN="$SAVED_DOMAIN"
fi
NGROK_DOMAIN="$(printf '%s' "$NGROK_DOMAIN" | sed -E 's#^https?://##; s#/.*$##; s/[[:space:]]//g')"
[ -n "$NGROK_DOMAIN" ] || fail "ngrok 주소가 필요합니다."

GEMINI_API_KEY="${GEMINI_API_KEY:-}"
if [ -z "$GEMINI_API_KEY" ]; then
  if [ -f "$APP_DIR/secrets.properties" ]; then ask GEMINI_API_KEY "제미나이 API 키 (Enter = 유지)" hidden
  else ask GEMINI_API_KEY "제미나이 API 키 (없으면 Enter, AI 대화만 꺼집니다)" hidden; fi
fi

KEEPALIVE="${KEEPALIVE:-}"
if [ -z "$KEEPALIVE" ]; then
  ask KEEPALIVE "오라클 유휴 회수 방지용으로 매시간 5분간 CPU 를 쓰는 작업을 켤까요? [Y/n]"
fi
case "$KEEPALIVE" in n|N|no|NO|아니오) KEEPALIVE=no ;; *) KEEPALIVE=yes ;; esac

# ── 4. 파일 쓰기 ────────────────────────────────────────────────────────────────
say "[4/6] 실행 파일과 설정 파일을 준비합니다..."
if [ -n "$NGROK_AUTHTOKEN" ]; then
  printf 'version: "3"\nauthtoken: %s\n' "$NGROK_AUTHTOKEN" > "$APP_DIR/ngrok.yml"
  chmod 600 "$APP_DIR/ngrok.yml"
fi
printf '%s\n' "$NGROK_DOMAIN" > "$APP_DIR/ngrok-domain"
if [ -n "$GEMINI_API_KEY" ]; then
  printf '# 제미나이 API 키. 바꾸려면 이 파일을 고치고: sudo systemctl restart kakao-news-bot\nGEMINI_API_KEY=%s\n' "$GEMINI_API_KEY" > "$APP_DIR/secrets.properties"
  chmod 600 "$APP_DIR/secrets.properties"
fi
URL_FLAG="$(printf '%s' "$URL_FLAG" | sed "s/__DOMAIN__/$NGROK_DOMAIN/")"

if [ -z "$DRY_RUN" ]; then
  curl -fsSL --retry 3 -o "$APP_DIR/update.sh" "$RAW_URL/update.sh"
  chmod 755 "$APP_DIR/update.sh"
  say "실행 파일을 내려받습니다 (약 20MB)..."
  TMP="$(mktemp)"
  curl -fL --retry 3 -# -o "$TMP" "$JAR_URL"
  EXPECTED="$(curl -fsSL --retry 3 "$JAR_URL.sha256" | cut -d' ' -f1)"
  [ "$EXPECTED" = "$(sha256sum "$TMP" | cut -d' ' -f1)" ] || fail "내려받은 실행 파일이 손상됐습니다. 다시 실행하세요."
  mv "$TMP" "$APP_DIR/app.jar"
  chown -R "$APP_USER:$APP_USER" "$APP_DIR"
  chmod 644 "$APP_DIR/app.jar"
else
  : > "$APP_DIR/app.jar"; : > "$APP_DIR/update.sh"
fi

# ── 5. systemd 서비스 ──────────────────────────────────────────────────────────
say "[5/6] 자동 시작 서비스를 등록합니다..."
cat > "$UNIT_DIR/kakao-news-bot.service" <<UNIT
[Unit]
Description=Kakao News Bot (뉴스 챗봇 서버)
After=network-online.target
Wants=network-online.target

[Service]
User=$APP_USER
WorkingDirectory=/opt/kakao-news-bot
Environment=LANG=C.UTF-8 TZ=Asia/Seoul
ExecStart=$JAVA -Xmx320m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -jar /opt/kakao-news-bot/app.jar --server.port=$PORT
Restart=always
RestartSec=10
SuccessExitStatus=143
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
UNIT
cat > "$UNIT_DIR/kakao-news-bot-ngrok.service" <<UNIT
[Unit]
Description=ngrok tunnel for Kakao News Bot
After=network-online.target kakao-news-bot.service
Wants=network-online.target

[Service]
User=$APP_USER
ExecStart=$NGROK http --config /opt/kakao-news-bot/ngrok.yml $URL_FLAG --log=stdout $PORT
Restart=always
RestartSec=10
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
UNIT
cat > "$UNIT_DIR/kakao-news-bot-update.service" <<UNIT
[Unit]
Description=Kakao News Bot 최신 버전 내려받기

[Service]
Type=oneshot
ExecStart=/opt/kakao-news-bot/update.sh
UNIT
cat > "$UNIT_DIR/kakao-news-bot-update.timer" <<UNIT
[Unit]
Description=매일 새벽 Kakao News Bot 자동 업데이트

[Timer]
OnCalendar=*-*-* 05:17:00
RandomizedDelaySec=20min
Persistent=true

[Install]
WantedBy=timers.target
UNIT
cat > "$UNIT_DIR/kakao-news-bot-keepalive.service" <<UNIT
[Unit]
Description=오라클 유휴 회수 방지 (매시간 5분 CPU 사용)

[Service]
Type=oneshot
User=$APP_USER
Nice=19
ExecStart=/usr/bin/timeout 300 /bin/sh -c 'while :; do :; done'
SuccessExitStatus=124
UNIT
cat > "$UNIT_DIR/kakao-news-bot-keepalive.timer" <<UNIT
[Unit]
Description=매시간 Kakao News Bot 유휴 회수 방지 실행

[Timer]
OnCalendar=*-*-* *:07:00
Persistent=true

[Install]
WantedBy=timers.target
UNIT

if [ -n "$DRY_RUN" ]; then
  say "DRY_RUN: 설정 파일만 만들었습니다 → $UNIT_DIR, $APP_DIR (keepalive=$KEEPALIVE)"
  exit 0
fi

systemctl daemon-reload
systemctl enable --now kakao-news-bot.service kakao-news-bot-ngrok.service kakao-news-bot-update.timer >/dev/null
systemctl restart kakao-news-bot.service kakao-news-bot-ngrok.service
if [ "$KEEPALIVE" = yes ]; then systemctl enable --now kakao-news-bot-keepalive.timer >/dev/null
else systemctl disable --now kakao-news-bot-keepalive.timer >/dev/null 2>&1 || true; fi

# ── 6. 확인 ─────────────────────────────────────────────────────────────────────
say "[6/6] 서버가 켜지기를 기다립니다 (작은 서버라 1분쯤 걸릴 수 있습니다)..."
OK=""; i=0
while [ $i -lt 60 ]; do
  if curl -fsS "http://localhost:$PORT/kakao/skill/news" >/dev/null 2>&1; then OK=1; break; fi
  i=$((i + 1)); sleep 3
done
TUNNEL="$(curl -fsS http://localhost:4040/api/tunnels 2>/dev/null | grep -o '"public_url":"[^"]*"' | head -1 | cut -d'"' -f4 || true)"
GEMINI_LINE="$(journalctl -u kakao-news-bot --no-pager -o cat 2>/dev/null | grep -a '제미나이 \[' | tail -1 | sed 's/^ *- //' || true)"
say ""
say "============================================================"
if [ -n "$OK" ]; then say "  설치 완료! 뉴스 챗봇 서버가 켜졌습니다."; else say "  설치는 됐지만 서버가 아직 응답하지 않습니다. 아래 로그 명령으로 확인하세요."; fi
say "  - 뉴스 스킬     https://$NGROK_DOMAIN/kakao/skill/news"
say "  - AI 대화 스킬  https://$NGROK_DOMAIN/kakao/skill/chat"
if [ -n "$TUNNEL" ]; then say "  - ngrok 터널    연결됨 ($TUNNEL)"; else say "  - ngrok 터널    아직 확인 안 됨 → sudo journalctl -u kakao-news-bot-ngrok -n 30"; fi
[ -n "$GEMINI_LINE" ] && say "  - $GEMINI_LINE"
say "  - 유휴 회수 방지  $KEEPALIVE"
say "  - 상태 보기     sudo systemctl status kakao-news-bot kakao-news-bot-ngrok"
say "  - 로그 보기     sudo journalctl -u kakao-news-bot -f"
say "  - 지금 업데이트 sudo /opt/kakao-news-bot/update.sh   (매일 05:17 자동)"
say "============================================================"
