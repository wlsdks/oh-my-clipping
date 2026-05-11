# Slack App Manifest — Phase 3 PR3b (link_shared) 후속 조치

> **대상 독자:** 워크스페이스 admin / 운영 담당자
>
> PR3b 배포 전 반드시 아래 절차를 수행해야 `share.passive.matched` 카운터가 올라간다.

PR3b 는 Slack `link_shared` 이벤트를 받아 다이제스트 URL 의 재공유를 passive 하게 감지한다. 이를 위해 Slack app 에 **새로운 OAuth scope** 와 **이벤트 구독** 이 필요하다. 추가된 권한이므로 워크스페이스 admin 의 재승인이 필수다.

---

## 1) Slack App Manifest 수정

Slack API 대시보드 → **App Manifest** 에서 다음을 추가한다.

### 1.1 OAuth scopes

```yaml
oauth_config:
  scopes:
    bot:
      - links:read        # ← 신규 (PR3b)
      # ... 기존 scope 유지
```

### 1.2 Event Subscriptions

```yaml
settings:
  event_subscriptions:
    bot_events:
      - link_shared       # ← 신규 (PR3b)
      # ... 기존 bot_events 유지
```

### 1.3 App unfurl domain 등록

`link_shared` 이벤트는 `event_subscriptions.app_unfurl_domains` 에 등록된 도메인의 링크가 공유될 때만 발동한다. `clipping-mcp-server.app.base-url` (기본 `APP_BASE_URL` 환경변수) 의 host 를 등록:

```yaml
settings:
  event_subscriptions:
    app_unfurl_domains:
      - clipping.example.com   # ← 실제 baseUrl host 로 교체
```

> Slack 은 unfurl domain 최대 5개까지 등록 가능. HTTP / HTTPS 둘 다 같은 도메인으로 간주된다.

---

## 2) 앱 재설치 (워크스페이스 admin 필수)

권한이 추가되었으므로 기존 토큰은 새 scope 를 포함하지 않는다.

1. Slack API 대시보드 → **Install App** → `Reinstall to Workspace` 클릭
2. 워크스페이스 admin 이 새 권한(`links:read`) 을 승인
3. 사내 공지:
   - "Clipping bot 이 새로운 권한을 요청합니다. admin 이 승인하도록 요청 바랍니다."
4. 재설치 후 발급된 `xoxb-` 토큰을 `SLACK_BOT_TOKEN` 에 반영 (변경되지 않을 수도 있으나 확인 필수)

---

## 3) 배포 전 감사 게이트 (AUDIT GATE)

> **⚠️ 코드 머지 ≠ 자동 운영 반영.**
> 아래 절차로 capture_rate 를 확인한 후에야 기능을 정식 운영으로 전환한다.

1. 배포 후 1주일간 운영 — 로그와 메트릭 관찰
2. Prometheus / Micrometer 에서 다음을 조회:
   - `share.passive.matched` (우리 tracking URL 매칭 성공)
   - `share.passive.rejected` (매칭 실패)
3. capture_rate 계산:
   ```
   capture_rate = matched / (matched + 발송 기사 수 대비 예상 공유 수 추정치)
   ```
   또는 단순 비교:
   ```
   matched / 총 link_shared 이벤트 수 >= 5%
   ```
4. **capture_rate >= 5% → 정식 운영 유지**
5. **capture_rate < 5% → 기능 비활성화 결정**
   - 코드 롤백 또는 `link_shared` 이벤트 구독 해제 (manifest 에서 제거)

---

## 4) 참고 자료

- Slack Events API · `link_shared`: https://api.slack.com/events/link_shared
- OAuth scope `links:read`: https://api.slack.com/scopes/links:read
- App Manifest 포맷: https://api.slack.com/reference/manifests

---

## 5) 검증 체크리스트

- [ ] Slack App manifest 에 `links:read` scope 추가
- [ ] `bot_events` 에 `link_shared` 추가
- [ ] `app_unfurl_domains` 에 baseUrl host 등록
- [ ] 워크스페이스 admin 이 앱 재설치 완료
- [ ] `SLACK_BOT_TOKEN` 값 재확인 (변경 시 환경변수 업데이트)
- [ ] 테스트 채널에 bot 초대 후 tracking URL 붙여넣기 → 로그에서 `share.passive.matched` 증가 확인
- [ ] 1주 운영 후 capture_rate 측정 → go/no-go 결정
