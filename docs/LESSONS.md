# Lessons Learned — 버그/장애에서 나온 규칙의 근거

> `AGENTS.md` 의 규칙 중 **과거 버그/장애에서 나온 것**의 상세 배경. 규칙 자체는 AGENTS.md 에 유지하고, 여기서는 **왜 그 규칙이 있는지**(문제 → 원인 → 교훈) 만 기록한다.
>
> 새 규칙을 AGENTS.md 에 추가할 때 맥락 설명이 길어지면 여기로 옮긴다. AGENTS.md 에는 한 줄 규칙 + `LESSONS.md#...` 앵커 링크만 남긴다.

---

<a id="l-001"></a>
## L-001: Slack/LLM 이모지 중복 — 같은 버그 두 번 재발 (PR #454)

**관련 AGENTS.md 규칙**: §1.3.1 Slack/LLM 텍스트 정리 규칙

**문제 배경**:
2026-04 동안 같은 이모지 중복 버그(📌 📌 / 📌 💼 등)가 두 번 재발 (#381, #454). 원인: LLM(Gemini 2.5)이 persona prompt 에 따라 출력 라인 맨 앞에 장식 이모지(📌/💡/🍃)를 붙이는데, 우리 렌더링도 prefix 이모지를 또 붙여서 겹침. 첫 fix 는 "paragraph 시작"만 strip 해서 중간 줄은 놓침.

**교훈 (규칙화)**:
1. **Line-level strip** 이 문단 단위보다 우선. `split("\n")` 후 각 라인에 `stripLeadingDecoration()` 적용 후 다시 join. 문단 단위만 strip 하면 `buildDigestParagraphs` 같은 후속 병합 단계에서 중간 줄의 장식이 살아남는다.
2. **Surrogate pair 카운팅 주의** — 이모지는 대부분 2-char surrogate pair. `result.count { it.toString() == "📌" }` 는 항상 0 을 반환한다. `result.split("📌").size - 1` 로 카운트.
3. **Regression fixture 다양성** — LLM 출력 패턴은 prompt 버전/모델 업데이트마다 변한다. 회귀 테스트에 3~5 개가 아니라 **9 개 이상의 realistic fixture** 를 둔다 (emoji at start / mid / nested bullet / mixed emoji / 중문/영문 혼용 등).
4. **Sanitizer 는 `internal` 가시성** — `private` 유지하면 회귀 테스트가 상위 메서드를 거쳐야 해서 원인 범위가 넓어진다.

**테스트 위치**: `DigestServiceSanitizeSummaryTest` — `summarizeForSlackText 다양한 LLM 패턴 회귀 방지` 블록.

---

<a id="l-002"></a>
## L-002: DB Migration V117 saga — FK 추가가 ApplicationContext 전체를 날림

**관련 AGENTS.md 규칙**: §1.5 DB 마이그레이션 & FK 추가 체크리스트, §2.1.2 DB Migration 안전 규칙 보강

**문제 배경**:
V117 에서 `audit_log.actor_id` 에 FK 를 추가했는데:
- 1차 배포: `NOT NULL` 컬럼에 `SET NULL` 을 시도해서 `null value in column` 실패
- 2차 배포: EXISTS 서브쿼리에서 outer alias 의 column 으로 실수 매칭되어 orphan 삭제 로직이 의도와 다르게 동작

추가로 seed 데이터(`local-bootstrap.sql`)에 orphan FK 값이 있어서 ApplicationContext 전체가 cascade 실패.

**교훈 (규칙화)**:
1. **삭제 대상은 백업 테이블로 스냅샷** — `CREATE TABLE IF NOT EXISTS _v{N}_backup_{table} AS SELECT * FROM ...` 먼저, 그 뒤 DELETE. 7 일 이상 prod 검증 후 별도 마이그레이션(`V{N+M}__drop_v{N}_backups.sql`)에서 DROP.
2. **`NOT NULL` 컬럼에 `SET NULL` 하려면 먼저 `DROP NOT NULL`** — 순서 뒤집으면 `null value in column` 실패. `ALTER TABLE ... ALTER COLUMN ... DROP NOT NULL` 을 같은 마이그레이션 최상단에 둔다.
3. **EXISTS 서브쿼리 column 은 table-qualify** — PostgreSQL 은 `WHERE source_id = s.id` 처럼 쓰면 outer alias 의 column 으로 실수 매칭될 수 있음. 항상 `WHERE inner.source_id = s.id` 로 명시.
4. **Idempotent 재실행 가능** — `CREATE TABLE IF NOT EXISTS`, `DROP CONSTRAINT IF EXISTS`, `ALTER TABLE IF EXISTS`. Flyway repair 이전에도 안전하게 rerun 가능.
5. **FK 정의 시 `ON DELETE` 액션 명시** — `SET NULL` (감사/이력), `CASCADE` (종속 소유), `RESTRICT` (기본 무결성). 미지정은 RESTRICT 이라 의도 외 블록 발생.

---

<a id="l-003"></a>
## L-003: audit_log.actor_id — Bearer 토큰 principal 대응 (PR #401)

**관련 AGENTS.md 규칙**: §2.1.1 감사 로그 actor_id 규칙, §1.5 체크리스트

**문제 배경**:
`ADMIN_API_TOKEN` 경로는 `admin_users` 에 없는 가상 principal(`"admin-api"`)을 쓴다. 감사로그/변경이력에 actor FK 를 추가할 때 non-null FK 를 쓰면 Bearer 토큰 액션이 전부 실패.

**교훈**:
- audit_log 계열 FK 는 **nullable + `ON DELETE SET NULL`** 이 안전.
- `authentication.name` (username) 을 `actor_id` 에 직접 저장하면 V117/V120 의 `fk_audit_log_actor` FK 제약 위반. `AuditActorResolver.resolve(principal)` 경유 필수.

---

<a id="l-004"></a>
## L-004: UI 재설계 PR 이 E2E 셀렉터를 무력화 (PR #427)

**관련 AGENTS.md 규칙**: §5.1.0.5 UI 재설계 PR 은 E2E 동기 갱신 필수

**문제 배경**:
2026-04-18 회귀 리뷰에서 PR #377 / #407 / #423 세 차례 재설계가 모두 E2E 셀렉터를 무력화시킨 사실이 뒤늦게 드러나 10 개의 테스트가 며칠~몇 주간 silent 실패 상태였다 (PR #427 에서 일괄 수정).

**교훈 (규칙화)**:
1. UI 재설계 PR body 에 **변경된 heading/label/route/testid 리스트** 를 남긴다.
2. 관련 `tests/e2e/**` 의 `getByRole("heading", { name: ... })`, `locator(...)` 를 같은 커밋에서 갱신.
3. 구조적 변화(예: `aria-hidden="true"` 랩퍼 추가)는 테스트가 `getByRole` 기본으로 숨겨진 요소를 못 찾는다는 점을 유념. mock 으로 "non-hidden 경로" 를 강제하거나 `test.skip` + 이유 명시.
4. `/ultrareview` 는 "해당 PR 의 E2E 가 갱신됐는가" 를 확인 항목에 넣는다.

이 규칙을 어긴 PR 은 merge 허용하되, 즉시 follow-up 브랜치로 E2E 를 보충한다.

---

<a id="l-005"></a>
## L-005: Weak assertion 패턴 76 개 일괄 교체 (PR #396)

**관련 AGENTS.md 규칙**: §5.1.1 Weak assertion 금지

**문제 배경**:
`expect(result).toBeDefined()` **단독** assertion 이 76 개 발견. 제목에 "URL 인코딩", "필터 파라미터" 등 동작 주장이 있지만 실제로 그 동작을 검증하지 않아 regression detection 실패.

**교훈**:
테스트 제목이 동작을 주장하면 **실제로 그 동작을 검증**한다. 예:

```ts
// ❌ Weak — 제목에 "URL 인코딩" 이 있지만 실제 검증 안 함
it("id를 URL 인코딩하여 요청해야 한다", async () => {
  const result = await service.get("a/b");
  expect(result).toBeDefined();
});

// ✅ MSW request capture 로 실제 요청 URL 검증
it("id를 URL 인코딩하여 요청해야 한다", async () => {
  let capturedPath: string | undefined;
  server.use(
    http.get("*/items/:id", ({ request }) => {
      capturedPath = new URL(request.url).pathname;
      return HttpResponse.json(mockItem);
    })
  );
  const result = await service.get("a/b");
  expect(capturedPath).toContain("a%2Fb");
  expect(result).toEqual(mockItem);
});
```

---

<a id="l-006"></a>
## L-006: JdbcRssSourceStoreTest flake — 공유 H2 잔존 row (PR #413)

**관련 AGENTS.md 규칙**: §5.1 백엔드 — Store/Repository integration test Absolute count 금지

**문제 배경**:
`@SpringBootTest + @Transactional` 만으로는 **타 테스트 클래스가 커밋한 잔존 row** 오염을 막지 못한다 (공유 H2). `list.size shouldBe 1` / `count shouldBe 3` 같은 절대값 단언이 2 개 테스트를 flake 로 만듦.

**교훈**:
- **Delta-based** — `val before = store.count(); save(...); val after = store.count(); (after - before) shouldBe 3`
- **Category-scoped filter** — `store.findAll(...).filter { it.categoryId == category.id }` 로 범위 한정

---

<a id="l-007"></a>
## L-007: Nullable column GROUP BY NPE (PR #422)

**관련 AGENTS.md 규칙**: §1.3 "Nullable column 캐스팅 방어"

**문제 배경**:
native query `GROUP BY nullable_col` 결과를 `row[0] as String` 으로 non-null cast 하면 NULL 그룹에서 NPE. `rss_items.rss_source_id` 는 수동 URL 입력 항목이 NULL 이라 GROUP BY 결과에 NULL 그룹이 섞임.

**교훈 (2 단계 방어)**:
1. SQL 에 `WHERE col IS NOT NULL` 로 NULL 그룹을 제외하거나 의도적으로 포함.
2. 결과 캐스팅을 `as? String` + `mapNotNull` 로 감싸 `Array<Any?>` 타입으로 받는다.

---

<a id="l-008"></a>
## L-008: EMPTY_RESULT 진단 태그 누락 (PR #414)

**관련 AGENTS.md 규칙**: §9.1.5 EMPTY_RESULT 진단 태그 유지

**문제 배경**:
`ClippingSummarizer.summarizeArticle()` 에 null-return 경로를 추가했는데 `lastRejectReason` 을 set 하지 않아 `llm_runs.error_message=NULL` 이 되어 prod 장애 시 원인 판별 불가.

**교훈 (현재 유지되는 태그)**:
- `API_NULL_RESPONSE` — Gemini 가 빈 응답
- `JSON_EXTRACT_FAIL` — 모델 출력에 JSON 블록 없음
- `NORMALIZED_NULL` — validator accepted 이지만 normalized=null
- `EXCEPTION:<ClassName>` — 모든 unhandled 예외
- 품질 거부: `CHARS_TOO_SHORT`, `PARAGRAPHS_TOO_FEW`, `SENTENCES_TOO_FEW`, `SENTENCES_AFTER_CLAMP` (validator 가 설정)

새 실패 경로 추가 시 `lastRejectReason.set("NEW_TAG")` 필수. `JdbcPipelineAnalyticsStore` 의 거부 사유 집계 쿼리에도 반영.

---

<a id="l-009"></a>
## L-009: DB 감사 해석 — "0 rows / NULL 누락" 이 정상인 경우

**관련 AGENTS.md 규칙**: §2.1.3 DB 감사 해석 규칙

**배경**: 감사에서 '버그' 로 오해됐던 실제 정상 케이스:
- `clipping_retention_policies` 0 rows — 의도된 카테고리별 오버라이드 테이블. 기본값은 `retentionDefaultDays` env var (30 일). 시드 금지. (retention 정책 참고)
- `summary_cache` 0 rows — 같은 (article, persona) 재요청 시에만 쌓이는 구조. 저트래픽이면 비어있는 게 정상.

**교훈**: "0 rows / NULL" 이 감사에 걸려도 먼저 Explore 에이전트로 코드 참조를 조사. 조사 전 '고쳐야 할 것' 으로 단정하지 말 것.

---

<a id="l-010"></a>
## L-010: DEV_BOOTSTRAP=true 로 실 사용자 데이터 DELETE

**관련 AGENTS.md 규칙**: §9.1.1 `DEV_BOOTSTRAP` 파괴적 동작 경고

**배경**: Phase C+D 작업 중 E2E 테스트를 위해 `.env` 의 `DEV_BOOTSTRAP` 을 `false → true` 로 수정하고 원복하지 않은 채 `pkill -f bootRun` 후 재기동. `LocalDevBootstrapConfig` 가 `LocalDevSupportService.bootstrap()` 을 실행했고, 내부에서 `local-bootstrap.sql` 이 다음 테이블들을 DELETE:
- `clipping_user_requests` — 구독 요청 (주제·소스·Slack 채널·페르소나 등 사용자별 설정) (주제·소스·Slack 채널·페르소나 등)
- `user_delivery_schedules` — 발송 요일/시간
- `summary_feedback`, `clipping_review_items`, `batch_summaries`, `rss_items`, ...

실 사용자 다수의 구독 전부 소실. 복구 경로 확인:
- Postgres `archive_mode=off` → PITR 불가
- macOS Time Machine 로컬 스냅샷 없음 → 파일시스템 롤백 불가
- WAL dead tuple 추출은 이론상 가능하지만 전문가 작업 + 성공 보장 X

**근본 원인**:
1. `.env` 수정 후 원복 절차 누락
2. `DEV_BOOTSTRAP=true` 가 파괴적이라는 경고가 코드/문서 어디에도 명시 안 됨
3. 실 데이터 있는 DB 에서 bootstrap 을 실행하는 걸 막는 가드 없음
4. docker-compose 의 `clipping-postgres` 컨테이너는 dev/prod 구분이 없음 (공유 DB)

**교훈**:
- `.env` 의 파괴적 플래그(`DEV_BOOTSTRAP`, `TRUNCATE_ON_START` 등)는 **원칙적으로 false**, 임시 변경 후 반드시 원복.
- 재기동 전 `grep DEV_BOOTSTRAP .env` 로 상태 확인 습관.
- 코드 레벨 가드가 최후의 안전망이다 — `LocalDevBootstrapConfig` 에 `admin_users` 실 사용자 row 존재 시 `IllegalStateException` 으로 hard-abort 하는 pre-flight guard 추가 (이 PR). Guard 회피는 의도적 수동 DELETE 로만 가능, 플래그 바이패스 제공 안 함.
- Postgres 로컬 dev 환경에도 `archive_mode=on` + `archive_command` 세팅 검토. 디스크 50MB/일 수준이면 WAL 보관 7일로 충분.

**재발방지**:
1. `LocalDevBootstrapConfig` pre-flight guard (이 PR)
2. `AGENTS.md §9.1.1` 경고 섹션
3. `.env.example` 의 `DEV_BOOTSTRAP=false` 기본값 + 경고 주석
4. 테스트: `LocalDevBootstrapConfigTest` 4 개 시나리오 (real user 존재 → abort / seed only → 진행 / disabled → skip / 테이블 없음 → 진행)

---

## 참고
- `docs/ADR.md` — 아키텍처 결정 기록
- `AGENTS.md` — 현행 정책 카탈로그
- `docs/DEFERRED_WORK.md` — 미뤄둔 작업
