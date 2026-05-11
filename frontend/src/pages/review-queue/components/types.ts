/**
 * AI 추천 승인 패널 / 스팟체크 다이얼로그가 다루는 경량 리뷰 항목 요약.
 *
 * `ReviewQueueItem` 의 전체 필드를 필요로 하지 않는 컴포넌트용 축약 타입이다.
 * 호출부에서 `{ summaryId, title: item.title, score: item.importanceScore,
 * eventType: item.statusReason ?? null }` 형태로 매핑해서 넘긴다.
 */
export interface ReviewItemSummary {
  summaryId: string;
  title: string;
  /** 중요도 점수 (0.0 ~ 1.0) */
  score: number;
  /** event_type 또는 유사 분류 라벨. 없으면 null */
  eventType: string | null;
}
