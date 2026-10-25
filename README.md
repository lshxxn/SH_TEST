# 카카오톡 아침 뉴스 봇

매일 **오전 9시(한국 시간)** 에 뉴스 RSS를 모아 카카오톡으로 보내는 Spring Boot 애플리케이션입니다.
기사 본문은 가져오지 않고 **제목과 원문 링크만** 보냅니다.

- Java 21, Spring Boot 4.1, Maven
- 뉴스 출처: 구글 뉴스 RSS(기본), 키워드 검색, 언론사 RSS
- 발송 방식 두 가지 (동시에 켤 수 있음)

| | ① 나에게 보내기 | ② 카카오톡 채널 → 채널 친구 |
|---|---|---|
| 받는 사람 | 나 한 명 ('나와의 채팅') | 채널을 친구 추가한 사람들 |
| 비용 | 무료 | 건당 과금 (솔라피 요금표 참고) |
| 필요한 것 | 카카오 계정, 카카오디벨로퍼스 앱 | **사업자등록증**, 비즈니스 채널, 솔라피 계정 |
| 메시지 형태 | 리스트형 카드 (기사 3개씩) | 텍스트형 브랜드 메시지 (최대 1,300자) |
| 바로 시작 | 가능 (약 10분) | 비즈니스 인증 심사가 필요 |

> 카카오톡 채널을 만드는 것 자체는 무료이고 누구나 할 수 있습니다.
> 하지만 **채널 이름으로 친구들에게 자동 발송하는 API는 사업자 전용**입니다.
> 친구톡은 2026년 1월 1일부로 종료되어 **브랜드 메시지**로 대체되었고, 공식 딜러사(솔라피 등)를 통해서만 보낼 수 있습니다.
> 개인 용도라면 ① 부터 시작하세요.

---

## 0. 무료로 시작하기: 내 컴퓨터 + 카카오톡 채널 챗봇 + 제미나이

- 분야 블록(정치, 경제 등)을 고르면 그 순간 뉴스를 가져와 기사 5개를 카드로 답합니다.
- 챗봇이 못 알아들은 말(폴백 블록)은 제미나이가 대화로 답합니다.
- 사업자등록과 비용이 필요 없습니다. 내 컴퓨터와 ngrok 이 켜져 있는 동안만 답합니다.

```
친구 → 카카오 챗봇 → ngrok 주소 → 내 컴퓨터 :8080 ─┬─ /kakao/skill/news → 구글 뉴스 RSS
                                                    └─ /kakao/skill/chat → 제미나이 API
```

### 처음 한 번 준비

1. **Java 21 설치**: https://adoptium.net 에서 Temurin 21 (JDK)
2. **제미나이 키 넣기**: `secrets.properties.example` 을 복사해 이름을 `secrets.properties` 로 바꾸고
   `GEMINI_API_KEY=` 뒤에 키를 붙여 넣습니다. 이 파일은 GitHub 에 올라가지 않습니다.
3. **챗봇 서버 켜기**: 윈도우는 `start-chatbot.bat` 더블클릭, 맥은 터미널에서 `sh start-chatbot.sh`
   - 켜지면 `=====` 로 둘러싼 안내 상자가 나옵니다. `제미나이 [정상]` 이면 키가 맞습니다.
     `[꺼짐]` 이나 `[문제]` 면 같은 줄에 고칠 곳이 적혀 있습니다.
   - 브라우저에서 http://localhost:8080/kakao/skill/chat 을 열어도 제미나이 상태를 볼 수 있습니다.
   - 메모장이 `secrets.properties.txt` 로 저장해도 읽습니다. 키 앞뒤의 따옴표와 공백은 자동으로 지웁니다.
4. **ngrok 으로 공개 주소 받기**: https://ngrok.com 무료 가입 → 설치 → 인증 토큰 등록 → `ngrok http 8080`
   - 화면의 `Forwarding` 주소(`https://....ngrok-free.dev`)는 무료 계정에 고정으로 주어집니다.

