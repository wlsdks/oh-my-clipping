# Architecture Decision Records (ADR)

> 주요 기술 결정과 그 이유. "왜 이렇게 했는지"를 기록한다.

---

## ADR-001: PostgreSQL만 사용 (Redis/Kafka 없음)

**상태**: 채택 (2026-02)

**맥락**: 비동기 작업 큐, 세마포어, 서킷브레이커가 필요한데 Redis나 Kafka를 도입할지 결정.

**결정**: PostgreSQL 하나로 모든 것을 처리한다.
- 작업 큐: `clipping_jobs` 테이블 + `SELECT ... FOR UPDATE SKIP LOCKED`
- 세마포어: `resource_semaphore` 테이블 (Gemini 최대 2, Slack 최대 3)
- 서킷브레이커: `circuit_breaker` 테이블 (CLOSED/OPEN/HALF_OPEN)
- 중복 발송 방지: `delivery_log` UNIQUE 제약조건

**이유**:
- 인프라 단순화 — 단일 진실 원천 (ACID 보장)
- 현재 규모 (300명, 일 2,000건) 에서 충분한 성능
- 서버 재시작해도 상태 유지 (in-memory 대비 안전)
- 향후 멀티 인스턴스 확장 시 코드 변경 없음

**트레이드오프**: 폴링 지연 (ms→sec), Redis 대비 약간 느림. 현재 수용 가능.

---

## ADR-002: 결정론적 파이프라인 + 선별적 AI

**상태**: 채택 (2026-03)

**맥락**: "AI Agent가 자율적으로 뉴스를 수집/요약/발송" vs "고정 파이프라인에서 AI를 도구로만 사용"

**결정**: 결정론적 파이프라인을 기본으로 하고, AI는 스크리닝과 요약에만 사용.
- 1단계: RSS 수집 (고정, 예측 가능)
- 2단계: AI 중요도 점수 (0~1, 저비용)
- 3단계: 임계값 이상만 AI 요약 (고비용)
- Ralph 오케스트레이션: 선택적, 기본 꺼짐

**이유**:
- 뉴스/컴플라이언스 맥락에서 **재현성 > 자율성**
- 운영자가 "왜 이게 포함됐는지" 설명 가능해야 함
- AI 비용 최적화: 전체 기사의 30%만 요약 → 비용 70% 절감
- 에이전트 루프 실패 시 전체 서비스 중단 위험 제거

**트레이드오프**: 새 기능 추가가 느림 (코드 변경 필요). 자율 에이전트 대비 유연성 낮음.

---

## ADR-003: React 19 + React Compiler

**상태**: 채택 (2026-03)

**맥락**: React 18의 `useMemo`/`useCallback` 보일러플레이트 vs React 19의 자동 최적화.

**결정**: React 19 + `babel-plugin-react-compiler` 사용. `useMemo`, `useCallback`, `React.memo` 금지.

**이유**:
- 컴파일러가 자동으로 메모이제이션 → 개발자 실수 제거
- 코드가 더 읽기 쉬움 (최적화 코드 없음)
- ESLint 플러그인 (`eslint-plugin-react-compiler`)이 위반 자동 감지

**트레이드오프**: 새 기술이라 에코시스템 미성숙. 일부 패턴에서 빌드 경고 발생 가능.

---

## ADR-004: Tailwind v4 CSS-first (config.js 없음)

**상태**: 채택 (2026-03)

**맥락**: Tailwind v3의 `tailwind.config.js` vs v4의 CSS-first 설정.

**결정**: Tailwind v4 사용. CSS 파일에서 `@import "tailwindcss"` + `@theme` 블록으로 설정.

**이유**:
- 디자인 토큰이 CSS 변수 (`--color-primary`, `--radius`)로 관리됨
- 다크모드가 자연스럽게 동작 (`dark:` 변형자)
- `tailwind.config.js` 관리 부담 없음
- 시맨틱 토큰 (`text-primary`, `bg-muted`) 강제 → 일관성

**트레이드오프**: v4 커뮤니티/레퍼런스가 v3 대비 적음.

---

## ADR-005: shadcn/ui 원본 유지 정책

**상태**: 채택 (2026-03)

**맥락**: shadcn/ui 컴포넌트를 프로젝트에 맞게 커스텀할지, 원본을 유지할지.

**결정**: `components/ui/`는 shadcn 원본을 최대한 유지. 커스텀이 필요하면 `components/shared/`에 래퍼 생성.

**이유**:
- shadcn 업데이트 시 교체 가능
- Radix 접근성 보장 유지
- 버그 발견 시 커뮤니티 수정 즉시 반영 가능

**예외**: `select.tsx`에 `max-h-[300px]` 추가 — 드롭다운 스크롤 버그 수정. 최소한의 변경.

---

## ADR-006: MCP Server (Model Context Protocol)

**상태**: 채택 (2026-02)

**맥락**: 관리 도구를 REST API로만 제공할지, AI 에이전트가 직접 호출할 수 있게 할지.

**결정**: Spring AI MCP Server (WebFlux SSE 기반)로 13개 도구 노출.

**이유**:
- Claude/AI 에이전트가 직접 수집/요약/발송 가능
- 관리자가 자연어로 "AI 뉴스 수집해줘" 가능
- REST API와 MCP 도구가 같은 서비스 레이어 공유 → 중복 없음

**도구 목록**: `admin_collect`, `admin_summarize`, `admin_send_digest`, `admin_export`, `admin_pipeline`, `admin_list_categories` 등 admin 도구 13개와 `user_list_categories`, `user_search_summaries` 등 user 도구 9개 — 총 22개 (PR-04 기준). 중복되던 `clip_search`/`clip_get_summaries`/`clip_get_original`은 user 전용 대응 도구로 통합 삭제되었다.

---

## ADR-007: Java 21 (Virtual Threads 미사용)

**상태**: 채택 (2026-02)

**맥락**: Java 21의 Virtual Threads 사용 여부.

**결정**: Virtual Threads 사용하지 않음. 기존 플랫폼 스레드 유지.

**이유**:
- WebFlux + JDBC = Virtual Threads 이점 제한적
- HikariCP의 `synchronized` 블록 → VT 피닝 위험
- 현재 8 스레드 (스케줄러 6 + IO 일부) 로 충분

**재검토 조건**: 사용자 500명 이상, 동시 요청 100+ 시.

---

## ADR-008: 폼 로그인 + Bearer 토큰 이중 인증

**상태**: 채택 (2026-03)

**맥락**: 웹 UI와 API를 같은 서버에서 서빙하면서 인증 방식 결정.

**결정**:
- 웹 UI: Spring Security 폼 로그인 → 세션 쿠키
- API 호출: `Authorization: Bearer <token>` → 토큰 검증
- CSRF: 비활성화 (SPA에서 불필요, Bearer 토큰이 대체)

**이유**:
- 웹 UI는 세션 기반이 자연스러움 (리다이렉트 플로우)
- MCP/외부 API 호출은 토큰이 편리
- 단일 SecurityConfig에서 경로 기반 분기

---

## ADR-009: Flyway 마이그레이션 + 개발용 시드 데이터 분리

**상태**: 채택 (2026-03)

**맥락**: DB 스키마 관리와 개발 데이터 주입 방식.

**결정**:
- 스키마: `db/migration/V*.sql` (Flyway, 프로덕션에서도 실행)
- 시드 데이터: `db/dev-seed/local-bootstrap.sql` (local 프로파일에서만 실행)
- 시드 패턴: `INSERT ... WHERE NOT EXISTS` (멱등성)

**이유**:
- 프로덕션에 테스트 데이터가 절대 들어가지 않음
- 서버 재시작할 때마다 시드 데이터가 안전하게 재적용됨
- 새 개발자가 빈 화면 없이 즉시 기능 확인 가능

---

## ADR-010: RSS 소스 헬스 판단 기준

**상태**: 채택 (2026-04)

**맥락**: 어드민 대시보드 v3 리디자인에서 "RSS 소스 헬스" 카드를 신설하면서 어떤 소스를 unhealthy로 분류할지 정해야 함.

**결정**: 다음 두 조건 중 하나라도 충족하면 unhealthy로 분류한다.
- `lastSuccessAt`이 24시간 이전이거나 null
- `crawlFailCount >= 3` (연속 실패 3회 이상)

unhealthy 리스트는 `lastSuccessAt` 오래된 순으로 정렬, **최대 5건만** 표시한다.

**이유**:
- 24시간: 대부분 RSS 피드는 일간 갱신 주기, 24시간 미수신은 명백한 이상
- 연속 3회: 일시적 네트워크 오류(1~2회)는 자동 복구 가능, 3회 이상은 구조적 문제
- 5건 제한: 대시보드 카드 가독성 (전체 상태는 healthyCount/totalCount로 충분)

**트레이드오프**:
- 일간 RSS(예: 뉴스레터)는 24시간 기준이 타이트할 수 있음
- 향후 소스별 주기 프로파일(`expectedIntervalHours`)을 도입하면 정확도 향상 가능
- 현재는 단순함 우선, 운영 데이터로 임계값 튜닝 후 고도화 예정

**영향**:
- 신규 컬럼: `rss_sources.last_success_at` (V73)
- 신규 API: `GET /api/admin/sources/health`
- 신규 서비스: `SourceHealthService`


---

## ADR-011: Persona Analytics 패키지 신설 (Slice 1)

**상태**: 채택 (2026-04-09)

**맥락**: `AnalyticsService.getPersonaStats()` 와 그 응답 DTO `PersonaStatsResponse` 가 죽은 필드 4개(`weeklySubscriptionDelta`, `customConversionRate`, `recentConversions`, `mostChurnedPreset`)를 하드코딩으로 반환하고, 라벨 명도 의미와 어긋난다(`customConversionRate` 는 사실 비율). 또한 활성 구독 정의가 단순히 `is_active=TRUE` 라 zombie 구독 포함으로 부정확하다. 이대로는 데이터 기반 프리셋 의사결정이 불가능하다.

추가로 검증 과정에서 PersonaStore 의 `countTotalSubscriptionUsers` / `countPresetSubscriptionUsers` 가 존재하지 않는 `batch_categories.user_id` 컬럼을 참조하는 사전 버그가 발견됐다 (5xx 발생).

**결정**: `service/analytics/` 패키지를 신설하고 다음 5 슬라이스로 나눠 구축한다.
- Slice 1: 이관 + 청소 + 기반 (이 PR)
- Slice 2: 주간 스냅샷 + 트렌드 + 백필
- Slice 3: 이상치 탐지 + Slack 리포트 + 관찰성
- Slice 4: 커스텀 페르소나 키워드 추출 (Lv2)
- Slice 5: 임베딩 클러스터링 + 프리셋 후보 채택 워크플로우 (Lv3)

활성 구독 판정은 `PersonaSubscriptionActivityJudge` 의 "발송 기회 기반 N=2" 룰로 통일한다 (직전 2 회 예정 발송 중 최소 1 회 SENT). Slice 1 에서는 컴포넌트만 도입하고 실제 와이어업은 Slice 2 의 weekly snapshot 에서 한다.

기존 `getPersonaStats()` / `PersonaStatsResponse` 는 제거 대상이었으며 Slice 3 에서 broken store 메서드와 함께 정리한다.

**이유**:
- 점진 출시 → 매주 기능 가치 확인 가능
- 기존 API 와 신규 API 가 공존하며 프론트 이관 부담 없음
- "발송 기회 기반" 정의로 zombie 자동 제외 (정책 확장)
- Lv3 까지 가야 "데이터 기반 프리셋 의사결정" 의 의사결정 루프가 닫힘

**트레이드오프**:
- 5 슬라이스로 나뉘어 전체 완료까지 시간 소요
- 신규 테이블 6 개 도입 (snapshot, subscription_state, batch_run, anomaly, embedding, cluster)
- Slice 1 단계의 라이브 스냅샷은 여전히 기존 `is_active=TRUE` 정의를 사용 (Slice 2 에서 정확한 정의로 전환)

**영향**:
- 신규 서비스 패키지: `service/analytics/` (PersonaAnalyticsService, ReadModel, ActivityJudge, AnalyticsTime, exception sealed class 등)
- 신규 마이그레이션: V75 (user_events.summary_id 컬럼) — Slice 2~5 에서 V76~V81 추가 예정
- 신규 엔드포인트: `GET /api/admin/analytics/personas/live`
- 새 frontend 탭: Analytics > Persona Insights
- 축소: PersonasPage > StyleStatsTab (158 → 53 라인)
- 함께 수정: `DigestService` 의 `LocalDate.now()` 3 건을 `ZonedDateTime.now(KST).toLocalDate()` 로 교체 (사전 검증에서 발견된 KST 버그)

**참고**:
- 스펙: `docs/superpowers/specs/2026-04-09-persona-usage-analytics-design.md`
- Slice 1 plan: `docs/superpowers/plans/2026-04-09-persona-analytics-slice-1-foundation.md`

---

## ADR-012: 경쟁사 전용 수집 파이프라인

**상태**: 채택 (2026-04)

**맥락**: 경쟁사 뉴스 수집을 기존 카테고리 파이프라인에 합칠지, 별도 파이프라인으로 분리할지 결정.

**결정**: 별도 `CompetitorCollectionScheduler`를 신설하고 `__competitor__` 시스템 카테고리로 격리한다.
- Google News RSS를 경쟁사 이름/별칭 기반으로 동적 생성
- `batch_summary_competitors` junction 테이블로 요약-경쟁사 다대다 매핑
- `__competitor__` 카테고리는 V87 마이그레이션으로 자동 생성 (일반 카테고리 목록에서 숨김)
- 수집 주기: 1일 2회 (07:10, 19:10)

**이유**:
- 경쟁사 수집은 검색 기반(Google News)이라 RSS 구독 기반 파이프라인과 성격이 다름
- 스케줄/빈도/소스 관리가 독립적 — 기존 4시간 수집 주기와 무관하게 운용 가능
- junction 테이블로 하나의 기사가 여러 경쟁사에 매핑 가능 (SOV 분석 지원)
- `__competitor__` 카테고리 격리로 일반 카테고리 통계/발송에 영향 없음

**트레이드오프**: 별도 스케줄러/서비스로 코드량 증가. Google News RSS에 의존적 (차단 시 대체 필요).

**영향**:
- 신규 마이그레이션: V84 (competitors + junction + RSS feeds), V85 (복합 인덱스), V87 (__competitor__ 카테고리), V89 (aliases + exclude_keywords)
- 신규 서비스: `CompetitorService`, `CompetitorCollectionScheduler`
- 신규 페이지: 관리자 경쟁사 관리, 사용자 경쟁사 뉴스 조회

---

## ADR-013: Slack Member ID와 DM Channel ID 분리

**상태**: 채택 (2026-04)

**맥락**: 기존에 `slackDmChannelId` 한 컬럼에 Slack user ID(`U...`)와 DM channel ID(`D...`)가 혼용되어 저장되고 있었다. DM 발송 시 `conversations.open`을 매번 호출하거나, 잘못된 ID 형식으로 발송 실패가 발생.

**결정**: `slack_member_id`(`U...`) 컬럼을 신규 추가하고, `slackDmChannelId`(`D...`)는 DM 채널 전용으로 분리한다.
- 사용자가 Slack member ID를 저장하면 서버가 `conversations.open`으로 DM 채널을 자동 획득
- 기존 데이터 하위 호환 유지

