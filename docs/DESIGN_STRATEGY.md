# Clipping MCP Server — 디자인 전략

> 이 문서는 UI/UX 설계의 **원칙, 패턴, 결정 이유**를 정리한다.
> "왜 이렇게 생겼는지"를 설명하는 문서.

---

## 어드민 홈 Daily Operator Dashboard (2026-04-18)

- 실패 건수 우선 노출, 성공률은 부가 정보
- 4-Tier 구조: 조치 필요 (있을 때만) / 오늘 대기 / 24h 요약 / 참고
- vanity 지표 제거: 오늘 수집 / 성공률 99% / 지난 7일 요약 / 최근 발송 5건
- "가장 오래된 N일 전" urgency 중심 미리보기

---

## 페르소나 인사이트 — risk/growth 이중 렌즈 (2026-04-19, ADR-025)

Analytics > Persona Insights 탭을 "현황 테이블 3섹션" 에서 **두 축 랜딩** 으로 재구성.

### 구조
1. **배치 실패 배너** (조건부, 최상단) — 24h+ 지연 or FAILED 시 빨간 banner + 나머지 영역 `aria-hidden="true"` + `pointer-events-none`.
2. **헤더 스트립** — "주의 N (NEW M) · ↑ 성장 K · 유휴 L" 한 줄 요약.
3. **위험 섹션** (운영) — `CHURN_EXCESS` / `IDLE` / `ENGAGEMENT_DROP` 3 타입 카드. 지속 주차 오름차순 정렬, 비어있으면 "✓ 이번 주 주의가 필요한 페르소나가 없어요" 초록 카드 유지(섹션 숨김 금지).
4. **성장 섹션** (프로덕트) — `SUBS_SURGE` / `ENGAGEMENT_RISE` / `FIRST_SUBSCRIPTION`. 위험과 동일한 카드 primitive, positive framing.
5. **주간 추이** — 8색 팔레트, 기본 필터 "변화 있는 페르소나만" ON, 진행 중인 주(ISO week)는 `null` 처리로 가짜 0 급락 제거.
6. **포트폴리오** — 4 메트릭 카드 + "분석 대상 외" Tooltip(excluded reason) + 최근 커스텀 3.
7. **푸터** — 배치 신선도 + `12주치 재집계` 링크.

### 카드 primitive 규약
- 모든 위험·성장 카드는 `"prev → current (±Δabs)"` / `"±pct%"` / `"±Δpp"` 형식 중 하나를 primary line 에 노출.
- 위험 아이콘 `⚠` (AlertTriangle), 성장 아이콘 `↑` (TrendingUp), 색상으로 tone 구분 (danger vs primary 토큰).
- NEW 뱃지: `persistentWeeks === 1` → "NEW", 그 외 `${N}주차`.
- CTA 는 지속 주차 기반 차등 (1~2주차 일반 / 3주차+ "비활성화 제안" 강조).

### 결정 근거
- 위험 0 주차에도 성장 섹션이 살아있어 페이지가 쓸모있다.
- 신호 6 타입을 discriminated union 으로 표현해 카드 UI 가 타입 안전 (`SignalDetails` sealed class + `@JsonTypeInfo`).
- 임계치는 `analytics.risk.*` / `analytics.growth.*` env 주입 — 파일럿 관찰 결과 즉시 반영 가능.

### 관련
- ADR-025, 스펙 `docs/superpowers/specs/2026-04-17-persona-insights-redesign-design.md`
- PR: #402 (BE `/signals`), #407 (FE PR-C), #424 (sample disclaimer), #426 (E2E sync)

---

## RSS 소스 품질 — 운영자 action-oriented 페이지 (2026-04-20)

기존 `/admin/content-levers` "콘텐츠 레버" 대시보드를 **RSS 소스 품질 관리 전용** 페이지(`/admin/sources/quality`)로 재설계. PersonaSatisfaction 집계는 약한 신호(참여 편향 + 귀인 모호 + cell 희소)로 완전 제거하고, SourceQuality 만 살려 운영자가 "문제 소스를 빠르게 식별 → 조치" 하는 단일 책임 페이지로 단순화.

### 구조
1. **KPI 카드 4열** — `검토 필요 N건` / `신호 부족 N건` / `평균 클릭률 %` / `총 발송 N`. 금전/달성률이 아니라 **action-oriented 지표** (카운트가 0 아니면 바로 할 일이 있음).
2. **상태 필터 5-chip (radiogroup)** — `전체 (활성)` / `정상` / `검토 필요` / `신호 부족` / `비활성`. 기본은 `전체 (활성)` — **비활성 소스는 기본 숨김** (D6 결정).
3. **정렬/필터 테이블** — 소스명 / 카테고리 / 상태 / 발송수 / 클릭률 / 마지막 수집 / 액션. 정렬 가능 컬럼 4개(발송수/클릭률/마지막수집/상태). 행당 액션: `편집` (SourceEditModal 모달 오픈), `수집 일시중지` (확인 다이얼로그), `활성화` (비활성 행만).
4. **SourceEditModal 재사용** — 별도 편집 페이지 없이 기존 모달을 인라인으로 호출. `source`, `categories`, `open`, `onClose` props 그대로 사용.

