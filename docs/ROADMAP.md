# 로드맵

> 지금까지 뭘 했고, 앞으로 뭘 할 건지.

---

## 완료된 Phase

### Phase 0: 기반 구축 (2026-01 ~ 02)
- [x] RSS 수집 → 본문 추출 → AI 요약 → Slack 발송 파이프라인
- [x] MCP 서버 13개 도구
- [x] PostgreSQL + Flyway 마이그레이션
- [x] Spring Security 인증 (폼 + Bearer)
- [x] Docker 기반 로컬 개발 환경

### Phase 1: 품질 게이트 (2026-03 초)
- [x] AI 중요도 점수 (0~1) + 임계값 기반 자동 분류
- [x] 2단계 필터링: 저비용 스크리닝 → 고비용 요약
- [x] 콘텐츠 안전성 검사 (광고, 혐오, 허위정보 차단)
- [x] 감사 로그 (모든 관리자 액션 기록)
- [x] 백엔드 테스트 1,589+, 프론트 테스트 1,145+
- [x] 레질리언스: 서킷브레이커, 세마포어, 작업 큐

### Phase 2: 사용자 자율화 + 어드민 강화 (2026-03 중)
- [x] 유저 셀프서비스 위자드 (5단계)
- [x] 주제 둘러보기 / DM 구독
- [x] 구독 관리 (토글, 설정 변경, 탈퇴)
- [x] 관리자 대시보드 강화 (KPI + 파이프라인 + 시스템 + AI비용)
- [x] 사이드바 뱃지 시스템 (4곳: 회원관리, 검토, 발송, 파이프라인)
- [x] 회원관리 리디자인 (요약카드, 칩필터, 검색, 재승인)
- [x] 시드 데이터 확충 (카테고리 10+, 소스 20+, 감사로그, 발송이력)
- [x] 종합 UX 리뷰 29개 항목 처리
- [x] 문서 정비 (API_REFERENCE, ADR, ONBOARDING, ROADMAP, DESIGN_STRATEGY)

---

## 진행 중