**이유**:
- 역할이 다른 두 ID를 한 컬럼에 넣으면 타입 안전성이 없고 버그 원인이 됨
- `conversations.open` 호출을 저장 시점 1회로 제한하여 발송 시 불필요한 API 호출 제거
- member ID는 멘션/프로필 조회에, DM channel ID는 메시지 발송에 각각 필요

**트레이드오프**: 컬럼 추가로 마이그레이션 1건 (V88). 기존 데이터 마이그레이션 불필요 (기존 slackDmChannelId 그대로 유지).

---

## ADR-014: 프리셋 선택 시 ID 직접 참조

**상태**: 채택 (2026-04)

**맥락**: 위자드에서 프리셋 페르소나를 선택하면 복사본을 생성해 사용자에게 할당하는 방식이었다. 복사본이 원본과 동기화되지 않아 관리자가 프리셋을 수정해도 기존 사용자에게 반영되지 않는 버그 발생.

**결정**: 프리셋 선택 시 복사본을 만들지 않고, 프리셋 persona의 ID를 직접 참조한다.

**이유**:
- 관리자가 프리셋을 수정하면 해당 프리셋을 사용하는 모든 구독자에게 즉시 반영 (일괄 관리)
- 불필요한 복사본 생성으로 인한 데이터 증가 방지
- "프리셋은 공유 자원"이라는 구독 모델 원칙에 부합

**트레이드오프**: 사용자별 커스텀이 필요하면 커스텀 페르소나를 별도 생성해야 함. 프리셋 수정의 영향 범위가 넓어짐 (의도된 설계).

---

## ADR-015: 경쟁사 주간 요약 Slack 발송

**상태**: 채택 (2026-04)

**맥락**: 경쟁사 뉴스 수집 데이터를 주간 단위로 요약하여 조직에 공유하는 방법 결정. 기존 다이제스트 파이프라인에 합칠지, 별도 스케줄러로 분리할지.

**결정**: `CompetitorWeeklyDigestScheduler`를 별도 신설하고, Slack Block Kit 메시지로 발송한다.
- SOV(Share of Voice) 변화율 + TOP 3 기사 + Gemini AI 인사이트
- 관리자가 요일/시간/채널/DM 모드를 RuntimeSettings로 설정
- DM 모드: off / all / selected
- 중복 방지: `report_delivery_log`의 period_key (ISO week)

**이유**:
- 주간 요약은 일일 다이제스트와 발송 주기/포맷/대상이 완전히 다름 — 별도 스케줄러가 관심사 분리에 유리
- Block Kit으로 SOV 변화를 시각적으로 표현 (delta emoji + 바 차트)
- AI graceful degradation: Gemini 실패 시에도 데이터 기반 정보(SOV, 기사 목록)는 정상 발송
- 1회/주 AI 호출로 비용 최소화

**트레이드오프**: 별도 스케줄러로 코드량 증가. AI 인사이트 품질이 데이터 양에 의존적.

---

## ADR-016: UX 일괄 품질 개선 (42개 이슈 배치 수정)

**상태**: 채택 (2026-04)

**맥락**: 코드 리뷰 및 사용성 테스트에서 42개 UX 이슈가 누적됨. 개별 PR로 처리하면 리뷰 부담이 과도하고, 하나의 배치로 묶으면 변경 범위가 넓어 회귀 위험이 있음.

**결정**: 4개 카테고리(Copy/UX/IA+Motion/DataViz+Perf)로 분류하여 4개 PR로 나눠 처리한다.
- PR #277: Copy — 톤 통일(해요체), 버튼 라벨, enum fallback 한국어
- PR #278: UX — window.confirm→ConfirmModal, Skeleton, pagination, 대시보드 인사말
- PR #279: IA+Motion — 고스트 라우트 제거, 분석 그룹 분리, 페이지 전환, 탭 fade, 사이드바 아코디언
- PR #280: DataViz+Perf — 차트 다크모드, 4개 페이지 lazy-load, html2canvas 동적 import, font-display swap

**이유**:
- 카테고리별 분리로 리뷰어가 한 PR에서 관련 변경만 검토 가능
- 회귀 발생 시 카테고리 단위로 롤백 가능
- E2E 테스트에서 waitForTimeout 130건 이상 제거하여 테스트 안정성 동시 개선

**트레이드오프**: 4개 PR 동시 관리 부담. 일부 변경이 카테고리 경계에 걸치는 경우 있음.

---

## ADR-017: Slack 텍스트 렌더링 엣지케이스 이중 방어 전략

**상태**: 채택 (2026-04)

**맥락**: 사용자가 입력한 카테고리명, AI가 생성한 기사 제목/요약/키워드, RSS 원문 링크가 Slack 다이제스트에 그대로 노출된다.
입력 저장 시점의 중립화만으로는 (1) LLM이 새로 생성하는 텍스트, (2) 과거 저장 데이터, (3) 외부 RSS 원문의 escape 누락을
모두 방어할 수 없다. 또한 Java `String.take(n)` 은 char 단위라 한글 조합 문자와 이모지 ZWJ sequence 를 중간에 잘라 깨뜨린다.

**결정**: 입력 저장 시점의 중립화(PR G)와는 독립된 렌더링 출력 경계 이중 방어 layer 를 추가한다.