### 결정 근거
- **D5 — 재활성화 시 `crawl_fail_count=0` 리셋**: 과거 실패 횟수가 누적되어 즉시 다시 비활성화되는 현상 방지. 재활성화는 운영자의 명시적 재시작 의사 표명이므로 fail count 를 초기화하고 audit (`source_reactivated`) 에 이전 값을 기록.
- **D6 — 비활성 소스 기본 숨김**: 대부분의 소스 수가 활성이고 비활성은 소수이나, 목록 상단에 섞이면 "정상 → 검토 → 신호부족" 분류 scan 이 흐려진다. `비활성` 필터를 선택해야 노출되며, 이 뷰에서는 액션 컬럼에 `[활성화]` 버튼만 노출.
- **에러 재시도 UX (§8.3 규칙 7)** — 재시도 시 화면 깜빡임 금지. 에러 화면 유지 + 백그라운드 호출, 성공 시 결과 표시.

### 관련
- ADR-026 (PersonaSatisfaction 제거), 브랜치 `feat/source-quality-redesign`
- 제거 대상: `ContentLeversPage.tsx`, `PersonaSatisfactionPanel.tsx`, `SourceQualityPanel.tsx`, `PersonaSatisfactionQueryHelper` + 2 endpoints + 3 DTOs

---

## 1. 디자인 방향: 모던 미니멀

### 왜 모던 미니멀인가
이런 디자인은 "복잡한 것을 단순하게" 만드는 데 성공한 한국 서비스다. 뉴스 인텔리전스도 본질적으로 같은 도전:
- 수십 개 설정 → 사용자는 3개만 신경 쓰고 싶다
- 기술 용어 가득한 관리 화면 → 비개발자 관리자가 써야 한다
- AI가 뭘 하는지 모르겠는 불안감 → 모든 결정이 설명 가능해야 한다

### 핵심 원칙 5가지

| 원칙 | 의미 | 예시 |
|------|------|------|
| **한 화면에 하나의 목적** | 스크롤 대신 탭/단계 분리 | 위자드 5단계, 탭 기반 페이지 |
| **결과 먼저, 설정 나중** | 요약 카드 → 상세 테이블 순서 | 대시보드 KPI 카드가 최상단 |
| **사용자 언어로** | 기술 용어 → 일상어 | "카테고리" → "주제", "파이프라인" → "뉴스 가져오기" |
| **불안감 제거** | 상태/결과를 즉시 피드백 | 토스트 알림, 뱃지, 상태 dot |
| **점진적 공개** | 기본 옵션 → 고급 설정 접기 | 운영설정 "고급(Ralph)" 접힘 |

---

## 2. 시각 체계

### 색상 전략

```
브랜드 Primary: 인디고 계열 (--color-primary)
성공/정상:      시맨틱 토큰 (text-primary, bg-primary/5)
위험/실패:      시맨틱 토큰 (text-destructive, bg-destructive)
보조 정보:      시맨틱 토큰 (text-muted-foreground, bg-muted)
```

**금지 사항:**
- ❌ Tailwind 기본 `green-500`, `amber-500`, `red-500` 직접 사용
- ❌ 하드코딩 hex 색상 (`#3b82f6` 등)
- ❌ AI 서비스 특유의 "무지개 그라데이션" 느낌

**이유:** 제네릭한 초록/노랑/빨강은 "AI가 만든 대시보드" 느낌을 줌. 브랜드 인디고 톤으로 절제하면 전문적이고 신뢰감 있는 인상.

### Slack 운영 로그 색상 팔레트 (2026-04-17 추가)

Slack Attachment `color` 바는 3색 + neutral:

| 상태 | 색상 코드 | 이모지 | 용도 |
|------|---------|-------|------|
| GOOD | `#36a64f` | 📊 | 파이프라인 성공, 복구, 일간 예보 |
| WARNING | `#e8a838` | ⚠️ | 경고, 쿨다운 억제, 예산 80~89% |
| DANGER | `#e01e5a` | 🚨 | 파이프라인 실패, 인시던트, 예산 90%+ |
| NEUTRAL | `#dddddd` | (없음) | 주간 리포트 등 정보성 메시지 |

구현: `SlackStatusColor` enum (`src/main/kotlin/com/ohmyclipping/adapter/out/slack/builder/SlackStatusColor.kt`)

