#!/bin/sh
# 맥/리눅스: 터미널에서 ./start-chatbot.sh
# 뉴스 챗봇 서버를 8080 포트로 켭니다. 끄려면 Ctrl + C
cd "$(dirname "$0")" || exit 1
export LANG="${LANG:-C.UTF-8}"
exec ./mvnw -q spring-boot:run
