/**
 * 자동 제외 감사(auto-exclude audit) 드로어 도메인 타입.
 *
 * `categoryRule.ts` 는 dry-run / 감사 뷰 / 복구를 한 파일에 섞어 뒀지만, 상세 드로어에서
 * 기사 본문 · 원문 링크 · 룰 근거를 렌더링하게 되면서 타입 경계가 드로어 중심으로 커진다.
 * 이 파일은 드로어가 사용하는 단일 항목/리스트/복구 결과 타입을 모은 도메인 전용 파일이다.
 *
 * 원본 `types/categoryRule.ts` 는 한 릴리스 동안 type-only re-export stub 로 유지해
 * 기존 import 경로가 깨지지 않도록 한다.
 *
 * 백엔드 대응:
 *  - `GET  /api/admin/review-items/auto-excluded` → {@link AutoExcludedResponse}
 *  - `POST /api/admin/review-items/{summaryId}/restore-to-review` → {@link RestoreFromAutoExcludeResult}
 *  - `GET  /api/admin/category-rules/{categoryId}` → {@link CategoryRule} (드로어의 룰 근거 섹션)
 */

/**
 * 자동 제외 감사 화면의 단일 항목.
 *
 * `reason` 은 기계 판독 접두어 포함 (예: `"rule:event_type_blacklist"` / `"rule:zero_signal"`).
 * `sourceUrl`/`sourceName`/`publishedAt` 은 LEFT JOIN 방어로 nullable — UI 는 null 이면 해당 줄 생략.
 * `translatedTitle` 은 번역 전인 항목에서 null.
 * `eventType`/`sentiment` 는 분류 실패 시 null — zero_signal 룰 근거 렌더에서 데이터 불일치 경고로 처리.
 */
export interface AutoExcludedItem {
  summaryId: string;
  title: string;
  originalTitle: string;
  translatedTitle: string | null;
  categoryId: string;
  categoryName: string;
  score: number;
  /** `rule:{ruleName}` 형태. 예: `"rule:event_type_blacklist"`. */
  reason: string;
  /** ISO-8601. `clipping_review_items.reviewed_at` 직렬화. */
  excludedAt: string;
  /** batch_summaries.summary — 드로어 본문 렌더. */
  summary: string;
  /** rss_items.link — 원문 링크. rss_items 조인 실패 시 null. */
  sourceUrl: string | null;
  /** rss_sources.name — 언론사/피드명. rss_sources 조인 실패 시 null. */
  sourceName: string | null;
  /** rss_items.published_at — ISO-8601. 원문 발행 시각. */
  publishedAt: string | null;
  /** batch_summaries.event_type — LLM 분류 결과. 분류 실패 시 null. */
  eventType: string | null;
  /** batch_summaries.sentiment — POSITIVE/NEUTRAL/NEGATIVE. 분류 실패 시 null. */
  sentiment: string | null;
}

/**
 * `GET /api/admin/review-items/auto-excluded` 응답.
 *
 * 페이지네이션된 `items` + 전체 건수 + reason 별 분포.
 * `reasonBreakdown` 은 필터(카테고리/기간) 기준 집계이므로 UI 에서 차트/요약에 직접 사용 가능.
 */
export interface AutoExcludedResponse {
  items: AutoExcludedItem[];
  totalCount: number;
  reasonBreakdown: Record<string, number>;
}

/**
 * 자동 제외된 항목을 REVIEW 로 복구한 결과.
 *
 * 백엔드가 성공 시에만 200 응답을 주므로 별도 `ok` 필드는 필요없다.
 * `newStatus` 는 항상 리터럴 `"REVIEW"` — 복구 외 상태 전이는 발생하지 않는다.
 */
export interface RestoreFromAutoExcludeResult {
  summaryId: string;
  newStatus: "REVIEW";
}

/**
 * 드로어의 룰 근거 섹션에서 카테고리 정책(차단 이벤트 타입 / 필수 키워드)을 렌더하기 위한 타입.
 *
 * `types/category.ts` 에 이미 같은 이름의 전체 DTO(CategoryRule)가 있으므로 중복 선언을 피하고
 * 거기서 re-export 한다. 드로어가 실제로 사용하는 필드는 `excludeEventTypes` / `includeKeywords`
 * 두 개지만, 서버 응답 계약을 유지하기 위해 전체 타입을 그대로 노출한다.
 */
export type { CategoryRule } from "./category";