적용 범위: M1~M13 모든 운영 알림(`SlackOpsLogNotifier`)에서 `attachments.color`로 전달됨 (2026-04-17 완료). 기존 digest/auto-report 발송 경로(`SlackMessageSender` 비색상 호출)는 변경 없음.

### 다크모드
- `next-themes` + Tailwind `dark:` 변형자
- CSS 변수 기반으로 자동 전환
- 모든 컴포넌트는 `bg-background`, `text-foreground` 등 시맨틱 토큰 사용
- 하드코딩 `bg-white`, `text-gray-600` 금지 → 다크모드에서 깨짐

### 타이포그래피
- 본문: Noto Sans KR (한국어 최적화)
- 숫자/데이터: Inter (tabular-nums)
- 제목/강조: Outfit (웨이트 감 있는 서양 폰트)

---

## 3. 컴포넌트 패턴

### 카드
```
border-radius: 16px (rounded-2xl)
border: 1px solid border
shadow: 미세한 shadow-sm
hover: translateY(-1px) + shadow 강화
```
- 정보를 그룹핑하는 기본 단위
- 클릭 가능한 카드는 호버 효과 필수
- 메트릭 카드: 아이콘 + 라벨 + 큰 숫자

### 뱃지/칩
```
Pill 형태: rounded-full
상태 뱃지: bg-primary/10 text-primary (기본), bg-destructive (에러)
필터 칩: 선택 시 bg-primary text-white, 미선택 시 bg-muted
```
- 사이드바 뱃지: 파란(primary) = 처리 필요, 빨간(destructive) = 에러/실패
- 뱃지 variant 구분: `"default"` vs `"destructive"`

### 버튼 위계
```
Primary:     variant="default"  — 주 행동 (승인, 저장, 다음)
Secondary:   variant="outline"  — 보조 행동 (반려, 취소, 이전)
Destructive: variant="destructive" — 위험 행동 (삭제, 탈퇴)
Ghost:       variant="ghost"    — 덜 중요한 행동
```
- **주 행동이 시각적으로 가장 강해야** 한다
- 반려/삭제가 승인보다 눈에 띄면 안 됨 (이전에 빨간 반려 버튼이 파란 승인보다 강했던 문제 수정)

### 모달
```
border-radius: 20px
max-width: 480px
max-height: min(70vh, 640px)
padding: 0 → 자식별 개별 padding
스크롤: wrapper에 overflow-y: auto + flex: 1 1 auto; min-height: 0
```

### 테이블
```
행 호버: hover:bg-muted/50 transition-colors
헤더 줄바꿈 방지: whitespace-nowrap
상태 컬럼: Badge 컴포넌트 사용
날짜: 상대시간 (formatRelativeDate)
긴 텍스트: max-w-[200px] truncate
```

### 빈 상태 (Empty State)
- 아이콘 + 제목 + 설명 + CTA
- 긍정적 톤: "모든 처리를 완료했습니다" (달성감)
- 안내형: "새로운 항목이 들어오면 여기에 표시됩니다"
- 검색 결과 없음: 검색어 포함 + "초기화" 버튼

---

## 4. 레이아웃 전략

### 사이드바
- 너비: 64 (w-64, 256px)
- 5개 그룹 아코디언: 홈 / 콘텐츠 설정 / 운영 / 분석 / 시스템
- 그룹 상태: localStorage 저장 (새로고침해도 유지)
- 활성 메뉴: `bg-sidebar-accent` 하이라이트
- 뱃지: 처리 필요 건수 실시간 표시 (TanStack Query 캐시 공유)

### 페이지 구조
```
[헤더: 제목 + 서브타이틀]
[요약 카드 / KPI 영역]
[필터 바]
[메인 콘텐츠 (테이블, 카드 그리드 등)]
[페이지네이션]
```
- 모든 페이지가 이 패턴을 따름
- 탭이 있는 페이지: 탭 전환 시 서브타이틀도 맥락에 맞게 변경

### 필터 패턴
- 성격이 다른 필터 그룹은 **세로 구분선 `|`** 으로 분리
- 칩 필터: 동일 카테고리 내 단일 선택
- Select 드롭다운: 목록이 길거나 보조 필터인 경우
- 검색: 300ms 디바운스, 한글 초성 지원 (es-hangul)

---

## 5. 인터랙션 원칙

### 피드백 즉시 제공
| 행동 | 피드백 |
|------|--------|
| 저장/승인/반려 | Toast 알림 (sonner) |
| 로딩 | 스켈레톤 placeholder |
| 에러 | 인라인 메시지 + 재시도 버튼 |
| 토글 | 즉시 상태 변경 + Toast |

### 에러 재시도 UX
- ❌ 재시도 시 화면 전환(로딩 스켈레톤) → 깜빡임
- ✅ 에러 화면 유지 + 백그라운드 재호출 → 성공 시 결과 표시, 실패 시 Toast

