# Slack 앱 설정 가이드

이 문서는 Clipping 서비스의 Slack 연동에 필요한 앱 생성, 권한 설정, 토큰 발급 절차를 설명한다.
이 문서만 보고 처음부터 설정할 수 있도록 작성했다.

## 대상 독자 / 선행조건

- Slack 워크스페이스 관리자 권한 필요
- 서버 환경변수 설정 권한 필요

## 빠른 시작

1. Slack 앱 생성
2. Bot Token Scopes 설정
3. 앱 설치 (워크스페이스에 Install)
4. 토큰 복사 → 서버 환경변수 설정
5. Socket Mode 활성화 (선택)

---

## 1. Slack 앱 생성

1. https://api.slack.com/apps 접속
2. **Create New App** → **From scratch** 선택
3. App Name: `Clipping` (또는 원하는 이름)
4. Workspace: 대상 워크스페이스 선택
5. **Create App** 클릭

---

## 2. Bot Token Scopes 설정

**OAuth & Permissions** 페이지 → **Bot Token Scopes** 섹션에서 아래 스코프를 추가한다.

### 필수 스코프 (서비스 동작에 반드시 필요)

| 스코프 | 용도 | 사용 위치 |
|--------|------|----------|
| `chat:write` | 채널/DM에 메시지 전송 | 다이제스트 발송, 운영 알림, 사용자 DM 알림 |
| `im:write` | DM 채널 열기 | 사용자에게 승인/반려/실패 DM 전송 |
| `im:history` | DM 이력 읽기 | Socket Mode 이벤트 수신 시 DM 컨텍스트 확인 |
| `channels:history` | 공개 채널 이력 읽기 | Socket Mode 이벤트 수신 시 채널 컨텍스트 확인 |
| `users:read` | 사용자 정보 조회 | 봇 토큰 유효성 검증 (`auth.test`) |
| `users:read.email` | 사용자 이메일 조회 | 사용자 매칭 (선택적) |
| `app_mentions:read` | 앱 멘션 이벤트 수신 | Socket Mode에서 멘션 반응 |
| `commands` | 슬래시 커맨드 | MCP 도구 호출 (Socket Mode) |
| `reactions:write` | 이모지 리액션 추가 | 메시지 수신 확인 표시 |

### 권장 스코프 (관리 기능에 필요)

| 스코프 | 용도 | 사용 위치 |
|--------|------|----------|
| `channels:read` | **공개 채널 이름/정보 조회** | 런타임 설정에서 채널 이름 표시, 연결 테스트 |
| `groups:read` | **비공개 채널 이름/정보 조회** | 비공개 채널에 발송하는 경우 |

> **`channels:read` 미설정 시:** 서비스 동작에는 문제없지만, 관리 화면에서 채널 이름이 표시되지 않고
> 연결 테스트 시 "채널 조회 스코프가 없어 채널 상세 확인을 건너뜁니다" 경고가 나타난다.

### 전체 스코프 한 줄 복사용

```
chat:write,im:write,im:history,channels:history,channels:read,groups:read,users:read,users:read.email,app_mentions:read,commands,reactions:write
```

---

## 3. 앱 설치

1. **OAuth & Permissions** 페이지 상단 → **Install to Workspace** 클릭
2. 권한 확인 후 **Allow** 클릭
3. **Bot User OAuth Token** 복사 (형식: `xoxb-...`)

> **스코프를 변경한 후에는 반드시 Reinstall to Workspace를 해야 새 권한이 적용된다.**

---

## 4. Socket Mode 설정 (선택)

Socket Mode는 MCP 도구 호출과 실시간 이벤트 수신에 사용된다.
사용하지 않으면 이 섹션을 건너뛴다.

1. **Settings** → **Socket Mode** → **Enable Socket Mode** 활성화
2. **App-Level Token** 생성:
   - Token Name: `clipping-socket`
   - Scope: `connections:write` (자동 선택됨)
   - **Generate** 클릭
3. 생성된 App-Level Token 복사 (형식: `xapp-...`)

### Event Subscriptions 설정

Socket Mode 활성화 후:

1. **Features** → **Event Subscriptions** → **Enable Events** 켜기
2. **Subscribe to bot events** 에서 추가:
   - `app_mention` — 봇 멘션 시 반응
   - `message.im` — DM 메시지 수신
3. **Save Changes** 클릭

---

## 5. 서버 환경변수 설정

