# 온보딩 가이드

> 새 개발자/관리자가 처음 왔을 때 읽는 문서. 10분 안에 로컬 환경 세팅 + 서비스 이해.

---

## 1. 이 서비스가 뭔가요?

RSS 뉴스를 자동 수집 → AI 요약 → Slack으로 발송하는 **뉴스 인텔리전스 플랫폼**.
직원은 2분 안에 업무 관련 뉴스를 파악하고, 관리자는 콘텐츠 품질을 통제합니다.


---

## 2. 로컬 환경 세팅

### 필수 도구
- Docker Desktop (PostgreSQL용)
- Java 21 (sdkman 추천: `sdk install java 21.0.6-librca`)
- pnpm (`npm install -g pnpm`)
- Git

### 3분 세팅

```bash
# 1. DB 시작
docker compose up -d

# 2. 환경변수 준비 (한 번만)
cp .env.example .env
# .env 열어서 DB_PASSWORD 채우고, 필요하면 GEMINI_API_KEY / SLACK_BOT_TOKEN 도 채운다

# 3. 서버 시작 — dev-start.sh 가 .env 로드 + 포트 체크 + bootRun 실행
./scripts/dev-start.sh

# 4. 브라우저
open http://localhost:8086
```

> **환경변수 값은 팀 내부에서 공유합니다.** 처음 세팅 시 팀 리드에게 문의하세요.
> `DEV_BOOTSTRAP=true` (`.env.example` 기본값)로 하면 매 기동마다 시드 데이터가 초기화됩니다.
>
> 직접 gradle 로 기동하려면: `./gradlew bootRun -PskipFrontendBuild=true` (.env 자동 로드는 되지 않으니 shell 에 먼저 `set -a; source .env; set +a` 필요).

첫 시작 시 Flyway 마이그레이션이 자동 실행되고, `local-bootstrap.sql`이 시드 데이터를 넣어줍니다.

### 로그인

`DEV_BOOTSTRAP=true` 로 기동하면 시드 계정이 생성된다. 로그인 폼에 직접 입력:
- 관리자: `dev.admin@clipping.local` / `LocalPass123!`
- 회원: `dev.user@clipping.local` / `LocalPass123!`