### 위험 행동 보호
- 삭제/탈퇴: 확인 다이얼로그 + 체크박스 ("위 내용을 확인했으며 동의합니다")
- 회원 탈퇴: 확인 → 로그아웃 → 로그인 페이지 리다이렉트
- 일괄 처리: 선택 건수 명시 ("3건을 승인하시겠습니까?")

### 전환 효과
```
transition: 0.15~0.25s ease
hover: translateY(-1px) 또는 shadow 강화
accordion: max-height 0.25s ease
```
- 자연스럽고 빠르게. 과도한 애니메이션 금지.

---

## 6. 정보 아키텍처

### 관리자 사이드바 구조
```
홈
  └ 대시보드 (KPI + 조치 필요 + 파이프라인 + 시스템 + AI비용)

콘텐츠 설정
  ├ 뉴스 소스 (RSS 소스 관리)
  ├ 주제관리 (카테고리)
  ├ 요약스타일 (페르소나 프리셋)
  ├ 키워드 규칙 (포함/제외 + 임계값)
  └ 경쟁사 관리 (등록/별칭/제외키워드/SOV)

운영
  ├ 파이프라인 (실행 이력)
  ├ 뉴스 검토 (INCLUDE/REVIEW/EXCLUDE)
  ├ 발송 관리 (이력 + 재발송)
  └ 회원관리 (가입 승인 + 회원 현황)

분석
  ├ 통합 분석 (DAU, 퍼널, 기사 CTR)
  └ 뉴스 리포트 (트렌드, 경쟁사, 스냅샷, 카드, 리포트)

시스템
  ├ 시스템 상태 (서버/DB/Slack/스케줄러)
  ├ 감사 로그
  └ 운영설정 (Slack/파이프라인/자동리포트)
```

### 유저 사이드바 구조
```
홈 (내 뉴스 현황 + 빠른세팅 진입)
내 구독 관리 (구독 토글 + 설정 변경)
주제 둘러보기 (카테고리 검색 + DM 구독)
진행 상태 [뱃지: PENDING 건수]
뉴스 리포트 (월간 통계)
내 기사 목록 (수신 이력 + 북마크)
```

---

## 7. 위자드 설계 (빠른세팅)

### 5단계 구조
```
1. 지역 필터    → "국내? 해외? 둘 다?"
2. 소스 추가    → "어떤 키워드/RSS로 수집?"
3. 요약 스타일  → "어떤 톤으로 요약?" (프리셋 선택)
4. 상세 설정    → "몇 건? 제외 키워드? 발송 시간?"
5. 채널 선택    → "Slack 채널? 개인 DM?"
```

### 설계 원칙
- **마지막 단계는 가장 단순하게** — 채널/DM 선택만
- **프리셋으로 시작, 커스텀은 선택** — "경영진 브리핑", "캐주얼 뉴스" 등 바로 선택 가능
- **선택됨 = 명확한 시각 피드백** — ring + 체크마크 아이콘
- **되돌아가기 가능** — 모든 단계에서 "이전" 버튼

---

## 8. 반응형 & 접근성

### 현재 상태
- 데스크톱 우선 (최소 1024px)
- 사이드바 고정 (모바일 미지원)
- shadcn/ui (Radix 기반) → 키보드 네비게이션, 스크린리더 기본 지원

### 향후 고려
- 모바일 반응형 (사이드바 → 바텀 네비게이션)
- 태블릿 대응 (사이드바 접기)

---

## 9. 디자인 결정 기록

| 결정 | 이유 | 날짜 |
|------|------|------|
| 모던 미니멀 채택 | 비개발자도 즉시 이해, 한국 사용자 친숙 | 2026-03 |
| 드롭다운 → 칩 필터 | 현재 선택 상태가 한눈에 보임 | 2026-03-19 |
| 사이드바 뱃지 시스템 | "지금 봐야 할 것"을 사이드바에서 즉시 인지 | 2026-03-19 |
| 상태 뱃지 2종 (파란/빨간) | 파란=처리 필요, 빨간=에러/실패 구분 | 2026-03-19 |
| 위자드 4→5단계 | 마지막 단계에 설정이 섞여 있어 혼란 | 2026-03-20 |
| 회원현황 필터 11→3개 | 칩이 너무 많으면 오히려 뭐가 뭔지 모름 | 2026-03-20 |
| 필터 그룹 구분선 | 성격 다른 필터가 한 줄에 있으면 어색 | 2026-03-20 |
| Select max-h 300px | 항목 20+ 시 화면 넘침 방지 | 2026-03-19 |
| 반려 뷰에 "재승인" 추가 | 반려 후 후속 액션 없으면 막다른 길 | 2026-03-19 |
| formatRelativeDate 통합 | "12일 전"이 "2026-03-07 07:38:54"보다 직관적 | 2026-03-19 |

---

## 10. 디자인 시스템 요약