| 환경변수 | 값 | 필수 |
|---------|-----|------|
| `SLACK_BOT_TOKEN` | `xoxb-...` (Bot User OAuth Token) | 필수 |
| `SLACK_APP_TOKEN` | `xapp-...` (App-Level Token) | Socket Mode 사용 시 |
| `SLACK_CHANNEL_ID` | 기본 알림 채널 ID (예: `C0123456789`) | 필수 |
| `SLACK_SOCKET_MODE_ENABLED` | `true` / `false` | 선택 (기본: false) |
| `SLACK_AUTO_DIGEST_ENABLED` | `true` / `false` | 선택 (기본: false) |

### docker-compose 예시

```yaml
environment:
  SLACK_BOT_TOKEN: "xoxb-your-bot-token"
  SLACK_APP_TOKEN: "xapp-your-app-token"
  SLACK_CHANNEL_ID: "C0123456789"
  SLACK_SOCKET_MODE_ENABLED: "true"
  SLACK_AUTO_DIGEST_ENABLED: "true"
```

### application-local.yml (로컬 개발)

이미 `.gitignore`에 포함되어 있으므로 로컬에서 직접 작성한다:

```yaml
clipping-mcp-server:
  slack:
    bot-token: "xoxb-your-bot-token"
    app-level-token: "xapp-your-app-token"
    default-channel-id: "C0123456789"
    socket-mode-enabled: true
    auto-digest-enabled: false
```

---

## 6. 채널 ID 찾는 방법

Slack 채널 ID는 채널 URL이나 설정에서 확인할 수 있다:

1. Slack에서 해당 채널 열기
2. 채널 이름 클릭 → 하단의 **채널 ID** 복사 (형식: `C0123456789`)

또는 URL에서 확인:
```
https://app.slack.com/client/T.../C0123456789
                                    ^^^^^^^^^^^^ 이 부분이 채널 ID
```

### DM 채널 ID

사용자 DM 채널 ID (형식: `D...`)는 사용자 회원가입 시 입력하거나,
관리 화면에서 사용자 계정 정보에 설정한다.

---

## 7. 연결 확인

서버 기동 후 관리 화면 또는 API로 연결을 확인한다:

```bash
# 봇 토큰 + 채널 연결 테스트
curl -X POST http://localhost:8086/api/admin/runtime-settings/slack/verify \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"slackChannelId":"C0123456789"}'
```

정상 응답 예시:
```json
{
  "ok": true,
  "botUser": "clipping-bot",
  "team": "MyWorkspace",
  "channelId": "C0123456789",
  "channelName": "clipping-alerts",
  "message": "Slack 연결이 정상입니다."
}
```

`channelName`이 `null`로 나오면 → `channels:read` 스코프 추가 후 Reinstall 필요.

### Socket Mode 연결 테스트

```bash
curl -X POST http://localhost:8086/api/admin/runtime-settings/slack/socket-mode/verify \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{}'
```

---

## 8. 봇을 채널에 초대

봇이 메시지를 보내려면 해당 채널에 초대되어야 한다:

1. Slack에서 대상 채널 열기
2. `/invite @Clipping` 입력 (또는 봇 이름)
3. 또는 채널 설정 → **Integrations** → **Add apps** → Clipping 앱 추가

> 봇이 초대되지 않은 채널에 메시지를 보내면 `not_in_channel` 에러가 발생한다.

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| `invalid_auth` | 봇 토큰이 잘못됨 | 토큰 재확인, Reinstall 후 새 토큰 복사 |
| `not_in_channel` | 봇이 채널에 미초대 | `/invite @봇이름` 실행 |
| `missing_scope` | 필요한 스코프 없음 | 스코프 추가 → Reinstall |
| `channel_not_found` | 채널 ID가 잘못됨 | 채널 ID 재확인 (C로 시작) |
| `account_inactive` | 봇 앱이 비활성화됨 | Slack 앱 관리에서 재활성화 |
| 채널 이름 `null` | `channels:read` 스코프 없음 | 스코프 추가 → Reinstall |
| Socket Mode 연결 실패 | App-Level Token 없음/잘못됨 | `xapp-...` 토큰 재생성 |
| DM 전송 실패 | `im:write` 스코프 없음 또는 DM 채널 ID 미설정 | 스코프 확인 + 사용자 DM 채널 ID 설정 |

---

## 참고 링크

- [Slack API Scopes](https://api.slack.com/scopes)
- [Slack Bot Token](https://api.slack.com/authentication/token-types#bot)
- [Socket Mode](https://api.slack.com/apis/connections/socket)
- [Block Kit Builder](https://app.slack.com/block-kit-builder)
