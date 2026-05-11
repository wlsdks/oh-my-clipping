package com.clipping.mcpserver.service.dto

/**
 * 점수 분포 — 히스토그램 단일 버킷.
 *
 * - [range] 은 `"0.0-0.1"` 처럼 소수점 한 자리 두 개를 하이픈으로 연결한 표시용 라벨이다.
 *   UI 의 X 축 라벨에 그대로 사용한다.
 * - [count] 는 해당 버킷에 속하는 summary 수. 음수가 될 수 없다.
 */
data class ScoreDistributionBucket(
    val range: String,
    val count: Int,
)

/**
 * `batch_summaries.importance_score` 의 10 버킷 히스토그램 집계 DTO.
 *
 * 관리자 대시보드 `/admin/review-queue` 의 "점수 분포" 차트 렌더링용이다.
 * 버킷 경계는 `[0.0, 0.1), [0.1, 0.2), …, [0.9, 1.0]` 으로 고정한다 — 마지막 버킷만 양 끝 포함.
 * 집계 대상은 주어진 `days` 기간 내 `created_at` 기준 summary 이며, [categoryId] 가 지정되면
 * 해당 카테고리로 필터링된다.
 *
 * 필드 의미:
 *  - [buckets]: 항상 10 개. summary 가 0 건이어도 `count=0` 버킷 10 개를 반환한다.
 *  - [totalCount]: 집계 대상 전체 summary 수.
 *  - [medianScore]: 중앙값 근사. 버킷 누적 합산 후 해당 버킷의 중심점(`(idx + 0.5) / 10`) 을 반환한다.
 *    데이터가 없으면 `0f`.
 *  - [meanScore]: `AVG(importance_score)`. 데이터가 없으면 `0f`.
 */
data class ScoreDistribution(
    val buckets: List<ScoreDistributionBucket>,
    val totalCount: Int,
    val medianScore: Float,
    val meanScore: Float,
)