### 사용하는 것
- **shadcn/ui**: Button, Badge, Dialog, Select, Table, Tabs, Input, Textarea, Switch, Label
- **lucide-react**: 아이콘 (일관된 스타일)
- **sonner**: Toast 알림
- **framer-motion**: 전환 효과 (제한적 사용)
- **recharts**: 차트
- **Tailwind v4**: CSS-first, 시맨틱 토큰

### 사용하지 않는 것
- ❌ `useMemo`, `useCallback`, `React.memo` — React Compiler가 자동 처리
- ❌ `style={{}}` 인라인 스타일 — Tailwind 유틸리티만
- ❌ CSS 모듈, styled-components — Tailwind로 통일
- ❌ 커스텀 아이콘 — lucide-react 통일

---

## 11. 결정 로그 (2026-04-09): Persona Analytics 이관

| 항목 | 결정 | 근거 |
|---|---|---|
| `PersonasPage > StyleStatsTab` | 158 라인 → 53 라인 축소 | 페르소나 관리 페이지는 CRUD 에 집중. 차트/포트폴리오는 Analytics 페이지로 이관 |
| `Analytics > 페르소나 인사이트` 탭 신설 | `?tab=personas` 쿼리 라우팅 | 다른 Analytics 탭과 동일 패턴 (useTabSync) |
| `LiveTotalsCards` 4 카드 | 시맨틱 색상 토큰만 사용 | raw Tailwind `bg-red-500` 등 금지 규칙 (AGENTS.md 2.4) |
| `PresetPortfolioTable` 정렬 | HEALTHY > WATCHING > DECLINING > UNUSED | 관리자 시점에서 "건강한 것"부터 보고, "정리해야 할 것"이 아래로 |
| `weekOverWeekDelta` 처리 | Slice 1 단계에서는 항상 null | 시계열 인프라가 Slice 2 에서 도입됨. UI 는 null 일 때 "—" 렌더 |
| `StyleStatsTab` Analytics 링크 | 카드 + CTA 박스 | 모던 미니멀 progressive disclosure: 핵심만 먼저, 상세는 한 번 더 클릭 |

### 결정 로그 (2026-04-11): 경쟁사 관리 + 기타

| 결정 | 이유 | 날짜 |
|------|------|------|
| 경쟁사 관리 페이지를 "콘텐츠 설정" 그룹에 배치 | 경쟁사도 수집 대상 콘텐츠 설정이므로 소스/주제와 같은 그룹 | 2026-04 |
| 별칭 + 제외 키워드 칩 입력 UX | 태그 입력 패턴으로 직관적 추가/삭제. 최대 개수 표시 | 2026-04 |
| 프리셋 직접 참조 (복사본 제거) | 관리자 수정이 즉시 반영되어야 함. 복사본은 데이터 불일치 유발 | 2026-04 |
| 로그인 애니메이션 톤다운 | 과도한 그라데이션/반짝임이 AI-slop 느낌. 절제된 인디고 톤으로 | 2026-04 |
| "뉴스 채널" → "뉴스 소스" 리네이밍 | "채널"이 Slack 채널과 혼동. "소스"가 RSS 피드 의미에 더 정확 | 2026-04 |

### 결정 로그 (2026-04-11): UX 일괄 폴리시 (42개 이슈)

| 결정 | 이유 | 날짜 |
|------|------|------|
| 톤 통일 (해요체) | Copy 톤이 페이지마다 달라 어색함. 전체 해요체로 통일 | 2026-04-11 |
| 버튼 라벨 "저장"으로 통일 | "확인", "적용", "저장" 등 혼재. "저장"이 동작을 가장 명확히 전달 | 2026-04-11 |
| window.confirm → ConfirmModal 전환 | 브라우저 기본 confirm은 스타일 불일치. 커스텀 모달로 UX 통일 | 2026-04-11 |
| Skeleton 컴포넌트 도입 | 로딩 중 빈 화면 대신 콘텐츠 레이아웃 힌트를 제공 | 2026-04-11 |
| 페이지 전환 애니메이션 | 라우트 변경 시 자연스러운 fade 전환으로 SPA 체감 향상 | 2026-04-11 |
| 탭 콘텐츠 fade | 탭 전환 시 컨텐츠가 fade-in되어 시각적 끊김 감소 | 2026-04-11 |
| 사이드바 아코디언 상태 복원 수정 | localStorage에서 복원 시 첫 렌더에서 깜빡이는 버그 수정 | 2026-04-11 |
| 고스트 라우트 제거 | 실제 페이지 없는 사이드바 메뉴 항목 정리 | 2026-04-11 |
| 분석 그룹 독립 | 기존 운영 그룹에서 분석 메뉴를 별도 아코디언 그룹으로 분리 | 2026-04-11 |
| 차트 다크모드 CSS 변수 | 차트 색상을 하드코딩에서 CSS 변수로 전환하여 다크모드 지원 | 2026-04-11 |
| 4개 페이지 lazy-load | AdminDashboard, Analytics, NewsReport, CostOverview를 React.lazy로 전환 | 2026-04-11 |
| html2canvas/jspdf 동적 import | 리포트 내보내기 라이브러리를 사용 시점에만 로드 | 2026-04-11 |
| font-display: swap | 웹폰트 로딩 중 FOIT 방지 | 2026-04-11 |
| 대시보드 인사말 개인화 | 시간대별 인사말 + 사용자 이름 표시 | 2026-04-11 |
| 페이지네이션 일관성 | 모든 테이블에 동일한 페이지네이션 컴포넌트 적용 | 2026-04-11 |
| enum fallback 한국어 | 알 수 없는 enum 값에 대해 영문 코드 대신 한국어 기본값 표시 | 2026-04-11 |

