# API Reference

> 전체 150+ 엔드포인트. 인증 방식: Form Login(세션) 또는 Bearer Token.
>
> 현재 구현 기준 인증 경계와 정책 우선순위는 `AGENTS.md`를 따른다.
>
> OpenAPI: `/api-docs` (JSON), `/swagger-ui.html` (UI)

---

## 인증

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/login` | 없음 | 폼 로그인 (username + password → 세션 쿠키) |
| POST | `/api/public/user/auth/signup` | 없음 | 사용자 회원가입 (JSON, React 회원가입 화면) |
| POST | `/admin/signup` | 없음 | 관리자 회원가입 (폼) |
| POST | `/user/signup` | 없음 | 사용자 회원가입 레거시 폼 |
| GET | `/api/me` | 세션 | 현재 로그인 사용자 정보 |
| GET | `/api/public/admin/auth/signup-availability` | 없음 | 관리자 가입 가능 여부 |
| GET | `/api/public/admin/auth/check-username` | 없음 | 아이디 중복 확인 |
| GET | `/api/public/user/auth/signup-availability` | 없음 | 유저 가입 가능 여부 |
| GET | `/api/public/user/auth/check-username` | 없음 | 아이디 중복 확인 |

---

## 관리자 API (`/api/admin/*`)

### Rate Limit 공통 정책

모든 `/api/admin/**` 요청은 관리자명(actor) 기준으로 Redis 슬라이딩 윈도우 rate limit이 적용된다.
쓰기/읽기 버킷이 분리되어 콘솔 폴링이 쓰기 예산을 소진하지 않는다.

| HTTP 메서드 | 기본 한도 | Redis 키 prefix |
|-------------|----------|-----------------|
| POST / PUT / PATCH / DELETE | 관리자별 분당 100회 | `rl:admin:write:{actor}` |
| GET / HEAD / OPTIONS | 관리자별 분당 500회 | `rl:admin:read:{actor}` |

- 한도 초과 시 HTTP 429를 반환한다.
- `Authentication`의 Principal이 없으면 `anonymous-admin` 단일 버킷으로 집계된다.
- 기본 한도는 `MAX_ADMIN_WRITE_REQUESTS_PER_MINUTE`, `MAX_ADMIN_READ_REQUESTS_PER_MINUTE` 환경변수로 조정할 수 있다.
- 엔드포인트별 추가 제한(예: Slack 검증 10회/분)이 있는 경우 별도 표기한다.
- 화이트리스트: `/actuator/**`, `/sse`, `/mcp/message`는 본 rate limit 대상이 아니다 (각각 Prometheus, SSE long-lived, `McpRateLimiter`가 별도 처리).


### 카테고리
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/categories` | 전체 목록 (통계 포함) |
| GET | `/api/admin/categories/{id}` | 단건 조회 |
| POST | `/api/admin/categories` | 생성 |
| PUT | `/api/admin/categories/{id}` | 수정 |
| DELETE | `/api/admin/categories/{id}` | 삭제 |
| GET | `/api/admin/categories/{id}/history?limit=20` | 통합 변경 이력 (최신순, 1..100) |
| POST | `/api/admin/categories/{id}/restore` | revision으로 되돌리기 — 바디: `{revisionId, expectedUpdatedAt}` |

### 소스 (RSS)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/sources` | 목록 — 쿼리: `categoryId`, `q`, `complianceStatus`(EXPIRED\|EXPIRING_SOON\|NEVER_REVIEWED\|VALID), `page`, `size`(최대 500) |
| GET | `/api/admin/sources/{id}` | 단건 |
| POST | `/api/admin/sources` | 생성 |
| PUT | `/api/admin/sources/{id}` | 수정 |
| DELETE | `/api/admin/sources/{id}` | 삭제 |
| POST | `/api/admin/sources/{id}/verify` | 피드 접근 검증 |
| POST | `/api/admin/sources/{id}/approve` | 법적 승인 |
| POST | `/api/admin/sources/validate-url` | URL 유효성 검사 |
| POST | `/api/admin/sources/discover` | RSS 자동 탐색 |
| GET | `/api/admin/sources/{id}/history?limit=20` | 통합 변경 이력 (최신순, 1..100) |
| POST | `/api/admin/sources/{id}/restore` | revision으로 되돌리기 — 바디: `{revisionId, expectedUpdatedAt}` |
| GET | `/api/admin/source-compliance/{sourceId}` | 저작권 상태 |
| PUT | `/api/admin/source-compliance/{sourceId}` | 저작권 수정 |
| GET | `/api/admin/sources/compliance-summary` | 저작권 재검토 필요 건수 요약 (사이드바 뱃지용) — 응답: `{attentionCount}` |
| GET | `/api/admin/sources/stats/article-counts?days=7` | 소스별 기사 수집 건수 (최근 N일) |
| GET | `/api/admin/sources/stats/ai-costs?days=30` | 소스별 AI 비용 집계. `estimatedUsd`는 소액 사용량이 0으로 보이지 않도록 USD 소수 6자리까지 반환 |
| GET | `/api/admin/sources/{id}/analytics?days=30` | 특정 소스 수집 analytics |
| GET | `/api/admin/sources/{id}/crawl-history?days=30` | 크롤 이력 + uptime |
| GET | `/api/admin/sources/coverage-gaps` | 카테고리별 커버리지 갭 |
| POST | `/api/admin/sources/bulk/verify` | 일괄 연결 확인 — 바디: `{ids: [...]}` |
| POST | `/api/admin/sources/bulk/archive` | 일괄 보관(반려) — 바디: `{ids: [...]}` |

### 페르소나 (요약스타일)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/personas` | 전체 목록 |
| GET | `/api/admin/personas/{id}` | 단건 |
| POST | `/api/admin/personas` | 생성 |
| PUT | `/api/admin/personas/{id}` | 수정 |
| PATCH | `/api/admin/personas/{id}/active` | 활성 상태만 부분 업데이트. body: `{"isActive": boolean}`. 프리셋 비활성화 시도는 409. 감사 로그 기록. |
| DELETE | `/api/admin/personas/{id}` | 삭제 |
| GET | `/api/admin/personas/{id}/versions` | 버전 이력 (persona_versions — 하위 호환) |
| GET | `/api/admin/personas/{id}/versions/{v}` | 특정 버전 |
| POST | `/api/admin/personas/{id}/rollback/{v}` | 버전 롤백 |
| GET | `/api/admin/personas/{id}/history?limit=20` | 통합 변경 이력 (`entity_revision_history`, 최신순, 1..100) |
| POST | `/api/admin/personas/{id}/restore` | revision으로 되돌리기 — 바디: `{revisionId, expectedUpdatedAt}` |

### 키워드 규칙
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/category-rules/{categoryId}` | 규칙 조회 (응답에 `autoApproveThreshold?` 포함) |
| PUT | `/api/admin/category-rules/{categoryId}` | 규칙 수정 — 바디에 `includeThreshold`, `reviewThreshold`, `autoApproveThreshold`(0..1, nullable), `clearAutoApproveThreshold`(true면 null로 해제) 지원 |
| GET | `/api/admin/category-rules/stats` | 규칙 통계 (7일 기본) |
| GET | `/api/admin/category-rules/{categoryId}/excluded-items` | 제외된 항목 |
| GET | `/api/admin/category-rules/{categoryId}/history?limit=20` | 통합 변경 이력 (최신순, 1..100) |
| POST | `/api/admin/category-rules/{categoryId}/restore` | revision으로 되돌리기 — 바디: `{revisionId, expectedUpdatedAt}` |
| POST | `/api/admin/category-rules/{categoryId}/dry-run` | **PR-3-lite** — 룰 변경 dry-run 시뮬레이션 (read-only). 바디: `{excludeEventTypes: string[], days?: 1..90 기본 30, maxSamples?: 1..50 기본 5}`. 응답: `{analyzedCount, wouldAutoExclude, wouldStayUnchanged, samples: [{summaryId, title, eventType, score, reason}]}`. `reason` ∈ `{event_type_blacklist, zero_signal}`. |

### 뉴스 검토
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/review-items` | 목록 — 쿼리: `categoryId?`, `status?`, `limit`(1..300, 기본 100), `perCategory?`(1..limit; categoryId 미지정 시 카테고리별 top-N 샘플링) |
| GET | `/api/admin/review-items/summary` | 카테고리별 건수 요약 |
| GET | `/api/admin/review-items/stats?period=7d|30d` | AI 정확도 통계 |
| GET | `/api/admin/review-items/{summaryId}/audits` | 감사 이력 |
| POST | `/api/admin/review-items/{summaryId}/approve` | 포함 확정 (다음 발송에 포함) |
| POST | `/api/admin/review-items/{summaryId}/exclude` | 제외 |
| POST | `/api/admin/review-items/{summaryId}/review` | 재검토 |
| POST | `/api/admin/review-items/bulk-approve` | 일괄 승인 (ids: 1..100) |
| POST | `/api/admin/review-items/bulk-exclude` | 일괄 제외 (ids: 1..100) |
| POST | `/api/admin/review-items/bulk-revert` | 일괄 되돌리기 (각 항목별 previousStatus, 1..100) |
| GET | `/api/admin/review-items/policy-status` | 대시보드용 카테고리별 리뷰 정책 현황 집계. 응답: `{ categories: [ReviewPolicyStatus...], generatedAt }`. 각 카테고리에는 임계값(include/review/autoApprove), pending 누적, 7일 처리 지표, 평균 점수, event_type 분포, 마지막 처리 시각 포함 |
| GET | `/api/admin/review-items/score-distribution?categoryId=&days=` | `batch_summaries.importance_score` 10-버킷 히스토그램 (`[0.0, 0.1) … [0.9, 1.0]`). 쿼리: `categoryId?` (미지정 시 전체), `days`(1..90, 기본 7, 서비스에서 clamp). 응답: `{ buckets: [{ range, count } * 10], totalCount, medianScore, meanScore }` |
| GET | `/api/admin/review-items/auto-excluded` | **PR-3-lite** — 룰 엔진 자동 제외 감사 뷰. 쿼리: `categoryId?`, `reason?`(`rule:event_type_blacklist` / `rule:zero_signal`), `days`(1..30, 기본 7), `page`(>=0, 기본 0), `size`(1..100, 기본 20). 응답: `{items: [{summaryId, title, categoryName, score, reason, excludedAt}], totalCount, reasonBreakdown: {reason: count}}`. |
| POST | `/api/admin/review-items/{summaryId}/restore-to-review` | **PR-3-lite** — 자동 제외 항목을 REVIEW 로 복구. 대상 가드: `reviewed_by='policy-auto' AND status=EXCLUDE` 만 허용 (ConflictException). 감사에 `reason='manual_restore_from_auto_exclude'` 기록. 응답: `{summaryId, newStatus: "REVIEW"}`. |

### 파이프라인
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/admin/pipeline/execute` | 파이프라인 실행 (202 Accepted, runId 반환) |
| GET | `/api/admin/pipeline/runs` | 파이프라인 실행 이력. 필터: categoryId, status, from, to, offset, limit. `?personaId=<id>` 옵션: 해당 페르소나의 활성 카테고리 범위로 필터 |
| GET | `/api/admin/pipeline/runs/{runId}` | 실행 상세 + 단계별 추적 |
| GET | `/api/admin/pipeline/latest` | 최근 실행 |

### 발송 관리
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/delivery/summary` | 요약 (날짜 기본: 오늘) |
| GET | `/api/admin/delivery/logs` | 이력 (페이지네이션, 필터) |
| POST | `/api/admin/delivery/{logId}/retry` | 실패 건 재발송 |

### 회원관리
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/user-accounts` | 회원 목록 조회. status: PENDING/APPROVED/REJECTED. `?personaId=<id>` 옵션: 해당 페르소나의 활성 카테고리를 구독한 APPROVED 사용자만 반환 |
| POST | `/api/admin/user-accounts/{id}/approve` | 승인 |
| POST | `/api/admin/user-accounts/{id}/reject` | 반려 (사유 필수) |
| POST | `/api/admin/user-accounts/{id}/withdraw` | 탈퇴 처리 |
| POST | `/api/admin/user-accounts/bulk-approve` | 일괄 승인 (최대 50) |
| POST | `/api/admin/user-accounts/bulk-reject` | 일괄 반려 (최대 50) |

### 채널 신청 관리
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/user-requests` | 목록 (status 필터) |
| GET | `/api/admin/user-requests/stats` | 통계 (대기, 평균 승인 시간, 인기 토픽) |
| POST | `/api/admin/user-requests/{id}/approve` | 승인 |
| POST | `/api/admin/user-requests/{id}/reject` | 반려 |
| POST | `/api/admin/user-requests/bulk-approve` | 일괄 승인 |
| POST | `/api/admin/user-requests/bulk-reject` | 일괄 반려 |

### 분석
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/analytics/dau` | DAU (기본 7일) |
| GET | `/api/admin/analytics/wizard-funnel` | 위자드 퍼널 (기본 30일) |
| GET | `/api/admin/analytics/article-ranking` | 기사 순위 (클릭/CTR/북마크) |
| GET | `/api/admin/analytics/category-stats` | 카테고리별 통계 |
| GET | `/api/admin/analytics/personas/live` | 실시간 페르소나 현황 (5분 캐시). totals + presetPortfolio + customSummary |
| GET | `/api/admin/analytics/personas/trends?weeks=12` | 주간 트렌드 시리즈 (30분 캐시). weeks 1~52, 기본 12 |
| GET | `/api/admin/analytics/personas/signals?lookbackWeeks=4` | 위험/성장 페르소나 신호 (5분 캐시). `{asOfWeekIso, asOfSnapshotDate, isWeekComplete, risks[], growth[], excluded[]}`. lookbackWeeks 1~12, 기본 4 (PR #402) |
| GET | `/api/admin/analytics/personas/batch-runs?limit=10` | 배치 실행 이력 (started_at DESC). 기본 10건 |
| POST | `/api/admin/analytics/personas/backfill?weeks=12` | 과거 N주 스냅샷 백필. weeks 1~52, 기본 12. **호출자 principal 은 `SecurityContext` 에서 자동 유도** — PR #424 이후 `adminUserId` 쿼리 파라미터는 제거. 임의 문자열을 감사 로그에 심지 못하도록 한 보안 개선 |

### RSS 소스 품질 분석 (PR #458 — 구 "콘텐츠 레버")

> 배경: `docs/superpowers/specs/2026-04-18-analytics-discovery.md` §5 운영자 레버 3 (RSS 소스 품질 관리). 2026-04-20 PR #458 재설계 — 레버 1 (Persona 만족도) 집계는 약한 신호로 제거 (ADR-033).

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/analytics/content-levers/summary?period=7d\|14d\|28d\|90d` | 기간 요약 (기본 `28d`). 응답 `ContentLeversSummary`: `{period, sourceQuality}`. `sourceQuality`: `SourceQualityRow[]` — `{sourceId?, sourceName, delivered, uniqueUserClicks, clickRatePct, likes, dislikes, likeRatePct, statusLabel, isActive, updatedAt}`. `sourceId=null`은 `(수동 URL)` 버킷. `statusLabel`: `"normal"`(클릭률 > 15%) / `"review"`(< 5%) / `"default"`(신호 부족 — 샘플 < 10). `isActive`: 소스 활성 여부 (수동 URL 은 `true` fallback). `updatedAt`: ISO 8601, optimistic lock 용 (수동 URL 은 EPOCH) |

**구 endpoints (제거됨)** — ADR-033 (2026-04-20):
- ~~`GET /categories?personaId=...`~~ 삭제
- ~~`GET /categories/{id}/disliked?period=...`~~ 삭제
- ~~`GET /source-quality?period=...`~~ → `/summary` 응답의 `sourceQuality` 필드로 통합

UI: `/admin/sources/quality` (구 `/admin/content-levers` → redirect 유지).

### 조직 (경쟁사/고객사 — Phase 3 PR #431)

> 배경: `docs/superpowers/specs/2026-04-18-analytics-discovery.md` §3.3 Ontology 확장. Organization 과 Category 는 tenant 단위 many-to-many.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/organizations?type=COMPETITOR\|CUSTOMER\|PARTNER\|OTHER` | 목록. `type` 생략 시 전체. 응답 `Organization[]` |
| GET | `/api/admin/organizations/{id}` | 단건. 응답 `Organization` |
| POST | `/api/admin/organizations` | 생성 — 바디: `{name, type, domain?, description?}`. 같은 tenant 내 `name` 중복 시 409 |
| PATCH | `/api/admin/organizations/{id}` | 부분 수정 — 바디: `{name?, type?, domain?, description?}` |
| DELETE | `/api/admin/organizations/{id}` | 삭제. `category_organizations` row는 `ON DELETE CASCADE`로 함께 제거 |
| GET | `/api/admin/categories/{categoryId}/organizations` | 카테고리에 연결된 조직 목록. 응답 `Organization[]` |
| PUT | `/api/admin/categories/{categoryId}/organizations` | 카테고리 조직 연결 전체 치환 — 바디 `{organizationIds: string[]}`. 204 |

`Organization` 스키마: `{id, tenantId, name, type, domain?, description?, createdAt, updatedAt}`. `tenantId`는 현재 모두 `'default'` (외부 판매 시 분리 예정).

### 대시보드 운영 요약 (PR #410)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/dashboard/ops-summary` | 홈 Daily Operator Dashboard용 종합. 응답 `OpsSummary`: `{todayDeliveriesFailed, todayDeliveriesSent, todayPipelineFailed, todayPipelineSucceeded, pendingUserAccounts, pendingReviewItems, ...}`. 날짜 기준 KST 자정 (within=1d = 오늘, within=7d = 오늘-6일). 파이프라인 성공 상태는 `SUCCEEDED`(과거 `SUCCESS` 아님) |

### AI 비용
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/costs/overview` | 비용 개요 (예산 포함) |
| GET | `/api/admin/costs/overview/hourly` | 시간별 비용 |
| GET | `/api/admin/costs/llm` | 채널별 LLM 비용 |
| GET | `/api/admin/costs/models` | 모델/프롬프트 버전별 |
| GET | `/api/admin/costs/reliability` | 성공/실패 비율 |
| GET | `/api/admin/costs/detail` | 채널별 상세. `from`, `to`, `categoryId` 쿼리 지원 |
| GET | `/api/admin/costs/budget` | 예산 설정 |
| PUT | `/api/admin/costs/budget` | 예산 수정 |

### 통계
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/stats/monthly` | 월간 통계 |
| GET | `/api/admin/stats/daily-kpi` | 일간 KPI |

### 클리핑 설정
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/clipping/settings` | 전체 카테고리 설정 |
| GET | `/api/admin/clipping/{categoryId}/settings` | 카테고리 설정 |
| PUT | `/api/admin/clipping/{categoryId}/settings` | 설정 수정 |
| POST | `/api/admin/clipping/{categoryId}/digest` | 수동 다이제스트 |
| POST | `/api/admin/clipping/{categoryId}/pipeline` | 수동 파이프라인 |

### 트렌드 & 키워드
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/keywords/trend` | 키워드 트렌드 |
| GET | `/api/admin/keywords/entities` | 키워드 엔티티 분류 |
| GET | `/api/admin/sentiment/trend` | 감성 트렌드 |
| GET | `/api/admin/trends/snapshot` | 트렌드 스냅샷 |

### 경쟁사 관리
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/competitors` | 전체 경쟁사 목록 |
| POST | `/api/admin/competitors` | 경쟁사 등록 (이름, 별칭, 제외키워드, RSS피드) |
| PUT | `/api/admin/competitors/{id}` | 경쟁사 수정 |
| DELETE | `/api/admin/competitors/{id}` | 경쟁사 삭제 (junction 데이터 cascade) |
| POST | `/api/admin/competitors/collect` | 수동 수집 트리거 |
| POST | `/api/admin/competitors/keyword-preview` | 키워드 미리보기 (등록 전 결과 확인) |

### 브리핑 & 기사
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/briefing/today` | 오늘 브리핑 |
| GET | `/api/admin/articles/top` | 인기 기사 |
| GET | `/api/admin/feedback/hot` | 핫 피드백 |

### 리포트
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/report-settings` | 자동 리포트 설정 |
| PUT | `/api/admin/report-settings` | 설정 수정 |

### 운영설정
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/runtime-settings` | 전체 설정. 응답에 `maintenanceMode`, `maintenanceMessage` 포함. 경쟁사 주간 요약 6개 필드(`competitor_weekly_*`) 포함. Slack 운영 로그 14개 필드(`ops_*`) 포함 (아래 표 참조) |
| PUT | `/api/admin/runtime-settings` | 설정 수정. `opsLogChannelId`/`opsRequestChannelId`/`competitorWeeklyChannelId`는 저장 전 `SlackChannelIdNormalizer`를 거쳐 정규화됨 — URL(`https://.../archives/Cxxxxx`), `#channel`, `ID:Cxxx` 표기 자동 지원. 형식 불일치는 400 + `{message: "... 형식이 올바르지 않습니다 (예: C0123456789)"}` |
| POST | `/api/admin/runtime-settings/reset` | 기본값 복원 (Slack 봇 토큰/차단 채널은 보존) |

#### 신규 `ops_*` RuntimeSettings 필드 (Slack 운영 로그 재설계, 2026-04-17)
| 키 | 기본값 | 설명 |
|----|--------|------|
| `ops_logs_enabled` | `true` | 운영 로그 Slack 발송 킬스위치. `false`로 즉시 비활성화 |
| `ops_notification_profile` | `FULL` | 알림 상세도 (`FULL` / `BATCHED` / `CRITICAL_ONLY`) |
| `ops_daily_forecast_hour` | `8` | 매일 일간 예보 발송 시각 (KST, 0~23) |
| `ops_weekly_report_day` | `MONDAY` | 주간 리포트 발송 요일 (`MONDAY`~`SUNDAY`) |
| `ops_weekly_report_hour` | `9` | 주간 리포트 발송 시각 (KST, 0~23) |
| `ops_pipeline_cooldown_minutes` | `60` | 파이프라인 실패 알림 쿨다운 (분) |
| `ops_incident_window_minutes` | `5` | 인시던트 판정 윈도우 (분) |
| `ops_incident_threshold_categories` | `3` | 인시던트 트리거 최소 카테고리 수 |
| `ops_schedule_miss_grace_minutes` | `10` | 스케줄 미스 유예 시간 (분) |
| `ops_recovery_streak_threshold` | `3` | 복구 신호 연속 성공 횟수 |
| `ops_budget_warn_pct` | `80` | 일간 예보에만 포함되는 예산 경고 임계값 (%) |
| `ops_budget_critical_pct` | `90` | 즉시 WARN 발송되는 예산 임계값 (%) |
| `ops_admin_base_url` | `""` | Slack 딥링크용 관리자 UI 기본 URL |
| `ops_silent_hours_enabled` | `true` | 무음 시간대 활성화 (22:00~08:00 KST + 주말); critical은 항상 발송 |

#### 데이터 보관 기간 RuntimeSettings 필드 (PR-2 retention admin API)
| 키 | 타입 | 기본값 | 유효 범위 | 설명 |
|----|------|--------|-----------|------|
| `retentionRssItemsDays` | Int | `30` | 7..365 | RSS 아이템(`rss_items`) 보관 기간(일). 범위 초과 시 400 |
| `retentionBatchSummariesDays` | Int | `90` | 7..730 | 배치 요약(`batch_summaries`) 보관 기간(일). 범위 초과 시 400 |

GET 응답과 PUT 요청 모두에 포함된다. PUT 시 null 필드는 현재 값을 유지(부분 업데이트).

| GET | `/api/admin/runtime-settings/audits` | 변경 이력 (limit: 1~1000) |
| POST | `/api/admin/runtime-settings/slack/verify` | Slack 연결 검증. 응답에 `neededScopes`/`providedScopes` 포함 — missing_scope 실패 시 클라이언트가 필요한 OAuth 스코프를 사용자에게 안내 가능. **관리자별 분당 10회 제한**, 초과 시 429 `{code: "RATE_LIMITED"}` |
| POST | `/api/admin/runtime-settings/slack/socket/verify` | Slack Socket Mode 앱 토큰 검증. **관리자별 분당 10회 제한**, 초과 시 429 `{code: "RATE_LIMITED"}` |
| POST | `/api/admin/runtime-settings/slack/block-kit/preview` | 블록킷 미리보기 |
| POST | `/api/admin/runtime-settings/slack/block-kit/test-send` | 테스트 전송. **관리자별 분당 10회 제한**, 초과 시 429 `{code: "RATE_LIMITED"}` |
| GET | `/api/admin/slack/blocked-channels` | 차단 채널 목록. 응답 `blockedByUserId`는 항상 상수 `"관리자"`로 익명화되어 내려온다. 감사 DB에는 원본 관리자 username이 보존되며, 운영팀은 DB 직접 조회로 추적 가능 |
| POST | `/api/admin/slack/blocked-channels` | 채널 차단. `channelId`는 `SlackChannelIdNormalizer`로 정규화(URL/#channel/ID:Cxxx 자동 지원), 형식 오류 시 400. `reason`은 trim 후 최대 200자, 초과 시 400 `{message: "차단 사유는 200자 이하로 입력해 주세요"}`. 응답 `blockedByUserId`는 상수 `"관리자"`로 익명화 |
| DELETE | `/api/admin/slack/blocked-channels/{channelId}` | 차단 해제. `channelId`는 정규화 후 조회 |
| GET | `/api/admin/slack/blocked-channels/available-channels?type=public_channel\|private_channel` | 추가 가능 채널 목록 |

### 시스템
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/system/status` | 서버/DB/Slack/스케줄러 상태 |
| GET | `/api/admin/system/token-health` | Slack Bot / Gemini 토큰 헬스 상태 — 응답 `{slackBot, gemini, ok}` (F8) |
| GET | `/api/admin/schedulers/status` | 스케줄러 실시간 상태 목록 — 마지막 실행 · 다음 실행 · 에러 메시지 · 소요시간 |
| GET | `/api/admin/reports/history?reportType=WEEKLY\|MONTHLY&limit=50` | 자동 리포트 발송 이력 (최신순, 최대 200건) |
| GET | `/api/admin/audit-log` | 감사 로그 (페이지네이션, 필터) |
| GET | `/api/admin/audit-log/filters` | 필터 옵션 (액션, 대상 타입) |
| GET | `/api/admin/ops/db-metrics?forceRefresh=false` | DB 크기 스냅샷 (`/admin/db-health` 대시보드용). 응답 `DbSizeSnapshot`: `{databaseSizeBytes, databaseSizeMegabytes, databaseSizePercentOfLimit, limitBytes, thresholdLevel, topTables[], retentionEligible{}, dailyGrowth{}, lastRefreshedAt}`. 쿼리: `forceRefresh` (true면 5분 캐시 바이패스, rate-limited 1회/30s). 캐싱: 서버 AtomicReference 5분 TTL |

### 편집 presence (충돌 예방 UX)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/admin/editing-sessions/heartbeat` | 편집 세션 생성/갱신 — body `{resourceType, resourceId}`, TTL 60s, 204 |
| DELETE | `/api/admin/editing-sessions` | 편집 세션 즉시 해제 — body `{resourceType, resourceId}`, 204 |
| GET | `/api/admin/editing-sessions?resourceType=&resourceId=` | 해당 리소스의 현재 편집자 목록 (본인 제외) |

지원 resourceType: `persona`, `category`, `categoryRule`, `rssSource`. 그 외 값은 400.
응답 요소: `{userId, displayName, startedAt}`. 편집 모달 open 시 30초 간격 heartbeat, 모달 close 시 DELETE.

---

## 유저 API (`/api/user/*`)

### 구독 신청
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/user/requests` | 내 신청 목록 |
| POST | `/api/user/requests` | 새 신청 |
| POST | `/api/user/requests/{id}/withdraw` | 대기 중 신청 철회 |
| POST | `/api/user/requests/{id}/unsubscribe` | 승인된 구독 해제 |
| DELETE | `/api/user/requests/{id}/remove` | 반려/철회 신청 삭제 |
| POST | `/api/user/requests/wizard-ownership` | 위자드 생성 리소스 등록 |
| POST | `/api/user/requests/rss-sources` | 추가 RSS 소스 등록 |

### 구독 설정
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/user/subscriptions/{requestId}/preferences` | 구독 설정 조회 |
| PUT | `/api/user/subscriptions/{requestId}/preferences` | 구독 설정 수정 |

### 주제 둘러보기
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/user/categories/browse` | 구독 가능한 주제 목록 |
| POST | `/api/user/categories/{categoryId}/subscribe` | DM 구독 |

### 발송 스케줄
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/user/delivery-schedule` | 내 발송 스케줄 |
| PUT | `/api/user/delivery-schedule` | 스케줄 수정 |

### 기사 & 브리핑
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/user/briefing/today` | 오늘 브리핑 |
| GET | `/api/user/history/articles` | 기사 검색 (페이지네이션, 필터) |
| GET | `/api/user/history/articles/{summaryId}` | 기사 상세 |
| POST | `/api/user/history/articles/{summaryId}/bookmark` | 북마크 토글 |
| GET | `/api/user/articles/top` | 인기 기사 |

### 통계
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/user/stats/monthly` | 월간 통계. `itemsSent`는 `delivery_log` SENT 기준(실제 발송 성공). `itemsCollected`/`itemsSummarized`는 카테고리 파이프라인 누계 |
| GET | `/api/user/reports/monthly.csv` | CSV 다운로드 |
| GET | `/api/user/keywords/trend` | 키워드 트렌드 |
| GET | `/api/user/competitors/timeline` | 경쟁사 뉴스 타임라인 |
| GET | `/api/user/competitors/snapshot` | 경쟁사 뉴스 스냅샷 |
| GET | `/api/user/competitors/sov` | Share of Voice 분석 |

### 셋업 (위자드)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/user/setup/categories` | 카테고리 생성 |
| GET | `/api/user/setup/sources/curated` | 검증된 소스 목록 |
| POST | `/api/user/setup/sources` | 소스 생성 |
| GET | `/api/user/setup/preset-personas` | 프리셋 목록 |
| POST | `/api/user/setup/personas` | 커스텀 페르소나 생성 |
| POST | `/api/user/setup/slack/verify` | Slack 채널 검증 |

### 계정 관리
| 메서드 | 경로 | 설명 |
|--------|------|------|
| PATCH | `/api/user/account/slack` | Slack member ID 저장 (자동으로 conversations.open으로 DM 채널 획득) |
| POST | `/api/user/account/withdraw` | 셀프 탈퇴 (비밀번호 확인 필수) |
| GET | `/api/user/account/data-export?format=json\|csv` | 본인 개인정보 열람 (개인정보보호법 제35조). 하루 3회 제한, `PERSONAL_DATA_EXPORT` 감사 로그 기록. 응답은 `Content-Disposition: attachment`로 반환된다. |

### 이벤트
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/user/events` | 이벤트 배치 전송 (분석용) |

---

## 퍼블릭 API (`/api/public/*`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/public/maintenance` | 점검 모드 상태 |
| GET | `/api/public/dev/login-shortcuts` | 개발용 로그인 단축키 (local 프로파일만) |

---

## 클릭 추적 API

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/track/click/slack/{sid}?url={originalUrl}` | 없음 | Slack 링크 클릭 태깅 (Phase 3 PR #428). `sid`=summaryId, `url`=원 기사 URL. `user_events(event_type='article_click', source='slack')` 저장 후 302 redirect. Referer 헤더 `*.slack.com` fallback 도 `source='slack'` 로 태깅 |
| GET | `/api/track/click?sid={summaryId}&url={originalUrl}` | 없음 | 레거시 경로 — 기존 발송 메시지 URL 보호용 backward compat. Referer 로 `source` 추정, 실패 시 null. 신규 URL 빌더는 path 기반(`/slack/{sid}`)을 사용한다 |

`source` 필드는 allowlist 정규화 — 허용: `slack`. 그 외 값은 null 로 저장 (오염 방지).

---

## 관찰(Observability) API

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/client-errors` | 세션 또는 Bearer | 프론트 ErrorBoundary가 포착한 렌더링 예외를 보고 (202 Accepted, 본문 없음). IP 기준 분당 30회 제한, 초과 시 429. |

요청 바디 (`ClientErrorReport`):

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `message` | string | 필수, 1~2000자 | 에러 메시지 |
| `stack` | string? | 최대 10000자 | JS Error.stack |
| `componentStack` | string? | 최대 10000자 | React errorInfo.componentStack |
| `url` | string? | 최대 500자 | 에러 발생 페이지 경로(+쿼리) |
| `userAgent` | string? | 최대 500자 | 브라우저 User-Agent |
| `reactErrorCode` | string? | 최대 50자 | React Minified 에러 코드 |
| `tags` | object? | 최대 20 entry, key/value 각 100자 | 부가 태그 맵 |

현재는 관찰 용도로만 쓰이며 서버는 warn 로그만 남긴다 (DB 저장 없음).

---

## 공통 패턴

**페이지네이션**: `?page=0&size=30` → `{ content: [], totalCount, page, size }`

**검색**: 카테고리/소스 목록의 검색 파라미터는 `?q=검색어` (PR #188에서 `search` → `q`로 변경)

**날짜**: ISO 8601 (`2026-03-20`, `2026-03-20T09:00:00Z`)

**에러 응답**: `{ timestamp, path, status, error, message?, requestId }`

**일괄 처리**: `{ ids: string[], reviewNote?: string }` → `BulkActionResponse{ succeeded: string[], failed: [{id, reason}] }`

**Idempotency-Key 헤더** (optional, F1 PR 3 도입):

아래 관리자 수정 엔드포인트는 `Idempotency-Key: <uuid>` 헤더를 받으면 같은 키의 재요청에 대해 DB 를 다시 건드리지 않고 첫 응답을 그대로 재사용한다 (TTL 1시간).

- `PUT /api/admin/personas/{id}`
- `PUT /api/admin/categories/{id}`
- `PUT /api/admin/category-rules/{categoryId}`
- `PUT /api/admin/sources/{id}`

프론트 (`kyInstance.ts`) 는 PUT/PATCH/POST/DELETE 요청에 자동으로 UUID v4 헤더를 부여한다. 헤더를 제공하지 않으면 멱등성 보장 없이 기존 동작대로 처리된다. 저장소 Redis 장애 시 fail-open (멱등성 포기 + 정상 supplier 실행). 상세 정책은 `AGENTS.md` §26 참고.

---

**변경 이력 응답 스키마** (F1 PR 4 도입):

GET `{resource}/{id}/history` 응답은 최신순 JSON 배열.

```json
[
  {
    "revisionId": "c5e9a0c0-...",
    "revisionNumber": 3,
    "editorId": "admin-user",
    "editorName": "관리자",
    "changedFields": ["name", "systemPrompt"],
    "createdAt": "2026-04-17T10:00:00Z"
  }
]
```

- `editorName`: UUID/`system` 등 비식별 actor는 `"관리자"`/`"시스템"`으로 익명화된다.
- `changedFields`: 변경 필드 이름 배열. 빈 배열 허용.
- `snapshot`은 목록 응답에 포함하지 않는다 (크기 절감). 복원은 `revisionId`만 전달.

POST `{resource}/{id}/restore` 요청 바디:

```json
{
  "revisionId": "c5e9a0c0-...",
  "expectedUpdatedAt": "2026-04-17T10:05:00Z"
}
```

- `expectedUpdatedAt`: 현재 엔티티 `updatedAt` 값. 불일치 시 `409 Conflict` (ADR-019의 StaleEdit 정책 동일 적용).
- 존재하지 않는 `revisionId` 또는 다른 리소스의 revision 전달 시 `404 Not Found`.
- 복원된 엔티티(각 도메인 타입)가 응답으로 반환되며, 복원 자체도 새 revision으로 append된다.
- retention: Phase 2 F9(`DataCleanupScheduler`)에서 통합 처리 예정. 이 PR 시점에는 cleanup 없음. 상세는 `docs/ADR.md` ADR-020.