### Phase 2.5: 안정화 + 배포 전 품질 강화 (2026-03 말 ~ 04)
- [x] 16명 전문가 에이전트 종합 리뷰 (제품 6 + 개발 10, 28개 이슈 발견)
- [x] 보안 기본값 강화 (ENCRYPTION_KEY, SLACK_SIGNING_SECRET, SESSION_COOKIE_SECURE)
- [x] 승인 상태 체인 완성 (/api/me approvalStatus + ProtectedRoute 방어)
- [x] ErrorBoundary 추가 (React 에러 시 흰 화면 방지)
- [x] 시맨틱 색상 토큰 전환 (150건 raw Tailwind → CSS variable)
- [x] 다크모드 하드코딩 색상 제거 (AdminLayout, Sidebar, UserLayout, LoginPage)
- [x] FSD 계층 위반 정리 (QuickSetupWizard → features/, WithdrawDialog → shared/)
- [x] 서킷브레이커 지수 백오프 (30s → 60s → 120s → 300s)
- [x] 스레드풀 분리 (digest 5 + general 20)
- [x] Slack retry 개선 (InterruptibleSleep, 최대 블록 20초)
- [x] Gemini transient retry (2회 재시도, CB-aware)
- [x] 무제한 쿼리 안전 제한 (Store 레이어 .take(N))
- [x] 300유저 성능 최적화 (HikariCP 40, N+1 수정, 인덱스 추가)
- [x] 사용자 셀프 탈퇴 API (개인정보보호법 제36조 대응)
- [x] DB 백업 스크립트 (pg_dump + gzip + 7일 보존)
- [x] 프론트 서비스 테스트 80개 추가 (1,145 total)
- [x] E2E Playwright 전체 통과 (216 passed, waitForTimeout 130건 제거)
- [x] disabled 상태 WCAG AA 대비율 개선
- [x] 경쟁사 관리 기능 (CRUD, 자동 수집, SOV 분석, junction 다대다)
- [x] slackDefaultChannelId 제거 → opsLogChannelId로 대체
- [x] Slack member ID / DM channel ID 분리 (V88)
- [x] 프리셋 선택 시 ID 직접 참조 (복사본 생성 버그 수정)
- [x] 뉴스 채널 → 뉴스 소스 리네이밍
- [x] 발송 시간 통일 (8/12/18 슬롯)
- [x] formatRelativeDate KST 수정
- [x] 경쟁사 주간 요약 발송 (Slack Block Kit + AI 인사이트 + DM 모드)
- [x] Slack 운영 로그 재설계 (Block Kit + 3단계 프로파일 + 스레딩, PR feat/slack-ops-logs-redesign)
- [x] 발송 무손실 보장 (retry backoff + merged delivery + web fallback)
- [x] UX 일괄 폴리시 42개 이슈 (Copy/UX/IA+Motion/DataViz+Perf)
- [x] 구독 제한 완화 (total 8, 월간 cap 제거)
- [x] 프론트 커버리지 설정 (@vitest/coverage-v8)
- [x] E2E 테스트 개선 (waitForTimeout 130건 제거, auto-wait 패턴)
- [x] 백엔드 품질 개선 25건 (트랜잭션, 검증, 보안, 모니터링)
- [x] 뉴스 검토 일괄 UX v2 (feature flag + 20건 상한 + 2단계 확인, PR #327 후속)
- [x] UI 가독성 공용 컴포넌트화 (TruncatedText, EmojiWithText, MetaDot, CardTitle — 2026-04-17)
- [x] Review policy dashboard + AI recommend bulk UX (PR-1, 2026-04-19) — `/admin/review-queue` 상단에 카테고리 정책 현황 카드 그리드 + importance_score 히스토그램 + AI 추천 일괄 승인 (50건+ 스팟체크 필수)
- [x] Review policy rule engine MVP (PR-3-lite, 2026-04-19) — `exclude_event_types` 블랙리스트 + `zero_signal` 자동 EXCLUDE (default-on, runtime setting 으로 비활성 가능) + dry-run 프리뷰 + 자동 제외 감사 뷰 + REVIEW 복구. 모든 자동 판정은 `reviewed_by='policy-auto'` + `reason='rule:*'` 감사 로그 기록.
- [x] RSS 소스 품질 페이지 재설계 (2026-04-20) — 콘텐츠 레버 → RSS 소스 품질. `/admin/sources/quality` 신규 경로 + 구 경로 redirect, action-oriented KPI (검토 필요 / 신호 부족 / 평균 클릭률 / 총 발송), 정렬/필터 테이블, `SourceEditModal` 재사용, 비활성 기본 숨김 + 재활성화 시 `crawl_fail_count=0` 리셋.
- [x] PersonaSatisfaction 집계 제거 (2026-04-20, weak signal §8.0) — 참여 편향(2-5%) + 귀인 모호 + cell 희소 3중 이슈. `PersonaSatisfactionQueryHelper` + 2 endpoints + 3 DTOs + MCP tool description persona 언급 제거. `summary_feedback` 테이블은 유지 (raw 데이터 보존).
- [x] Slack 다이제스트 이모지 중복 재발 방지 (2026-04-20, PR #454) — line-level strip + 9 개 회귀 fixture + surrogate-pair-safe counting. `DigestServiceSanitizeSummaryTest` + `AGENTS.md` §1.3.1 규칙 등재.
- [x] 경쟁사↔조직 자동 동기화 + 조직 관리 사이드바 숨김 (2026-04-20, PR #455/#457, ADR-030) — `/admin/competitors` 단일 입력점 + `CompetitorOrganizationSynchronizer` mirror + V133 백필 + `/admin/organizations` 사이드바 숨김(URL/페이지 유지).
- [ ] Playwright 회귀 테스트 CI 자동화

### Phase 2.6: 법률/컴플라이언스 (배포 전 필수)
- [ ] 뉴스 저작권 합의 (언론사 라이선스 또는 전문 저장 범위 제한)
- [ ] 개인정보처리방침 작성 + 회원가입 동의 UI
- [ ] Google Gemini DPA (Data Processing Agreement) 확인
- [x] 개인정보 열람권(제35조) 대응: 본인 개인정보 JSON/CSV export API + 일 3회 rate limit + 감사 로그 (F10)

---

## 계획된 Phase

### Phase 3: Analytics Ontology + Content Levers (2026-04-18 진행)

> 배경/설계: `docs/superpowers/specs/2026-04-18-analytics-discovery.md`
> 운영자 레버 역방향 설계 (신호 → 표시) + Observability Boundary + 4차원 Layer B (이탈/유지/만족도/영향).

- [x] Category metadata 필드 (V123: `purpose`, `background`, `problem_statement`) + User `team` 필드 (V124) + `DepartmentNormalizer` + ProfileEditModal (PR #430)
- [x] Organization 엔티티 + Category 다대다 연결 (V125/V126) + `/admin/organizations` CRUD + OrganizationMultiSelect (PR #431)
- [x] Path-based Slack tracking URL `/api/track/click/slack/{sid}` + 재방문 집계 helper (24h < gap ≤ 30d) (PR #428)
- [x] ~~Content Levers 대시보드 (`/admin/content-levers`)~~ → RSS 소스 품질 페이지로 재설계 (2026-04-20) — PersonaSatisfaction 패널은 약한 신호로 제거, SourceQuality 만 `/admin/sources/quality` 에 재배치 (PR #429 → Phase 2.5 재설계 항목 참고)
- [x] Slack `link_shared` passive 공유 리스너 + `article_share_passive` 이벤트 + `links:read` scope 요구 (PR #432)

후속 (Phase 3 확장 후보):
- [ ] 주간/월간 트렌드 종합 리포트 자동 생성
- [ ] 인포그래픽 카드 (이미지 기반 요약)
- [ ] 카테고리별 감성 분석 (긍정/중립/부정)
- [ ] 역할별 맞춤 요약 (경영진 vs 실무자 다른 톤)
- [ ] 팀 기반 추천 ("같은 부서 동료가 많이 본 기사")
- [ ] **조직 관리 탭 재노출 조건 충족 시 사이드바 재등록** (ADR-030) — 고객사/파트너 기반 분석 소비자(예: 카테고리 링크 기반 필터, 도메인 unfurl 매칭) 중 1개 이상 실운영에 반영될 때 `adminRoutes.ts` 에 `organizations` 엔트리 재추가.
- [ ] **⏸ User Recipe Sharing (보류, 2026-04-20 ADR-031)** — 유저가 본인 구독 설정을 레시피로 공개 → 동료가 구독/변형. Round 1 리뷰 결과 critical 19건 + 전략 objection 4건 (empty launch 리스크 + ADR-029 교훈 충돌). **파일럿 6주 관찰(~2026-06-01) 후 아래 3 트리거 중 2개 이상 관측 시 MVP-minimal (Subscribe only) 로 재진입, 0~1개면 취소**:
  1. Admin 승인 큐 중앙값 대기시간 > 48h
  2. 파일럿 유저 피드백에서 "내 구독 설정 공유하고 싶다" 자발적 언급 3회 이상
  3. "다른 사람은 뭘 구독해요?" admin 문의 3회 이상

  → 스펙 원본(uncommitted): `docs/superpowers/specs/2026-04-20-user-recipe-sharing-design.md`, 결정 근거: `docs/ADR.md` ADR-031.

6주 관찰 기간 중(PR4 머지 후): sunset 기준에 따라 유지/축소/제거 결정 — 상세 `specs/2026-04-18-analytics-discovery.md` §10.5.

### Phase 4: 스케일링 (완료)
- [x] 소스 가상 스크롤 / 서버 사이드 페이지네이션 (300+ 소스 대응) — `@tanstack/react-virtual` 기반, 80건 초과 시 자동 활성화
- [x] 스케줄러 실시간 상태 추적 — `GET /api/admin/schedulers/status` + `SchedulerStatusPanel`
- [x] 리포트 스케줄 실행 이력 표시 — `GET /api/admin/reports/history` + `ReportDeliveryHistoryPanel`
- [x] 모바일 반응형 — Admin/User 모두 바텀 네비게이션 (`BottomNavigation`, `UserBottomNavigation`)

### Phase 5: 확장 (구상)
- [ ] SSO/OAuth 연동 (SAML, OIDC)
- [ ] Webhook 알림 (Slack 외 채널)
- [ ] PDF 리포트 내보내기
- [ ] 사용자 간 기사 공유 / 코멘트
- [ ] API 외부 공개 (파트너 연동)

---

## 성공 지표 추적

| 지표 | 목표 | 현재 | Phase |
|------|------|------|-------|
| 노이즈 비율 | < 10% | 측정 중 | 1 |
| 중복률 | < 5% | 측정 중 | 1 |
| 관리자 검토 시간 (100건) | < 4시간 | 측정 중 | 2 |
| 직원 "도움됨" 점수 | +25% | 미측정 | 3 |
| 직원당 월 비용 | < $2 | 측정 중 | 2 |
| 서비스 가동률 | 99%+ | 로컬 | 4 |
