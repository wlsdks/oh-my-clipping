# Clipping MCP Server - Claude 작업 가이드

이 문서는 Claude(및 유사 코딩 에이전트)가 이 저장소에서
일관된 품질로 작업하기 위한 실행 가이드다.
세부 원칙은 `AGENTS.md`를 기본으로 하며, 본 문서는 **실행 순서와 체크리스트**를 강화한다.

## 1) 작업 기본 원칙
1. 변경은 작고 명확하게 만든다.
2. 아키텍처 경계를 넘는 import/의존성을 만들지 않는다.
3. 모든 변경은 테스트 또는 검증 명령으로 확인한다.
4. 문서 변경은 코드 변경과 같은 PR에서 함께 관리한다.
5. **사용자 관점으로 기획하고 개발한다** — 관리자/개발자가 아닌 최종 사용자의 경험을 우선 고려한다.
6. **UI/UX는 모던 미니멀 디자인을 지향한다** — 아래 "UI/UX 디자인 원칙" 섹션 참고.
7. **항상 구조화된 프로덕션 품질 코드를 작성한다** — "일단 돌아가게" 식의 급조 코드 금지. 아래 세부 규칙 참고.
8. **코드 변경 시 관련 문서를 반드시 함께 갱신한다** — 아래 "문서 동기화 규칙" 참고.
9. **정책/보안/운영 규칙 변경 시 테스트를 가장 먼저 강화한다** — service/store/migration/integration을 함께 본다.
10. **전략/설계 문서 작성 시 반드시 3단계 리뷰를 거친다** — 아래 "전략 문서 리뷰 프로세스" 참고.

### 1.4 전략 문서 리뷰 프로세스
> **전략/설계 문서는 작성 → 자가 리뷰 → 전문가 리뷰를 반드시 거친다.** 리뷰 없이 실행하면 잘못된 가정이 코드에 반영된다.

#### 3단계 프로세스
1. **작성**: 전문가 에이전트를 섭외하여 현재 코드베이스를 분석하고 전략 문서를 작성한다.
2. **자가 리뷰**: 작성 직후 문서를 읽고 placeholders, 내부 모순, 범위 초과를 점검한다.
3. **전문가 리뷰**: 별도 리뷰 에이전트를 보내 **실제 코드와 대조 검증**한다:
   - 파일 경로/행번호가 정확한지 확인
   - SQL이 DB 방언 규칙(1.3절)을 준수하는지 확인
   - 의존성 순서가 꼬여있지 않은지 확인
   - 우선순위가 리스크 기반으로 합리적인지 확인
   - 누락된 고려사항(성능, 마이그레이션 안전성, 하위 호환성)이 없는지 확인

#### 리뷰 결과 반영
- 리뷰에서 발견된 오류는 **문서에 즉시 반영**한다.
- 리뷰 없이 전략 문서를 기반으로 구현을 시작하지 않는다.

### 1.2 문서 동기화 규칙
> **코드와 문서는 항상 일치해야 한다.** 코드만 바꾸고 문서를 안 바꾸면 다음 작업자가 잘못된 정보로 작업한다.

#### 변경 유형별 갱신 대상

| 변경 유형 | 갱신 대상 문서 |
|----------|--------------|
| API 엔드포인트 추가/수정/삭제 | `docs/API_REFERENCE.md` |
| 새 기능/페이지 추가 | `docs/ROADMAP.md` (완료 체크), `AGENTS.md` (폴더 구조, 테스트 현황) |
| 아키텍처 결정 (기술 선택, 패턴 변경) | `docs/ADR.md` (신규 ADR 추가) |
| UI/UX 패턴 변경 (컴포넌트, 레이아웃, 인터랙션) | `docs/DESIGN_STRATEGY.md` (디자인 결정 기록) |
| 테스트 전략/도구 변경 | `docs/TEST_STRATEGY.md` |
| 환경 변수/설정 추가 | `docs/ONBOARDING.md` (환경 변수 섹션) |
| 의존성 버전 업그레이드 | `AGENTS.md` (기술 스택 테이블) |
| 폴더 구조 변경 | `AGENTS.md` (폴더 구조), `AGENTS.md` |

#### 실행 규칙
1. **PR에 코드와 문서를 함께 포함**한다. "문서는 나중에" 금지.
2. 문서 갱신이 필요한지 **커밋 전에 체크**한다 — 위 표를 기준으로.
3. 테스트 현황 숫자(`AGENTS.md` 5.1절)는 **대규모 테스트 추가 시에만** 갱신한다 (매 커밋마다 할 필요 없음).
4. `docs/ROADMAP.md`의 체크박스는 **기능이 main에 머지되면** 즉시 체크한다.
5. 새 ADR은 **결정 시점에 즉시** 작성한다 — 나중에 쓰면 이유를 잊는다.
6. 백엔드 정책 변경 시 `AGENTS.md`를 우선 갱신하고,
7. 메시지 개수, retry, threshold처럼 숫자형 운영 기준을 바꾸면

### 1.1 구조화된 코드 작성 규칙
- **인라인 스타일 남용 금지**: 3개 이상의 Tailwind 유틸리티가 반복되면 컴포넌트로 추출한다. `style={{}}` 직접 작성 금지 — Tailwind v4 유틸리티 클래스를 사용한다.
- **매직 넘버/문자열 금지**: 색상, 크기, 임계값 등은 Tailwind 디자인 토큰(`text-primary`, `bg-muted` 등) 또는 상수로 정의한다.
- **React Compiler 규칙 엄수**: `useMemo`, `useCallback`, `React.memo`를 **절대 사용하지 않는다** — React Compiler(babel-plugin-react-compiler)가 자동으로 최적화한다. `eslint-plugin-react-compiler`가 위반을 에러로 감지한다.
- **컴포넌트 책임 분리**: 한 컴포넌트가 데이터 로딩 + 필터링 + 렌더링을 모두 하되, 렌더링 블록이 50줄을 넘으면 하위 컴포넌트로 분리한다.
- **에이전트/팀 작업 시 리드가 코드 리뷰**: 에이전트가 생성한 코드는 반드시 리드(또는 사용자)가 직접 읽고, 아키텍처 위반·URL 오류·스펙 불일치를 검수한 후에야 완료로 처리한다.
- **공통 패턴 재사용**: 로딩/에러/빈 상태 처리, 서비스/쿼리 키 패턴, 폼 패턴 등 기존 코드베이스의 확립된 패턴을 따른다 (아래 2.3, 2.4 참고).
- **스펙과 구현 일치 검증**: API 응답 형식, URL 경로, 필드명이 PRD/스펙 문서와 정확히 일치하는지 확인한다.