> 과거 로그인 화면의 "관리자/회원" 단축 버튼은 2026-04-17 (PR #376) 에서 제거되었다.

---

## 3. 프로젝트 구조

```
oh-my-clipping/
├── settings.gradle.kts          # Gradle 멀티모듈 선언
├── build.gradle.kts             # root app 빌드 + Spring Boot 실행 설정
├── core/                        # 순수 타입과 계약 (Spring/JPA/IO 없음)
│   ├── domain/                  # 도메인 모델 (Category, RssSource, Persona, AdminUser …)
│   │   └── src/main/kotlin/com/ohmyclipping/model/
│   ├── api-models/              # API/MCP/서비스 결과 DTO + 파이프라인 실행 이력 DTO + cross-cutting DTO
│   │   └── src/main/kotlin/com/ohmyclipping/service/dto/
│   │       ├── clipping/        # API/MCP 응답 DTO
│   │       └── pipeline/        # 파이프라인 실행 이력 DTO
│   └── error-types/             # 서비스 공통 예외/에러 코드 계약
│       └── src/main/kotlin/com/ohmyclipping/error/
├── ports/                       # 앱 내부 경계 인터페이스 (구현 없음)
│   ├── persistence/             # DB 접근 포트 + store 반환 DTO
│   │   └── src/main/kotlin/com/ohmyclipping/
│   │       ├── store/
│   │       ├── store/analytics/dto/
│   │       └── store/pipeline/
│   └── workflow/                # workflow / notification 포트 + DTO
│       └── src/main/kotlin/com/ohmyclipping/service/port/
│           ├── DigestDeliveryWorkflowPort.kt
│           ├── OpsLogNotifier.kt
│           └── NotificationEvent.kt
├── adapters/                    # 외부 시스템 어댑터 (Spring 허용)
│   ├── persistence/             # JPA entity + Spring Data repository + Jpa/Jdbc store 구현
│   │   └── src/main/kotlin/com/ohmyclipping/{entity,repository,store}/
│   └── notification/            # 운영/사용자 알림 application 서비스
│       └── src/main/kotlin/com/ohmyclipping/service/notification/
├── modules/                     # 피처 모듈 (Spring service 허용, root app 역참조 금지)
│   ├── digest-policy/           # 클리핑 엔진 코어 (Spring/JPA/store/app model 의존 없음)
│   │   └── src/main/kotlin/com/ohmyclipping/service/
│   │       ├── digest/          # 다이제스트 선정/섹션/문서 구성 정책
│   │       ├── pipeline/        # deterministic pipeline 실행 순서
│   │       └── port/            # RSS/LLM/Slack/파이프라인 포트 DTO
│   ├── collection/              # RSS/수동 URL/Naver 뉴스 수집 application 서비스
│   │   └── src/main/kotlin/com/ohmyclipping/service/collection/
│   ├── source/                  # RSS source 검증/탐색/헬스/SLA application 서비스
│   │   └── src/main/kotlin/com/ohmyclipping/service/source/
│   ├── digest/                  # 다이제스트 application 보조 서비스/포트 매핑
│   │   └── src/main/kotlin/com/ohmyclipping/service/digest/
│   ├── user/                    # 사용자 구독/요청/이벤트 application 서비스 + 사용자 DTO
│   │   └── src/main/kotlin/com/ohmyclipping/service/
│   ├── admin/                   # 관리자 application DTO (서비스 로직은 아직 root app)
│   │   └── src/main/kotlin/com/ohmyclipping/service/dto/
│   └── analytics/               # 통계/트렌드 조회 application 서비스 + analytics DTO
│       └── src/main/kotlin/com/ohmyclipping/service/
├── src/main/kotlin/             # root app: Spring Boot composition root
│   └── com/ohmyclipping/
│       ├── admin/               # REST 컨트롤러 (인바운드)
│       ├── user/                # 사용자 API 컨트롤러 (인바운드)
│       ├── mcp/                 # MCP 서버/인증/도구 진입점
│       ├── service/             # 앱 서비스, scheduler, workflow, 포트 어댑터
│       │   ├── digest/          # 다이제스트 application orchestration/rendering/delivery workflow
│       │   ├── pipeline/        # root app pipeline orchestration/adapter
│       │   ├── collection/      # RSS/URL 수집 포트 어댑터/호환 경계
│       │   └── notification/    # 운영/사용자 알림 호환 경계
│       ├── adapter/             # 외부 시스템 어댑터 (Slack, Naver 등)
│       ├── rss/                 # RSS/본문/robots HTTP 어댑터
│       ├── ai/                  # Gemini 구현 어댑터
│       ├── config/              # 설정
│       └── support/             # 공통 유틸/정규화/보안 보조
├── src/main/resources/
│   ├── db/migration/            # Flyway SQL
│   ├── db/migration-pg/         # PostgreSQL 전용 Flyway SQL
│   ├── db/dev-seed/             # 개발용 시드 데이터
│   └── application.yml          # 서버 설정
├── frontend/                    # 프론트엔드 (React 19 + TypeScript)
│   └── src/
│       ├── pages/               # 페이지 컴포넌트
│       ├── components/          # 공통 컴포넌트
│       ├── services/            # API 호출
│       └── ...
└── docs/                        # 문서
```

백엔드는 root app과 14개 Gradle 서브모듈, 총 15개 빌드 단위로 구성된다. 서브모듈은 `core/`, `ports/`, `adapters/`, `modules/` 네 그룹으로 묶인다:

- root app: 실제 서버 실행 모듈. Spring Boot, DB, API, scheduler, 외부 어댑터를 조립한다.
- `core/domain`: 순수 도메인 모델. `Category`, `RssSource`, `Persona`, `AdminUser` 같은 비즈니스 모델만 소유한다.
- `core/api-models`: API/MCP/서비스 결과 DTO(`service.dto.clipping`), 파이프라인 실행 이력 DTO(`service.dto.pipeline`), 그리고 모듈을 가로지르는 cross-cutting DTO를 가진다.
- `core/error-types`: 서비스 공통 예외/에러 타입을 가진다.
- `ports/persistence`: DB 접근 포트와 store 반환 DTO를 가진다.
- `ports/workflow`: root app 내부 workflow/notification 경계 모듈. prepared digest workflow와 운영 알림 포트를 가진다.
- `adapters/persistence`: JPA entity, Spring Data repository, Jpa/Jdbc store 구현을 소유한다.
- `adapters/notification`: 운영/사용자 알림 application 서비스. Slack 발송, runtime 설정, dedup 저장소를 포트로 접근한다.
- `modules/digest-policy`: 클리핑 엔진 코어. Spring/JPA/store/root app model 의존 없이 재사용 가능한 정책과 포트를 가진다.
- `modules/collection`: RSS/수동 URL/Naver 뉴스 수집 application 서비스를 가진다.
- `modules/source`: RSS source 검증/탐색/헬스/커버리지/SLA와 카테고리 기반 source 동기화 application 서비스를 가진다.
- `modules/digest`: 다이제스트 application 보조 서비스와 포트 매핑/알림 DTO 변환 경계를 가진다.
- `modules/user`: 사용자 구독/요청/이벤트/전달 로그 application 서비스 중 root 구현 의존이 없는 유스케이스와 사용자 응답 DTO를 가진다.
- `modules/admin`: 관리자 application DTO를 가진다. 관리자 서비스 로직은 현재 root app에 있고 추후 마이그레이션에서 이 모듈로 이동할 수 있다.
- `modules/analytics`: 키워드/감성/상위 기사/트렌드/통계 조회 application 서비스와 analytics 응답 DTO를 가진다.

모듈 경계는 `./gradlew check`에 포함된 `:core:domain:checkDomainBoundaries`,
`:core:api-models:checkApiModelBoundaries`,
`:core:error-types:checkErrorTypeBoundaries`,
`:ports:persistence:checkStoreSpiBoundaries`,
`:ports:workflow:checkAppPortBoundaries`,
`:adapters:persistence:checkPersistenceBoundaries`,
`:adapters:notification:checkNotificationBoundaries`,
`:modules:admin:checkAdminModelBoundaries`,
`:modules:digest-policy:checkEngineBoundaries`,
`:modules:collection:checkCollectionBoundaries`,
`:modules:source:checkSourceBoundaries`,
`:modules:digest:checkDigestApplicationBoundaries`,
`:modules:user:checkUserApplicationBoundaries`,
`:modules:analytics:checkAnalyticsApplicationBoundaries`가 검증한다.

### 핵심 문서
| 문서 | 내용 |
|------|------|
| `AGENTS.md` | 코딩 가이드 (필독!) |
| `docs/ARCHITECTURE.md` | 아키텍처 규칙 |
| `docs/DESIGN_STRATEGY.md` | 디자인 철학 |
| `docs/API_REFERENCE.md` | API 전체 목록 |

---

## 4. 개발 명령어

### 백엔드
```bash
./gradlew test -PskipFrontendBuild=true     # 빠른 백엔드 회귀
./gradlew check -PskipFrontendBuild=true    # 테스트 + 모듈 경계/SQL/broad catch 기준선 검사
./gradlew bootRun                           # 서버 시작 (프론트 포함)
./gradlew bootRun -PskipFrontendBuild=true  # 서버만 (빠름)
```

### 프론트엔드
```bash
cd frontend
pnpm dev              # Vite 개발 서버 (HMR)
pnpm build            # 프로덕션 빌드
pnpm typecheck        # TypeScript 검사
pnpm lint             # ESLint
pnpm test             # Vitest 유닛 테스트
pnpm e2e              # Playwright E2E
```

### 서버 재시작
```bash
# 안전한 방법:
pkill -f 'bootRun'

# 절대 금지:
lsof -ti:8086 | xargs kill  # Safari 등 다른 프로세스도 죽음!
```

---

## 5. 코드 작성 규칙 (요약)

### 백엔드
- Clean Architecture: Controller → Service → Store
- 한글 KDoc 필수 (클래스, 메서드)
- 메서드 40줄 이내, 가로 120자 이내
- 테스트: JUnit 5 + MockK + 한글 백틱 테스트명

### 프론트엔드
- `useMemo`, `useCallback`, `React.memo` **절대 금지** (React Compiler 사용)
- Tailwind 시맨틱 토큰만 (`text-primary`, `bg-muted` 등, `text-blue-500` 금지)
- 서비스: ky 기반 (`services/*.ts`)
- 쿼리 키: `queries/*Keys.ts`에 집중 관리

### Git
- main 직접 커밋 금지 → feature 브랜치 → PR → squash merge
- 커밋 메시지: **영어**
- docs/superpowers md 파일: **커밋 금지**

자세한 규칙: `AGENTS.md`

---

## 6. 주요 기능 흐름

### 유저 플로우
```
가입 → 관리자 승인 → 로그인
  → 빠른세팅 (5단계: 지역 → 소스 → 스타일 → 상세 → 채널)
  → 매일 Slack 다이제스트 수신
  → 내 구독 관리 (토글, 설정 변경)
```

### 관리자 플로우
```
로그인 → 대시보드 (KPI + 조치 필요)
  → 뉴스 검토 (INCLUDE/REVIEW/EXCLUDE)
  → 회원 관리 (가입 승인/반려)
  → 발송 관리 (이력 확인, 실패 재발송)
```

### 파이프라인 플로우
```
RSS 수집 → 본문 추출 → AI 중요도 점수
  → 임계값 이상: AI 요약 생성
  → 검토 큐 (애매한 것은 관리자 판단)
  → Slack 발송 (예정 시간에 맞춰)
```

---

## 7. 환경 변수 (프로덕션)

### 필수
| 변수명 | 설명 |
|--------|------|
| `DB_PASSWORD` | 데이터베이스 비밀번호 (**기본값 없음, 반드시 설정**) |
| `DB_URL` | JDBC 접속 URL (`jdbc:postgresql://host:5432/clipping`) |
| `DB_USERNAME` | 데이터베이스 사용자명 |
| `GEMINI_API_KEY` | AI 요약용 API 키 |
| `SLACK_BOT_TOKEN` | Slack 발송용 봇 토큰 (`xoxb-` 시작) |
| `SLACK_APP_TOKEN` | Slack Socket Mode 앱 토큰 (`xapp-` 시작) — 피드백 버튼/채널 공유 기능에 필요 |
| `ADMIN_API_TOKEN` | Bearer 인증 토큰 |

### 선택 (기능 활성화)
| 변수명 | 설명 |
|--------|------|
| `SLACK_SOCKET_MODE_ENABLED` | `true` 설정 시 Slack Socket Mode 활성화 (피드백 👍😐👎 버튼, 채널 공유 버튼) |
| `DEV_BOOTSTRAP` | `true`면 매 기동마다 시드 데이터 초기화 (기본: `true`, 실 테스트 시 `false` 권장) |
| `OPS_LOG_CHANNEL_ID` | 운영 로그 채널 ID (파이프라인 실행 요약 등) |
| `LLM_INPUT_COST_PER_MILLION_USD` | LLM 입력 100만 토큰당 USD 단가. 기본값은 `gemini-2.5-flash-lite` Standard 기준 `0.10` |
| `LLM_OUTPUT_COST_PER_MILLION_USD` | LLM 출력 100만 토큰당 USD 단가. 기본값은 `gemini-2.5-flash-lite` Standard 기준 `0.40` |

### 선택 (보안 강화)
| 변수명 | 설명 |
|--------|------|
| `ENCRYPTION_KEY` | AES-256 Base64 키 (생성: `openssl rand -base64 32`) — 설정 시 Slack 토큰 등 민감 데이터를 암호화 저장. 프로덕션 필수 |
| `SLACK_SIGNING_SECRET` | Slack Webhook 서명 검증용 시크릿. 프로덕션 필수 (미설정 시 위조 요청 수용) |
| `CORS_ALLOWED_ORIGINS` | CORS 허용 오리진 (쉼표 구분, 기본: localhost) |
| `SESSION_COOKIE_SECURE` | HTTPS 환경에서 `true` 설정 (세션 쿠키 Secure 플래그). 프로덕션 필수 |
| `MAX_LOGIN_ATTEMPTS_PER_MINUTE` | 로그인 Rate Limit (기본: 프로덕션 10, 테스트 60) |
| `MAX_USER_REQUESTS_PER_MINUTE` | 사용자 API Rate Limit (기본: 60) |
| `MAX_ADMIN_WRITE_REQUESTS_PER_MINUTE` | 관리자 쓰기 API Rate Limit (기본: 100) |
| `MAX_ADMIN_READ_REQUESTS_PER_MINUTE` | 관리자 읽기 API Rate Limit (기본: 500, 로컬 E2E 병렬 실행 시 상향 가능) |
| `MAX_PUBLIC_IP_REQUESTS_PER_MINUTE` | 공개 API IP Rate Limit (기본: 60) |

### 선택 (MCP 서버)
| 변수명 | 설명 |
|--------|------|
| `CLIPPING_MCP_SERVER_ENABLED` | `true` 설정 시 MCP 서버(`/sse`, `/mcp/message`) 활성화 (기본: `false`). Spring AI MCP autoconfig 도 이 변수로 제어 |
| `CLIPPING_MCP_SERVICE_TOKEN` | MCP 엔드포인트 Bearer 인증 토큰 (최소 32자, 생성: `openssl rand -base64 32`). MCP 활성 시 필수 |

### 프론트 빌드 타임 변수 (Vite `VITE_*`, `frontend/.env` 또는 배포 파이프라인에서 주입)
| 변수명 | 설명 |
|--------|------|
| `VITE_SENTRY_DSN` | 프론트 Sentry DSN. 비어있으면 Sentry 비활성 (no-op) |
| `VITE_SENTRY_ENVIRONMENT` | Sentry environment 태그. 미설정 시 Vite `MODE` 사용 |
| `VITE_SENTRY_RELEASE` | Sentry release 라벨. CI 에서 커밋 SHA 주입 권장 |
| `VITE_SENTRY_TRACES_SAMPLE_RATE` | 0.0~1.0. 기본 0 (성능 트레이스 비활성, 에러만 캡처) |

로컬에서는 `application.yml`의 기본값 + `docker-compose.yml` 사용.

> **참고 (2026-04):** `SLACK_CHANNEL_ID` 환경변수는 제거되었다. 기본 채널은 런타임 설정의 `opsLogChannelId`로 관리한다. 또한 `user_accounts.slack_member_id` 컬럼이 추가되어 Slack DM 발송 시 member ID와 DM channel ID가 분리 관리된다.

### Phase 3 PR #432 — Slack link_shared passive listener 요구사항

Phase 3 에서 도입한 `article_share_passive` 공유 신호 수집은 Slack app 설정 추가가 필요하다. 기존 워크스페이스 admin 의 **재승인** 이 필수.

1. Slack app manifest 에 아래 추가 (상세: `docs/SLACK_APP_MANIFEST.md`)
   - Bot Token Scopes: `links:read`
   - Event Subscriptions: `link_shared` 이벤트 + 도메인 화이트리스트 (tracking URL 호스트)
2. 워크스페이스 admin 이 앱 재승인 — 이메일/Slack 공지로 사내 안내 후 진행.
3. 재설치 완료 후 smoke: 테스트 digest 링크를 다른 채널로 공유 → `article_share_passive` 이벤트 수신 확인.
4. 1주 운영 후 **audit gate** — `capture_rate ≥ 5%` 이면 정식 운영, 미달 시 기능 비활성화 (상세 운영 정책 참고).

Self-hosted / 외부 판매 단계에서는 per-tenant 재승인 campaign 이 필요하다. 사내 도그푸딩 단계에서는 1회 self-approve.

---

## 8. 도움이 필요할 때

- 코딩 규칙: `AGENTS.md`
- API 목록: `docs/API_REFERENCE.md`
- 디자인 원칙: `docs/DESIGN_STRATEGY.md`
- 아키텍처 결정: `docs/ADR.md`