### 챗봇 관리자센터 설정

| 할 일 | 내용 |
|---|---|
| 스킬 "뉴스" | URL `https://....ngrok-free.dev/kakao/skill/news` |
| 스킬 "AI 대화" | URL `https://....ngrok-free.dev/kakao/skill/chat` |
| 웰컴 블록 | 스킬 "뉴스" → 봇 응답 "스킬데이터 사용". 처음 들어온 친구에게 분야 선택 버튼이 나옵니다 |
| 폴백 블록 | 스킬 "AI 대화" → 봇 응답 "스킬데이터 사용". 분야 버튼은 뉴스 카드로, 그 밖의 말은 제미나이가 답합니다 |
| 확인 | [봇 테스트] 후 [배포] |

분야별 블록(정치, 경제 …)을 따로 만들 필요는 없습니다. 만들고 싶다면 사용자 발화에 분야 이름을 넣고 스킬 "뉴스"를 연결하면 됩니다.

### 동작 방식

- 분야는 블록 파라미터 `category` → 보낸 말 → 블록 이름 순서로 찾습니다. 분야 이름은 `newsbot.news.sections` 의 `name` 입니다.
- 리스트 카드는 카카오 규격(헤더 15자, 기사 제목 30자, 출처 16자, 항목 5개)에 맞춰 자릅니다. 넘기면 "말풍선 포맷이 올바르지 않습니다"로 거부됩니다.
- 카카오에 보낼 응답을 미리 보려면 브라우저에서 `http://localhost:8080/kakao/skill/news?say=정치` 를 엽니다.
- 카카오는 5초 안에 답을 받아야 합니다. 뉴스는 연결 1.5초·응답 2.5초, 제미나이는 3.5초에서 끊고 안내 문구로 답합니다.
- 같은 분야를 5분 안에 다시 물으면 방금 가져온 뉴스를 씁니다 (`newsbot.chatbot.cache-ttl`).
- 제미나이는 친구별로 최근 대화 3번을 30분 동안 기억합니다. "새 대화"라고 보내면 잊습니다.
- AI 대화 스킬은 분야 버튼("정치")이나 뉴스를 찾는 말("경제 뉴스 알려줘")을 받으면 제미나이 대신 뉴스 카드로 답합니다.
- 긴 답변이 필요하면: 챗봇을 **AI 챗봇으로 전환 신청**(카카오에 이메일)한 뒤 폴백 블록의 **콜백**을 켭니다.
  그러면 "생각하고 있어요"를 먼저 보내고 답을 이어서 보냅니다. 서버 쪽 코드는 이미 준비돼 있습니다.
- 제미나이의 "생각(thinking)" 단계는 끄고 씁니다. 끄는 설정이 모델마다 달라서 켜질 때 자동으로 맞는 방식을 고릅니다.
  시작 화면의 `시험 응답 N초` 가 3초를 넘으면 카카오 5초 제한에 걸리기 쉬우니 더 빠른 모델로 바꾸세요.
- 기본 모델은 `gemini-flash-lite-latest` 입니다. 바꾸려면 `secrets.properties` 에 `GEMINI_MODEL=모델이름`.
- 아무나 스킬 주소를 부르지 못하게 하려면 `NEWSBOT_SKILL_TOKEN` 을 정하고 두 스킬 URL 끝에 `?token=값` 을 붙입니다.

---

## 1. 카카오톡 채널 만들기

