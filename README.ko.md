<div align="center">

# oh-my-clipping

**오픈소스 뉴스 클리핑 에이전트.**
RSS 수집 → AI 요약 → Slack 다이제스트, 어드민 UI와 MCP 서버 인터페이스 제공.

[English README](README.md)

</div>

---

## 무엇을 하는가

원하는 RSS 피드를 oh-my-clipping 에 등록하면, 새 글을 수집하고 LLM 으로 요약한 뒤, 리뷰 가능한 룰 엔진을 통과시켜 페르소나별 일일 다이제스트를 Slack 으로 발송합니다. 사용자/채널 수에 제한 없음.

같은 기능이 **MCP 도구** 로도 노출돼 있어서, LLM 에이전트가 직접 수집·요약·발송하게 할 수 있습니다.

## 기능

- 🗞️ **RSS + 수동 URL 수집** — SSRF 안전 검증, 소스별 헬스 추적
- 🤖 **LLM 요약 파이프라인** — Gemini 기본, 통합 교체 가능, 실행별 비용 추적
- ✅ **리뷰 큐** — 다이제스트 진입 전 사람이 승인. 자동 제외 룰 + 감사 로그
- 💬 **Slack 발송** — Block Kit 다이제스트, 카테고리별 채널, 링크 공유 분석, HMAC 검증
- 🎛️ **어드민 + 사용자 UI** — React 19 어드민 콘솔, 셀프서비스 사용자 화면
- 🔌 **MCP 서버** — `/mcp` 엔드포인트, 어드민 13 + 사용자 9 도구
- 🧱 **모듈러 모노리스** — 15개 Gradle 서브모듈, 의존성 경계 자동 검사

## 기술 스택

| 영역 | 선택 |
|---|---|
| 백엔드 | Java 21, Kotlin 2.3, Spring Boot 3.5 |
| 프론트엔드 | React 19, Vite 6, TypeScript 5.9, Tailwind v4 |
| 데이터베이스 | PostgreSQL 16 (테스트는 H2) |
| 캐시·레이트리밋 | Redis 7 |
| LLM | Google Gemini (Spring AI) |
| 빌드 | Gradle (Kotlin DSL), pnpm |

## 빠른 시작

```bash
# 1. 환경 설정
cp .env.example .env
# DB_PASSWORD 채우기. 로컬 시드 계정 쓰려면 DEV_BOOTSTRAP=true

# 2. Postgres + Redis 기동
docker compose up -d postgres redis

# 3. 백엔드
./gradlew bootRun -PskipFrontendBuild=true     # http://localhost:8086

# 4. 프론트엔드 (별도 터미널)
cd frontend && pnpm install && pnpm dev        # http://localhost:5173
```

`DEV_BOOTSTRAP=true` 일 때:

- 관리자: `dev.admin@clipping.local` / `LocalPass123!`
- 사용자: `dev.user@clipping.local` / `LocalPass123!`

> ⚠️ **로컬 개발 전용입니다.** `DEV_BOOTSTRAP=true` 는 user 데이터 테이블을 DELETE 한 뒤 다시 시드합니다. 실 사용자가 있는 DB 감지 시 pre-flight guard 가 abort 하지만, 프로덕션엔 절대 켜지 마세요.

자세한 셋업 (Slack 앱, Gemini 키, Slack interactivity 터널) 은 [`docs/ONBOARDING.md`](docs/ONBOARDING.md) 참고.

## 문서

| 위치 | 내용 |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 모듈 맵, 의존성 규칙, 변경 시작점 |
| [`docs/ONBOARDING.md`](docs/ONBOARDING.md) | 로컬 셋업, 개발 명령, Slack 앱 설정 |
| [`docs/API_REFERENCE.md`](docs/API_REFERENCE.md) | 어드민/사용자/MCP API 계약 |
| [`docs/ADR.md`](docs/ADR.md) | 아키텍처 결정 기록 |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | 완료/예정 작업 |
| [`docs/TEST_STRATEGY.md`](docs/TEST_STRATEGY.md) | 테스트 피라미드, 커버리지 |
| [`docs/LESSONS.md`](docs/LESSONS.md) | 과거 버그에서 나온 규칙 |
| [`docs/DESIGN_STRATEGY.md`](docs/DESIGN_STRATEGY.md) | UI/UX 원칙 |
| [`docs/DEPLOY_GUIDE.md`](docs/DEPLOY_GUIDE.md) | 배포 체크리스트 |
| [`docs/SLACK_SETUP.md`](docs/SLACK_SETUP.md) · [`docs/SLACK_APP_MANIFEST.md`](docs/SLACK_APP_MANIFEST.md) | Slack 워크스페이스/앱 설정 |
| [`AGENTS.md`](AGENTS.md) | 엔지니어링 룰북 — 아키텍처 경계, 컨벤션, 품질 게이트. 본 README 와 충돌 시 AGENTS.md 우선 |

## 품질 게이트

```bash
# 백엔드
./gradlew check -PskipFrontendBuild=true

# 프론트엔드
cd frontend
pnpm typecheck && pnpm lint && pnpm build && pnpm test

# E2E
cd frontend
pnpm exec playwright test --timeout=60000 --reporter=list
```

## 기여

PR 환영합니다. [`CONTRIBUTING.md`](CONTRIBUTING.md) 참고. 요점:

1. Fork → 브랜치 → PR (절대 `main` 직접 커밋 금지)
2. 품질 게이트 통과 후 PR
3. 기능에는 테스트, 버그픽스에는 회귀 테스트

기여 시 MIT 라이선스에 동의하는 것으로 간주합니다.

## 보안

취약점 신고는 **공개 이슈로 올리지 마세요** — [`SECURITY.md`](SECURITY.md) 참고.

## 라이선스

[MIT](LICENSE)
