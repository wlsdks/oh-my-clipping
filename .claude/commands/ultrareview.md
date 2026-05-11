---
description: 머지 직전 최종 리뷰 — 여러 에이전트 병렬로 아키텍처/보안/성능/커버리지/문서 drift 검증
---

# /ultrareview

PR 이 main 에 머지되기 직전의 **최종 필터**. subagent 2단 리뷰로 놓친 이슈를 마지막에 걸러내기 위한 슬래시 커맨드.

## 언제 쓰는가

**꼭 돌린다**:
- 머지 직전 최종 관문 — subagent 2단 리뷰로 놓친 이슈 마지막 필터
- 고위험 변경 — 인증/보안, DB 마이그레이션, 결제 로직, 권한 체크, MCP 서버 외부 노출
- 대형 PR — 500+ lines diff
- 대규모 리팩토링 직후 — 숨은 regression / 의존성 체인 깨짐 잡기

**굳이 안 써도 된다**:
- 작은 버그픽스 (1~5 lines), 오타/문구 수정
- 단순 의존성 패치 버전 업그레이드
- 이미 subagent 2단 리뷰가 탄탄히 돌아간 소규모 PR

## 호출 방법

- `/ultrareview` — 현재 브랜치 (vs `origin/main`) 리뷰. feature 브랜치 checkout 상태에서 실행.
- `/ultrareview <PR#>` — 특정 PR 을 페치해서 리뷰. 머지된 PR 에도 사후 리뷰 가능 (follow-up PR 재료).

## 실행 절차

아래를 **병렬 에이전트** 로 분담 실행한다 (§6.3 병렬 에이전트 배치 규칙 준수).

### 에이전트 1 — 아키텍처 & 경계

- Clean Architecture 경계 위반 import 유무 (`admin`/`user`/`tool` → `service` 직접 호출, `service` → `store` 구현 체 직접 참조)
- Feature-Sliced Design 레이어 역참조 (pages 간 cross-import, shared 에 도메인 정책)
- 에이전트 생성 코드의 아키텍처/URL/스펙 불일치

### 에이전트 2 — 보안 & 운영

- 비밀번호/토큰/세션 관련 변경 시 §9.4 보안 정책 체크리스트 충족 여부
- FK 추가, DDL, seed orphan (§1.5 / §2.1.2)
- `lastRejectReason` 태그 누락 (§9.1.5)
- actor_id 직접 username 저장 (§2.1.1 AuditActorResolver 경유 위반)

### 에이전트 3 — 성능 & 회귀

- N+1 쿼리, 누락된 index, nullable cast 방어 (§1.3)
- `Thread.sleep` / generic 예외 / 광범위 catch / DB 방언 누수
- 시그니처 변경된 메서드의 모든 호출부 갱신 여부

### 에이전트 4 — 테스트 & 커버리지

- 해피패스 + 엣지케이스 + 에러 경로 3종 커버 (§5.1)
- weak assertion (`toBeDefined()` 단독) 없음 (§5.1.1)
- Store integration test absolute count 금지 (§5.1 delta-based)
- UI 재설계 PR 의 E2E spec 동기 갱신 (§5.1.0.5)

### 에이전트 5 — 문서 drift (2026-04-20 추가)

PR 이 아래 중 하나라도 건드렸는지 판정:

```bash
git diff origin/main --name-only | grep -E \
  "frontend/src/pages/|@GetMapping|@PostMapping|@PatchMapping|@DeleteMapping|db/migration(-pg)?/V"
```

변경 있으면 아래 문서 중 해당되는 것이 같은 PR 에서 함께 갱신됐는지 확인:

| 코드 변경 범위 | 갱신 필요 문서 |
|----------------|---------------|
| UI 페이지 추가/삭제/재설계 | `docs/DESIGN_STRATEGY.md`, (해당 시) `docs/ROADMAP.md` 체크박스 |
| API 엔드포인트 추가/수정/삭제 | `docs/API_REFERENCE.md` |
| DB 마이그레이션 추가 | `AGENTS.md`, `AGENTS.md` (숫자형 정책/장애 시나리오) |
| 아키텍처 결정 | `docs/ADR.md` (신규 ADR) |
| 비즈니스 정책 변경 | `AGENTS.md` |
| 환경변수 추가 | `docs/ONBOARDING.md`, `AGENTS.md §9.5`, `docs/DEPLOY_CHECKLIST.md §1` |
| 폴더 구조/기술 스택 변경 | `AGENTS.md §2.1~2.3`, `AGENTS.md` |
| MCP 도구 추가/Rate limit 변경 | `AGENTS.md §9.4` MCP Rate Limit 표 |

문서 수정이 전혀 없으면 **명시적으로 질문**한다 (묵시 통과 금지):

> 이 PR 은 {UI 페이지 / API / 마이그레이션 / ...} 를 변경했는데 관련 문서 갱신이 없습니다.
> 의도적인가요? 아니면 누락인가요?

#### 배경

PR #458 에서 ContentLeversPage 삭제 시 `docs/DESIGN_STRATEGY.md §12.1/12.2` 가 그대로 남아 doc drift 가 발생. 이전 `/ultrareview` 가 이를 잡지 못한 사례 → 본 체크포인트 추가.

## 완료 기준

1. 다섯 에이전트 보고를 병합해 **심각도 (Blocker / Major / Minor / Nit)** 로 분류한다.
2. Blocker/Major 가 1건이라도 있으면 머지 금지. 수정 후 재실행.
3. Doc drift 에이전트가 명시 질문을 올렸는데 답이 안 나왔으면 **Blocker** 로 간주.
4. 최종 리포트에 PR URL, 커밋 SHA, 적용된 정책 참조 (AGENTS.md 섹션 번호) 를 포함한다.

## 참고

- AGENTS.md §4.1 — 외부 루프 (PR 단위 표준 워크플로)
- AGENTS.md §5.1.0.5 — UI 재설계 PR 의 E2E 동기 갱신 필수
- AGENTS.md §6.3 — 병렬 에이전트 배치 규칙
- `docs/LESSONS.md` — 과거 인시던트 교훈