1. [카카오톡 채널 관리자센터](https://center-pf.kakao.com)에 카카오 계정으로 로그인합니다.
2. **새 채널 만들기**를 누르고 채널 이름, 검색용 아이디, 프로필 사진, 카테고리를 입력합니다.
3. 채널 설정에서 **채널 공개**와 **검색 허용**을 켭니다.
4. 채널 홈 주소(`https://pf.kakao.com/_xxxx`)나 QR 코드를 공유해서 친구를 모읍니다.
5. ②로 발송하려면 관리자센터에서 **비즈니스 채널 인증**을 신청합니다. 사업자등록증이 필요합니다.

---

## 2. 방식 ① 나에게 보내기 설정

### 2-1. 카카오디벨로퍼스 앱 만들기

[카카오디벨로퍼스](https://developers.kakao.com) > 내 애플리케이션에서 앱을 하나 만들고 아래를 설정합니다.
메뉴 이름은 2025년 콘솔 개편 기준이며 바뀔 수 있습니다.

| 메뉴 | 할 일 |
|---|---|
| 앱 > 플랫폼 키 > **REST API 키** | 키 값을 복사 → `KAKAO_REST_API_KEY` |
| 같은 화면 > **리다이렉트 URI** | `http://localhost:8080/kakao/callback` 등록 |
| 같은 화면 > **클라이언트 시크릿** | 사용 중이면 값을 복사 → `KAKAO_CLIENT_SECRET` |
| 카카오 로그인 | **사용 설정 ON** |
| 카카오 로그인 > **동의항목** | **카카오톡 메시지 전송 (talk_message)** 을 '선택 동의'로 설정 |
| 앱 > 제품 링크 관리 > **웹 도메인** | `https://news.google.com` 등록 |

> **웹 도메인 등록은 꼭 하세요.** 등록하지 않은 도메인의 링크는 메시지에서 열리지 않습니다.
> 기본 설정은 구글 뉴스 RSS라서 모든 기사 링크가 `news.google.com` 하나로 통일됩니다.
> 언론사 RSS를 추가했다면 그 언론사 도메인도 등록해야 합니다.

### 2-2. 실행

```bash
export NEWSBOT_ADMIN_TOKEN=$(openssl rand -hex 24)   # 관리용 토큰. 값을 따로 적어 두세요
export KAKAO_ENABLED=true                             # 나에게 보내기 켜기 (기본은 꺼짐)
export KAKAO_REST_API_KEY=발급받은_REST_API_키
export KAKAO_CLIENT_SECRET=클라이언트_시크릿          # 사용하지 않으면 생략

./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

### 2-3. 카카오 계정 연결 (최초 1회)

브라우저에서 아래 주소를 열고, 카카오 로그인과 **카카오톡 메시지 전송** 동의를 진행합니다.

```
http://localhost:8080/kakao/login?token=<NEWSBOT_ADMIN_TOKEN 값>
```

"카카오 계정 연결 완료!"가 보이면 토큰이 `./data/kakao-token.json` 에 저장된 것입니다.
액세스 토큰은 발송할 때마다 자동으로 갱신되고, 리프레시 토큰도 카카오가 만료 1개월 전부터 자동으로 새로 줍니다.
**앱이 한 달에 한 번 이상 발송하고 있으면 다시 로그인할 일이 없습니다.**

### 2-4. 바로 테스트

```bash
# 보내지 않고 어떤 기사가 모이는지만 확인
curl -H "X-Admin-Token: $NEWSBOT_ADMIN_TOKEN" http://localhost:8080/api/news/preview

# 지금 바로 발송 → 카카오톡 '나와의 채팅'을 확인하세요
curl -X POST -H "X-Admin-Token: $NEWSBOT_ADMIN_TOKEN" http://localhost:8080/api/news/send-now
```

이후에는 앱이 켜져 있는 동안 매일 09:00에 자동으로 발송됩니다.

---

## 3. 방식 ② 카카오톡 채널 친구에게 보내기 (솔라피)

1. 1장의 채널을 **비즈니스 채널**로 인증받습니다.
2. [솔라피](https://solapi.com)에 가입하고 카카오 채널을 연동합니다. 연동이 끝나면 **pfId**(`KA01PF...`)가 나옵니다.
3. 솔라피 콘솔에서 **API Key / API Secret** 을 발급받고 잔액을 충전합니다.
4. 받는 사람의 휴대폰 번호를 준비합니다. **채널 친구이면서 광고성 정보 수신에 동의한 사람**이어야 합니다.

```bash
export SOLAPI_ENABLED=true
export SOLAPI_API_KEY=...
export SOLAPI_API_SECRET=...
export SOLAPI_PF_ID=KA01PF...
export SOLAPI_RECIPIENTS=01011112222,01033334444
```

- 발송 형식은 솔라피 공식 SDK 예제의 `BMS_FREE` + `TEXT` 와 같습니다. 검수 없이 바로 보낼 수 있는 자유형입니다.
- `targeting: I` 는 모든 채널에서 쓸 수 있습니다. `M`, `N` 은 카카오 인허가 채널 전용입니다.
- 카카오톡 발송이 실패해도 문자로 대체 발송하지 않도록 기본값을 `disable-sms: true` 로 두었습니다.
- 본문은 1,300자 제한에 맞춰 들어가는 만큼만 기사를 담습니다. 링크 없이 제목만 보내려면 `newsbot.solapi.include-links=false`.
- 브랜드 메시지는 **광고성 메시지**입니다. 야간에는 발송이 제한되므로 발송 시각을 밤으로 바꾸지 마세요. 처음에는 본인 번호 하나로 테스트하세요.

---

## 4. 뉴스 설정 바꾸기

`src/main/resources/application.yml`

```yaml
newsbot:
  schedule:
    cron: "0 0 9 * * *"     # 초 분 시 일 월 요일. 평일만: "0 0 9 * * MON-FRI"
    zone: Asia/Seoul
  news:
    max-items-per-section: 5
    lookback: 24h
    sections:
      - name: 주요 뉴스
        rss: https://news.google.com/rss?hl=ko&gl=KR&ceid=KR:ko
      - name: 경제
        rss: https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=ko&gl=KR&ceid=KR:ko
      - name: AI                  # 키워드 → 구글 뉴스 최근 1일 검색
        keyword: 인공지능
```

- 섹션마다 `rss`(피드 주소) 또는 `keyword`(검색어) 중 하나를 씁니다.
- 여러 섹션에 같은 기사가 나오면 처음 한 번만 보냅니다.
- 피드 하나가 실패해도 나머지 섹션은 그대로 발송됩니다.

---

## 5. 서버 계속 켜 두기

챗봇은 스프링 서버와 ngrok 이 켜져 있는 동안만 답합니다. 꺼져 있으면 챗봇 관리자센터에
"스킬 서버 연결 오류 (1002)" 가 쌓입니다.

### 방법 A. 맥에서 자동 실행 (추천, 5분)

터미널에서 켜 둔 스프링 서버 창과 ngrok 창을 Ctrl+C 로 끈 뒤, 프로젝트 폴더에서 한 번만 실행합니다.

```bash
sh deploy/mac/install.sh prelaunch-singing-placard.ngrok-free.dev   # 본인 ngrok 주소
```

- 로그인하면 서버와 ngrok 이 자동으로 켜지고, 꺼지면 10초 뒤 다시 켜집니다.
- 전원이 연결돼 있으면 맥이 잠들지 않게 막습니다. **뚜껑을 닫으면 잠드니 열어 두세요.**
- 재부팅 뒤에는 로그인해야 켜집니다. 시스템 설정 > 사용자 및 그룹 > 자동 로그인을 켜 두면 편합니다.
- 새 버전으로 바꿀 때는 ZIP 을 다시 받아 같은 명령을 실행하면 됩니다. 키 파일은 유지됩니다.
- 로그: `~/Library/Logs/kakao-news-bot/app.log`, `ngrok.log` / 끄기: `sh deploy/mac/uninstall.sh`

### 방법 B. 리눅스 서버 (클라우드 VM 등)

맥을 끄고도 돌리려면 항상 켜져 있는 서버가 필요합니다. 오라클 클라우드 무료 VM 같은 곳에
Java 21 과 ngrok 을 설치하고 아래처럼 systemd 로 등록합니다.

```bash
./mvnw package
sudo useradd -r -s /usr/sbin/nologin newsbot
sudo mkdir -p /opt/kakao-news-bot/data
sudo cp target/kakao-news-bot-0.1.0.jar /opt/kakao-news-bot/kakao-news-bot.jar
sudo cp secrets.properties /opt/kakao-news-bot/            # 제미나이 키
sudo cp deploy/newsbot.env.example /opt/kakao-news-bot/newsbot.env
sudo chmod 600 /opt/kakao-news-bot/secrets.properties /opt/kakao-news-bot/newsbot.env
sudo chown -R newsbot: /opt/kakao-news-bot
sudo cp deploy/kakao-news-bot.service /etc/systemd/system/
sudo systemctl enable --now kakao-news-bot
journalctl -u kakao-news-bot -f
```

ngrok 도 같은 방식으로 `ngrok http --url=https://<주소> 8080` 을 systemd 서비스로 등록하거나
`ngrok service install` 을 쓰면 됩니다.

## 6. 문제 해결

| 증상 | 원인과 해결 |
|---|---|
| `KOE006` | 리다이렉트 URI 미등록. REST API 키 설정에 `KAKAO_REDIRECT_URI` 와 똑같은 값을 등록 |
| `KOE010`, `invalid_client` | 클라이언트 시크릿 불일치. `KAKAO_CLIENT_SECRET` 확인 |
| `-402` | 메시지 전송 동의 없음. 동의항목 설정 후 `/kakao/login` 다시 |
| `KOE320`, `invalid_grant` | 인가 코드 재사용 또는 리프레시 토큰 만료. `/kakao/login` 다시 |
| 메시지는 오는데 링크가 안 열림 | 제품 링크 관리 > 웹 도메인에 기사 도메인 미등록 |
| 로그에 한글이 `???` 로 보임 | 서버 로케일 문제. `LANG=C.UTF-8` 설정 |
| 9시에 안 옴 | 앱이 꺼져 있었거나 기사가 0건. `journalctl` 로그 확인 |

연결 상태 확인:

```bash
curl "http://localhost:8080/kakao/status?token=$NEWSBOT_ADMIN_TOKEN"
```

---

## 7. 주의할 점

- **저작권**: 기사 본문을 긁어서 재배포하면 저작권 문제가 생길 수 있습니다. 이 봇은 제목과 링크만 보냅니다.
- **구글 뉴스 RSS**는 개인 용도로 쓰기 좋습니다. 채널로 여러 사람에게 보낸다면 언론사 공식 RSS와 각 이용 약관을 확인하세요.
- **광고성 정보**: 채널 친구에게 보내는 브랜드 메시지는 정보통신망법상 광고성 정보 규정을 따라야 합니다. 수신 동의를 받은 번호에만 보내세요.
- `data/kakao-token.json`, `newsbot.env` 는 비밀번호와 같습니다. 깃에 올리지 마세요. `.gitignore` 에 이미 넣어 두었습니다.

---

## 프로젝트 구조

```
src/main/java/com/example/newsbot
├── NewsBotApplication.java          # 진입점 (@EnableScheduling)
├── config/                          # 설정 바인딩, HTTP 클라이언트
├── news/                            # RSS 수집·파싱·중복 제거
├── delivery/
│   ├── NewsSender.java              # 발송 채널 인터페이스
│   ├── kakao/                       # ① 나에게 보내기 + OAuth 토큰 관리
│   └── solapi/                      # ② 브랜드 메시지 (HMAC 인증)
├── chatbot/                         # 카카오 챗봇 스킬 서버 (분야별 뉴스 5건, AI 대화)
├── ai/                              # 제미나이 API 클라이언트, 대화 기억
├── site/                            # (선택) 정적 뉴스 페이지 생성
├── job/DailyNewsJob.java            # 매일 09:00 실행
└── web/                             # 관리용 API (미리보기, 즉시 발송)
```

테스트 실행: `./mvnw test`