### 결정 로그 (2026-04-17): 뉴스 검토 일괄 UX (안전한 배치 v2)

> PR #327을 복원하지 않고, 14개 Critical 엣지케이스를 해결한 가드레일 버전을 새로 구축했다.

| 결정 | 이유 | 날짜 |
|------|------|------|
| 일괄 UI는 feature flag로만 활성화 | 300 유저 운영 중 rubber-stamping/크래시 위험. `runtimeSettings.reviewBatchUxEnabled`가 킬 스위치 역할 | 2026-04-17 |
| 상한 20건 프론트 강제 | 백엔드는 1-100 허용하지만, 실사용 UX에서 20건 이상은 rubber-stamping 위험이 급증. `MAX_BULK_SELECT = 20`으로 프론트에서 차단 | 2026-04-17 |
| `useVirtualizer` 금지 | 20건 상한이면 불필요. 이전 PR #185 크래시의 원인이며 React Compiler와 공식 비호환 | 2026-04-17 |
| 모바일에서 배치 UI 비노출 | 좁은 화면에서 체크박스 + FloatingActionBar + 다이얼로그가 겹치는 UX 문제. `useMediaQuery("(max-width: 767px)")`로 데스크톱 한정 | 2026-04-17 |
| 2단계 확인 다이얼로그 | 단순 `confirm()`은 rubber-stamping 가능. 샘플 3건 노출 + "확인했어요" 체크박스 필수 → 시각적 읽기 행위 강제 | 2026-04-17 |
| 저신뢰 항목 경고 배너 | `importanceScore < 0.7`인 항목이 섞이면 다이얼로그 상단 `role="alert"` 배너로 "개별 확인 권장" 안내 | 2026-04-17 |
| undo는 `onMutate` snapshot 기반 | `allItems` 참조는 invalidate 이후 변경될 수 있음. onMutate 시점에 원본 status를 Map으로 캡처하여 정확한 상태로 복원 | 2026-04-17 |
| 부분 실패 토스트 상세화 | 실패 코드별 한국어 메시지 집계 (`ALREADY_PROCESSED 2건, NOT_FOUND 1건` 등). 에러는 사용자가 다음 행동을 알 수 있어야 함 | 2026-04-17 |
| 체크박스 선택 중 S/X 단축키 비활성 | 단건/일괄 상태가 혼재하면 사용자 혼동. 보수적으로 단축키 차단하고 시각적 힌트만 유지 | 2026-04-17 |
| `useBulkSelection` 내용 기반 비교 | 기존 구현은 array 참조 비교로 react-query refetch마다 선택 리셋. `itemIds.join()` 직렬화 key로 전환 | 2026-04-17 |
| `useReadTracking` write debounce 200ms | 매 클릭마다 JSON.stringify가 메인 스레드 점유. debounce + pagehide/visibilitychange flush로 UX 유지하며 비용 감소 | 2026-04-17 |
| BulkApproveDialog 초기 포커스=취소 | 파괴적 버튼에 초기 포커스는 실수 엔터 키로 대량 승인 유발. `autoFocus`를 "취소"에 부여 | 2026-04-17 |

### 결정 로그 (2026-04-17): UI 공용 컴포넌트 4종 추가

카드/테이블/모달에서 반복되는 truncate, 이모지 정렬, `·` 구분자, 제목+뱃지 패턴을 `components/shared/`의 표준 컴포넌트로 수렴했다.

| 컴포넌트 | 책임 | 주요 Props |
|---------|------|-----------|
| `TruncatedText` | 한 줄(`truncate`) / 여러 줄(`line-clamp-*`) 말줄임 + `title` 속성 자동 | `lines: 1\|2\|3`, `as`, `showTitle`, `className` |
| `EmojiWithText` | 이모지 + 텍스트 baseline 정렬(iOS Safari 대응). 이모지 없으면 `fallbackIcon` 렌더 | `emoji`, `text`, `fallbackIcon`, `size: sm\|md\|lg`, `lines` |
| `MetaDot` | `A · B · C` 메타 행을 일관되게. null/빈값 자동 필터 | `items`, `separator`, `itemMaxLength` |
| `CardTitle` | 카드 상단 `제목 + 우측 뱃지` 패턴. `min-w-0` + `justify-between` 기본 | `rightSlot`, `size`, `lines`, `titleClassName` |

