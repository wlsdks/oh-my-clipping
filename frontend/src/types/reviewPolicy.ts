/**
 * 카테고리별 리뷰 정책 현황 — `GET /api/admin/review-items/policy-status` 응답 타입.
 *
 * 백엔드 DTO(`ReviewPolicyStatus`) 와 1:1 매핑. 필드 의미는 서버 KDoc 참조.
 * 임계값(Double) 과 평균/점수(Float) 는 JSON 직렬화 후 모두 `number` 로 전달된다.
 */
export interface ReviewPolicyStatus {
  categoryId: string;
  categoryName: string;
  /** INCLUDE 자동 승인 임계값. null = 비활성 */
  autoApproveThreshold: number | null;
  /** REVIEW 하한 임계값. null = 규칙 미등록 */
  reviewThreshold: number | null;
  /** 현재 REVIEW 상태 누적 건수 */
  pendingReviewCount: number;
  /** 최근 7일 처리 건수(INCLUDE + EXCLUDE) */
  last7DaysProcessed: number;
  /** 최근 7일 policy-auto 처리 건수 */
  last7DaysAutoApproved: number;
  /** 최근 7일 수동 처리 건수 */
  last7DaysManuallyReviewed: number;
  /** 리뷰 항목 요약의 importance_score 평균 (0.0 ~ 1.0) */
  avgScore: number;
  /** event_type 분포 — NULL/빈 문자열은 "NULL" 키 */
  eventTypeDistribution: Record<string, number>;
  /** 가장 최근 처리 시각 ISO-8601. 이력 없으면 null */
  lastReviewedAt: string | null;
}

export interface ReviewPolicyStatusResponse {
  categories: ReviewPolicyStatus[];
  /** 응답 조립 시각 ISO-8601 */
  generatedAt: string;
}

/**
 * 점수 분포 단일 버킷.
 *
 * `range` 는 `"0.0-0.1"` 처럼 서버에서 포맷된 X축 라벨.
 */
export interface ScoreDistributionBucket {
  range: string;
  count: number;
}

/**
 * `GET /api/admin/review-items/score-distribution` 응답 타입.
 *
 * `buckets` 는 항상 10개. summary 가 0건이어도 `count=0` 버킷 10개가 반환된다.
 * `medianScore`/`meanScore` 는 데이터가 없으면 0.
 */
export interface ScoreDistribution {
  buckets: ScoreDistributionBucket[];
  totalCount: number;
  medianScore: number;
  meanScore: number;
}