- `SlackEscapeUtil` — HTML entity escape(`&<>`) + mrkdwn 포맷 escape(`*_~``) + 링크 label 치환 + URL scheme 화이트리스트 + Unicode bidi 오버라이드 중성화(`neutralizeBidiOverride`, U+202A~U+202E, U+2066~U+2069)
- `SlackTextNormalizer` — CRLF/U+2028/U+2029/zero-width/전각 공백 정규화
- `GraphemeTruncator` — `BreakIterator.getCharacterInstance(Locale.KOREAN)` 기반 grapheme cluster truncate
- `DigestService.buildDigestParagraphs` — `\s+` 를 `[ \t]+` 로 변경해 `\n` 을 paragraph 경계로 보존
- Slack section text 3,000자 한계를 그래프임 truncate 로 enforce
- `javascript:`/`data:` 등 unsafe scheme 은 button 제거 + fallback 문구
- `InputSanitizer` 저장 경계에서 동일한 bidi 제어 문자 9종을 제거해 이중 방어를 건다 (UI spoofing 차단)

**이유**:
- 저장 시점 중립화는 단일 방어선 — LLM 재생성/레거시 데이터/외부 RSS 변경에 취약
- 렌더링 출력은 Slack API 경계라서 escape 누락 시 사용자 화면에 즉시 노출됨
- `java.text.BreakIterator` 는 JDK 표준, 추가 의존성 없이 grapheme 지원 (가족 이모지/스킨톤 검증 완료)
- 3,000자 한계는 `length > 3000` 으로 1차 필터 후에만 BreakIterator 실행해 평균 O(1)

**트레이드오프**:
- escape 호출 추가로 렌더 비용 증가 (실측 영향 미미, per-digest 10~20회 호출)
- `BreakIterator` 는 Locale 의존적 — UAX #29 를 완벽히 따르지 않는 JDK 버전에서는 일부 최신 이모지 시퀀스를 분리할 수 있음.
  현재 대상 JDK(17+)에서 가족/스킨톤 기본 케이스는 통과 확인.
- `\s+` → `[ \t]+` 변경으로 기존 다이제스트 출력의 줄바꿈 표현이 바뀔 수 있음 (품질 향상 방향이므로 수용)

## ADR-018: Slack 에러 코드 5분류 처리 매트릭스

**상태**: 채택 (2026-04, PR F3)

**맥락**: Slack Web API 는 chat.postMessage 하나만으로도 70여 종의 에러 코드를 돌려준다. 기존 `SlackApiMessageSender.sendMessage` 는 모든 비(非)정상 응답을 단일 `DependencyFailureException("Slack API error: $code")` 로 바꿔 throw했다. 이 방식은 아래 문제를 만든다.

- 인증 실패(`invalid_auth`) 와 일시 rate limit(`ratelimited`) 이 동일한 severity 로 처리됨 → 장애 알림 피로도 증가
- `msg_blocks_too_long` 같은 payload 에러가 그대로 retry 대상으로 넘어가 orchestrator 가 같은 입력을 7회 재시도 → 사용자 알림은 끝내 누락
- JSON body 의 `ratelimited` 응답이 HTTP 429 와 다른 분기를 타서 `Retry-After` 존중이 일관되지 않음
- sender 내부 rate-limit 루프와 orchestrator 지수 백오프가 중첩되어 이중 재시도 발생 가능성

**결정**: Slack 에러 코드를 5 + 2(TRANSIENT/UNKNOWN) 카테고리로 분류하고 각 분류별 처치를 명시한다.

- `SlackErrorClassifier` (`support/SlackErrorClassifier.kt`) — 70여 에러 코드를 `SlackErrorCategory {AUTH, SCOPE, CHANNEL, PAYLOAD, RATE, TRANSIENT, UNKNOWN}` 로 매핑
- `SlackDeliveryFailureException` (`support/SlackDeliveryFailure.kt`) — 분류 결과와 `SlackFailureSeverity {INFO, WARN, CRITICAL}` 를 상위 알림기에 전달
- `SlackApiMessageSender.sendMessage` — 분기별 처치:
  - AUTH/SCOPE: 재시도 없이 CRITICAL throw (향후 F8 전용 채널 라우팅)
  - CHANNEL: WARN 로 throw, 카테고리 일시 비활성 시그널 (후속 PR 에서 auto-pause)
  - PAYLOAD: grapheme truncate 1회 재시도 → text-only fallback → `delivery_log.fallback_used=true`
  - RATE: `Retry-After` 를 `DependencyFailureException.retryAfterSeconds` 로 담아 throw → orchestrator 에 위임
- **재시도 경계 고정**: sender 내부 재시도 = payload 복구 1회만. rate limit/네트워크 재시도 = orchestrator 전담. 이중 재시도 금지.
- **V107 마이그레이션**: `delivery_log.fallback_used BOOLEAN NOT NULL DEFAULT FALSE` + composite index. 24h 내 반복 fallback 탐지에 사용.
- **fallback UX**: 첫 줄에 `⚠️ 일부 서식이 생략되었습니다. 전체 내용은 <{APP_BASE_URL}/user/news-report|Clipping에서 보기>` 를 붙이고 blocks 없이 text-only 로 전송.

**이유**:

- Slack 공식 에러 코드 카탈로그는 복잡하지만 운영 조치는 5가지로 충분 — 분류에 맞는 처치가 사용자 경험과 운영 피로도를 동시에 개선
- payload 에러는 "같은 입력으로 재시도해봐야 실패" 하므로 sender 에서 즉시 복구 시도 + fallback 이 최적
- rate limit 재시도 위치를 orchestrator 로 단일화해야 이중 재시도 회피 + 메트릭 단일화 가능
- fallback text 는 사용자가 중요한 알림을 놓치지 않도록 안전망 — 링크로 완전한 경험으로 복귀 가능

**트레이드오프**:

- `SlackMessageSender` 인터페이스에 `sendMessage` 메서드를 추가 → 기존 호출부(notifier/scheduler) 는 default 구현을 통해 영향 없으나, 발송 핵심 경로(`DigestService.sendDigestToSlack`) 는 새 메서드로 마이그레이션 필요
- `DependencyFailureException` 에 `retryAfterSeconds` 필드 추가로 일부 호출부의 equality 비교가 변동 (확인된 영향 없음)
- classifier 는 정적 매핑이므로 Slack 이 신규 에러 코드를 도입하면 UNKNOWN 으로 떨어져 TRANSIENT 로 취급됨 (허용 가능, 실 운영 시 로그 모니터링으로 감지)

**후속 작업** (별도 PR):

- CHANNEL 카테고리 → 카테고리 자동 pause 로직
- 24h 내 fallback 2회 이상 발생 시 관리자 Slack 알림
- F8 전용 채널 라우팅에 `SlackFailureSeverity.CRITICAL` 연결

---

## ADR-019: 낙관적 잠금 옵션 B 전면 적용 (F1)

**상태**: 채택 (2026-04-17)

**맥락**: 관리자 동시 편집 충돌 감지가 Category/RssSource에만 도입됐었고 Persona/CategoryRule은 마지막 저장자 우선(last-writer-wins)으로 동작. 스케줄러가 `updated_at`을 건드려 서로 다른 관리자 세션의 낙관적 잠금이 허위 충돌로 오작동할 수 있었다.

**결정**: 옵션 B — `updated_at` 컬럼을 **사용자 편집 시각**으로만 사용하고, 스케줄러/크롤러가 갱신하는 **시스템 상태 변경 시각**은 `system_updated_at` 컬럼으로 분리한다.

- **적용 대상**: `clipping_personas`, `batch_categories`, `rss_sources`, `clipping_category_rules` (4개 테이블) — V111 마이그레이션.
- **Store 규약**: `updateWithExpectedUpdatedAt(entity, expectedUpdatedAt)` 는 affected=1이면 갱신된 엔티티, 아니면 `null` 반환. 서비스 레이어에서 `ConflictException(staleEditInfo)`로 승격.
- **Scheduler-safe 경로**: `JdbcRssSourceStore.updateApproval / updateVerificationStatus / incrementFailCount / resetFailCount / deactivate / reactivate / updateReliabilityScores`, `JdbcCategoryStore.pause / resume`은 `updated_at`을 건드리지 않고 `system_updated_at`만 갱신.
- **API 응답**: `ConflictException`에 `staleEditInfo: StaleEditInfo?` 필드 추가. 409 body에 `latestUpdatedAt / latestEditorName / changedFieldNames / code="STALE_EDIT"` 포함 (`@JsonInclude(NON_NULL)`로 다른 충돌에서는 생략).
- **`CategoryRule.version → revision` 리네이밍**: 저장 시마다 `+1` 증가. `CategoryRuleResponse` DTO는 `revision`만 반환한다.
- **Force-overwrite 미제공**: F1 PR 1 스코프. 프론트에서 충돌 시 화면을 새로고침해 최신 상태를 읽은 뒤 재저장하는 플로우만 지원한다.

**이유**:

- `updated_at` 하나에 사용자 편집 시각과 시스템 상태 변경 시각이 섞이면 관리자가 "모달 열어놓은 사이 스케줄러가 `crawl_fail_count`를 증가시켜" 무관한 충돌을 맞는다. 두 시각을 분리하면 사용자 편집 루틴과 스케줄러 루틴이 서로 간섭하지 않는다.
- null-반환 규약은 Store를 얇게 유지하고 (도메인 예외/응답 구성은 서비스 책임), 다른 Store 구현(JPA, 테스트용 in-memory)에 동일한 계약을 강제하기 쉽다.
- `StaleEditInfo`를 응답에 실으면 프론트가 재조회 없이 "관리자B가 먼저 저장했습니다" 모달을 바로 렌더할 수 있다 (PR 2에서 구현).
- `version → revision` 리네이밍은 "낙관적 잠금 버전 카운터"로 오인되는 것을 막는다 (실제 잠금 기준은 `updated_at`).

**트레이드오프**:

- 컬럼 추가로 테이블당 디스크 8bytes 증가 × 4 테이블 = 무시 가능.
- 기존 `version` 응답을 쓰던 클라이언트는 `revision`으로 마이그레이션해야 한다.
- 스케줄러 경로가 많으므로 (9개 메서드) 신규 scheduler-style store 메서드 추가 시 "updated_at 건드리면 안 된다"를 잊기 쉽다 → 테스트로 회귀를 잠금 (`JdbcCategoryStoreTest`, `JdbcRssSourceStoreTest`).

**후속 작업** (별도 PR):

- PR 2: 프론트 충돌 모달 + 409 핸들러 + staleEditInfo 시각화
- PR 3: Idempotency-Key 헤더 지원
- PR 4: 버전 히스토리 감사 로그
- PR 5: Editing presence indicator
- `CategoryRuleResponse.version` 제거 완료. 신규 클라이언트는 `revision`만 사용한다.

---

## ADR-020: 엔티티 버전 히스토리 테이블 통일 (F1 PR 4)

**상태**: 채택 (2026-04-17)

**맥락**:

F1 옵션 B 낙관적 잠금(ADR-019)으로 편집 충돌은 감지할 수 있지만, 실수로 저장한 값을 "이 버전으로 되돌리기" 하는 UX와 저장 후 잠깐 동안 "되돌리기" action을 노출하는 Toast Undo 패턴이 공통으로 필요했다. 페르소나는 이미 `persona_versions` 테이블과 `PersonaVersionStore`를 갖고 있지만, Category/CategoryRule/RssSource는 별도의 변경 이력 수단이 없었다.

도메인별로 `*_versions` 테이블을 따로 두는 방식(v1)과, 4개 도메인을 한 테이블에 통합하는 방식(v2) 사이에서 선택이 필요했다.

**결정**: **통합 테이블 `entity_revision_history`** 방식을 채택한다 (V112 마이그레이션).

- 스키마: `(resource_type, resource_id, revision_number)` 복합 식별자. `snapshot`은 업데이트 직후 엔티티의 JSON 직렬화 문자열(TEXT).
- `append-only` 저장, Retention은 Phase 2 `DataCleanupScheduler`에서 통합 처리.
- 공용 엔드포인트: `GET /api/admin/{resource}/{id}/history?limit=20`, `POST /api/admin/{resource}/{id}/restore`
  - 지원 resource: `personas`, `categories`, `category-rules`, `sources` (4개)
- `restore`는 `expectedUpdatedAt` 필수. 복원된 상태도 새 revision으로 append되어 "되돌리기"도 이력에 남는다.
- 기존 `persona_versions`와 `PersonaVersionStore`는 롤백용으로 **유지**한다. 통합 이력 UI는 `entity_revision_history`를 본다.
- `EntityRevisionRecorder`가 Jackson + `findAndRegisterModules()`로 Instant를 ISO-8601 문자열로 직렬화해 사람이 DB 값을 검증할 수 있게 한다.
- 프론트에는 공용 `RevisionHistoryList` 컴포넌트와 `showSaveToastWithUndo` 유틸을 제공해 4개 mutation(PresetDetailModal, useSubscriptionMutations.editCategory, KeywordRulesDrawer, SourceEditModal)에서 동일한 Toast Undo UX를 제공한다.

**이유**:

- 도메인별로 테이블을 따로 두면 새 도메인을 추가할 때마다 migration + store + controller + 프론트 키/서비스가 중복되며, "되돌리기" 버튼 UI를 매번 재작성해야 한다.
- 통합 테이블은 retention 정책, 감사 쿼리("최근 1시간 동안 누가 뭘 바꿨는지"), 추후 diff viewer 구현 시 하나의 구조로 처리할 수 있다.
- `snapshot`은 검색 대상이 아니므로 PostgreSQL `JSONB` 대신 `TEXT`로도 충분하다 (H2 호환성 확보).
- persona_versions는 기존 롤백 UI가 의존하고 있어 하위 호환성을 위해 유지. 통합 이력이 나중에 주력이 되면 별도 PR에서 정리한다.

**트레이드오프**:

- 한 테이블이 4개 도메인 이력을 모두 저장하므로 행 수가 빠르게 커진다 → `idx_entity_revision_resource` (DESC revision_number)로 리소스 단위 조회는 O(log n) 유지, retention으로 제어.
- snapshot JSON 크기 상한을 두지 않았다 (Persona systemPrompt 5KB, 카테고리 규칙 keyword 리스트 등 최대 수십 KB 수준 예상). 특정 도메인이 비대해지면 그때 압축/ truncate 정책 도입.
- persona는 이력이 두 테이블(`persona_versions` + `entity_revision_history`)에 중복 저장된다. 쓰기 비용은 무시 가능하지만, 두 이력이 불일치할 가능성이 있으므로 PR 후속으로 `persona_versions` 사용처를 통합 이력으로 교체하는 것을 권장한다.

**검증**:

- `EntityRevisionStoreTest`: append → revision 1부터 단조 증가, listRecent 최신순 + limit, findById null 처리, resource 독립성
- `AdminPersonaServiceVersionTest`: 수정 시 append 여부 + 변경 없으면 skip + restore 경로(해피/stale/프리셋 제약)
- `AdminHistoryControllerTest`: GET history 2건/limit, POST restore 해피/stale/not-found
- `FlywayMigrationTest`: V112 테이블/컬럼/인덱스 생성 확인
- 프론트: `historyService.test` (URL 구성 + 응답 파싱 + 409 전파), `RevisionHistoryList.test` (접힘/펼침/빈 상태/되돌리기 confirm)

## ADR-021: 편집 presence heartbeat (Redis) (F1 PR 5)

**상태**: 채택 (2026-04-17)

**맥락**:

F1 PR 3에서 서버측 멱등 키(Idempotency-Key)와 PR 1~2에서 낙관적 잠금(STALE_EDIT) UX를 확보했지만, 두 관리자가 동일 리소스를 동시에 편집하다가 충돌이 뜨는 경험 자체는 여전히 남는다. 충돌을 "발생 후 복구"하는 흐름보다 "사전에 경고"하는 쪽이 관리자 에러를 크게 줄일 수 있다. 또한 편집 중에 다른 관리자가 저장해 버리면, 저장 시점까지 자기 편집이 stale 한 줄 모르고 입력을 계속하는 문제도 있다.

**결정**: **Redis TTL 기반 presence heartbeat + 편집 모달 내 변경 감지 띠**를 도입한다.

- 새 서비스 `EditingPresenceService`가 Redis `StringRedisTemplate` 를 재활용해 `editing:{resourceType}:{resourceId}:{userId}` 키에 JSON 값 `{userId, displayName, startedAt}` 를 TTL 60초로 저장한다.
- 새 컨트롤러 `/api/admin/editing-sessions` — `POST /heartbeat`, `DELETE`, `GET?resourceType=&resourceId=` 3 엔드포인트. `resourceType` 은 4종 화이트리스트(`persona`, `category`, `categoryRule`, `rssSource`)로 제한한다.
- 프론트 `useEditingPresence` 훅이 모달 open 시 즉시 heartbeat 1회 + 30초 간격 재heartbeat, TanStack Query 로 listActive 를 30초 poll한다. unmount/enable=false 시 즉시 release 호출.
- `EditingPresenceBadge` — 다른 관리자 presence가 1명 이상이면 "A님이 N분 전부터 편집 중이에요" 배지(Pill, warning 토큰)를 모달 헤더에 표시.
- `ChangeDetectionStrip` — 모달이 기억한 `initialUpdatedAt` 과 30초 주기로 재조회한 `currentUpdatedAt` 이 다르면 sticky 띠 + "최신 불러오기" 버튼을 제공. PR 2 staleEditBus 와는 독립적이며, 서버 저장 "전"에 선제적으로 감지한다.
- 4개 편집 UI — `PresetDetailModal`(persona), `OperationSidePanel`(category 편집 엔트리), `KeywordRulesDrawer`(categoryRule), `SourceEditModal`(rssSource) — 에 동일 훅/컴포넌트를 적용한다.

**이유**:

- Redis는 이미 Idempotency/RateLimit/Session 용도로 사용 중이라 새 인프라가 필요 없다. TTL 로 자동 정리되므로 별도 스케줄러/DB 트랜잭션이 필요 없다.
- 서버 재시작/크래시에도 TTL 60초 이내에 모든 presence 가 자동 증발해 유령 세션이 남지 않는다. heartbeat 주기 30초 로 1회 누락은 허용한다.
- displayName 을 서버가 조회(`AdminAuthService.findByUsername`) 해 응답에 포함시키므로 프론트에서 위조가 불가능하다. blank/null 이면 "관리자" 로 익명화.
- listActive 에서 호출자 본인 세션은 기본 제외한다 — "당신이 편집 중입니다" 는 사용자에게 불필요한 소음이다.

**트레이드오프**:

- Redis 장애 시 fail-open: heartbeat/release 는 경고 로그만 남기고 통과하며, listActive 는 빈 배열을 반환해 배지/띠가 뜨지 않는다. presence는 UX 보조 기능이라 편집 자체를 차단하지 않는다.
- 뱃지 상대시간("2분 전부터")은 클라이언트 현재 시간 - `startedAt` 으로 계산하므로 큰 시계 편차가 있는 환경에서는 정확성이 떨어질 수 있다. 분 단위 표시라 실무에서 문제가 되지 않는다고 판단.
- ChangeDetectionStrip 의 polling 주기(30초)는 낙관적 잠금과 중복이지만, 서버 저장 전에 사용자에게 알려 데이터를 잃을 위험을 줄인다. 필요하면 WebSocket/SSE 로 교체 가능.

**검증**:

- `EditingPresenceServiceTest`: heartbeat 최초/재호출(startedAt 유지) / release / listActive SCAN 수집 + excludeUserId 필터 / 깨진 JSON skip / Redis 장애 fail-open / 화이트리스트 거부
- `EditingPresenceControllerTest`: heartbeat 204 / unauthenticated 401 / invalid resourceType 400 / release 204 / list 빈 배열 / list 화이트리스트 거부
- 프론트: `useEditingPresence.test` (즉시/주기 heartbeat, enabled=false 무호출, unmount release) / `EditingPresenceBadge.test` (0/1/N editors) / `ChangeDetectionStrip.test` (동일 시 숨김, 변화 시 배너, 재조회 후 숨김)

## ADR-022: 감사로그 actor_id nullable + AuditActorResolver (2026-04-18)

**상태**: 채택

**맥락**:
V117에서 `audit_log.actor_id → admin_users.id` FK(`fk_audit_log_actor`, `ON DELETE SET NULL`)를 추가했는데, `ADMIN_API_TOKEN` Bearer 경로는 Spring Security principal로 가상 문자열(`"admin-api"`)을 사용한다. 이 값은 `admin_users`에 존재하지 않아 모든 admin Bearer API 호출이 audit write에서 FK 제약 위반(500 에러)을 일으켰다.

**결정**:
1. `AuditLogEntity.actorId: String?`, `AuditLogEntry.actorId: String?`, API 응답 DTO의 `actorId: String?` — 전부 nullable.
2. `JpaAuditLogStore.resolveActorId(principal)` — `admin_users.id` 에 존재하는 경우만 반환, 아니면 `null`. `actorName`은 principal 문자열을 그대로 보존해 trail 식별 가능.
3. 마이그레이션 V119에서 FK 먼저 drop → V120에서 nullable 스키마 + FK 재설정 (2단 migration).

**이유**:
- FK 설계 의도(`ON DELETE SET NULL`)와 코드가 일치 — 원본 actor가 삭제/존재하지 않아도 trail은 남는다.
- Bearer 토큰 운영 자동화(`ADMIN_API_TOKEN`)를 계속 사용할 수 있다.
- `actor_name`은 "admin-api" 리터럴이 그대로 기록되므로 "자동화 vs 사람" 구분이 로그에서 가능하다.

**트레이드오프**:
- `actorId is null` 인 감사로그가 섞인다. 관리 UI는 이를 "자동 시스템" 표시로 매핑해야 한다.
- 추후 Bearer 토큰마다 고유 계정을 만드는 방향으로 갈 수도 있으나, 현재 규모에서는 과잉 설계.

**검증**:
- `AuditLogStoreTest` — actor_id null 허용, actor_name 유지
- `GlobalExceptionHandlerTest` — Bearer principal 경로에서 audit write 500 안 남
- Integration: `ADMIN_API_TOKEN` 으로 DELETE 호출 → 200 + audit_log row에 `actor_id IS NULL, actor_name = 'admin-api'`

## ADR-023: 모바일 반응형 — 바텀 네비게이션 전환 (2026-04-18)

**상태**: 채택

**맥락**:
좌측 고정 Sidebar는 Admin 5그룹 15+ 메뉴를 트리로 보여주는 데 최적화되어 있었지만, 모바일 뷰포트(<768px)에서는 사이드바가 화면을 잠식하거나 숨김 상태로만 접근 가능해 정보 밀도가 매우 낮았다. 내부 사용자가 모바일에서도 뉴스 리포트/알림을 확인할 수 있어야 한다는 요구.

**결정**: Tailwind `md:` breakpoint(768px) 기준으로 분기 — 데스크탑은 Sidebar 유지, 모바일은 하단 고정 `BottomNavigation`(5탭). Admin/User 각각 별도 컴포넌트(`BottomNavigation`, `UserBottomNavigation`).

5탭 선정 기준: **사이드바 그룹의 대표 경로만**. Admin은 홈/콘텐츠/운영/분석/시스템 (5그룹 그대로), User는 홈/구독/탐색/리포트/기사. 세부 하위 메뉴는 해당 탭 진입 후 노출.

**이유**:
- 모바일에서 tap target 최소 48px 유지 가능.
- `aria-current="page"` + `role="navigation"` 으로 접근성 확보.
- Sidebar 상태(펼침/접힘) 로직과 독립적이라 SSR/초기 렌더 단순.

**트레이드오프**:
- 5탭으로 압축하면 일부 하위 메뉴(예: 시스템 설정 세부)는 한 번 더 클릭해야 한다 — 모바일은 상세 설정보다 조회 용도라는 가정.
- Desktop/Mobile 간 메뉴 구조가 달라지면 양쪽 동기 유지 부담.

**검증**:
- `BottomNavigation.test.tsx` / `UserBottomNavigation.test.tsx` — exact + prefix active 매칭, no-match 검증, `md:hidden` class 존재 확인
- E2E `a11y/responsive.spec.ts` — mobile/tablet/desktop 3 viewport로 heading 렌더 확인

## ADR-024: 스케줄러 실시간 상태 추적 (2026-04-18)

**상태**: 채택

**맥락**:
운영자가 "어제 주간 리포트가 왜 안 왔나?" 같은 질문에 답하려면 개발자가 로그를 뒤져야 했다. Spring의 `@Scheduled` 메타데이터와 실제 실행 이력이 UI에서 조회 불가.

**결정**: `SchedulerRunTracker` 인메모리 ledger(`record(name, success, durationMs, lastError)`) + Spring `CronExpression.parse`로 다음 실행 시각 계산 → `SchedulerStatusService`가 이 둘을 합쳐 `GET /api/admin/schedulers/status` 로 노출. 프론트 `SchedulerStatusPanel`이 1분 주기로 polling.

**이유**:
- 외부 추가 인프라 없이(Redis/DB 불필요) 즉시 가능.
- 인스턴스 로컬 데이터라 Prometheus `clipping.scheduler.runs` 메트릭과 역할 분리(집계는 메트릭, 개별 인스턴스 상태는 ledger).
- `lastError` 200자 trim — UI에 요약, 전체는 logs에.

**트레이드오프**:
- 멀티 인스턴스에서 인스턴스별 view만 가능 — 글로벌 집계는 Prometheus로. 운영 가이드에 명시.
- 서버 재시작 시 ledger 초기화 — 장기 이력은 별도 `report_delivery_log` 테이블 참조.

**검증**:
- `SchedulerStatusServiceTest`(9) + `SchedulerRunTrackerTest` +7 + 프론트 panel 테스트 6

## ADR-025: 페르소나 인사이트 — risk/growth 이중 렌즈 랜딩 (2026-04-19)

**상태**: 채택

**맥락**:
Analytics > Persona Insights 탭은 "실시간 현황 + 템플릿 포트폴리오 + 최근 커스텀 스타일" 3섹션으로 구성되었으나, 90% 이상의 주차에서 "위험 페르소나 0개"로 플랫한 트렌드 차트 7줄만 겹쳐져 **운영자에게 '지금 무엇을 해야 하는가'의 답을 주지 못했다**. 반대로 잘 되고 있는 페르소나(프로덕트 관점의 복제·확장 후보)도 드러나지 않아 프로덕트 의사결정 근거 부족.

**결정**:
탭 랜딩을 **두 축**으로 재조직한다:
1. **위험 섹션** (운영 관점) — `CHURN_EXCESS` / `IDLE` / `ENGAGEMENT_DROP` 3 타입을 카드로, 지속 주차 오름차순 정렬
2. **성장 섹션** (프로덕트 관점) — `SUBS_SURGE` / `ENGAGEMENT_RISE` / `FIRST_SUBSCRIPTION` 3 타입을 공유 카드 primitive 로 positive framing

신호 계산은 `/api/admin/analytics/personas/signals` 단일 엔드포인트에서 수행 (PR #402 — `PersonaRiskClassifier` + `@ConfigurationProperties("analytics.risk"/"analytics.growth")` 임계치). 프론트는 `PersonaSignalCardBase` 공유 primitive 로 tone("risk"/"growth")만 다르게 넘긴다 (PR #407).

**이유**:
- 위험 0 주차에도 성장 섹션이 살아있어 **페이지가 쓸모있다**.
- 스펙 §1 의 "배치 실패 배너" 모드(`aria-hidden="true"` + `pointer-events-none`)로 신선도 의심 상태에서 수치 오독 방지.
- 신호 판정은 Kotlin sealed class `SignalDetails` + `@JsonTypeInfo(use=NAME, property="type")` 로 타입 안전 (`Map<String, Any>` 금지) — 6개 서브타입 (`ChurnExcessDetails`, `IdleDetails` 등).
- 임계치는 `analytics.risk.*` / `analytics.growth.*` YAML → 코드 상수 아님. 파일럿 관찰 결과 반영한 튜닝 부담 최소.

**트레이드오프**:
- 공유 primitive 유지 비용 — 위험/성장 카드 중 하나가 새 CTA 를 추가하면 다른 쪽 영향 고려 필요.
- `bannerActive` 시 전체 영역 `aria-hidden` — 스펙대로지만 E2E 테스트가 이 상태에서 heading 을 못 찾는 부작용 (PR #426 에서 mock 으로 우회).
- 기존 "프리셋 포트폴리오 테이블" 삭제 — `PortfolioSummary` 의 excluded tooltip 으로 기능 일부 대체하나 상세 표는 빠짐. 포트폴리오 상세는 향후 별도 탭으로 재개할 여지.

**검증**:
- 백엔드: `PersonaRiskClassifierTest` (24+ 케이스) + `PersonaAnalyticsReadModelTest` + `PersonaAnalyticsBackfillServiceTest`(cache invalidation)
- 프론트: `utils/personaRisk.test.ts`(26) + `AtRiskPersonaCard.test.tsx`(3) + `GrowthPersonaCard.test.tsx`(3) + `PersonaInsightsTab.test.tsx`(3) + Playwright smoke
- 스펙: `docs/superpowers/specs/2026-04-17-persona-insights-redesign-design.md` §1–§8
- 관련 PR: #402(BE PR-B), #407(FE PR-C), #424(sample marker), #426(E2E sync)

---

## ADR-026: Phase 3 — Ontology-driven analytics (Category metadata + Organization + User team)

**상태**: 채택 (2026-04-18)

**맥락**:
Phase 3 의 목표는 "대시보드 신규 추가" 가 아니라 **운영자가 어떤 레버를 가졌고, 그 레버를 당기기 위해 어떤 신호가 필요한가** 를 먼저 정의하는 것이다 (`docs/superpowers/specs/2026-04-18-analytics-discovery.md` §5). 그런데 코드 검증 결과 **본질 엔티티가 부족**했다.

- `admin_users` 에 부서/팀이 없음 → 부서별 만족도 drill 불가.
- `batch_categories` 에 "이 카테고리 왜 만들었는지"(purpose, background, problem) 메타데이터 없음 → 영업용/리서치용 구독 구분 못 함.
- **Organization (경쟁사/고객사) 엔티티 자체 부재** — 원래 제품의 핵심 목적인데 구조화되지 않아 Category 이름에 "MegaCorp" 하드코딩.

**결정**: 대시보드 추가 **전에** Ontology 를 먼저 확장한다.
- `batch_categories` — `purpose VARCHAR(32) CHECK IN ('SALES','RESEARCH','COMPETITIVE','CUSTOMER_CARE','OTHER')`, `background TEXT`, `problem_statement TEXT` (모두 nullable, V123)
- `admin_users` — `team VARCHAR(64)` 추가 (V124; `department` 는 V23 기존). `DepartmentNormalizer` (trim + lowercase + 공백 압축) 로 "HR ", "hr", "h r" 동일 취급.
- `organizations(id, tenant_id DEFAULT 'default', name, type CHECK IN ('COMPETITOR','CUSTOMER','PARTNER','OTHER'), domain, description)` (V125) + `category_organizations` join (V126, `ON DELETE CASCADE`, tenant-scoped UNIQUE).

**이유**:
- Ontology 확장 없이 대시보드를 만들면 **집계 차원이 카테고리 하나뿐** — 다음 drill 에서 막힌다.
- `tenant_id DEFAULT 'default'` 로 외부 판매 (multi-tenant) 대비 미리 column shape 확보.
- `purpose` CHECK 제약으로 자유 텍스트 대신 enum 강제 — 집계 가능한 형태.

**트레이드오프**:
- 기존 카테고리 백필이 필요하지만 자동화 안 함. 상위 30개 카테고리 수동 매핑을 **acceptance gate** 로 두어 stale 상태 방지 (`2026-04-18-analytics-discovery.md` §9 PR2).
- Organization 연결률이 낮으면 "아직 회사 연결 없음" 배너로 UX 가이드.

**관련 PR**: #430 (metadata + team), #431 (Organization CRUD).

**관련 문서**: `docs/superpowers/specs/2026-04-18-analytics-discovery.md` §3.3, §9 PR1/PR2.

---

## ADR-027: Path-based Slack tracking URL (`/api/track/click/slack/{sid}`)

**상태**: 채택 (2026-04-18)

**맥락**:
Phase 3 분석의 출발 신호인 **클릭 count per 소스** 는 지금까지 `ArticleClickTrackController` 에서 `?sid=...&url=...` 쿼리로만 처리했고 **어떤 채널에서 온 클릭인지** 구분이 없었다. 아이디어 초안은 `?source=slack` 쿼리 파라미터를 추가하는 것이었다.

**결정**: 쿼리 파라미터 대신 **경로 기반** 엔드포인트 `/api/track/click/slack/{sid}` 를 도입한다. 레거시 `/api/track/click?sid=...` 는 backward compat 용으로 유지.

**이유**:
- **복붙/북마크 survive**: 사용자가 Slack 메시지의 링크를 다른 곳에 복사/북마크해도 경로는 보존된다. 쿼리는 공유 과정에서 자주 떨어져 나간다.
- **Referer fallback 독립**: Referer 헤더가 `*.slack.com` 이면 경로 기반이 아니어도 `source='slack'` 로 태깅 (사내 Slack 앱에서만 신뢰).
- **Allowlist 정규화**: `UserEventService.saveClick(..., source)` 가 `source` 를 allowlist (`"slack"`) 로만 받아들이고 그 외는 null 로 저장 — 악의적 쿼리 오염 차단.
- URL 자체가 dimension 이 되므로 access log 분석만으로도 source 분포 확인 가능.

**트레이드오프**:
- 두 경로를 동시에 운영해야 함. 6개월 이상 레거시 링크가 살아 있으므로 정리 지점은 미정.
- source allowlist 에 새 채널 (이메일 등) 추가 시 enum 같은 관리가 필요 — 현재는 상수 Set.

**관련 PR**: #428.

**관련 문서**: `docs/superpowers/specs/2026-04-18-analytics-discovery.md` §6.4, §9 PR3a.

---

## ADR-028: Slack `link_shared` passive listener (vs custom share button)

**상태**: 채택 (2026-04-18, Pre-deploy audit gate 적용)

**맥락**:
"공유" 는 Phase 3 에서 선택한 4개 신호 중 가장 강하지만(팀 전파 = 높은 가치 주장), 수집이 까다롭다. 옵션은 두 가지였다.

1. **A. 커스텀 공유 버튼**: digest 메시지에 "채널 공유" 버튼 추가. 클릭 시 서버에 기록.
2. **B. Slack `link_shared` 이벤트 구독 (passive)**: Slack 에서 URL 포함 메시지를 workspace 에 올리면 Slack 이 알아서 이벤트를 발송.

**결정**: B (passive listener) 를 채택. 단, **배포 전 manual audit gate** 를 필수로 둔다 (1주 운영 후 `capture_rate ≥ 5%` 조건).

**이유**:
- A 는 **Slack native forward 와 UI 중복**. Slack 에서 이미 작동하는 "메시지 → 다른 채널로 전달" 을 우리가 다시 UI로 만들면 사용자 혼란.
- B 는 bot scope `links:read` 만 필요 — `message` scope 보다 프라이버시 노출 적음. URL 포함 메시지만 Slack 이 선별 전달 → 처리량 절감.
- Dedup: `user_events (summary_id, target_channel_id, slack_message_ts)` partial UNIQUE index 로 Postgres 에서 idempotency 보장 (`link_shared` 는 메시지 edit/delete 에 재발생).

**트레이드오프**:
- **커버리지 ~50-70% 수용**. Bot 이 없는 DM/외부 워크스페이스/미초대 채널 공유는 못 본다 — 이는 측정 포기가 아니라 **명시적 Observability Boundary**.
- Slack app manifest 변경 + **워크스페이스 admin 재승인** 운영 task 필요 (`docs/SLACK_APP_MANIFEST.md` 참고).
- `capture_rate < 5%` 가 나오면 **기능 자체 drop** — 300 user 규모에서 신호가 의미 없으면 유지 비용이 가치보다 크다.

**관련 PR**: #432.

**관련 문서**: `docs/superpowers/specs/2026-04-18-analytics-discovery.md` §6.5, §9 PR3b, `docs/SLACK_APP_MANIFEST.md`.

---

## ADR-029: PersonaSatisfaction 신호 사용 중단 (2026-04-20)

**상태**: 채택 (2026-04-20).

**결정**: `personaSatisfaction` 집계 + 관련 대시보드 섹션/API/DTO 를 전면 제거한다. 다만 원본 데이터 수집(`summary_feedback` 테이블 + 유저의 👍/👎 토글 API)은 **그대로 유지** — 추후 대체 지표 설계 시 재활용 여지 확보.

**배경**:
`/admin/content-levers` 페이지의 핵심 섹션이었던 PersonaSatisfactionPanel 은 Phase 3 PR #429 에서 도입됐으나, 1 주 운영 결과 3 중 이슈로 판단 근거가 되지 못함이 드러남.

1. **참여 편향** — 전체 독자 대비 👍/👎 표시율이 2~5% 수준. 행복한 다수가 침묵하고 일부만 반응 → 전체 만족도의 proxy 로 쓸 수 없음.
2. **귀인 모호** — "별로였다" 가 무엇에 대한 것인지 분리 불가: 기사 자체 / 요약 템플릿 / RSS 소스 품질 / 배송 타이밍 → 개선 액션을 뭘 해야 할지 판단 불가.
3. **Cell 희소** — `(카테고리 × 템플릿 × 기간)` 매트릭스에서 대부분 cell 의 N < 5. 통계적 유의성 없이 순위가 요동쳐 운영자 의사결정에 오히려 해로움.

**고려한 대안**:
- **(a) 유지** — 유지 비용(FE 패널 + 2 endpoints + 3 DTOs + helper + 테스트) 대비 의사결정 가치 없음. 기각.
- **(b) 이 PR — 제거** ✅. 페이지 IA 를 "RSS 소스 품질 관리" 단일 목적으로 단순화. 약한 신호 코드는 빠르게 걷어내어 진짜 유의미한 지표 공간 확보.
- **(c) 선택률 기반 대체 지표** — "digest 에 포함된 기사 중 실제 클릭된 비율" 등 참여 편향이 덜한 지표로 교체. 이 PR 범위 밖 — 추후 개별 PR 로 설계/검증.

**영향 범위**:
- **Backend**: `PersonaSatisfactionQueryHelper` 삭제 + `AnalyticsContentLeversService` 에서 persona 집계 로직 제거 + 2 endpoints (`/persona-satisfaction`, `/disliked-articles`) 삭제 + 3 DTOs (`PersonaSatisfactionRow`, `CategorySatisfactionRow`, `DislikedArticleRow`) 삭제 + MCP tool description 의 persona 언급 제거. `ContentLeversSummary.personaSatisfaction` 필드 제거.
- **Frontend**: `PersonaSatisfactionPanel.tsx` + `SourceQualityPanel.tsx` + 관련 타입/테스트 전체 삭제. `ContentLeversPage.tsx` → `SourceQualityPage.tsx` 로 경로 이전(`/admin/content-levers` → `/admin/sources/quality`, 구 경로 redirect).
- **API surface**: `GET /api/admin/analytics/content-levers/summary` 는 **유지**하되 응답에서 `personaSatisfaction` 필드 제거, `sourceQuality` 만 반환. `/persona-satisfaction` + `/disliked-articles` 두 sub-endpoint 는 완전 삭제.
- **MCP**: `admin_content_levers_summary` tool description 에서 persona 관련 문장 제거. rate limit 유지.
- **DB**: 마이그레이션 없음. `summary_feedback` 테이블 + 관련 read API (사용자 토글)은 모두 유지.

**트레이드오프**:
- 👍/👎 토글 UI 는 유저 앱에 유지 — 수집은 계속된다. 대체 지표 설계 시 재활용.
- "제거했다가 재도입" 이 되면 데이터 helper 를 다시 써야 함. 단, git history 에 코드가 남아있고 `summary_feedback` raw 데이터 보존 덕에 복구 비용은 제한적.

**관련 PR**: (이 PR — 브랜치 `feat/source-quality-redesign`).

**관련 문서**: `docs/DESIGN_STRATEGY.md` "RSS 소스 품질" 섹션, `docs/ROADMAP.md` Phase 2.5 / Phase 3 추가 항목.

## ADR-030: 경쟁사↔조직 자동 동기화 + 조직 관리 탭 사이드바 숨김 (2026-04-20)

**상태**: 채택 (2026-04-20).

**결정**: 경쟁사(`competitor_watchlist`) 와 조직(`organizations`) 을 **단일 입력점** 으로 통합한다:

1. 경쟁사 CRUD 는 **`/admin/competitors` 에서만** 수행. COMPETITOR 타입 organizations 는 `CompetitorOrganizationSynchronizer` 가 자동 mirror.
2. `/admin/organizations` 페이지는 **사이드바에서 숨김** (라우트/페이지/서비스/DB 는 유지). 직접 URL 진입 시 COMPETITOR 행은 읽기 전용 + "경쟁사 관리에서 편집" 안내.
3. V133 백필 마이그레이션으로 기존 경쟁사 N 건을 idempotent 하게 organizations 에 복사.

**배경**:
Phase 3 PR #431 로 organizations 엔티티가 도입됐으나, 파일럿 직전 리뷰에서 3 중 문제 확인:

1. **데이터 중복** — `competitor_watchlist` 와 `organizations(type=COMPETITOR)` 가 동일 개념을 두 테이블에 분리 저장. 관리자가 어디에 등록해야 할지 혼란.
2. **반쪽 기능** — `organization.domain` 필드를 소비하는 로직 없음 (Slack unfurl 미연결). `category_organizations` 링크도 LLM/필터/분석에서 미소비.
3. **파일럿 UX 오염** — "조직 관리 탭에서 경쟁사를 또 등록해야 하나?" 라는 질문이 첫 온보딩에서 반복 발생 가능.

**고려한 대안**:
- **(a) organizations 완전 삭제** — V125/V126/V131 되돌리기, OrganizationService/Tool/Page 모두 제거. Phase 3+ 에서 재설계 시 재생성 비용 큼. 기각.
- **(b) 두 탭 병존 + 수동 기입** — 사용자가 경쟁사를 양쪽에 따로 관리. 실수/drift 발생 구조적 보장 없음. 기각.
- **(c) 이 PR — 통합 + 자동 mirror + 사이드바 숨김** ✅. 경쟁사는 한 곳에서만 입력, organizations 는 추후 고객사/파트너 분석 소비자 붙을 때 재노출.

**영향 범위**:
- **Backend**: `CompetitorOrganizationSynchronizer` 신설 (`onCompetitorCreated` / `onCompetitorRenamed` / `onCompetitorDeleted`). `CompetitorWatchlistService` 의 create/update/delete 경로에서 호출. 예외 격리 — mirror 실패가 경쟁사 CRUD 를 블록하지 않음 (`DataAccessException` 만 narrow catch + 로그).
- **DB**: V133 마이그레이션 — `INSERT INTO organizations ... WHERE NOT EXISTS` 기반 idempotent 백필. COMPETITOR type 으로만 복사.
- **Frontend**: `/admin/organizations` COMPETITOR 행은 편집/삭제 버튼 대신 "경쟁사 관리에서 편집" pill link. Organization 생성 폼 드롭다운에서 COMPETITOR 제외. `/admin/competitors` 상단 info banner — "조직 관리에도 자동 동기화돼요". 사이드바 `adminRoutes.ts` 에서 `organizations` 엔트리 제거 (라우트는 `router/index.tsx` 에 유지).

**트레이드오프**:
- COMPETITOR type organizations 는 항상 경쟁사에서 파생 — organizations API 만 보는 외부 도구가 있다면 "CUSTOMER/PARTNER/OTHER 만 가능" 제약 반영 필요. 현재는 없음.
- 재노출 조건을 ROADMAP 에 명시: "고객사/파트너 기반 분석 기능(예: 카테고리 링크 기반 필터, 도메인 unfurl 매칭) 중 1개 이상 실운영 소비가 확정될 때".
- 사이드바 숨김은 코드 레벨이 아닌 설정(`adminRoutes.ts`) 수준 — 재노출 비용은 1 커밋.

**관련 PR**:
- PR #455 (추정 — 경쟁사↔조직 auto-sync + V133 백필 + UI mirror)
- PR #457 (조직 관리 사이드바 숨김)

**관련 문서**: `AGENTS.md` §4.x 경쟁사↔조직 동기화 정책, `docs/ROADMAP.md` Phase 3 재노출 조건 항목.

## ADR-031: User Recipe Sharing 피처 보류 (2026-04-20, 파일럿 증거 수집 후 재평가)

**상태**: 보류 (2026-04-20). 2026-06-01 경 재평가 예정.

**결정**: "유저가 본인 구독 설정을 레시피로 공개 → 동료가 구독/변형" 기능의 구현을 **파일럿 6 주 관찰 후로 연기**한다. 설계 스펙은 `docs/superpowers/specs/2026-04-20-user-recipe-sharing-design.md` 에 보존하되 미구현 상태로 둔다. 재평가 시점에 **MVP-minimal (Subscribe only)** 로 스코프 축소해 진입 여부를 결정한다.

**배경**:
2026-04-20 브레인스토밍 세션에서 5 개 결정축(Subscribe vs Clone / Attribution / Discovery / Quality signal / Fork chain)을 확정하고 초안 스펙 작성. 같은 날 4 명 전문가 에이전트 병렬 리뷰(Backend Architect / UX / Product Strategy / Security) 실행. 결과:

- **Critical 19 건** (스키마 오류, 마이그레이션 SQL 실행 불가, 보안 IDOR/prompt injection 등)
- **전략적 objection 4 건** — 특히 "admin 병목" 전제가 **검증되지 않은 가설**, 파일럿이 오늘 시작하는데 20 명 규모에서 creator density ~5% 가정 시 **Week 1 크리에이터 1 명** → Discovery 페이지 3 탭 + 7 태그가 **empty launch 고정** 됨
- **ADR-029 (같은 날 머지) 교훈과 정면 충돌** — CTR 뱃지 / 구독자 5+ 게이트는 조직이 방금 rejected "약한 신호" 패턴 (참여 편향 + cell 희소 + 귀인 모호) 재현

**고려한 대안**:
- **(a) 현 스펙 전면 수정 후 파일럿과 함께 launch** — Critical 19 건 정정 + 전략적 축소 없이 강행. 기각 이유: empty launch + ADR-029 위반 + 파일럿 학습 신호 혼탁 (뉴스 품질 vs 레시피 공유 책임 분리 불가).
- **(b) MVP-minimal (Subscribe only) 로 축소 후 파일럿과 함께 launch** — Clone / Fork chain / CTR / materialized view / 7 태그 모두 컷. ~150 LOC. 기각 이유: "admin 병목" 전제 자체가 미검증. 증거 없이 만드는 건 YAGNI 위반. 파일럿 첫 주 ROI 는 "뉴스 퀄리티 개선" > "공유 피처 prototype".
- **(c) 이 PR — 6 주 defer + 증거 기반 재평가** ✅. 파일럿 중 admin 승인 큐 / 유저 자발적 피드백 / admin 문의 데이터 수집. 2 개 이상 관측 시 MVP-minimal 로 재진입, 0 건이면 취소.

**재평가 트리거 (2026-06-01 경)**:
다음 3 지표 중 **2 개 이상** 관측 시 스펙을 `docs/superpowers/specs/2026-04-20-user-recipe-sharing-design.md` 에서 복구해 MVP-minimal 재작성:

1. `admin 승인 큐 중앙값 대기시간 > 48 h` — 데이터 소스: `audit_log` 의 `action='subscription.approve'` 와 `clipping_user_requests.createdAt` 차이 p50.
2. **파일럿 유저 피드백에서 "내 구독 설정 공유하고 싶다" 자발적 언급 3 회 이상** — 데이터 소스: 파일럿 슬랙 채널 + 1:1 인터뷰 노트.
3. **"다른 사람은 뭘 구독해요?" 문의가 admin 에게 3 회 이상** — 데이터 소스: admin DM / 슬랙 멘션.

0 ~ 1 개만 관측되면 **피처 자체 취소 권장** — 스펙 삭제 또는 "영구 보류" 표시.

**영향 범위**:
- **Code**: 없음 (미구현).
- **DB Migration**: V134 예약되지 않음. 다음 PR 이 V134 사용 가능.
- **ROADMAP**: Phase 3+ 후보로 등재, 재평가 트리거 명시.
- **스펙 보존**: `docs/superpowers/specs/2026-04-20-user-recipe-sharing-design.md` 유지 (AGENTS.md 규칙에 따라 커밋은 하지 않음). 상단 DEFERRED 배너 + Round 1 리뷰 교훈 요약 삽입.
- **외부 dev-log**: 별도 작업 노트에 섹션 추가 (저장소 외부).

**트레이드오프**:
- 6 주 후 증거가 충분하면 이 ADR 이 사전 설계 baseline 으로 작용 — 0 에서 시작하지 않고 축소만 하면 되므로 복구 비용 저렴.
- 6 주 후에도 증거가 모호하면 다시 판단 유보하거나 인터뷰 기반 validation 을 먼저 돌려야 함 — 이 경우 본 ADR 이 "왜 계속 안 만들고 있는지" 설명 역할.
- 스펙 자체는 `docs/superpowers/` 에 있어 git main 에 없음 — 발견 경로 취약성 있음. 이를 보완하기 위해 ADR (이 문서) 와 ROADMAP 이 포인터 역할.

**재진입 시 반드시 반영해야 할 Round 1 교훈** (스펙 상단에도 보존):
- 스키마 이름: `batch_categories`, `admin_users`, `rss_sources.category_id` (join table 없음)
- 마이그레이션 금지 패턴: `ADD CONSTRAINT IF NOT EXISTS`, `INTERVAL '30 days'` 리터럴
- 보안: creator_user_id 는 SecurityContext 에서만 주입, clone 은 public 또는 본인 소유만, name/description/tags prompt injection sanitize, LIKE 쿼리 tag whitelist + search ESCAPE
- 감사: `AuditActorResolver.resolve()` 필수 (user principal 은 `admin_users` 에 없으므로 NULL actor_id)
- Summary cache 특성: `buildCacheKey(title, content, personaId)` — category_id 아님. Clone 이 persona + sources 유지하면 캐시 hit, 수정 시 miss.
- UX: Week 0 empty 처리, 팀 seed 미완 시 fallback, Fork Gen-2 attribution 정책 (B 숨김 vs 전체 표시 재결정 필요)

**관련 PR**: 없음 (설계만 수행, 구현 없음).

**관련 문서**:
- 스펙 원본: `docs/superpowers/specs/2026-04-20-user-recipe-sharing-design.md`
- 브레인스토밍 기록: 외부 작업 노트 (저장소 외부)
- 선행 ADR-029 (약한 신호 제거 교훈 — 본 결정의 근거 중 하나)
- `AGENTS.md` 구독 모델 섹션
- `docs/ROADMAP.md` Phase 3+ 후보 섹션

---

## ADR-032: Rule Bundle Atomic Endpoint (2026-04-22)

**Context**: Previously, excludeEventTypes / includeKeywords / organizationIds / feature flags each had separate admin endpoints. In Phase C+D the `CategoryRuleEditModal` edits all four at once — partial failures leave the category in an inconsistent state.

**Decision**: Add `PUT /api/admin/categories/{id}/rule-bundle`. Accepts all four fields + shadowModeEnabled as a single `@Transactional` request; full rollback on any failure.

The service (`CategoryRuleBundleService`) uses primitives signature (no DTO import crossing layers). The controller extracts fields from `RuleBundleRequest` DTO and passes them as primitives.

A narrow `setKeywordsAndExcludeEventTypes` method was added to `CategoryRuleStore` to update only `include_keywords` + `exclude_event_types` without touching thresholds or delivery schedule — preserving the least-surprise principle for other callers.

**Consequences**: One API call per modal save. Existing individual endpoints remain for other callers. The service layer uses `AuditActorResolver` so the audit row carries the UUID actor, not the username string.

**Update 2026-04-22**: Task D2 (`setShadowModeEnabled` on `CategoryFeatureFlagStore`, V137 migration) is now merged and active. `shadowModeEnabled` is fully wired in `CategoryRuleBundleService.updateRuleBundle()` — the `TODO(D2)` no-op comment has been removed. The field now performs a real upsert in the same `@Transactional` boundary as the other flag updates, maintaining atomicity.

**관련 PR**: feat/account-based-digest-phase-cd-impl (C8 커밋 + final-review fixes)

---

## ADR-033: V139 relaxes compound FK (batch_summaries.rss_item_id, category_id) for retention safety (2026-04-22)

**상태**: 채택 (2026-04-22)

**맥락**: V139 마이그레이션은 `batch_summaries.rss_item_id`를 NOT NULL → NULL로 전환하고, FK 액션을 RESTRICT → ON DELETE SET NULL으로 변경한다. 이 과정에서 기존의 복합 FK `fk_batch_summaries_rss_item_category`(rss_item_id + category_id 두 컬럼을 동시에 묶은 제약)가 함께 제거된다.

**결정**: 복합 FK를 제거하고 단일 컬럼 FK(`fk_batch_summaries_rss_item`)만 유지한다.

이유:
- retention 삭제 경로(`deleteOlderThanExcludingAnchored`)는 `batch_summaries` row를 직접 DELETE한다. 복합 FK가 살아 있으면 `rss_items` 삭제 시 `batch_summaries` row도 CASCADE DELETE되어 anchor(bookmark/feedback) 행까지 잃는다.
- `category_id`에 대한 일관성 강제는 애플리케이션 레이어(store INSERT)로 충분하다. 실제 INSERT 경로는 `JdbcBatchSummaryStore.kt` 한 곳뿐임을 grep으로 확인했다(`local-bootstrap.sql`의 seed INSERT는 개발 전용).

**결과**:
- (약화) `rss_item_id`와 `category_id`의 조합 일관성이 DB 레벨에서 강제되지 않는다. 잘못된 조합은 애플리케이션이 방어해야 한다.
- (강화) rss_items retention 삭제 시 batch_summaries가 CASCADE 삭제되지 않아 anchored 데이터가 보호된다.

**대안 검토**:
- 복합 FK 유지 + ON DELETE RESTRICT: rss_items를 지울 때 batch_summaries를 먼저 지워야 하는 순서 의존이 생겨 retention 스케줄러 복잡도가 높아진다.
- 복합 FK 유지 + ON DELETE CASCADE: anchored batch_summaries(bookmark/feedback 보유)까지 삭제되어 허용 불가.

**구현**: `src/main/kotlin/com/clipping/mcpserver/migration/V139__RetentionPrepBatchSummariesRssItemFk.kt`

---

## ADR-034: Extract clipping engine core Gradle module (2026-05-01)

**상태**: 채택

**맥락**: 클리핑/다이제스트 로직을 장기적으로 내부 엔진처럼 재사용하려면 Spring, JPA, store, 앱 도메인 모델에 묶이지 않은 코어 경계가 필요하다. 기존 `service/digest` 일부는 순수 로직이지만 `Organization` 모델과 앱 예외 타입에 직접 의존했다.

**결정**: `clipping-engine/` Gradle 서브모듈을 추가하고, Spring/DB와 무관한 다이제스트 코어를 이 모듈로 이동한다.

포함:
- `DigestMode`, `DigestModeResolver`
- `DigestComposition`
- `ArticleMatcher`
- `SectionLabelResolver`
- `SectionSilencePolicy`
- `DigestCandidateSelectionPolicy`
- `DigestDocumentBuilder`
- `DigestSummaryFormattingPolicy`
- deterministic pipeline 실행 순서/trace policy `EnginePipelineRunner`
- 엔진 DTO `DigestOrganization`
- 엔진 DTO `DigestCandidate`
- 엔진 DTO `DigestDocument`, `DigestDocumentItem`
- 엔진 예외 `EngineInvalidInputException`

앱 레이어는 `service/digest/DigestOrganizationAdapter.kt`에서 `Organization`을 `DigestOrganization`으로 변환한다. `DigestSelectionService`는 DB 조회, 리뷰 결정, 피드백 조회, 번역, Slack 표시 정규화를 유지하되 후보 중복 제거와 소스 다양성 기반 선정은 `DigestCandidateSelectionPolicy`에 위임한다. `DigestRenderer`는 Slack Block Kit/URL tracking/AppProperties 의존을 유지하되, 키워드 집계·선정 수·thin-day·fallback 여부 같은 플랫폼 중립 문서 계산은 `DigestDocumentBuilder`에 위임한다. LLM 요약 문단 분리, 섹션 이모지 정규화, leading decoration 제거는 `DigestSummaryFormattingPolicy`에 위임하고, mrkdwn escape는 Slack 출력 adapter인 `DigestRenderer`에 남긴다. `DeterministicPipelineRunner`는 앱 포트 호출과 앱 응답 모델 변환만 담당하고, collect → summarize → digest 실행 순서와 step trace 생성은 `EnginePipelineRunner`에 위임한다. 엔진 예외는 `GlobalExceptionHandler`에서 HTTP 400으로 매핑한다.

**결과**:
- 엔진 모듈은 독립 컴파일/테스트 가능하다.
- 앱은 기존 API와 동작을 유지한다.
- 이후 엔진 확장은 `clipping-engine`이 앱 모델, Spring bean, store interface를 import하지 않는 규칙을 지켜야 한다.

**검증**:
- `./gradlew :clipping-engine:test compileKotlin compileTestKotlin -PskipFrontendBuild=true --rerun-tasks`
- `./gradlew checkPostgresSpecificSql test -PskipFrontendBuild=true --rerun-tasks`

---

## ADR-035: Pipeline orchestration depends on app-side engine port (2026-05-01)

**상태**: 채택

**맥락**: RSS 수집, LLM 요약, Slack 발송까지 한 번에 `clipping-engine`으로 옮기면 외부 I/O와 저장소 트랜잭션 경계가 동시에 흔들린다. 기존 동작을 보존하면서 전체 파이프라인 엔진화를 준비하려면 오케스트레이션부터 구체 서비스 의존을 끊어야 한다.

**결정**: `service/port/ClippingPipelinePort`를 추가하고, `AdminClippingService`와 `RalphPipelineOrchestrator`는 `ClippingService` 대신 이 포트에 의존한다. 현재 구현체는 `ClippingPipelineAdapter`이며, 기존 `ClippingService`의 수집/요약/다이제스트 실행 순서와 앱 API 응답 모델은 그대로 유지한다.

**결과**:
- 관리자 deterministic pipeline과 Ralph pipeline은 구체 애플리케이션 서비스가 아니라 포트 계약에 기대므로, 이후 엔진 오케스트레이터나 어댑터 구현체로 교체하기 쉽다.
- 포트 반환 타입은 앱 모델(`CollectResult`, `SummarizeResult`, `DigestResult`)이 아니라 독립 DTO(`PipelineCollectResult`, `PipelineSummarizeResult`, `PipelineDigestResult`)다. 앱 응답 모델로의 변환은 오케스트레이션 경계 밖에서 명시적으로 수행한다.
- `PipelinePortBoundaryTest`가 오케스트레이터의 concrete `ClippingService` 역의존과 앱 모델 반환 타입 회귀를 막는다.

**Update 2026-05-01**: deterministic collect → summarize → digest 실행과 trace 작성은 `DeterministicPipelineRunner`로 분리했다. `AdminClippingService`는 Ralph 사용 여부 판단과 fallback 위임만 담당한다.

**Update 2026-05-02**: `ClippingService`가 `ClippingPipelinePort`를 직접 구현하지 않도록 분리하고, `ClippingPipelineAdapter`가 앱 결과 모델을 파이프라인 DTO로 변환한다. deterministic/Ralph 오케스트레이션은 포트 DTO로 집계한 뒤 기존 `PipelineRunResult` 반환 직전에 앱 모델로 되돌린다.

**Update 2026-05-02-2**: MCP 동기 수집/요약/발송, async collect/summarize worker, prepared digest 발송 worker도 concrete `ClippingService` 직접 주입을 제거했다. 조회성 MCP/user use case는 `ClippingQueryPort`, prepared digest 생성/전송/finalization은 `DigestDeliveryWorkflowPort`를 통해 호출한다. concrete `ClippingService` 의존은 `ClippingPipelineAdapter`, `ClippingQueryAdapter`, `DigestDeliveryWorkflowAdapter` 내부에만 허용한다.

**Update 2026-05-02-3**: 앱 모듈의 `service/digest` 패키지는 engine DTO adapter만 남기고 Spring/store 의존 코드와 조직 origin 상수는 각각 `service/section`, `model` 패키지로 이동했다. `PipelinePortBoundaryTest`가 이 패키지 경계를 회귀 테스트로 잠근다.

**Update 2026-05-02-4**: 로컬/운영 부트 로그 품질을 위해 Redis repository auto-scan은 비활성화하고 Caffeine 캐시는 `recordStats()`로 생성한다. macOS 로컬 DNS native resolver 경고는 runtime classifier 의존성으로 제거한다. 비동기 collect/summarize 및 pipeline worker 실패는 기존 retry/FAILED 전환과 저장 메시지를 유지하되 `BatchJobExecutionException`으로 래핑해 broad catch 경계를 축소한다.

**Update 2026-05-02-5**: `ClippingPipelinePort`와 `PipelineCollectResult`/`PipelineSummarizeResult`/`PipelineDigestResult` DTO를 앱 모듈에서 `clipping-engine` 모듈로 이동했다. 앱 모듈은 엔진 포트 계약을 구현하는 `ClippingPipelineAdapter`만 제공하며, `PipelinePortBoundaryTest`가 앱 소스에 pipeline port 계약이 다시 생기지 않도록 잠근다.

**Update 2026-05-02-6**: RSS 수집, LLM 요약, Slack 발송 포트(`RssCollectionPort`, `LlmSummarizationPort`, `SlackDeliveryPort`)를 `clipping-engine` 모듈로 이동했다. 앱 모듈은 RSS/AI/Slack 어댑터 구현과 앱 모델 매핑만 담당한다. 포트 DTO는 Spring/JPA/store/app model 의존을 금지하며 `PipelinePortBoundaryTest`가 이를 검증한다.

**Update 2026-05-02-7**: `ClippingQueryPort`와 `DigestDeliveryWorkflowPort`는 앱 조회 DTO 및 prepared digest workflow 성격이 강하므로 엔진 모듈로 이동하지 않는다. 대신 `:clipping-engine:checkEngineBoundaries` Gradle task를 추가해 엔진 모듈의 Spring/JPA/store/app model import를 빌드에서 차단하고, `./gradlew check`에 포함한다.

**Update 2026-05-02-8**: 앱 내부 경계 모듈 `clipping-app-ports/`를 추가하고 `DigestDeliveryWorkflowPort`를 이 모듈로 이동했다. prepared digest workflow는 더 이상 `PipelineDigestResult`를 포트 DTO로 재사용하지 않고 `PreparedDigestResult`/`PreparedDigestItemResult`를 사용한다. root app은 `DigestDeliveryWorkflowAdapter`와 `DigestDeliveryWorkflowMapper`에서 기존 `DigestResult`와 계약 DTO를 변환한다. `:clipping-app-ports:checkAppPortBoundaries`가 Spring/JPA/store/app model import를 차단하며 `./gradlew check`에 포함된다.

**Update 2026-05-02-9**: MVC 레이어 기준이 아니라 도메인 계약 기준으로 ops notification 경계를 분리했다. `OpsLogNotifier`, `OpsLogDtos`, `NotificationEvent`, `NotificationSeverity`를 `clipping-app-ports/`로 이동해 운영 알림 호출 서비스와 Slack 어댑터가 동일한 app port 모듈을 바라보게 했다. 패키지명과 public API는 유지해 기존 런타임 동작은 변경하지 않는다.

**Update 2026-05-02-10**: 단일 `NotificationEvent` enum을 공통 sealed contract와 도메인별 enum(`OpsNotificationEvent`, `UserNotificationEvent`, `OpsRequestNotificationEvent`)으로 분리했다. `OperationsNotificationService`의 public API도 각 발송 경로별 이벤트 타입만 받도록 좁혀 잘못된 채널 호출을 런타임 예외가 아니라 컴파일 단계에서 차단한다. enum entry 이름과 dedup 키 생성 문자열은 유지해 기존 Redis dedup key prefix 형식을 보존한다.

**Update 2026-05-02-11**: 루트 앱 내부에서도 MVC 기준이 아니라 도메인 기준으로 알림 정책 서비스를 `service/notification/` 패키지로 이동했다. `OperationsNotificationService`와 `UserNotificationService`는 알림 도메인 패키지가 소유하고, 기존 호출 서비스는 명시 import로 이 도메인 서비스에 의존한다.

**Update 2026-05-12**: `PreparedDigestResult`/`PreparedDigestItemResult` 를 삭제하고 `DigestDeliveryWorkflowPort` 가 엔진과 동일한 `PipelineDigestResult`/`PipelineDigestItemResult` 를 사용하도록 통합했다. Update -8 에서 분리한 prepared/pipeline DTO 가 11일간 한 번도 갈라지지 않은 채 1:1 매핑만 발생했고, `DigestDeliveryWorkflowMapper` 가 순수 forwarding 코드였다. 통합으로 다음 변화가 발생한다: (1) Pipeline DTO 6종(`PipelineCollect/Summarize/Digest*`)이 `core/api-models` 로 이동해 엔진 모듈(`modules/digest-policy`)과 app port 모듈(`ports/workflow`)이 공유한다. (2) `modules/digest-policy` 가 `core/api-models` 에 implementation 의존성을 추가한다. (3) `DigestDeliveryWorkflowMapper.kt` 를 삭제하고, `DigestDeliveryWorkflowAdapter` 와 `SlackDigestWorker` 가 기존 `ClippingPipelineResultMapper` 의 `toPipelineDigestResult/toDigestResult` 만 사용한다. (4) `PipelinePortBoundaryTest` 의 디지스트 워크플로 포트 어서션이 `PreparedDigestResult` 부재 + `PipelineDigestResult` 사용을 검증하도록 갱신됐다. 향후 prepared/pipeline DTO 가 실제로 갈라져야 하는 변경이 발생하면 그때 다시 분리한다.

**Update 2026-05-02-12**: 루트 앱의 파이프라인 orchestration/실행/로그/분석 서비스를 `service/pipeline/` 도메인 패키지로 이동했다. public bean 타입과 메서드 계약은 유지하고 import만 새 도메인 패키지로 변경해 기존 API/스케줄러/이벤트 동작을 보존한다.

**Update 2026-05-02-13**: `clipping-app-ports`에 섞여 있던 API 결과 DTO와 파이프라인 실행 이력 DTO를 각각 `clipping-api-models`, `clipping-pipeline-models`로 물리 분리했다. 패키지명(`service.dto.clipping`, `service.dto.pipeline`)은 유지해 기존 import와 직렬화 계약을 보존하고, 각 모듈에 Spring/JPA/store/app model import 차단 task를 추가했다.

**Update 2026-05-02-14**: JPA entity와 Spring Data repository를 `clipping-persistence` 모듈로 물리 분리했다. 패키지명(`com.clipping.mcpserver.entity`, `com.clipping.mcpserver.repository`)은 유지해 기존 store 구현과 테스트 import를 보존한다. 이 단계에서는 store 구현을 root app에 남겨 service/store SPI과 DB 구현 조립 책임을 유지했다.

**Update 2026-05-02-15**: DB 접근 포트와 store 반환 DTO를 `clipping-store-spi`로, Jpa/Jdbc store 구현을 `clipping-persistence`로 물리 분리했다. 공통 서비스 예외는 `clipping-error-types`로 이동해 root app과 persistence가 동일한 예외 계약을 공유한다. `PipelinePortBoundaryTest`와 각 Gradle boundary task가 root app store 구현 재유입과 하위 모듈의 app service/adapter 역참조를 차단한다.

**Update 2026-05-02-16**: 경계 모듈의 패키지 경계도 물리 모듈과 맞췄다. 알림 이벤트 계약은 `clipping-app-ports`의 `service.port.NotificationEvent`로, store analytics/pipeline 계약은 `clipping-store-spi`의 `store.analytics.dto`와 `store.pipeline`으로 이동했다. enum entry, DTO 필드, store 메서드 시그니처의 의미는 유지하고 import 경로만 갱신해 런타임 동작은 변경하지 않는다.

**Update 2026-05-02-17**: root app의 다이제스트 application orchestration을 `service/digest` 도메인 패키지로 정리했다. `DigestService`, `SlackDigestWorker`, prepared digest workflow adapter/mapper, finalization, rendering, preview, selection, ops notifier가 같은 다이제스트 application 경계에 위치한다. 엔진 모듈은 계속 Spring/store 의존을 금지하고, root app `service/digest`는 Spring Bean 조립과 DB/Slack workflow orchestration을 담당한다. `contracts` 모듈명은 당장 변경하지 않고, 추후 별도 마이그레이션에서 `api`/`spi`/`ports` 계열 이름으로 바꿀지 결정한다.

**Update 2026-05-02-18**: Gradle 서브모듈 이름에서 `contracts` 접미사를 제거하고 책임 기반 이름으로 변경했다. API/MCP 응답 DTO는 `clipping-api-models`, pipeline 실행 이력 DTO는 `clipping-pipeline-models`, store 포트/DTO는 `clipping-store-spi`, 앱 내부 workflow/notification 포트는 `clipping-app-ports`, 공유 예외 타입은 `clipping-error-types`가 소유한다. Kotlin package와 public DTO 필드는 유지해 런타임 직렬화와 기존 API 응답 형식은 변경하지 않는다.

**Update 2026-05-02-19**: application 도메인 물리 분리 pilot으로 `clipping-notification` 모듈을 추가했다. 운영/사용자 알림 application 서비스는 이 모듈이 소유하고, Slack 발송은 `SlackDeliveryPort`, runtime 알림 설정은 `NotificationRuntimeSettingsPort`, dedup 저장소는 `NotificationDedupPort`로 접근한다. root app의 `RuntimeSettingService`와 `RedisRateLimitService`는 해당 포트를 구현해 기존 런타임 동작과 설정 저장 위치를 유지한다. `:clipping-notification:checkNotificationBoundaries`가 root app 구현 패키지 역참조를 차단한다.

**Update 2026-05-02-20**: 두 번째 application 도메인 물리 분리로 `clipping-collection` 모듈을 추가했다. RSS source 수집, 수동 URL 수집, Naver 뉴스 수집 application 서비스는 이 모듈이 소유하고, content extraction, URL safety, metrics, stats, scheduler error 알림은 `Collection*Port`로 접근한다. root app은 기존 구현체를 adapter/port 구현으로 조립해 저장소, Slack/Gemini/RSS/Naver 런타임 동작을 유지한다.

**Update 2026-05-02-21**: 세 번째 application 도메인 물리 분리로 `clipping-source` 모듈을 추가했다. RSS source URL 검증, RSS 피드 탐색, 도메인 추출, source health/coverage/reliability/SLA, 카테고리 기반 RSS source 자동 동기화 정책은 이 모듈이 소유한다. root app의 URL safety, SLA 설정, 조직 조회, metrics, ops notification 구현은 `Source*Port` 어댑터로 주입해 기존 API/스케줄러/Slack 운영 알림 동작을 유지한다. `:clipping-source:checkSourceBoundaries`가 root app 구현 패키지 역참조를 차단한다.

**검증**:
- `./gradlew test --tests "com.clipping.mcpserver.service.AdminClippingServiceTest" --tests "com.clipping.mcpserver.service.RalphPipelineOrchestratorTest" --tests "com.clipping.mcpserver.service.DeterministicPipelineRunnerTest" --tests "com.clipping.mcpserver.service.ClippingPipelineAdapterTest" --tests "com.clipping.mcpserver.service.PipelinePortBoundaryTest" -PskipFrontendBuild=true`
- `./gradlew checkPostgresSpecificSql test -PskipFrontendBuild=true --rerun-tasks`

---

## ADR-036: RSS collection callers depend on app-side RSS port (2026-05-01)

**상태**: 채택

**맥락**: 전체 클리핑 엔진화를 진행하려면 RSS HTTP fetch/parser 구현체를 서비스 정책에서 분리해야 한다. 기존에는 `CollectionService`, 경쟁사 수집/프리뷰, 소스 헬스 스케줄러가 모두 concrete `RssFeedCollector`를 직접 주입받았다.

**결정**: `service/port/RssCollectionPort`를 추가하고 RSS 수집 호출자는 이 포트에 의존한다. 기존 `RssFeedCollector`의 HTTP fetch, cache, retry, content enrichment API는 유지하고, `rss/RssCollectionAdapter`가 `RssCollectionPort`를 구현하면서 기존 collector API와 포트 DTO를 매핑한다.

**결과**:
- RSS 수집 adapter를 교체하거나 엔진 facade 뒤로 이동할 수 있는 app-side port 경계가 생겼다.
- `RssCollectionPort`는 앱 모델(`RssItem`, `RssSource`) 대신 독립 DTO(`RssCollectionSource`, `RssCollectedItem`)를 사용한다. 앱 모델 변환은 `service/collection/RssCollectionMapper`와 `rss/RssCollectionAdapter` 경계에서만 수행한다.
- `RssCollectionPortBoundaryTest`가 RSS 호출 서비스의 concrete collector import와 포트 앱 모델 노출 회귀를 막는다.
- `RssCollectionAdapterTest`가 기존 collector 동작을 포트 DTO로 왕복 매핑하는 계약을 검증한다.

**검증**:
- `./gradlew :test --tests "com.clipping.mcpserver.service.CollectionServiceCrawlLogTest" --tests "com.clipping.mcpserver.service.ClippingServiceCollectReliabilityTest" --tests "com.clipping.mcpserver.service.CompetitorCollectionServiceTest" --tests "com.clipping.mcpserver.service.CompetitorWatchlistServiceTest" --tests "com.clipping.mcpserver.service.RssCollectionPortBoundaryTest" --tests "com.clipping.mcpserver.rss.RssCollectionAdapterTest" -PskipFrontendBuild=true`
- `./gradlew :clipping-source:test --tests "com.clipping.mcpserver.service.source.SourceHealthSchedulerTest" -PskipFrontendBuild=true`
- `./gradlew checkPostgresSpecificSql test -PskipFrontendBuild=true --rerun-tasks`

---

## ADR-037: LLM callers depend on app-side summarization port (2026-05-01)

**상태**: 채택

**맥락**: 요약, 중요도 스크리닝, 번역, 일일/경쟁사 인사이트 생성은 모두 외부 LLM 호출 adapter인 `ClippingSummarizer`에 묶여 있었다. 전체 클리핑 엔진화를 진행하려면 서비스 정책이 구체 Gemini adapter를 직접 보지 않아야 한다.

**결정**: `service/port/LlmSummarizationPort`를 추가하고 LLM 호출 서비스는 이 포트에 의존한다. 기존 `ClippingSummarizer`의 prompt, retry caller contract, token usage, reject reason API는 유지하고, `ai/LlmSummarizationAdapter`가 `LlmSummarizationPort`를 구현하면서 기존 Gemini 구현체와 포트 DTO를 매핑한다.

**결과**:
- 요약 adapter를 교체하거나 엔진 facade 뒤로 이동할 수 있는 app-side port 경계가 생겼다.
- 포트는 앱 모델(`AiSummaryResponse`, `AiDailySummaryResponse`, `Language`, `Persona`, `CompetitorTimelineItem`) 대신 독립 DTO(`LlmArticleSummaryResult`, `LlmDailySummaryResult`, `LlmArticleLanguage`, `LlmPersona`, `LlmCompetitorTimelineItem`)를 사용한다. 앱 모델 변환은 `LlmSummarizationMapper`와 `LlmSummarizationAdapter` 경계에서만 수행한다.
- `LlmSummarizationPortBoundaryTest`가 서비스의 concrete `ClippingSummarizer` import, 포트 앱 모델 노출, concrete 구현체의 직접 포트 구현 회귀를 막는다.
- `LlmSummarizationAdapterTest`가 기존 Gemini 구현체와 포트 DTO 사이의 매핑 계약을 검증한다.

**검증**:
- `./gradlew :test --tests "com.clipping.mcpserver.ai.LlmSummarizationAdapterTest" --tests "com.clipping.mcpserver.service.ItemSummarizationServiceTest" --tests "com.clipping.mcpserver.service.FallbackSummaryTest" --tests "com.clipping.mcpserver.service.CompetitorWeeklyDigestSchedulerTest" --tests "com.clipping.mcpserver.service.LlmSummarizationPortBoundaryTest" -PskipFrontendBuild=true`
- `./gradlew checkPostgresSpecificSql test -PskipFrontendBuild=true --rerun-tasks`

---

## ADR-038: Slack delivery callers depend on app-side delivery port (2026-05-01)

**상태**: 채택

**맥락**: 다이제스트, 자동 리포트, 운영 알림은 모두 Slack 전송 adapter에 의존한다. 전체 클리핑 파이프라인을 엔진 facade 뒤로 옮기려면 배치/파이프라인 서비스가 Slack 구현체나 운영 설정 API에 직접 묶이지 않아야 한다.

**결정**: `service/port/SlackDeliveryPort`를 추가하고 배치/파이프라인 Slack 발송 호출자는 이 포트에 의존한다. `SlackDeliveryAdapter`가 발송 전용 포트를 기존 `SlackMessageSender` 구현에 위임하며 payload 생성, fallback, Slack API 호출, token 사용 동작은 변경하지 않는다.

이번 범위:
- 다이제스트 발송: `DigestService`, `SlackDigestWorker`
- 경쟁사/자동/주간 리포트 발송: `CompetitorWeeklyDigestScheduler`, `AutoReportScheduler`, `WeeklySummaryScheduler`
- 운영 알림 발송: `OperationsNotificationService`, `PipelineLogService`, `SchedulerErrorNotifier`, `ErrorSlackNotifier`, `SlackOpsLogNotifier`

범위 밖:
- Slack 연결 검증, 채널 조회, DM 개설, Socket Mode 메시지 업데이트처럼 운영/설정 API가 필요한 호출자는 기존 `SlackMessageSender`에 남긴다.

**결과**:
- 배치/파이프라인 발송 경로는 `service.port` 출력 포트에 의존한다.
- `SlackDeliveryPortBoundaryTest`가 발송 호출자의 `SlackMessageSender` 회귀와 운영 API 호출 누수를 막는다.
- `SlackDeliveryPort`는 `sendMessage`/`updateMessage`만 노출하고, 결과는 독립 DTO `SlackDeliveryResult`로 반환한다.

**Update 2026-05-02**: `SlackDeliveryPort`가 더 이상 `SlackMessageSender`를 상속하지 않도록 좁혔다. `SlackDeliveryAdapter`가 발송 전용 포트를 기존 `SlackMessageSender` 구현에 위임하고, `SlackApiMessageSender`는 운영/설정 API 호환을 위해 기존 `SlackMessageSender` 구현체로 유지한다.

**Update 2026-05-02-2**: `SlackDeliveryPort` 반환 타입을 `SlackMessageSender.SendResult`에서 `SlackDeliveryResult`로 분리했다. 서비스/관측/운영 알림 발송 경로는 더 이상 `SlackMessageSender` 중첩 DTO에 의존하지 않는다.

**Update 2026-05-02-3**: `SlackDeliveryPort` 입력 타입도 독립 DTO로 분리했다. 호출자는 `SlackDeliveryMetadata`와 `SlackDeliveryColor`만 사용하고, `SlackDeliveryAdapter`가 기존 `SlackMetadata`/`SlackStatusColor`로 변환한다. 따라서 배치/파이프라인 발송 포트는 Slack 구현체 패키지와 `SlackMessageSender` 계약 타입을 직접 노출하지 않는다.

**Update 2026-05-02-4**: 운영 알림 DTO를 `service.dto.ops`에서 `service/port/OpsLogDtos.kt`로 옮겼다. `OpsLogNotifier` 포트가 파이프라인 실행/스텝/인시던트/예측/주간 리포트 계약 타입을 직접 소유하므로, Slack 운영 알림 어댑터와 호출 서비스는 별도 service DTO 패키지에 의존하지 않는다.

**Update 2026-05-02-5**: 운영 알림 포트와 DTO를 루트 앱 소스셋에서 `clipping-app-ports/`로 물리 이동했다. 알림 이벤트/심각도 계약도 같은 경계 모듈로 이동해 알림 도메인 계약을 한 곳에서 소유한다.

**Update 2026-05-02-6**: `NotificationEvent`를 발송 도메인별 enum으로 분리했다. `OpsLogNotifier.postOpsEvent`는 `OpsNotificationEvent`만 받으므로 운영 알림 어댑터가 사용자 DM/운영 요청 이벤트를 실수로 처리할 수 없다.

**Update 2026-05-02-7**: 내부 로직/외부 호출 래퍼의 broad `catch (Exception)` 6건을 구체 예외로 낮췄다. RSS 검증/사용자 URL 검증/본문 추출/DART XML 로딩/Block Kit JSON 파싱/운영 요청 Slack 발송 경로는 각각 `IOException`, `SecurityException`, `IllegalArgumentException`, `SAXException`, `ParserConfigurationException`, `JsonProcessingException`, `ServiceException` 등으로 제한한다. `checkBroadExceptionBaseline` 기준은 114건에서 108건으로 낮췄다.

**Update 2026-05-02-8**: SearchCo 뉴스 검색 어댑터의 broad `catch (Exception)` 3건을 `IOException`, `InterruptedException`, `RuntimeException`, `JsonProcessingException`, `DateTimeParseException`으로 낮췄다. 외부 API 실패 시 빈 리스트 반환, 잘못된 JSON 빈 리스트 반환, RFC 822 날짜 파싱 실패 시 `null` 반환 동작은 유지한다. `checkBroadExceptionBaseline` 기준은 108건에서 105건으로 낮췄다.

**Update 2026-05-02-9**: MCP 인자 redaction과 사용자 클릭 이벤트 저장 경로의 broad `catch (Exception)` 2건을 `IOException`, `JsonProcessingException`, `RuntimeException`으로 낮췄다. MCP 인자 파싱 실패 시 redaction error marker를 반환하고, 클릭 이벤트 저장 실패 시 리다이렉트 흐름을 끊지 않는 기존 fail-open 동작은 유지한다. `checkBroadExceptionBaseline` 기준은 105건에서 103건으로 낮췄다.

**검증**:
- `./gradlew :test --tests "com.clipping.mcpserver.service.SlackDeliveryPortBoundaryTest" --tests "com.clipping.mcpserver.service.DigestService*" --tests "com.clipping.mcpserver.service.CompetitorWeeklyDigestSchedulerTest" --tests "com.clipping.mcpserver.service.SlackDigestWorkerTest" -PskipFrontendBuild=true`
- `./gradlew checkPostgresSpecificSql test -PskipFrontendBuild=true --rerun-tasks`

---

## ADR-039: URL/RSS boundary exceptions stay explicit (2026-05-02)

**상태**: 채택

**맥락**: 배치 수집 안정성을 위해 URL 파싱, robots.txt 확인, RSS 후보 탐색 경로는 fail-open 또는 후보 경로 skip 동작이 필요하다. 다만 `catch (Exception)`을 남겨 두면 프로그래밍 오류까지 조용히 삼켜 원인 추적이 어려워진다.

**결정**: URL/RSS 경계의 기대 가능한 실패만 명시적으로 처리한다. URL 파싱 실패는 `URISyntaxException`/`IllegalArgumentException`, HTTP/IO 실패는 `IOException`, 보안 차단은 `SecurityException`으로 좁힌다. `RssFeedDiscoveryService`에서 `InterruptedException`이 발생하면 인터럽트 플래그를 복구하고 현재까지 찾은 후보만 반환한다.

**결과**:
- `DomainExtractor`, `RssFeedDiscoveryService`, `HttpRobotsPolicyClient`, `HttpSourceVerificationClient`는 더 이상 광범위 `catch (Exception)`을 사용하지 않는다.
- robots.txt 실패 시 수집을 막지 않는 기존 fail-open 동작은 유지한다.
- `UrlBoundaryExceptionHandlingTest`가 같은 경로에 broad catch가 재도입되는 것을 막는다.

**Update 2026-05-02**: MVC 레이어가 아니라 source 도메인 기준으로 URL/RSS 검증 정책을 `service/source/` 패키지로 이동했다. `SourceVerificationService`, `UserSourceValidationService`, `SourceVerificationClient`, `DomainExtractor`, `VerificationResult`가 같은 도메인 패키지에 위치하고, HTTP 접근 구현은 기존처럼 `rss/HttpSourceVerificationClient` 어댑터가 담당한다. 이어서 `SourceHealthService`, `SourceHealthScheduler`, `SourceReliabilityCalculator`, `SourceCoverageAnalyzer`, `RssFeedDiscoveryService`, `SourceRequestSlaScheduler`도 같은 source 도메인 패키지로 이동해 소스 헬스/커버리지/탐색/SLA 책임을 한 경계로 묶었다.

**검증**:
- `./gradlew :clipping-source:test --tests "com.clipping.mcpserver.service.source.DomainExtractorTest" --tests "com.clipping.mcpserver.service.source.RssFeedDiscoveryServiceTest" -PskipFrontendBuild=true`
- `./gradlew :test --tests "com.clipping.mcpserver.service.UrlBoundaryExceptionHandlingTest" -PskipFrontendBuild=true`
- `./gradlew checkPostgresSpecificSql test -PskipFrontendBuild=true --rerun-tasks`

---

## ADR-040: Hierarchical module layout and root package rename (2026-05-12)

**상태**: 채택

**맥락**: Pre-publish 시점에 15개의 Gradle 서브모듈이 모두 평탄한 `clipping-*` 이름(`clipping-domain`, `clipping-engine`, `clipping-app-ports` …)을 사용했고, 루트 Java 패키지는 `com.clipping.mcpserver`였다. 세 가지 문제가 있었다:

1. 디렉터리 목록만 봐서는 어떤 모듈이 어느 레이어에 속하는지 알 수 없었다.
2. 세 개의 "models" 모듈(`clipping-api-models`, `clipping-application-models`, `clipping-pipeline-models`)의 책임 경계가 불분명했고, 그 중 하나는 단일 파일만 소유했다.
3. `com.clipping.mcpserver` 루트 패키지가 새로운 공개 프로젝트 이름 `oh-my-clipping`과 일치하지 않았다.

**결정**: Gradle/IntelliJ Platform 관례를 따라 `core/`, `ports/`, `adapters/`, `modules/` 4 그룹의 디렉터리 레이아웃을 채택한다. 루트 패키지는 `com.ohmyclipping`으로 변경한다. `pipeline-models`는 `api-models`로 흡수하고, `application-models`는 피처별로 분리해 사용자 DTO는 `modules/user`, 관리자 DTO는 새로운 `modules/admin`, analytics DTO는 `modules/analytics`, 모듈을 가로지르는 cross-cutting DTO는 `core/api-models`로 옮긴다.

각 모듈 내부의 서브 패키지(`.engine.`, `.service.dto.` 등)는 새 모듈 이름과 맞추기 위해 다시 쓰지 않았다 — 디렉터리 트리가 아키텍처 신호를 충분히 전달하고, 안정된 패키지 경로는 그 자체로 가치 있는 식별자다. Gradle artifact `group`은 당분간 `com.clipping.mcpserver`를 유지한다(아직 외부에 발행되는 artifact가 없으므로, 발행 시점에 다시 검토한다).

**결과**:

- 저장소 루트의 `ls` 출력만으로 아키텍처가 드러난다.
- 새 모듈의 위치가 명확해진다: 순수 타입과 계약은 `core/`, 경계 인터페이스는 `ports/`, 외부 시스템 구현은 `adapters/`, 피처 로직은 `modules/`.
- 모든 디렉터리 이동은 `git mv` 체인으로 수행했으므로 `git log --follow`가 기존 파일 이력을 계속 추적한다.
- `modules/admin`은 현재 DTO 전용이다. 관리자 서비스 로직은 root app에 남아 있고, 추후 코드베이스 진화에 따라 이 모듈로 이동할 수 있다.

**참고**:

- 설계 문서: `docs/superpowers/specs/2026-05-11-module-restructure-design.md` (로컬 작업용)
- 실행 계획: `docs/superpowers/plans/2026-05-11-module-restructure.md` (로컬 작업용)

## ADR-041: `ClippingQueryAdapter` 1:1 pass-through 유지 (2026-05-12)

**상태**: 채택

**맥락**: 아키텍처 리뷰에서 `ClippingQueryPort` + `ClippingQueryAdapter`(68줄, 11개 forwarding 메서드, 두 번째 구현 없음)가 "one adapter = hypothetical seam" 안티패턴처럼 보였다. Adapter 모든 메서드가 `clippingService.foo() = clippingService.foo()` 형태로 mechanical forward 만 한다.

**검증된 deletion test 결과**: 단순 mechanical forward 처럼 보이지만 port 가 흡수하는 실질 비용이 셋이다.
- **Cross-module seam**: `modules/user/UserSubscriptionQueryService` 가 `clippingQueryPort.listRecentForCategories()` 를 호출한다. `modules/user` 는 별도 Gradle 모듈이라 root app 의 concrete `ClippingService` 를 직접 import 할 수 없다. Port 삭제 시 (a) `ClippingService` 를 모듈로 이동(과한 변경)하거나 (b) `modules/user` → root app 의존(금지 방향)을 만들어야 한다.
- **MCP tool 테스트 격리**: 5개 테스트 파일(`AdminExportToolTest`, `UserSummaryToolsTest`, `UserInsightToolsTest`, `UserSubscriptionToolsTest`, `UserSummaryDetailsTest`)이 `ClippingQueryPort` 를 mock 한다. Port 제거 시 의존성 20+ 의 `ClippingService` 전체를 mock 해야 해 테스트 setup 이 폭증한다.
- **엔진 추출 안정 seam**: ADR-035 Update -2 가 명시한 "concrete `ClippingService` 직접 주입 제거" 정책의 한 조각. 향후 엔진 또는 query 모듈로 분리 시 호출자 코드를 건드리지 않는다.

**결정**: `ClippingQueryAdapter` 의 mechanical forwarding 은 비용 < 효용 이므로 그대로 유지한다. "shallow module" 휴리스틱만 보고 제거하지 않는다.

**결과**:
- 향후 아키텍처 리뷰에서 동일 후보가 "1:1 pass-through 이므로 제거"로 다시 제안되지 않는다.
- Port 가 실제로 분기될 필요가 생기면(e.g. 두 번째 어댑터 등장, 모듈 추가 분리) 이 ADR 을 폐기/대체한다.
- 이 결정은 ADR-035 Update -2 의 연장이며, "shallow port = bad" 휴리스틱보다 "moduel boundary + test isolation 비용" 을 우선한다.

**참고**: 이 ADR 은 `improve-codebase-architecture` 리뷰에서 도출된 후보 #1 의 grilling 결과를 잠그기 위해 작성됐다. 같은 리뷰에서 도출된 후보 #2(PreparedDigestResult 통합)는 ADR-035 Update 2026-05-12 에 기록되어 진행됐다.

## ADR-042: `SummaryDeliveryStore` narrow port 유지 (2026-05-12)

**상태**: 채택

**맥락**: 아키텍처 리뷰에서 `SummaryDeliveryStore`(15줄, 3개 메서드 — `findUnsent` / `markSent` / `findLatestSentByCategoryId`)가 `BatchSummaryStore` 의 작은 슬라이스라 "병합해도 무방" 처럼 보였다. 두 인터페이스는 같은 어댑터(`JpaBatchSummaryStore`, `JdbcBatchSummaryStore`)가 구현하고, 실제로 `BatchSummaryStore : SummaryDeliveryStore` 상속 관계다.

**검증된 deletion test 결과**: 좁은 인터페이스가 **Interface Segregation Principle (ISP)** 가치를 흡수한다.
- 6개 서비스(`ClippingService`, `AdminReviewQueueService`, `RalphPipelineOrchestrator`, `DigestService`, `AdminCategoryService`, `DigestDeliveryFinalizationService`)가 발송 상태 조회/갱신만 필요하다. 이들이 모두 `BatchSummaryStore` 의 wide 인터페이스(~10개 메서드)에 의존하게 되면:
  - 테스트 mock 비용 3배 증가
  - 호출하지 말아야 할 메서드(예: `DigestService` 가 `findByDateRange` 호출)에 우발적 접근 가능
  - 코드만 봐도 "이 서비스는 발송 상태만 쓰는구나" 가독성 손실
- 더 깊게 키우는 안(retry count, dedup keys, failure tracking 추가) 은 현재 호출자가 필요로 하지 않는 기능이라 YAGNI 위반.

**결정**: `SummaryDeliveryStore` 의 3-메서드 narrow port 형태를 그대로 유지한다. "shallow port" 휴리스틱만 보고 병합하지 않는다. `BatchSummaryStore : SummaryDeliveryStore` 상속 + 어댑터 동일 구현 패턴도 그대로 둔다.

**결과**:
- 향후 아키텍처 리뷰에서 동일 후보가 "BatchSummaryStore 의 슬라이스이므로 병합"으로 다시 제안되지 않는다.
- Port 가 실제로 분기될 필요(다른 어댑터, 다른 backing store) 가 생기거나 의미적으로 다른 책임(예: send retry queue) 이 들어와야 하면 이 ADR 을 폐기/대체한다.

**참고**: ADR-041 과 같이 `improve-codebase-architecture` 리뷰의 "shallow seam" 휴리스틱이 ISP/test-isolation 가치를 못 본 사례. "narrow port" 와 "shallow port" 는 다르다.

## ADR-043: `:ports:persistence` group 변경으로 capability 충돌 해소 (2026-05-12)

**상태**: 채택

**맥락**: 모듈 재구성(ADR-040 / commit `e510f27`) 이후 `:ports:persistence` 와 `:adapters:persistence` 가 같은 default Gradle capability 를 갖게 됐다:
- 두 모듈 모두 `group = "com.ohmyclipping"`, `version = "2.0.0"`
- Gradle 의 default capability 형식은 `{group}:{project.name}:{version}` 인데, `project.name` 은 경로의 마지막 segment 인 `persistence` 로 같음
- 결과 capability `com.ohmyclipping:persistence:2.0.0` 가 두 모듈에 동시 존재

이로 인해 `:adapters:persistence` 의 `implementation(project(":ports:persistence"))` 가 Gradle 의존성 해결에서 자기 자신으로 substitution (`:ports:persistence -> :adapters:persistence`) 됐다. `:adapters:persistence:compileKotlin` 이 자기 jar 에 의존하는 순환 task graph 가 발생해 root app 의 `./gradlew check` 가 빌드 실패로 직행했다.

증거:
- `./gradlew :adapters:persistence:dependencies --configuration runtimeClasspath` 출력에서 `project :ports:persistence -> project :adapters:persistence (*)` substitution 화살표가 보임.
- `./gradlew :adapters:persistence:compileKotlin` 만 단독 실행해도 순환 task graph 에러로 실패.

**결정**: `:ports:persistence/build.gradle.kts` 의 `group` 을 `com.ohmyclipping` → `com.ohmyclipping.ports` 로 변경한다. capability 가 `com.ohmyclipping.ports:persistence:2.0.0` 으로 분리되어 substitution 이 풀린다. `archivesName.set("store-spi")` 도 함께 설정해 산출물 jar 이름(`store-spi-2.0.0.jar`) 까지 distinct 하게 한다.

`outgoing.capability(...)` 명시 방식, settings.gradle 의 project name 재정의 방식도 검토했으나 (a) 전자는 default capability 를 덮지 않고 추가만 됐고, (b) 후자는 project path 자체를 바꿔 9개 build 파일의 `project(":ports:persistence")` 참조를 모두 깨뜨렸다. group 변경이 가장 작은 surface.

**결과**:
- `./gradlew :compileKotlin`, `./gradlew :compileTestKotlin`, `./gradlew :test` 모두 진행 가능해진다.
- ADR-040 의 모듈 재구성 의도는 보존된다(경로/이름 변경 없음).
- 외부 publishing 설정이 없으므로 group prefix 변경의 외부 시스템 영향은 없다.
- **부수 효과 — 656건의 사전 존재 테스트 실패가 드러났다.** 빌드가 `e510f27` 이후로 깨져 있어 `./gradlew :test` 가 한 번도 실행되지 못한 사이 누적된 잠재 결함이다. 대표 증상: `@SpringBootTest` 들이 Hibernate Schema-validation 실패(`missing table [admin_users]`) 로 ApplicationContext 로딩에 실패. Flyway 가 H2 스키마를 채우기 전에 Hibernate 가 검증한다. 이 ADR 범위 밖이며 별도 후속 작업으로 추적해야 한다.

**향후 대안**: 모듈명을 path 와 일치시키고 싶다면 `project(":ports:persistence").name = "store-spi"` 로 rename + 9개 build 파일 참조를 함께 업데이트하는 방안이 더 의미적이지만, 본 ADR 의 범위에서는 group 변경만으로 충분하다.

## ADR-044: OSS 누락 outbound adapter 복원 (2026-05-12)

**상태**: 채택

**맥락**: ADR-043 의 후속 작업으로 `./gradlew :test` 를 실행할 수 있게 된 직후, 657건의 테스트가 `@SpringBootTest` ApplicationContext 로딩 단계에서 실패했다. 초기 증상은 `Schema-validation: missing table [admin_users]` 였으나 (Hibernate 가 본 메시지를 가장 먼저 던졌을 뿐), `spring.jpa.hibernate.ddl-auto: none` 으로 validation 만 비활성화하니 진짜 root cause 가 드러났다: **여러 outbound port (`SlackDeliveryPort`, `SlackMessageSender`, `OpsLogNotifier`, `CollectionUrlSafetyPort` 외 5종, `NaverNewsSearchPort`) 가 Spring 빈 구현체 없이 인터페이스만 남아 있었다.**

원인 추적: OSS sanitization 커밋(`842f3e6 chore: initial open-source release`) 이 "real Slack/secret material" 을 제거하면서 `src/main/kotlin/com/ohmyclipping/adapter/out/` 디렉토리 전체를 같이 제거했다. 포트 인터페이스, 호출 서비스, 테스트는 그대로 남아 빌드는 통과했지만 Spring DI 가 끊겨 모든 통합 테스트가 동일한 오류(`No qualifying bean of type ...`)로 실패했다. `config/quality/broad-exception-baseline.txt` 의 `adapter/out/slack/SlackApiMessageSender.kt=5` 항목, `ADR-038` 의 `SlackDeliveryAdapter` 언급 등이 잔존 흔적이다.

**결정**: `src/main/kotlin/com/ohmyclipping/adapter/out/` 디렉토리를 복원해 누락된 outbound 어댑터를 stub 으로 채운다. 외부 API 호출(Slack chat.postMessage, SearchCo, …) 은 본 stub 의 범위 밖이며, 운영 환경에서는 fork 가 본 stub 을 대체하는 production 빈을 등록한다.

복원한 어댑터:
- `adapter/out/slack/SlackApiMessageSender` — `SlackMessageSender` no-op stub (`ok=false` SendResult 반환).
- `adapter/out/slack/SlackDeliveryAdapter` — `SlackDeliveryPort` 를 위 sender 로 위임.
- `adapter/out/slack/SlackOpsLogNotifier` — `OpsLogNotifier` no-op stub (debug log 만 남김).
- `adapter/out/AppPortAdapters` (한 파일, 6 어댑터): `CollectionRuntimeSettingsAdapter`, `CollectionArticleExtractorAdapter`, `CollectionUrlSafetyAdapter`, `SourceUrlSafetyAdapter`, `SourceSlaSettingsAdapter`, `SourceOrganizationAdapter` — root app 의 기존 `UrlSafetyValidator`/`ArticleContentExtractor`/`OrganizationService`/`SlaEscalationProperties` 에 위임.
- `adapter/out/NaverNewsSearchAdapter` — `NaverNewsSearchPort` no-op stub (`isConfigured()=false`).

**결과**:
- `./gradlew :test` 실패 건수: 657 → 218 (67% 감소). 잔여 218건은 Spring DI 가 아니라 진짜 assertion/business logic 실패 (`SESSION cookie not found`, JPA empty result, 도메인 assertion mismatch 등) 로 별도 개별 fix 가 필요하다.
- `config/quality/broad-exception-baseline.txt` 에서 누락 파일 항목(`adapter/out/slack/SlackApiMessageSender.kt=5`) 을 제거했다. 본 stub 은 broad catch 가 없다.
- ADR-043 의 follow-up 인 "656 latent failure triage" 가 (대부분) 해소됐다. Spring 컨텍스트 로딩이 다시 표준 동작하므로 향후 root-app 리팩토링이 통합 테스트로 회귀 보호된다.

**향후 작업**: 잔여 218건 중 assertion 실패는 진단/수정이 필요하다 (대표 사례: `SESSION cookie not found`, JPA query result mismatch). 운영 환경에서 실제 Slack/SearchCo 통합이 필요한 경우 본 stub 들을 production 어댑터(`@Primary` 빈 또는 fork 단위 대체) 로 교체한다.