| 결정 | 이유 |
|------|------|
| 이모지 박스는 고정 픽셀 + `leading-none` | iOS Safari가 이모지를 기본 폰트 크기보다 크게 렌더해 베이스라인이 밀림 → 고정 박스로 정렬 안정화 |
| `MetaDot`은 null/빈값을 **조용히** 제거 | 카드 메타에서 옵션 필드(`targetAudience` 등)가 누락되어도 `· ·` 중복 구분자가 나오지 않도록. 부분 정규화 금지 규칙은 데이터 정합성 문제이므로 표시 전용에는 해당 없음 |
| `TruncatedText`는 `title` 속성 기본 On | 말줄임 시 접근성/UX 양쪽에서 전체 텍스트 확인 경로가 필요 |
| `components/ui/dialog.tsx` 원본 수정 금지 | shadcn 재설치/업그레이드 대비. 대신 `DialogTitle` 안에 `TruncatedText` 삽입 |

적용 파일: `pages/personas/PresetDetailModal.tsx`, `pages/personas/PresetCard.tsx`, `pages/user-clipping/UserManagePage.tsx`, `pages/user-clipping/SubscriptionsTab.tsx`, `pages/user-clipping/PendingRequestDetailModal.tsx`, `pages/subscriptions/ActiveSubscriptionsTable.tsx`.

---

## 12. Phase 3 UI 패턴 (2026-04-18)

> 배경 문서: `docs/superpowers/specs/2026-04-18-analytics-discovery.md`. 운영자 레버 1, 3 을 위한 대시보드 패턴 + Organization 엔티티 편집 UX.

### 12.1 Action-oriented KPI 카드 — SourceQualityPage

상단 4 장 카드로 "지금 조치가 필요한가" 를 한눈에 본다. 순수 수치보다 **운영 액션을 유도하는 배치**를 우선한다.

| 카드 | 산출 방식 | 의미 |
|------|----------|------|
| 검토 필요 | `status === "review"` 행 수 | 조치 후보 큐 크기 |
| 신호 부족 | `status === "insufficient"` 행 수 | 더 지켜볼 소스 |
| 평균 클릭률 | `delivered` 로 가중 평균, clickRate null 제외 | 전체 품질 지표 |
| 총 발송 | `sum(delivered)` | 기간 내 노출량 |

- delivered-weighted 평균이 기본 — 비율 단순 평균은 작은 소스가 왜곡을 만든다.
- 카드는 **정보 표시만** — 클릭해도 스크롤이 필터 영역으로 이동하지 않음 (오작동 유발).

