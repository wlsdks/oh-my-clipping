# 테스트 전략

> 무엇을, 어떻게, 왜 테스트하는지.

---

## 1. 테스트 피라미드

```
        E2E (Playwright)
       ──────────────────
      통합 (Spring Boot Test)
     ────────────────────────
    유닛 (Vitest, JUnit 5 + MockK)
   ──────────────────────────────────
```

| 레벨 | 도구 | 현재 파일 수 (2026-05-03) | 범위 |
|------|------|--------------------------|------|
| 유닛/통합 (FE) | Vitest 3.x + Testing Library + MSW | 204 | 유틸, 훅, 필터 로직, 서비스, 주요 페이지 모델 |
| 유닛/통합 (BE) | JUnit 5 + MockK + Spring Boot Test | 426 | 서비스 로직, store SPI/구현, 집계 SQL, 마이그레이션, 모듈 경계 |
| E2E | Playwright 1.x | 40 spec | 유저 플로우, 관리자 플로우, 로컬 서버 회귀 |

테스트 case 수는 리팩터링 중 변동이 잦으므로 고정 총합보다 파일 수와 필수 실행 명령을 기준으로 관리한다.

Phase 3 (2026-04-18 머지) 관련 신규 테스트 영역:
- `analytics/SourceQualityQueryHelper` — RSS 소스 품질 집계 SQL + `isActive`/`updatedAt` round-trip (H2 fixture 기반, PR #458 으로 확장). Persona 만족도 집계 helper 는 약한 신호로 2026-04-20 제거 — ADR-033
- `adapter/in/web/track/` — path-based tracking URL controller + Referer fallback
- `adapter/in/slack/link_shared/` — Slack event fixture 기반 listener + dedup regression
- `store/organization/` — Organization CRUD + `category_organizations` CASCADE
- `util/DepartmentNormalizerTest` — trim + lowercase + 공백 압축 정규화

---

## 2. 백엔드 테스트

### 프레임워크
- **JUnit 5**: 테스트 프레임워크
- **MockK**: 의존성 모킹 (`mockk<Store>()`, `slot<T>()`, `verify(exactly=1)`)
- **Kotest matchers**: 어서션 (`shouldBe`, `shouldContain`, `shouldThrow`)
- **H2**: 통합 테스트용 인메모리 DB

### 구조
```
src/test/kotlin/com/ohmyclipping/
├── service/        # 서비스 비즈니스 로직 (핵심)
├── store/          # JDBC 스토어 (데이터 접근)
├── admin/          # 컨트롤러 (입력 검증)
├── mcp/            # MCP 서버/도구/인증 계약 검증
├── integration/    # 전체 통합 (MCP 서버)
├── migration/      # Flyway 마이그레이션
├── security/       # 인증/보안
├── resilience/     # 서킷브레이커, 재시도
├── ai/             # Gemini 클라이언트
└── rss/            # RSS 파싱
```

멀티모듈 경계 검증:

```bash
./gradlew :core:domain:checkDomainBoundaries
./gradlew :core:api-models:checkApiModelBoundaries
./gradlew :core:error-types:checkErrorTypeBoundaries
./gradlew :ports:persistence:checkStoreSpiBoundaries
./gradlew :ports:workflow:checkAppPortBoundaries
./gradlew :adapters:persistence:checkPersistenceBoundaries
./gradlew :adapters:notification:checkNotificationBoundaries
./gradlew :modules:digest-policy:checkEngineBoundaries
./gradlew :modules:collection:checkCollectionBoundaries
./gradlew :modules:source:checkSourceBoundaries
./gradlew :modules:digest:checkDigestApplicationBoundaries
./gradlew :modules:user:checkUserApplicationBoundaries
./gradlew :modules:analytics:checkAnalyticsApplicationBoundaries
```

`./gradlew check`는 위 경계 검사를 포함한다. `core/domain`은 Spring, JPA, store,
entity, repository, service, adapter import를 허용하지 않는다. 엔진/API/app 계약
모듈(`modules/digest-policy`, `core/api-models`, `ports/workflow`)은 Spring, JPA, store, entity, repository, root app model import를 허용하지 않는다.
피처 모듈(`modules/collection`, `modules/source`, `modules/user`, `modules/analytics`, `modules/digest`, `adapters/notification`)은 Spring service bean은 허용하지만 root app 구현 패키지 역참조를 허용하지 않는다.
`ports/persistence`는 Spring/JPA/entity/repository/adapter import를 허용하지 않고, `adapters/persistence`는
app service/adapter/config import를 허용하지 않는다.

### 네이밍 규칙
```kotlin
@Nested
inner class `승인 시` {
    @Test
    fun `PENDING 상태의 사용자를 APPROVED로 변경한다`() { ... }

    @Test
    fun `이미 승인된 사용자는 예외를 던진다`() { ... }
}
```
- `@Nested` inner class + 한글 백틱 테스트명
- 그룹: 상황 (given) → 테스트: 행동 + 기대 (when + then)

### 필수 커버리지
- 새 서비스 메서드: 최소 **해피패스 1 + 에러 1 + 권한 1** = 3개
- 상태 전이: 허용 전이 + 불허 전이
- 경계값: null, 0, 최대/최소

---

## 3. 프론트엔드 테스트

### 프레임워크
- **Vitest 3.2**: 테스트 러너
- **@testing-library/react**: 컴포넌트 테스트
- **MSW 2.10**: API 모킹
- **jsdom**: DOM 시뮬레이션

### 파일 위치
```
src/
├── pages/domain/__tests__/domainHelpers.test.ts
├── utils/__tests__/utilName.test.ts
└── shared/lib/__tests__/helperName.test.ts
```
- 대상 파일 옆 `__tests__/{파일명}.test.ts`

### 우선 대상
1. `utils/` 순수 함수 (date, search, format)
2. `pages/*/model/` 필터/로직 함수
3. `shared/lib/` 헬퍼 (httpError, requestLabels)

### 테스트 패턴
```ts
describe("formatRelativeDate", () => {
  afterEach(() => { vi.useRealTimers(); });

  it("returns '오늘' for same day", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-03-19T15:00:00+09:00"));
    expect(formatRelativeDate("2026-03-19T09:00:00+09:00")).toBe("오늘");
  });

  it("returns '—' for null", () => {
    expect(formatRelativeDate(null)).toBe("—");
  });
});
```

---

## 4. E2E 테스트

### 프레임워크
- **Playwright 1.52**: 브라우저 자동화
- **Chromium**: 기본 브라우저
- **baseURL**: `http://127.0.0.1:8086`

### 파일 구조
```
tests/e2e/
├── app-regression.spec.ts   # 핵심 회귀 테스트
├── admin/                   # 관리자 플로우
├── user/                    # 유저 플로우
├── edge-cases/              # 에지 케이스
├── a11y/                    # 접근성
└── fixtures/                # 테스트 데이터
```

### 핵심 시나리오
1. 유저 위자드 → 구독 생성 → 관리자 승인 → 유저 확인
2. 관리자 런타임 설정 저장/리셋
3. DM 구독 → Slack 채널 검증

### 실행
```bash
cd frontend
pnpm exec playwright test --timeout=60000 --reporter=list
```

---

## 5. 품질 게이트

### PR 전 필수 실행
```bash
# 백엔드
./gradlew check -PskipFrontendBuild=true

# 프론트엔드
cd frontend
pnpm typecheck        # TypeScript 검사
pnpm lint             # ESLint (0 warnings)
pnpm build            # 프로덕션 빌드
pnpm test             # 유닛 테스트

# E2E
cd frontend
pnpm exec playwright test --timeout=60000 --reporter=list
```

### 자동화 (향후)
- GitHub Actions CI 파이프라인
- PR 생성 시 자동 실행
- E2E는 staging 환경에서

---

## 6. 테스트 작성 프로세스

1. **시나리오 도출**: 입력-출력 시나리오 먼저 정리
2. **해피패스**: 정상 입력 → 기대 결과
3. **경계값**: null, undefined, 빈 문자열, 0, 최대/최소
4. **에러 경로**: 잘못된 입력, 권한 없음, 리소스 미존재
5. **상태 전이**: 허용 전이 성공, 불허 전이 거부
6. **구현**: 테스트 통과시키는 최소 코드
7. **회귀**: 전체 테스트 실행

**원칙: 기능 구현 = 테스트 포함. 테스트 없는 기능은 미완성.**

---

## 7. 커버리지 측정

### 프레임워크
- **백엔드**: JaCoCo — `./gradlew test -PskipFrontendBuild=true` 실행 시 자동 생성 (`build/reports/jacoco/test/html/`)
- **프론트엔드**: `@vitest/coverage-v8` — `pnpm vitest run --coverage` 실행 시 생성 (`frontend/coverage/`)

### vite.config.ts 커버리지 설정
```ts
test: {
  coverage: {
    provider: 'v8',
    reporter: ['text', 'html', 'lcov'],
    include: ['src/**/*.{ts,tsx}'],
    exclude: ['src/**/*.test.*', 'src/test/**']
  }
}
```

### 최근 커버리지 기준선 (2026-04-11 측정)
| 영역 | Lines | Branches |
|------|-------|----------|
| 백엔드 (JaCoCo) | ~70%+ | ~53%+ |
| 프론트 (@vitest/coverage-v8) | ~15%+ | 79%+ |

커버리지 수치는 매 문서 변경마다 갱신하지 않는다. 대규모 테스트 추가나 정책 변경이 있을 때 같은 PR에서 재측정한다.