### 1.3 Kotlin/Spring 품질 규칙
- **메서드 정렬은 논리 흐름 기준**: Kotlin 공식 컨벤션에 맞춰 visibility 순 기계 정렬보다 읽기 흐름을 우선한다. 인터페이스 구현은 선언 순서를 유지하고 overload는 붙여 둔다.
- **generic 예외 남용 금지**: service/controller/store 경계에서 `IllegalArgumentException`/`RuntimeException`을 새로 추가하지 않는다. 사용자 입력은 `InvalidInputException`, 조회 실패는 `NotFoundException`, 상태 충돌은 `ConflictException`처럼 명확한 도메인 예외를 사용한다.
- **부분 정규화 금지**: 리스트/맵 입력을 정규화할 때 잘못된 원소를 조용히 버리지 않는다. 전체를 거부하거나, 부분 허용 정책을 문서와 테스트로 명시한다.
- **raw `Thread.sleep` 금지**: request/scheduler/event listener 경로에서 직접 sleep하지 않는다. 불가피한 대기는 인터럽트 복구가 가능한 helper로 감싼다.
- **광범위 catch 최소화**: `catch (Exception)`은 boundary 보호막에서만 허용한다. 내부 로직은 구체 예외를 잡거나 그대로 실패시켜 원인이 드러나게 한다.
- **DB 방언 누수 금지**: JDBC 저장소는 PostgreSQL 전용 SQL을 기본값으로 쓰지 않는다. 운영/테스트 DB가 다르면 vendor-neutral SQL 또는 양쪽 테스트를 같은 커밋에 포함한다.
- **Nullable column 캐스팅 방어**: native query `GROUP BY nullable_col` 결과를 non-null cast 하면 NPE. (1) SQL `WHERE col IS NOT NULL`, (2) `as? String` + `mapNotNull`. 상세: [LESSONS.md L-007](docs/LESSONS.md#l-007).

### 1.3.1 Slack/LLM 텍스트 정리 규칙
> **LLM 출력 문자열을 Slack 으로 직접 보내지 않는다. 반드시 sanitize 단계를 거친다.** Line-level strip (문단 단위 아님), surrogate pair 카운팅 주의, fixture 9+ 개, sanitizer `internal` 가시성. 배경/상세: [LESSONS.md L-001](docs/LESSONS.md#l-001). 테스트: `DigestServiceSanitizeSummaryTest`.

### 1.3.2 PostgreSQL 전용 SQL 스캐너 (2026-04-20)
> **`checkPostgresSpecificSql` gradle task 가 Kotlin 소스의 PG-only SQL 을 차단한다.** §1.5 "DB 방언 누수 금지" 규칙을 소스 레벨에서 enforce.

**스캔 대상 패턴 (false positive 방지를 위해 주석은 자동 제외)**:
- `INTERVAL '1 day'` 같은 PostgreSQL 인터벌 리터럴 → Java `Duration.ofDays(...)` 또는 `plusSeconds` 로 대체.
- `ON CONFLICT (col) DO UPDATE` → H2 MODE=PostgreSQL 은 컬럼 없는 `ON CONFLICT DO NOTHING` 만 지원. 업서트가 필요하면 2-step (SELECT → INSERT/UPDATE) 로 재작성.
- `::jsonb` 캐스트 → JPA converter 나 prepared statement 파라미터로 우회.

**실행 방법**:
```bash
./gradlew checkPostgresSpecificSql   # 단독 실행 (~1초)
./gradlew check                       # test + checkPostgresSpecificSql 동시 실행
```

**위반 시 메시지 예**:
```
path/to/File.kt:42 — PG-only SQL (INTERVAL literal): INTERVAL '180 days'
```

**Detekt 재도입 TODO**: Kotlin 2.3 을 지원하는 Detekt 2.0 stable 이 배포되면 `TooGenericExceptionCaught`/`TooGenericExceptionThrown`/`ForbiddenMethodCall(Thread.sleep)` 룰을 추가로 활성화한다 (현재 1.23.x 는 Kotlin 2.0.21 호환까지만). 그 전까지는 §1.3 규칙은 PR 코드 리뷰 + `/ultrareview` 로 enforce.

### 1.3.3 Broad catch baseline 스캐너 (2026-05-02)
> **`checkBroadExceptionBaseline` gradle task 가 `catch (Exception)` 증가를 차단한다.** 현재 잔여 broad catch는 외부 API, 필터, 캐시, 스케줄러 boundary 보호막이 섞여 있어 즉시 전부 제거하지 않고 shrink-only baseline으로 관리한다.

**실행 방법**:
```bash
./gradlew checkBroadExceptionBaseline   # 단독 실행
./gradlew check                         # test + 정적 검사 일괄 실행
```

**운영 규칙**:
- 새 `catch (Exception)` 파일 또는 파일별 count 증가를 금지한다.
- broad catch를 구체 예외로 낮춘 경우 `config/quality/broad-exception-baseline.txt`의 해당 count도 함께 낮춘다.
- baseline은 영구 allowlist가 아니라 점진적으로 줄이는 현황표다.
- boundary 보호막이 아닌 내부 로직에는 새 broad catch를 추가하지 않는다.

### 1.5 DB 마이그레이션 & FK 추가 체크리스트
> **FK/제약 추가는 기존 데이터를 파괴할 수 있다.** PR 머지 전 아래를 확인한다. 배경/상세 incident: [LESSONS.md L-002, L-003](docs/LESSONS.md#l-002).

#### FK 추가 시 필수 체크
1. **Seed 데이터 정합성** — `local-bootstrap.sql`, `*.seed.sql` 등 기존 seed 에 orphan FK 값 없는지 확인. 있으면 ApplicationContext 전체가 cascade 실패.
2. **Bearer 토큰 principal** — `ADMIN_API_TOKEN` 경로는 `admin_users` 에 없는 가상 principal (`"admin-api"`) 사용. audit 계열 actor FK 는 **nullable + `ON DELETE SET NULL`**.
3. **테스트 실행 순서** — `./gradlew test --tests "*FlywayMigrationTest"` 먼저.
4. **대규모 DDL 시 추가 규칙** — 삭제 전 `_v{N}_backup_{table}` 스냅샷, `SET NULL` 전에 `DROP NOT NULL`, EXISTS 서브쿼리 column 은 inner alias 로 qualify, idempotent 재실행, `ON DELETE` 액션 명시. (V117 saga 상세: LESSONS.md L-002)

#### H2 vs PostgreSQL 방언 체크리스트
H2 MODE=PostgreSQL 이 지원하지 **않는** 것들:
- `ON CONFLICT (col) DO ...` — 컬럼 지정 없는 `ON CONFLICT DO NOTHING` 만 허용
- `WITH cte AS (...) DELETE FROM ...` — 서브쿼리 인라인 + 단일컬럼 IN 으로 재작성
- `INTERVAL '180 days'` 리터럴 — Java 산술 또는 vendor-neutral 사용
- 테이블 여러 ALTER 를 한 명령에 묶기 — 각 ADD COLUMN/CONSTRAINT 를 별도 ALTER 로 분리
- `ALTER COLUMN ... SET NOT NULL` 직접 — 2단계(ADD nullable → UPDATE → SET NOT NULL)

#### 마이그레이션 번호 충돌 처리 (PR 병렬 작업 시)
1. 최신 main 의 가장 큰 `V*` 확인: `ls src/main/resources/db/migration/V*.sql | sort -V | tail -5`
2. 내 브랜치 번호를 main+1 이상으로 renumber: `git mv V120__foo.sql V122__foo.sql`
3. 테스트/코드 참조 업데이트 → `--amend` + force push (PR 링크 유지)

## 2) 아키텍처 기준

### 2.1 백엔드: Clean Architecture
- `admin`, `user`, `tool`은 Inbound Adapter로 동작하고 정책은 `service`에 둔다.
  - 사용자 컨트롤러는 `user/` 패키지에 위치한다 (PR #188에서 `admin/` → `user/`로 이동).
- `service`는 포트(인터페이스)에 의존하고, 구현은 `store/rss/content/ai` 어댑터가 담당한다.
- 인프라 변경이 서비스 정책을 오염시키지 않도록 분리한다.
- 파이프라인 오케스트레이션(`AdminClippingService`, `RalphPipelineOrchestrator`, `DeterministicPipelineRunner`)은 구체 `ClippingService`가 아니라 `modules/digest-policy`의 `service/port/ClippingPipelinePort`에 의존한다. 포트 반환 타입은 앱 응답 모델이 아니라 `PipelineCollectResult`/`PipelineSummarizeResult`/`PipelineDigestResult`이며, `ClippingPipelineAdapter`가 기존 `ClippingService` 결과를 변환한다.
- RSS 수집, LLM 요약, Slack 발송 포트(`RssCollectionPort`, `LlmSummarizationPort`, `SlackDeliveryPort`)도 `modules/digest-policy` 모듈이 소유한다. 앱 모듈은 이 포트의 어댑터 구현만 제공하고, 포트 DTO에 Spring/JPA/store/app model 의존성을 추가하지 않는다.
- MCP/사용자 조회성 진입점은 구체 `ClippingService`가 아니라 `service/port/ClippingQueryPort`에 의존한다. 기존 조회 응답 모델 호환은 `ClippingQueryAdapter`가 보장한다.
- prepared digest 생성/발송/finalization 경계는 `ports/workflow`의 `service/port/DigestDeliveryWorkflowPort`에 둔다. 포트는 pipeline DTO가 아니라 `PreparedDigestResult`/`PreparedDigestItemResult` 전용 DTO를 노출한다. `SlackDigestWorker`는 스케줄/예약/retry orchestration만 담당하고, 기존 `ClippingService` 호출은 `DigestDeliveryWorkflowAdapter` 내부로 제한한다.
- RSS 수집을 호출하는 서비스(`CollectionService`, 경쟁사 수집/프리뷰, 소스 헬스)는 구체 `RssFeedCollector`가 아니라 `service/port/RssCollectionPort`에 의존한다. 포트는 앱 모델 대신 `RssCollectionSource`/`RssCollectedItem` DTO를 사용하고, `rss/RssCollectionAdapter`가 기존 collector API와 매핑한다.
- RSS 수집 앱 모델과 엔진 포트 DTO 간 변환은 `service/collection/RssCollectionMapper`에 둔다.
- 단일 RSS source 수집, 중복 필터링, 저장, fail count, 메트릭, crawl log 기록은 `service/collection/RssSourceCollectionService`에 두고, `CollectionService`는 카테고리별 orchestration과 집계만 담당한다.
- 수동 URL 수집 유스케이스는 `service/collection/ManualUrlCollectionService`에 두고, `CollectionService.addUrl`은 기존 공개 계약 유지를 위한 위임만 담당한다.
- 수동 URL 수집의 robots 정책 포트는 `service/collection/RobotsPolicyClient`에 두고, HTTP 구현은 `rss/HttpRobotsPolicyClient` 어댑터가 담당한다.
- 원본 본문 보존 가능 여부 판단과 `original_content` 저장은 `service/collection/OriginalContentArchiver`에 둔다.
- RSS 소스별 크롤 시도 기록과 실패 원인 분류는 `service/collection/SourceCrawlLogRecorder`에 두고, `CollectionService`는 수집 흐름 조정만 담당한다.
- SearchCo 뉴스 보완 수집은 `service/collection/NaverNewsCollectionService`에 두고, 검색 계약은 `service/collection/NaverNewsSearchPort`가 담당한다. 외부 API 호출 구현은 `adapter/out/naver` 어댑터가 포트를 구현한다.
- 경쟁사 CRUD/미러링/스냅샷/수집/AI 요약/주간 리포트 정책은 일반 카테고리 수집과 분리해 `service/competitor/` 도메인 패키지에 둔다.
- LLM 요약/번역/스크리닝을 호출하는 서비스는 구체 `ClippingSummarizer`가 아니라 `service/port/LlmSummarizationPort`에 의존한다. 포트는 앱 모델 대신 `LlmArticleSummaryResult`/`LlmDailySummaryResult`/`LlmCompetitorTimelineItem`/`LlmPersona` DTO를 사용하고, `ai/LlmSummarizationAdapter`가 기존 Gemini 구현체와 매핑한다.
- 배치/파이프라인 Slack 발송 호출자는 발송 전용 `service/port/SlackDeliveryPort`에 의존한다. 포트는 `SlackDeliveryMetadata`/`SlackDeliveryColor`/`SlackDeliveryResult` 독립 DTO만 노출하고, 연결 검증, 채널 조회, DM 개설 같은 운영/설정 API는 기존 `SlackMessageSender` 경계에 남긴다.
- 운영 알림 발송 호출자는 `ports/workflow`의 `service/port/OpsLogNotifier`에 의존한다. 파이프라인 실행/스텝/인시던트/예측/리포트 DTO와 `OpsNotificationEvent`/`UserNotificationEvent`/`OpsRequestNotificationEvent`/`NotificationSeverity`는 app port 모듈의 도메인 알림 계약이다.
- 알림 발송 정책 서비스는 `adapters/notification` application 모듈의 `service/notification/`에 둔다. 이 모듈은 Slack 발송, runtime 설정, dedup 저장소를 각각 포트로만 접근하고 root app 구현 패키지를 직접 import하지 않는다.
- RSS/수동 URL/Naver 뉴스 수집 application 서비스는 `modules/collection` 모듈의 `service/collection/`에 둔다. content extraction, URL safety, metrics, stats, scheduler error 알림은 포트로 접근한다.
- 사용자 구독/요청/이벤트/전달 로그 application 서비스 중 root 구현 의존이 없는 유스케이스는 `modules/user` 모듈에 둔다. 아직 security/config/content/root service 의존이 남은 사용자 서비스는 root app에 남기고, 포트화가 끝난 뒤 이동한다.
- 키워드/감성/상위 기사/트렌드/통계 조회 application 서비스는 `modules/analytics` 모듈에 둔다. scheduler/config/observability에 직접 붙은 운영 analytics는 root app에 남긴다.
- 다이제스트 application 보조 서비스와 포트 매핑/알림 DTO 변환처럼 root 구현 의존이 없는 다이제스트 경계는 `modules/digest` 모듈에 둔다. Slack worker, renderer, preview, finalization orchestration처럼 config/support/root service 의존이 큰 코드는 root app에서 조립한다.
- 사용자/관리자 RSS 소스 URL 검증, RSS 피드 탐색, 도메인 추출, 소스 헬스/커버리지/신뢰도/SLA 정책과 카테고리 기반 RSS 소스 동기화는 `modules/source` 모듈의 `service/source/` 도메인 패키지에 둔다. URL safety, SLA 설정, 조직 조회, metrics, ops notification은 포트로 접근하고, HTTP 기반 실제 검증 구현은 `rss/HttpSourceVerificationClient` 어댑터가 담당한다.
- `modules/collection/` Gradle 서브모듈은 수집 application 서비스를 소유한다. Spring service bean은 허용하지만 root app `config`, `content`, `security`, `observability`, `rss`, `adapter`, `admin`, `user`, `ai` 구현 패키지는 직접 import하지 않는다.
- `modules/user/` Gradle 서브모듈은 사용자 application 서비스와 사용자 응답 DTO를 소유한다. Spring service bean은 허용하지만 root app 구현 패키지와 support/config/security/content/adapter를 직접 import하지 않는다.
- `modules/admin/` Gradle 서브모듈은 관리자 application DTO를 소유한다. 현재는 DTO 전용이며, 관리자 서비스 로직은 아직 root app에 남아 있고 추후 마이그레이션에서 이 모듈로 이동할 수 있다.
- `modules/analytics/` Gradle 서브모듈은 analytics application 서비스와 analytics 응답 DTO를 소유한다. Spring service bean은 허용하지만 root app 구현 패키지와 scheduler/config/observability를 직접 import하지 않는다.
- `modules/digest/` Gradle 서브모듈은 digest application 보조 서비스를 소유한다. Spring service bean은 허용하지만 root app 구현 패키지와 Slack/RSS/AI/config/support를 직접 import하지 않는다.
- `modules/source/` Gradle 서브모듈은 source application 서비스를 소유한다. Spring service bean은 허용하지만 root app `config`, `content`, `security`, `observability`, `rss`, `adapter`, `admin`, `user`, `ai` 구현 패키지는 직접 import하지 않는다.
- `core/domain/` Gradle 서브모듈은 순수 도메인 모델 모듈이다. `Category`, `RssSource`, `Persona`, `AdminUser` 같은 비즈니스 모델만 소유하며 Spring, JPA, store, service, adapter 에 의존하지 않는다.
- `adapters/persistence/` Gradle 서브모듈은 JPA entity, Spring Data repository, Jpa/Jdbc store 구현을 소유한다. app service/adapter/config에 의존하지 않으며, store SPI를 구현해 DB 접근을 담당한다.
- `core/api-models/` Gradle 서브모듈은 API/MCP/서비스 결과 DTO(`service.dto.clipping`), 파이프라인 실행 이력 DTO(`service.dto.pipeline`), 그리고 모듈을 가로지르는 cross-cutting DTO 계약을 소유한다. Spring, JPA, store, app model(`com.ohmyclipping.model`)에 의존하지 않는다.
- `ports/persistence/` Gradle 서브모듈은 DB 접근 포트와 store 반환 DTO 계약을 소유한다. Spring, JPA, entity, repository, adapter 에 의존하지 않는다.
- `ports/workflow/` Gradle 서브모듈은 앱 내부 workflow/notification 경계 모듈이다. prepared digest workflow 포트/DTO와 ops notification 포트/DTO를 소유하며 Spring, JPA, store, app model(`com.ohmyclipping.model`)에 의존하지 않는다.
- `adapters/notification/` Gradle 서브모듈은 운영/사용자 알림 application 서비스를 소유한다. Spring service bean은 허용하지만 root app `config`, `admin`, `user`, `adapter`, `rss`, `ai`, `content`, `security`, `observability` 구현 패키지는 직접 import하지 않는다.
- `core/error-types/` Gradle 서브모듈은 서비스 공통 예외/에러 코드 계약을 소유한다. root app과 persistence가 같은 예외 타입을 공유하며, app model/store/entity/repository/adapter 에 의존하지 않는다.
- `modules/digest-policy/` Gradle 서브모듈은 클리핑 엔진 코어다. Spring, JPA, store, app model(`com.ohmyclipping.model`)에 의존하지 않는다.
- `:core:domain:checkDomainBoundaries` Gradle task가 도메인 모델 모듈의 Spring/JPA/store/service/adapter import를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:adapters:persistence:checkPersistenceBoundaries` Gradle task가 persistence 모듈의 app service/adapter/config import를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:core:api-models:checkApiModelBoundaries` Gradle task가 API model 모듈(API 응답 DTO + pipeline 실행 이력 DTO + cross-cutting DTO)의 Spring/JPA/store/app model import를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:ports:persistence:checkStoreSpiBoundaries` Gradle task가 store SPI 모듈의 Spring/JPA/entity/repository/adapter import를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:ports:workflow:checkAppPortBoundaries` Gradle task가 app port 모듈의 Spring/JPA/store/app model import를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:modules:collection:checkCollectionBoundaries` Gradle task가 collection 모듈의 root app 구현 패키지 역참조를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:modules:user:checkUserApplicationBoundaries` Gradle task가 user application 모듈의 root app 구현 패키지 역참조를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:modules:analytics:checkAnalyticsApplicationBoundaries` Gradle task가 analytics application 모듈의 root app 구현 패키지 역참조를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:modules:digest:checkDigestApplicationBoundaries` Gradle task가 digest application 모듈의 root app 구현 패키지 역참조를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:modules:source:checkSourceBoundaries` Gradle task가 source 모듈의 root app 구현 패키지 역참조를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:adapters:notification:checkNotificationBoundaries` Gradle task가 notification 모듈의 root app 구현 패키지 역참조를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:core:error-types:checkErrorTypeBoundaries` Gradle task가 error type 모듈의 app/persistence/adapter import를 차단한다. `./gradlew check`는 이 task를 포함한다.
- `:modules:digest-policy:checkEngineBoundaries` Gradle task가 엔진 모듈의 Spring/JPA/store/app model import를 차단한다. `./gradlew check`는 이 task를 포함한다.
  - 허용: 순수 Kotlin/JDK, 엔진 DTO(`DigestArticle`, `DigestOrganization`, `DigestCandidate`, `DigestDocument`), 엔진 예외, 순수 선정/섹션/매칭/document/summary-formatting policy, deterministic pipeline 실행 순서/trace policy.
  - 엔진 레이어는 Spring/store 의존을 갖지 않는다. root app 의 `service/digest` 패키지는 다이제스트 application orchestration, Slack delivery workflow adapter, rendering/finalization 조립 책임을 둔다.
- OpenAPI 문서: `/api-docs` (JSON), `/swagger-ui.html` (UI)

### 2.1.1 감사 로그 actor_id 규칙 (필수)
> **audit_log.actor_id 는 반드시 `AuditActorResolver.resolve(principal)` 경유**. `authentication.name`(username)을 직접 저장하면 V117/V120 에서 추가된 `fk_audit_log_actor` FK 제약 위반.

```kotlin
// ❌ 금지 — username 을 actor_id 에 저장
auditLogStore.log(actorId = authentication.name, actorName = ...)

// ✅ 필수 — resolver 가 UUID(admin_users.id) 또는 null 로 정규화
val resolved = auditActorResolver.resolve(authentication.name)
auditLogStore.log(actorId = resolved.id, actorName = resolved.name, ...)
```

- 새 관리자 서비스 추가 시 `AuditActorResolver` 를 생성자 주입으로 받는다.
- store 레이어는 추가 검증하지 않는다 — FK 가 무결성 보장.

### 2.1.2 DB 감사 해석 규칙
> "0 rows / NULL 누락" 이 감사에 걸려도 **먼저 Explore 에이전트로 코드 참조를 조사**. 정상인 경우가 많다 — `clipping_retention_policies` (카테고리 오버라이드 테이블, 기본값은 env var), `summary_cache` (재요청 시만 적재). 상세: [LESSONS.md L-009](docs/LESSONS.md#l-009).

### 2.2 프론트: Feature-Sliced Design

실제 폴더 구조 (`frontend/src/`):
```
src/
├── app/            # 앱 진입점 (main.tsx, App.tsx, globals.css — Tailwind @theme)
├── pages/          # 라우트 단위 페이지 컴포넌트 (각 도메인 디렉터리 분리)
│   ├── source-quality/  # RSS 소스 품질 — action-oriented KPI + 정렬/필터 테이블 (2026-04-20, ADR-029)
│   ├── organizations/   # Phase 3 PR #431 — 경쟁사/고객사 CRUD + OrganizationMultiSelect
│   └── ...              # dashboard, personas, categories, sources, user-accounts 등
├── components/
│   ├── ui/         # shadcn/ui 헤드리스 컴포넌트 (Radix 기반)
│   └── shared/     # 도메인 무관 공통 컴포넌트 (EmptyState, AdminLayout, Sidebar, ErrorBoundary, WithdrawAccountDialog 등)
├── features/       # 도메인 기능 모듈 (news-intelligence, quick-setup, legal-review 등)
├── services/       # ky 기반 API 호출 함수 (도메인별 *Service.ts)
├── queries/        # TanStack Query 쿼리 키 팩토리 (*Keys.ts)
├── types/          # TypeScript 타입/인터페이스 정의 (도메인별 분리)
├── hooks/          # 커스텀 훅 (useDebounce, useAuth 등)
├── utils/          # 순수 유틸 함수 (date, search, format, cn 등)
├── lib/            # 인프라 유틸 (kyInstance, QueryProvider, theme)
├── shared/         # 크로스커팅 (api/, lib/, types/, ui/)
├── store/          # Zustand 글로벌 상태 슬라이스
├── router/         # createBrowserRouter + adminRoutes 라우터 설정
└── test/           # 테스트 셋업 (setup.ts)
```

필수 규칙:
1. **레이어 역참조 금지** — 하위 → 상위 import 금지. **pages 간 크로스 import 절대 금지**.
2. `components/ui/`는 shadcn 원본 유지 — 직접 수정 시 컴포넌트 교체가 불가해진다.
3. `shared`에는 도메인 정책 금지 — 특정 엔티티(Source, Category 등)에 의존하면 `pages/`로 이동한다.
4. 페이지 내 서브 컴포넌트는 같은 페이지 디렉터리에 위치시킨다 (`pages/sources/SourceListTable.tsx`).
5. **여러 페이지에서 재사용되는 컴포넌트는 `features/` 또는 `components/shared/`로 이동**한다 (예: QuickSetupWizard → `features/quick-setup/`).

### 2.3 프론트: FE 기술 스택

| 분류 | 라이브러리 | 버전 | 용도 |
|------|-----------|------|------|
| 런타임 | React | ^19.2.5 | UI 렌더링 |
| 빌드 | Vite | ^6.x | 번들러 |
| 패키지 | pnpm | - | 패키지 관리 (npm/yarn 사용 금지) |
| 언어 | TypeScript | ^5.9.3 | strict 모드 강제 |
| 스타일 | Tailwind CSS | ^4.2.2 | CSS-first 설정 (`tailwind.config.js` 없음) |
| 컴포넌트 | shadcn/ui (Radix) | - | 헤드리스 접근성 컴포넌트 |
| 최적화 | babel-plugin-react-compiler | ^1.0.0 | 자동 메모이제이션 |
| 라우팅 | react-router-dom | ^7.14.1 | `createBrowserRouter` |
| 서버 상태 | @tanstack/react-query | ^5.99.2 | 서버 상태 캐싱/동기화 |
| HTTP | ky | ^1.14.3 | fetch 래퍼 (axios 대체) |
| 전역 상태 | zustand | ^5.0.12 | 클라이언트 전역 상태 |
| 폼 | react-hook-form + zod | ^7.72 / ^3.25 | 폼 유효성 검사 |
| 차트 | recharts | ^3.8.1 | 통계 시각화 |
| 알림 | sonner | ^2.0.7 | Toast 알림 |
| 애니메이션 | framer-motion | ^12.38.0 | UI 전환 효과 |
| 아이콘 | lucide-react | ^0.513.0 | SVG 아이콘 |
| 한국어 검색 | es-hangul | ^2.3.8 | 초성 검색 |
| 다크모드 | next-themes | ^0.4.6 | 테마 전환 |
| 테스트 | Vitest + Testing Library + MSW + Playwright | ^3.x | 유닛/통합/E2E |
| 커버리지 | @vitest/coverage-v8 | ^3.2.4 | V8 기반 코드 커버리지 측정 |

### 2.4 프론트: 코딩 컨벤션

#### 서비스/HTTP 패턴
```ts
// services/sourceService.ts
import ky from "ky";
import { apiClient } from "@/lib/apiClient";  // ky 인스턴스

export const sourceService = {
  getAll: (categoryId?: string) =>
    apiClient.get("sources", { searchParams: categoryId ? { categoryId } : {} }).json<Source[]>(),
  create: (data: CreateSourceRequest) =>
    apiClient.post("sources", { json: data }).json<Source>(),
};
```

#### 쿼리 키 패턴
```ts
// queries/sourceKeys.ts — 쿼리 키는 반드시 *Keys.ts 파일에 집중 관리
export const sourceKeys = {
  all: ["sources"] as const,
  lists: () => [...sourceKeys.all, "list"] as const,
  listsByCategoryId: (id: string) => [...sourceKeys.lists(), id] as const,
  detail: (id: string) => [...sourceKeys.all, "detail", id] as const,
};
```

#### 폼 패턴
```ts
// 모든 입력 폼은 react-hook-form + zod 조합 필수
const schema = z.object({ name: z.string().min(1, "이름을 입력하세요") });
const { register, handleSubmit, formState: { errors } } = useForm({ resolver: zodResolver(schema) });
```

#### Tailwind v4 규칙
- `tailwind.config.js` 없음 — CSS 파일에서 `@import "tailwindcss"` + `@theme` 블록으로 설정
- 디자인 토큰은 CSS 변수(`--color-primary`, `--radius`)로 정의하고 Tailwind 클래스로 참조
- 다크모드: `dark:` 변형자 사용 (next-themes와 연동)

#### 시맨틱 색상 토큰 규칙 (필수)
> **raw Tailwind 색상 클래스(`bg-red-500`, `text-green-600` 등) 사용 금지.**
> `globals.css`에 정의된 시맨틱 CSS 변수를 사용한다.

| 용도 | Tailwind 클래스 | CSS 변수 |
|------|----------------|----------|
| 성공/활성 배경 | `bg-[var(--status-success-bg)]` | `--status-success-bg` |
| 성공/활성 텍스트 | `text-[var(--status-success-text)]` | `--status-success-text` |
| 경고/대기 배경 | `bg-[var(--status-warning-bg)]` | `--status-warning-bg` |
| 경고/대기 텍스트 | `text-[var(--status-warning-text)]` | `--status-warning-text` |
| 위험/실패 배경 | `bg-[var(--status-danger-bg)]` | `--status-danger-bg` |
| 위험/실패 텍스트 | `text-[var(--status-danger-text)]` | `--status-danger-text` |
| 정보/중립 배경 | `bg-[var(--status-neutral-bg)]` | `--status-neutral-bg` |
| 정보/중립 텍스트 | `text-[var(--status-neutral-text)]` | `--status-neutral-text` |
| 링크 | `text-primary` | `--color-primary` |
| 파괴적 액션 | `bg-destructive` / `text-destructive` | `--color-destructive` |

- 다크모드는 CSS 변수 override로 자동 처리 — `dark:` 변형자 불필요
- `bg-[#hex]` 하드코딩 금지 — 로고 등 브랜딩 요소는 `bg-primary` 사용
- 유일한 예외: ThemeToggle의 Sun 아이콘 (`text-amber-500` — 데코레이티브)

## 3) 문서 작성(Markdown) 실행 규칙
아래 규칙은 공식 가이드와 실무 커뮤니티 권장사항을 합친 최소 규칙이다.

1. 문서 타입부터 결정한다(Diátaxis): Tutorial / How-to / Reference / Explanation.
2. 제목 바로 아래에 3~5줄 요약을 둔다.
3. 절차는 번호 목록, 개념/속성은 불릿 목록으로 분리한다.
4. 코드/명령은 실행 가능한 형태로 제시한다.
5. 옵션/기본값/제약은 표 또는 고정 포맷으로 명확히 쓴다.
6. 문서 말미에 검증 방법과 관련 링크를 남긴다.

권장 문서 골격:
```md
# 제목

## 대상 독자 / 선행조건
## 빠른 시작
## 상세 절차 또는 규칙
## 검증 방법
## 트러블슈팅
## 참고 링크
```

## 4) 구현 절차 (표준)

### 4.0 내부 루프 (task 단위)
1. 요구사항을 한 문장으로 재정의한다.
2. 영향 범위를 파일 단위로 식별한다.
3. 실패 테스트를 먼저 작성한다.
4. 최소 구현으로 통과시킨다.
5. 리팩터링한다.
6. 전체 회귀 테스트를 실행한다.
7. 변경 이유와 트레이드오프를 문서화한다.

### 4.1 외부 루프 (PR 단위 표준 워크플로)
> 기능/픽스 단위 PR 은 기본적으로 아래 6단계를 순서대로 밟는다. 작은 버그픽스(1~5 lines), 오타/문구 수정은 생략 가능.

```
brainstorm → plan → subagent-driven (task별 2단 리뷰)
  → quality gate (typecheck / build / test / E2E)
  → /ultrareview (머지 전 최종 필터)
  → 사람 리뷰 / merge → cleanup
```

| 단계 | 도구/스킬 | 목적 |
|------|----------|------|
| 1. Brainstorm | `superpowers:brainstorming` | 요구사항 → 설계 합의, spec md 작성 |
| 2. Plan | `superpowers:writing-plans` | spec → 실행 단위로 분해, plan md 작성 |
| 3. Implement | `superpowers:subagent-driven-development` | task 별 fresh subagent + (spec 리뷰 → 코드 품질 리뷰) 2단 loop |
| 4. Quality gate | `pnpm typecheck && pnpm build && pnpm test` + `./gradlew test` + Playwright E2E | 자동 회귀 검증 (섹션 5.2) |
| 5. `/ultrareview` | Claude Code 슬래시 커맨드 | 머지 직전 최종 필터 — 여러 에이전트 병렬로 아키텍처/보안/성능/커버리지 리뷰 |
| 6. Merge + cleanup | `gh pr merge --squash --delete-branch` + worktree/브랜치 정리 | 섹션 6.2 cleanup 규칙 준수 |

#### `/ultrareview` 사용 가이드

**꼭 돌리는 경우**
- 머지 직전 최종 관문 — subagent 2단 리뷰로 놓친 이슈 마지막 필터
- 고위험 변경 — 인증/보안, DB 마이그레이션, 결제 로직, 권한 체크, MCP 서버 외부 노출
- 대형 PR — 500+ lines diff
- 대규모 리팩토링 직후 — 숨은 regression / 의존성 체인 깨짐 잡기

**굳이 안 써도 되는 경우**
- 작은 버그픽스 (1~5 lines), 오타/문구 수정
- 단순 의존성 패치 버전 업그레이드
- 이미 subagent 2단 리뷰가 탄탄히 돌아간 소규모 PR

**명령**
- `/ultrareview` — 현재 브랜치(vs origin/main) 리뷰. feature 브랜치 checkout 된 상태에서 실행.
- `/ultrareview <PR#>` — 특정 PR 을 페치해서 리뷰. 이미 머지된 PR 에도 사후 리뷰 가능 (follow-up PR 재료).

## 5) 품질 게이트
백엔드:
```bash
./gradlew test -PskipFrontendBuild=true
# JaCoCo 커버리지 리포트 자동 생성 → build/reports/jacoco/test/html/
```

Kotlin/Spring 고위험 변경 시 추가 확인:
```bash
rg -n "Thread\\.sleep|catch \\(e: Exception\\)|catch \\(_:\\s*Exception\\)|IllegalArgumentException\\(|RuntimeException\\(" src/main/kotlin
```

프론트:
```bash
cd frontend
pnpm lint
pnpm format:check
pnpm typecheck
pnpm build
```

프론트 자동 빌드 스킵(긴급 디버깅용):
```bash
./gradlew bootRun -PskipFrontendBuild=true
```

### 5.2 기능 수정 후 필수 프로세스
> **기능 변경/추가 시 반드시 아래 순서를 따른다.**

1. `pnpm typecheck && pnpm build` — 빌드 통과 확인
2. `pnpm exec playwright test` — **E2E 전체 회귀 테스트 실행** (깨지는 테스트 있으면 수정)
3. 서버/웹 재시작 — 변경 반영 확인
4. 커밋

E2E 테스트 실행 명령:
```bash
cd frontend
pnpm exec playwright test --timeout=60000 --reporter=list
```

깨지는 테스트가 있으면:
- **앱 버그인 경우** → 앱 코드 수정
- **UI 변경으로 셀렉터가 안 맞는 경우** → 테스트 코드를 현재 UI에 맞게 갱신
- 테스트 통과 후 커밋

### 5.3 외부 배포/ngrok 업데이트 전 파이프라인 dry-run
> 수집→요약→발송 파이프라인을 **Slack 전송 없이** 검증해서 릴리스 전 실제 회귀를 잡는다. 1분 이내 완결.

1. 서버 기동: `DEV_BOOTSTRAP=true` 로컬, `MAX_LOGIN_ATTEMPTS_PER_MINUTE=300`
2. admin 폼 로그인 (세션 쿠키): `POST /login` with `username=dev.admin@clipping.local`, `password=LocalPass123!`
3. 시드 카테고리 ID 확인: `GET /api/admin/categories?size=50`
4. Pipeline dry-run: `POST /api/admin/clipping/{categoryId}/pipeline` body `{"sendToSlack": false, "hoursBack": 168, "maxItems": 5}`
5. 응답에서 확인:
   - `collect.totalCollected > 0` — RSS 수집 동작
   - `summarize.totalSummarized > 0` — Gemini 요약 동작
   - `digest.selectedCount > 0` + `digestText` 비어있지 않음 — 다이제스트 조립 OK
   - `digest.postedToSlack: false` — Slack 미발송 확인

NPE/500이 나오면 `/tmp/clipping-server.log` 에서 `Unhandled exception` 을 찾아 즉시 수정. 통과하면 안전하게 외부 배포 가능.

### 5.1 테스트 작성 필수 규칙

> **기능 구현 = 테스트 포함**. 테스트 없는 기능은 미완성이다.

#### 원칙
1. 새 기능/버그 수정 시 **반드시 테스트를 함께 작성**한다 — PR에 테스트가 없으면 리뷰 거부 사유.
2. **해피패스 + 엣지케이스 + 에러 경로** 3가지를 모두 커버한다.
3. trivial getter/pass-through는 제외. 로직이 있는 곳만 테스트한다.
4. 기존 함수/메서드를 수정하면, 해당 테스트도 함께 갱신한다.
5. 정책/운영 규칙 변경 시 **service test + store/migration test + integration test**를 교차로 검토한다.
6. 보안/권한/스케줄러/필터 변경은 단위 테스트만으로 끝내지 않고 HTTP 또는 scheduler 흐름 검증을 포함한다.
7. 새 테스트는 "무엇을 허용/금지하는지"가 드러나는 이름으로 작성한다.
8. Kotlin 안티패턴 정리 시에는 **회귀 테스트 + 런타임 smoke + 문서 갱신**을 같은 배치로 묶는다.

#### 기능 구현 시 테스트 작성 프로세스
1. 구현할 함수/메서드의 **입력-출력 시나리오를 먼저 정리**한다.
2. 시나리오별로 테스트를 작성한다:
   - **해피패스**: 정상 입력 → 기대 결과
   - **경계값**: null, undefined, 빈 문자열, 0, 최대/최소값, 배열 빈/단일/최대 길이
   - **에러 경로**: 잘못된 입력 → 예외/에러 메시지, 권한 없음 → 거부, 존재하지 않는 리소스 → NotFoundException
   - **상태 전이**: 허용된 전이(PENDING→APPROVED) 성공, 불허 전이(APPROVED→PENDING) 거부
3. 구현 코드를 작성하고 테스트를 통과시킨다.
4. 전체 회귀 테스트(`./gradlew test` + `pnpm test`)를 실행한다.

#### 백엔드
- 프레임워크: JUnit 5 + MockK + Kotest matchers
- 구조: `@Nested` inner class + 한글 백틱 테스트명
- mock 패턴: `mockk<Store>()`, `slot<T>()` 캡처, `verify(exactly=1)`
- 위치: `src/test/kotlin/` 동일 패키지 경로
- **새 서비스 메서드 추가 시**: 최소 해피패스 1 + 에러 1 + 권한 1 = 3개 테스트
- scheduler/event 변경 시: 단위 테스트만 두지 말고, 가능하면 실제 HTTP/session 또는 store integration으로 한 번 더 잠근다.
- **Store/Repository integration test — Absolute count 금지**: 공유 H2 에서 잔존 row 로 flake. `list.size shouldBe 1` / `count shouldBe 3` 대신 **delta-based** (`(after - before) shouldBe 3`) 또는 **category-scoped filter** 사용. 배경: [LESSONS.md L-006](docs/LESSONS.md#l-006).

#### 프론트
- 프레임워크: Vitest (`vitest run`)
- 파일 위치: 대상 파일 옆 `__tests__/{파일명}.test.ts`
- 우선 대상: `shared/lib` 순수 유틸 → `pages/*/model` 로직
- **새 유틸 함수 추가 시**: 정상 입력 + null/undefined + 경계값 = 최소 3개 테스트

#### 5.1.0.5 UI 재설계 PR 은 E2E 동기 갱신 필수
> **heading/label/route/data-testid 를 바꾸는 PR 은 같은 커밋에서 관련 Playwright spec 도 갱신.** PR body 에 변경 리스트 남기고, `tests/e2e/**` 의 `getByRole(...)` / `locator(...)` 를 동일 커밋에서 수정. `aria-hidden` 랩퍼 추가 시 `getByRole` 이 숨겨진 요소를 못 찾는 점 주의. `/ultrareview` 는 "E2E 갱신됐는가" 확인 필수. 배경: [LESSONS.md L-004](docs/LESSONS.md#l-004).

#### 5.1.1 Weak assertion 금지
> `expect(result).toBeDefined()` **단독** assertion 금지. 제목이 동작을 주장하면 (예: "URL 인코딩", "필터 파라미터") 실제로 그 동작을 검증한다 — MSW request capture 로 URL/body 확인, 구체 필드 비교. 코드 예시 + 배경 (PR #396, 76 건 교체): [LESSONS.md L-005](docs/LESSONS.md#l-005).

#### 5.1.2 jsdom + Radix 호환 폴리필
Radix UI 컴포넌트를 jsdom에서 테스트할 때 다음이 누락되면 에러:
- `Element.prototype.hasPointerCapture` / `setPointerCapture` / `releasePointerCapture`
- `Element.prototype.scrollIntoView`
- `scrollTo` (framer-motion이 ref에 사용)

해당 테스트 파일 상단에 inline 폴리필 추가:
```ts
beforeAll(() => {
  Element.prototype.hasPointerCapture = Element.prototype.hasPointerCapture || (() => false);
  Element.prototype.setPointerCapture = Element.prototype.setPointerCapture || (() => {});
  Element.prototype.releasePointerCapture = Element.prototype.releasePointerCapture || (() => {});
  Element.prototype.scrollIntoView = Element.prototype.scrollIntoView || (() => {});
  window.HTMLElement.prototype.scrollTo = window.HTMLElement.prototype.scrollTo || (() => {});
});
```

#### 5.1.3 E2E 로그인 패턴
로그인 페이지의 dev shortcut 버튼(`"관리자"/"회원"`)은 **2026-04-17에 제거**됨 (PR #376). Fixture는 id/password로 로그인한다:
```ts
// frontend/tests/e2e/fixtures/auth.ts
await page.goto("/login");
await page.getByLabel("아이디").fill("dev.admin@clipping.local");
await page.getByLabel("비밀번호").fill("LocalPass123!");
await page.getByRole("button", { name: "로그인", exact: true }).click();
```
서버는 반드시 `DEV_BOOTSTRAP=true`로 기동 (seed 계정 생성). `MAX_LOGIN_ATTEMPTS_PER_MINUTE=300`로 올려야 E2E 전체 suite 통과 (기본값 60은 rate limit에 걸림).

## 6) 품질 레퍼런스
- Kotlin Coding Conventions (공식): https://kotlinlang.org/docs/coding-conventions.html
- Kotlin Exceptions & Preconditions (공식): https://kotlinlang.org/docs/exceptions.html
- Spring Framework Scheduling (공식): https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework Transaction-bound Events (공식): https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
- Detekt Gradle Plugin (공식): https://detekt.dev/docs/gettingstarted/gradle
- Diátaxis (공식): https://diataxis.fr/

#### 품질 게이트 (테스트)
```bash
# 백엔드
./gradlew check -PskipFrontendBuild=true

# 프론트엔드
cd frontend && pnpm typecheck && pnpm lint && pnpm build && pnpm test

# E2E
cd frontend && pnpm exec playwright test --timeout=60000 --reporter=list
```

#### 현재 테스트 파일 현황 (2026-05-03 기준)
| 영역 | 파일 수 | 비고 |
|------|--------|------|
| 프론트 유닛/통합 | 204 | `frontend/src/**/*.test.{ts,tsx}` 기준 |
| 백엔드 | 426 | root app + Gradle 서브모듈 `*Test.kt` 기준 |
| E2E (Playwright) | 40 spec | `frontend/tests/e2e/**/*.spec.ts` 기준 |

> 테스트 case 수는 리팩터링 중 변동이 잦아 고정 총합 대신 파일 수와 필수 명령 중심으로 관리한다.
> 최근 테스트 전략과 실행 기준은 `docs/TEST_STRATEGY.md` 참고.

#### 테스트 커버리지 현황 (2026-04-05 세션 종료 기준)

**백엔드 (JaCoCo):**
| 메트릭 | 커버리지 |
|--------|---------|
| Lines | ~70%+ |
| Branches | ~53%+ |
| Methods | ~69%+ |

주요 패키지별: service 84.5%+, model 91.6%, entity 88.4%, config 84.8%, resilience 97.1%+

**프론트엔드 (@vitest/coverage-v8):**
| 메트릭 | 커버리지 |
|--------|---------|
| Lines/Stmts | ~15%+ (서비스 테스트 80개 추가) |
| Branches | 79.0%+ |
| Functions | ~60%+ |

로직 레이어별: shared/lib 88.8%, utils 92.4%, store 100%, services 60%+, hooks 70%+, pages/model 80%+

#### 커버리지 측정 명령
```bash
# 백엔드 — JaCoCo HTML 리포트: build/reports/jacoco/test/html/
./gradlew test -PskipFrontendBuild=true
# test 실행 시 jacocoTestReport가 자동 실행됨 (finalizedBy)

# 프론트엔드 — HTML 리포트: frontend/coverage/
cd frontend && pnpm vitest run --coverage
```

## 6) 커밋/리뷰 체크리스트

### 6.0 **main 브랜치 직접 커밋/푸시 절대 금지**
> **⚠️ 이 규칙은 예외 없이 적용된다.**
>
> 모든 변경은 반드시 **feature 브랜치를 생성**하고, **PR을 통해 머지**해야 한다.
> 아무리 작은 수정(오타 1글자, CSS 1줄)이라도 main에 직접 커밋하지 않는다.
>
> ```bash
> # 올바른 플로우
> git checkout -b fix/description  # 브랜치 생성
> git add ... && git commit ...     # 커밋
> git push -u origin fix/description
> gh pr create --title "..." --body "..."
> gh pr merge --squash --delete-branch
> git checkout main && git pull     # main 동기화
> ```
>
> main에서 `git commit`을 실행하는 것 자체가 금지다.

### 6.1 커밋 품질 체크리스트
1. 커밋 하나당 하나의 논리 변경인가?
2. 테스트/린트/타입체크를 통과했는가?
3. 아키텍처 규칙 위반 import가 없는가?
4. 운영 영향(보안, Slack 전송, 데이터 파기)을 검토했는가?
5. 문서(README/AGENTS/CLAUDE/ADR)가 함께 갱신되었는가?

### 6.3 병렬 에이전트 배치 규칙 (실전 패턴)
여러 에이전트를 병렬로 돌릴 때 파일 충돌을 방지한다 — 세션 중 여러 번 검증된 패턴.

1. **파일 소유권 명시적 분리** — 에이전트 프롬프트에 "다른 에이전트가 수정하는 파일 목록"을 명시한다. 겹치는 파일은 한 에이전트에 몰아준다.
2. **Worktree 1개당 1 에이전트** — `.worktrees/{name}` 에 각자 독립 체크아웃. 절대 같은 worktree를 2개 에이전트가 쓰지 않는다.
3. **Migration 번호 선점** — 에이전트마다 다른 `V{N}` 번호를 할당한다. 충돌 시 위 §1.5 renumber 절차 적용.
4. **Test 파일은 추가만** — 커버리지 에이전트는 기존 테스트 수정 금지, 새 테스트 파일만 생성.
5. **공통 DB 조작은 단일 에이전트에게** — FK/seed 수정은 한 에이전트가 일괄 처리. 병렬 수정 시 mutual trash.
6. **에이전트 완료 후 즉시 rebase** — 한 에이전트가 끝나면 main에 머지하고, 다음 에이전트는 그 위에서 rebase. 병렬 2개 이상 끝난 걸 함께 머지하지 않는다.

### 6.2 **머지 후 브랜치/워크트리 정리 (필수)**
> **⚠️ PR merge는 머지만으로 끝나지 않는다.** 작업 완료 후 **같은 세션 안에서 아래 cleanup을 항상 수행**한다. merged 브랜치가 로컬/리모트/worktree에 쌓이면 다음 작업자가 혼란스럽고 `git branch` / `git worktree list` 가 오염된다.

#### 머지 직후 실행할 cleanup (순서 중요)
```bash
# 1) PR merge는 반드시 --delete-branch 플래그 사용 (리모트 자동 삭제)
gh pr merge <N> --squash --delete-branch

# 2) worktree 제거 (해당 브랜치를 사용하던 경우)
git worktree remove <worktree-path> --force

# 3) 로컬 브랜치 강제 삭제 (squash merge는 -d로 안 되므로 -D 필수)
git branch -D <branch-name>

# 4) main 동기화 (fast-forward 불가 시 원인 파악 후 진행)
git checkout main && git pull --ff-only origin main

# 5) 원격 stale ref 정리
git fetch origin --prune
```

#### 자동 확인 명령 (세션 종료 전)
```bash
# 머지됐는데 남아있는 로컬 브랜치 (main/active 제외) — 없어야 함
for b in $(git branch | tr -d '* +' | grep -v '^$' | grep -v '^main$'); do
  if [ "$(gh pr list --state merged --head "$b" --limit 1 --json number | python3 -c 'import json,sys;print(len(json.load(sys.stdin)))')" -gt "0" ]; then
    echo "STALE: $b"
  fi
done

# 머지된 PR의 원격 브랜치가 남아있는지
git branch -r | grep -v 'HEAD\|main' | while read ref; do
  b=${ref#origin/}
  if [ "$(gh pr list --state merged --head "$b" --limit 1 --json number | python3 -c 'import json,sys;print(len(json.load(sys.stdin)))')" -gt "0" ]; then
    echo "STALE REMOTE: $ref"
  fi
done
```

#### 절대 금지
- PR merge 후 cleanup 없이 다음 작업으로 넘어가기
- `gh pr merge` 를 `--delete-branch` 없이 실행
- worktree 를 "나중에 지워도 되겠지" 하고 방치

#### 실전 패턴
한 세션에서 N개의 PR을 merge했다면, **마지막 merge 직후 일괄 cleanup**해도 된다. 핵심은 세션 종료 전에 `git branch`, `git branch -r`, `git worktree list` 세 개가 모두 깨끗한 상태여야 한다는 것.

## 7) Kotlin 코드 작성 세부 규칙
1. 클래스/인터페이스 상단에는 한국어 KDoc으로 책임과 사용 맥락을 기록한다.
2. 메서드 상단에는 입력 제약, 부작용, 실패 조건을 설명하는 한글 KDoc을 기본 작성한다.
3. 코드 가로 길이는 120자를 넘기지 않는다.
4. 메서드 세로 길이는 선언부터 닫는 중괄호까지 최대 40줄을 기준으로 하고,
   초과 시 method extract를 적용한다.
5. 클래스 메서드 순서는 `override`를 최상단에 두고, `private fun`은 항상 하단에 배치한다.
6. 서비스 클래스 내부에 DTO/inner class/nested class를 두지 않고 전용 패키지로 분리한다.
7. 신규/수정 메서드 내부에서 핵심 로직(검증/분기/외부 호출/데이터 변환) 직전에는
   한 줄 한국어 인라인 주석으로 의도를 명시한다.
   단, 자명한 단순 getter/setter/리턴은 제외한다.

## 8) UI/UX 디자인 원칙 (모던 미니멀)

### 8.1 핵심 방향
- **사용자 관점 우선**: 기능을 만들기 전에 "사용자가 이걸 왜, 언제 쓸까?"를 먼저 답한다.
- **모던 미니멀 UI**: 깔끔하고 직관적인 인터페이스. 불필요한 장식 없이 정보 전달에 집중한다.
- **점진적 공개(Progressive Disclosure)**: 처음엔 핵심만, 필요할 때 상세를 노출한다.

### 8.2 CSS/컴포넌트 스타일 규칙
| 요소 | 스타일 |
|------|--------|
| 카드 | `border-radius: 16px`, 미세 `box-shadow`, 호버 `translateY(-1px)` + 그림자 강화 |
| 뱃지/태그 | Pill 형태 (`border-radius: 100px`), 상태별 배경색 구분 |
| 칩 버튼 | Pill 형태, 선택 시 `var(--primary)` 배경 + 흰 글자 |
| 모달 | `border-radius: 20px`, `max-width: 480px`, `max-height: min(70vh, 640px)`, `padding: 0` + 자식별 개별 padding |
| 모달 스크롤 | wrapper에 `overflow-y: auto` + `flex: 1 1 auto; min-height: 0`, 힌트는 `position: sticky; bottom: 0` |
| 토글 | iOS 스타일 pill toggle (44x24px) |
| 섹션 구분 | 밝은 회색 배경 (`#f8f9fb`) + `12px radius` |
| 태그 입력 | 부드러운 border, focus 시 `box-shadow` ring |
| 전환 효과 | `transition: 0.15~0.25s ease`, 부드럽고 자연스럽게 |

### 8.3 사용자 경험 설계 원칙
1. **즉시 반영 vs 검토 필요**를 명확히 구분한다 ("즉시 반영" 뱃지 표시).
2. **빈 상태(Empty State)**를 항상 설계한다 — 처음 방문한 사용자도 다음 행동을 알 수 있게.
3. **피드백 즉시 제공** — 저장 시 Toast, 로딩 시 스피너, 에러 시 인라인 메시지.
4. **원클릭 프리셋** — 복잡한 설정은 프리셋(평일만/매일/직접 선택)으로 제공하고 커스텀을 허용한다.
5. **내부 메타데이터 절대 노출 금지** — `requestNote` 등 백엔드 필드에 개발용 태그(`[baseRequestId=...]`, `[설정 변경]`, `[위자드]` 등)가 포함될 수 있다. 사용자에게 표시할 때는 반드시 `sanitizeRequestNote()` 등으로 대괄호 태그를 제거한다. UUID, 내부 ID, 코드 용어 등 사용자가 알 필요 없는 정보는 절대 UI에 노출하지 않는다.
   - **⚠️ 에러 메시지 한국어화 필수** — 백엔드에서 오는 영어 에러 메시지(`"URL scheme is required"`, `"Feed parsing failed"`, `"Connection timed out"` 등)를 **절대 그대로 사용자에게 보여주지 않는다.** 반드시 `userFriendlyMessage()` 또는 별도 매핑으로 한국어 안내 문구로 변환한다. 예: `"URL scheme is required"` → `"http:// 또는 https://로 시작하는 주소를 입력해 주세요"`. 영어 기술 용어(scheme, parse, timeout, validation, null, undefined 등)가 사용자 화면에 노출되면 **버그로 간주**한다.
6. **카드 메타 한 줄 요약** — 핵심 정보를 `#channel · 페르소나 · 국내 · 평일 오전 9시 · 이번 달 5건` 형태로.
7. **에러 재시도 UX** — 재시도 시 화면 깜빡임(로딩→에러 전환) 금지. 에러 화면 유지한 채 백그라운드 재호출 → 성공 시 결과 표시, 실패 시 토스트 알림.
8. **Mock 데이터 방어** — 프론트엔드 목 데이터(`mock-` 접두어 ID)로 백엔드 API를 호출하면 안 된다. 삭제/철회 등 변경 액션은 로컬 state에서 처리한다.
9. **차트/테이블 레이블 말줄임 금지 (내부 관리자 툴)** — 카테고리명/소스명 등 식별자 라벨은 말줄임(`…`) 금지. 풀네임이 레이아웃을 침범하면 **축/컬럼 폭을 넓히거나 레이아웃을 재설계**한다. hover 툴팁으로 풀네임 보이기도 대체책이 아니다 — 스캔 단계에서 이미 못 알아보면 hover 기회가 안 온다. 내부 툴은 **정보 가시성 > 미적 완벽성** (PR #420 교훈).
10. **반복 경고/갭 리스트 — Top-N + 펼치기 일관 패턴** — 커버리지 갭, 문제 소스, 리뷰 대기 등 "N건이면 N줄 스택" 패턴은 즉시 `DEFAULT_VISIBLE=3~5` + `{hidden}개 더 보기`/`접기` 토글로 접는다. HIGH severity 항목을 상단에 정렬해서 기본 N개 안에 포함되게 한다. 동일 repo 안에서 일관된 UX → 학습 비용 0. 참고 구현: `ProblemSourcesSection` (5), `CoverageGapBanner` (3) (PR #419 기준).

## 9) 서버 운영 주의사항

### 9.1 서버 재시작
- 서버 포트: **8086**
- **`DB_PASSWORD` 환경변수 필수** — 기본값이 없으므로 설정하지 않으면 서버가 시작되지 않는다.
- **권장**: `./restart.sh` 사용 — `.env` 를 자동으로 source 해서 `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `DEV_BOOTSTRAP`, `MAX_LOGIN_ATTEMPTS_PER_MINUTE` 등 필수 환경변수를 주입한다. Spring Boot 는 `.env` 를 자동 로드하지 않기 때문에 이 스크립트를 거치지 않으면 `@Value` default (예: `DEV_BOOTSTRAP=true`) 가 적용되어 사고 위험.
- 프론트 빌드가 이미 되어 있으면: `./gradlew bootRun -PskipFrontendBuild=true` (단, 환경변수는 사전에 export 필요)
- **절대 금지**: `lsof -ti:8086 | xargs kill` — Safari 등 같은 포트를 사용하는 다른 프로세스가 같이 죽는다.
- **안전한 방법**: `pkill -f 'bootRun'` 또는 `pkill -f 'clipping-mcp-server'`
- 빌드 완료 후에는 항상 서버를 재시작한다.

### 9.1.1 ⚠️ `DEV_BOOTSTRAP` 파괴적 동작 경고 (필독)
> **`DEV_BOOTSTRAP=true`로 서버를 기동하면 `local-bootstrap.sql`이 실행되며 이 SQL은 유저 데이터 테이블을 DELETE한다.** 과거 인시던트 회고: E2E 테스트용으로 `.env`를 `DEV_BOOTSTRAP=true`로 바꾸고 원복하지 않은 채 서버 재기동 → 실 사용자 구독 전부 소실. archive_mode=off 환경에서는 복구 불가.

#### 규칙
1. **`.env`의 `DEV_BOOTSTRAP`은 원칙적으로 `false`**. 테스트·fresh seed가 필요할 때만 **임시로** true로 바꾸고 **작업 후 반드시 원복**한다.
2. 재기동 직전 `grep DEV_BOOTSTRAP .env`로 상태 확인하는 습관을 들인다.
3. **Bootstrap은 실 사용자 데이터 있는 DB에서 돌리지 않는다.** `LocalDevBootstrapConfig`에 `admin_users` 중 seed ID 패턴(`00000000-0000-0000-0000-%`)이 아닌 row가 1개라도 있으면 `IllegalStateException`으로 abort하는 pre-flight guard가 들어있다. Guard가 트립하면 서버는 뜨지 않는다.
4. Guard를 우회하려면 실 사용자 row를 **의도적으로** 먼저 수동 DELETE해야 한다 (플래그 바이패스는 제공하지 않는다).

#### DELETE 대상 테이블 (local-bootstrap.sql)
- `clipping_user_requests` — 구독 요청 (주제, 소스, Slack채널, 페르소나, note)
- `user_delivery_schedules` — 발송 요일·시간
- `summary_feedback`, `clipping_review_items`, `clipping_review_item_audits`
- `batch_summaries`, `daily_summaries`
- `rss_items`, `clipping_stats`, `llm_runs`, `pipeline_step_traces`, `pipeline_runs`
- `competitor_watchlist` (E2E% 제외), `organizations` (E2E%) 등

### 9.1.5 EMPTY_RESULT 진단 태그 유지
> `ClippingSummarizer.summarizeArticle()` 의 null-return 경로 추가 시 **`lastRejectReason.set("NEW_TAG")` 필수**. 태그 없으면 `llm_runs.error_message=NULL` 로 prod 장애 원인 판별 불가. 현재 태그 목록 + `JdbcPipelineAnalyticsStore` 집계 쿼리 갱신 절차: [LESSONS.md L-008](docs/LESSONS.md#l-008).

### 9.4 보안 정책 요약
- **비밀번호 복잡도**: 최소 8 자 + 영문 1 자 + 숫자 1 자 (대문자/특수문자 권장)
- **로그인 Rate Limit**: 프로덕션 10 회/분, 테스트 60 회/분 (`MAX_LOGIN_ATTEMPTS_PER_MINUTE`)
- **CSRF**: `CookieCsrfTokenRepository` 활성화 (`/api/**` 제외)
- **세션 쿠키**: `HttpOnly=true`, `SameSite=lax`, `Secure`는 `SESSION_COOKIE_SECURE`
- **Slack 토큰 암호화**: `ENCRYPTION_KEY` 설정 시 AES-256-GCM, 미설정 시 평문 폴백
- **Slack Webhook 검증**: `SLACK_SIGNING_SECRET` 설정 시 HMAC-SHA256
- **HikariCP 풀**: 최대 40 커넥션 / **LLM maxOutputTokens**: 1024 / **Slack 메시지**: 3000자 상한
- **서킷 브레이커**: 5 회 실패 → OPEN, 지수 백오프 30s→60s→120s→300s
- **스레드풀**: sched-(20) 일반 + digest-(5) Slack 전용
- **Gemini API 재시도**: transient 시 최대 2 회, 2 초 대기, CB-aware
- **사용자 셀프 탈퇴**: `POST /api/user/account/withdraw`
- **MCP 서버**: `CLIPPING_MCP_SERVER_ENABLED=true` 시 `/sse` + `/mcp/message` 활성화, Bearer 토큰(`CLIPPING_MCP_SERVICE_TOKEN`) 인증
- **MCP 도구 역할 분리**: `admin_pipeline` 은 미리보기 전용(Slack 발송 없음), `admin_send_digest` 는 발송 전용(항상 Slack 게시)
- **MCP sync/async 가드**: `admin_collect` / `admin_summarize` 는 `categoryId` 필수 + `hoursBack <= 6`. 긴 range 는 `admin_collect_async` / `admin_summarize_async` 로 위임
- **MCP 감사 로그**: `mcp_audit_log`, append-only, 90 일 retention
- **MCP Rate Limit 상세** (per tokenKid, Redis 슬라이딩 윈도우): `AGENTS.md` 참고. 핵심값 — `admin_send_digest` 2 회/시간, `admin_pipeline` 5 회/시간, `admin_collect` 20 회/시간, user verb 60~120 회/시간.

### 9.5 환경변수 목록
> 전체 목록은 `docs/ONBOARDING.md` §7. 프로덕션 필수: `DB_PASSWORD`, `ENCRYPTION_KEY`, `SLACK_SIGNING_SECRET`, `SESSION_COOKIE_SECURE=true`. MCP 활성 시 추가: `CLIPPING_MCP_SERVER_ENABLED=true`, `CLIPPING_MCP_SERVICE_TOKEN` (32 자+).

### 9.2 신청 요청 생명주기 정책
- **철회/반려 항목은 영구 보관**한다 — 자동 삭제하지 않는다.
- 사용자가 원하면 반려(REJECTED) 또는 철회(WITHDRAWN) 항목을 **수동 삭제**할 수 있다 (`DELETE /api/user/requests/{id}/remove`).
- 승인(APPROVED)이나 검토 중(PENDING) 항목은 삭제 불가.
- 삭제된 항목은 DB에서 완전 제거된다 (soft delete 아님).
- 철회/반려 항목에서 **다시 신청하기** 시 위자드(최초 생성 UI)를 사용하며, 기존 데이터를 pre-fill한다.

### 9.3 발송 스케줄 정책
- **글로벌 기본 설정**: `user_delivery_schedules` 테이블 — 유저 단위 발송 요일/시간.
- **구독별 개별 설정**: `clipping_category_rules`의 `delivery_days/delivery_hour/delivery_preset` — 카테고리 단위.
- **폴백 규칙**: 구독별 설정이 NULL이면 글로벌 설정을 사용한다.
- **중복 발송 방지 키**: `userId:categoryId` 단위로 같은 날 재발송을 막는다.

### 9.6 user_events 이벤트 타입 & 소스 태그 (Phase 3)
> `user_events` 는 사용자 행동의 단일 append-only 테이블. 새 event_type 추가는 ADR 필요.

허용 `event_type` 값:
- `page_view` — 관리자/유저 페이지 조회
- `article_impression` — 기사 카드 노출
- `article_click` — 기사 링크 클릭 (Phase 3 PR #428: `event_data.source` 추가 — `"slack"` 또는 null, allowlist 정규화)
- `wizard_step` — 위자드 단계 전환
- `bookmark_toggle` — 북마크 on/off
- **`article_share_passive`** (Phase 3 PR #432 신규) — Slack `link_shared` 이벤트 기반. 전용 컬럼 `target_channel_id`, `slack_message_ts` 사용 + partial UNIQUE index `ux_user_events_share_dedup` (Postgres 전용; H2 는 application-level SELECT-before-INSERT fallback)

재방문 집계 정의 (PR #428 `ReVisitQueryHelper`): 같은 (user_id, summary_id) 에서 첫 클릭 이후 **24h 초과 ~ 30d 이내** 다음 클릭만 `revisit=1` 로 계산. 반복 클릭은 1회만 counted.

### 9.7 clipping_review_item_audits.reason 허용값 (리뷰 정책 감사)
> 리뷰 큐 판정의 근거를 기록하는 append-only 감사 로그. 새 reason 값 추가는 관련 정책 문서 동시 갱신 필수.

PR-3-lite (2026-04-19) 에서 추가된 값:
- `rule:event_type_blacklist` — 카테고리별 event_type 블랙리스트 룰로 자동 EXCLUDE (룰 엔진 정책 1).
- `rule:zero_signal` — zero-signal 룰(event_type=OTHER + sentiment=NEUTRAL + include_keywords 미일치)로 자동 EXCLUDE (룰 엔진 정책 2).
- `manual_restore_from_auto_exclude` — `POST /api/admin/review-items/{summaryId}/restore-to-review` 로 자동 EXCLUDE 된 항목을 REVIEW 로 수동 복구한 기록. `review_decisions.reason` + `review_item_audits.reason` 양쪽에 동일 리터럴.

자동 제외 감사 뷰 (`/admin/review-queue/auto-exclude-audit`) 는 위 두 `rule:*` reason 만 집계. 복구 가드 — 대상은 `reviewed_by='policy-auto' AND status=EXCLUDE` 로 엄격하게 제한 (ConflictException).

## 10) 참고 자료
- Diátaxis: https://diataxis.fr/
- Google Style Guide (Docs): https://developers.google.com/style
- Microsoft Writing Style Guide: https://learn.microsoft.com/style-guide/welcome/
- GitHub Markdown Docs: https://docs.github.com/en/get-started/writing-on-github
- Write the Docs Guide: https://www.writethedocs.org/guide/
- Feature-Sliced Design: https://feature-sliced.design/
- Clean Architecture (Uncle Bob): https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- 모던 디자인 시스템 (예: Material 3, HIG)