구현: `pages/source-quality/components/SourceQualityKpiCards.tsx` (PR #458).

### 12.2 정렬/필터 테이블 + 인라인 액션 — SourceQualityPage

| 규칙 | 내용 |
|------|------|
| 상태 필터 | 5 chip — 전체 활성 / 정상 / 검토 / 기본 / 비활성. 기본값은 "전체 활성" (비활성 숨김) |
| 정렬 컬럼 | `name` / `delivered` / `clickRate` / `likeRate` 4 축. 방향 토글, 세팅 기억 안 함 |
| 수동 URL 방어 | `sourceId === null` 행 (수동 URL) 은 편집/비활성 액션 disabled + 툴팁으로 이유 안내 |
| 비활성 기본 숨김 | D6 가이드 — 비활성 소스는 "비활성" chip 선택 시에만 표시 |
| 인라인 액션 | [편집] → 기존 `SourceEditModal` 재사용 · [수집 일시중지] → 확인 다이얼로그(되돌릴 수 있다고 명시) |
| 단일 책임 | Persona 만족도 drill-down 은 **2026-04-20 기준 제거** (ADR-033 — 약한 신호: 참여 편향 + 귀인 모호 + cell 희소). 본 페이지는 "문제 소스 식별 → 조치" 한 가지만 한다 |

구현: `pages/source-quality/components/SourceQualityTable.tsx` + `SourceDeactivateConfirmDialog.tsx` (PR #458).

### 12.3 OrganizationMultiSelect — chip 기반 다중 선택

Category 설정 탭에서 "이 카테고리와 관련된 회사" 를 여러 개 연결할 때 쓴다.

```
[검색 input: "회사 검색"] [▼]
──────────────────────
[칩: MegaCorp ✕] [칩: ConglomerateCo ✕] [칩: SemiCorp ✕]
```

| 규칙 | 내용 |
|------|------|
| 검색 | 300ms 디바운스, 한글 초성 지원 (`matchesKoreanSearch`) |
| 선택 뷰 | 선택된 Organization 은 칩(Pill, `rounded-full`) 으로 input 아래 영역에 표시. ✕ 로 즉시 제거 |
| 드롭다운 | 검색어가 있으면 필터된 Organization 리스트, 없으면 최근/자주 쓴 10개 |
| 빈 매핑 배너 | 아직 Organization 이 없는 Category 는 편집 시 "아직 회사 연결 없음 — 연결하면 분석이 풍부해집니다" 안내 |
| 저장 타이밍 | `PUT /api/admin/categories/{id}/organizations` 전체 치환 방식 — diff 계산 생략 |

구현: `pages/organizations/OrganizationMultiSelect.tsx` (PR #431), Category SettingsTab 에 통합.

### 12.4 Sunset 기준 — 대시보드 살아남기 규칙

> 대시보드는 **추가만큼이나 제거도 의식해서** 만든다. Phase 3 에서 도입한 레버 1, 3 대시보드는 6주 관찰 후 아래 기준으로 유지/축소/제거한다.

| 대시보드 | 살아남기 기준 (provisional) | 기준 미달 시 |
|---|---|---|
| Persona 만족도 (Level 1+2+3) | 6주 중 월 2회 이상 운영자 접속 + drill 1회 이상 | Level 1 기본 뷰만 유지, Level 2/3 drill 제거 |
| RSS 소스 품질 | 6주 중 소스 제거/정리 액션 1회 이상 발생 | 내부 SQL 쿼리로 전환, 관리 UI 제거 |
| 공유 passive 캡처 | 6주 capture_rate > 10% | PR #432 기능 자체 제거, manual audit 으로 만족 |

이 기준은 **PR0 baseline 측정 후 PR4 머지 직전에 최종 확정**한다 (`2026-04-18-analytics-discovery.md` §10.5). 앞으로 추가되는 모든 운영 대시보드는 **도입 시점에 sunset 조건을 함께 기록한다** — 방치된 대시보드가 "vanity metric" 을 만든다.

## 13. 결정 로그 (2026-04-19): Review Policy Dashboard 카드 그리드

> `/admin/review-queue` 상단에 카테고리별 리뷰 정책 현황 카드 그리드를 삽입 (PR-1). 기존 테이블/필터/액션 영역은 그대로 두고 **상단 섹션만 증설**하는 무중단 개편.

### 13.1 카드 그리드 패턴 (재사용 가능)

- **레이아웃**: `grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4` — 모바일 1열 → 태블릿 2열 → 데스크톱 3열.
- **카드 1장 = 1 카테고리**: 이름, 임계값(include/review/autoApprove), pending 누적, 7일 처리 지표, 평균 점수 요약.
- **클릭 인터랙션**: 카드 클릭 시 하단 테이블의 주제 필터(`topicId`)가 해당 카테고리로 즉시 전환. 별도 라우팅/모달 없음 — 같은 페이지 내 filter 연동이 사용자 맥락 유지에 유리.
- **Inline threshold 편집**: 카드 내부에서 threshold를 즉시 수정 가능. optimistic update + 실패 시 rollback + 토스트 피드백.

### 13.2 "임계값 미설정" 경고 배너 — 빠른 버튼 **의도적 제외**

- NULL threshold 카테고리가 1개 이상일 때 `EmptyThresholdBanner` 노출.
- 배너에는 **"일괄 0.5로 설정" 버튼을 두지 않는다** — 응급조치 값이 프로덕션에 고착되면 카테고리별 독자적 튜닝 기회를 놓친다.
- 대신 카드 inline editor로 **카테고리마다 직접 설정하도록 유도**한다.

### 13.3 AI 추천 일괄 승인 + 50건+ 스팟체크

- `AiRecommendPanel` — importance_score 기준 상위 N건을 "추천 승인"으로 표시.
- **50건 이상 일괄 승인 시 `SpotCheckDialog` 강제 노출** — 랜덤 5건을 운영자가 직접 검수하고 모두 통과해야 최종 승인. 통과 이력은 `mcp_audit_log` + `review_item_audits` 양쪽에 기록.
- 논리: "AI 추천"과 "관리자 검토"를 분리해서 bias-free bulk approve를 방지. 규모 임계값(50)은 운영자 피로도와 품질 리스크의 균형점.

### 13.4 기존 페이지에 섹션 삽입 시 원칙

- **기존 필터/테이블은 건드리지 않는다** — 회귀 위험 최소화.
- **새 섹션은 상단**에 두되, 숨김 토글(접기/펼치기) 없이 고정 노출 — 대시보드는 "스캔하자마자 보이는 것"이 제 일이다.
- 섹션 간 구분은 `border-b` 또는 `space-y-6` 으로 부드럽게.
