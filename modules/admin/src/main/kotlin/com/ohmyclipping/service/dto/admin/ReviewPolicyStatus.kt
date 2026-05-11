package com.ohmyclipping.service.dto.admin

import java.time.Instant

/**
 * 카테고리별 리뷰 정책 현황 집계 DTO.
 *
 * 관리자 대시보드(`/admin/review-queue`)에서 전체 카테고리의 리뷰 정책 상태를 한눈에
 * 파악하기 위해 사용한다. 수치는 모두 해당 카테고리 단위 집계이며, DB 원본 컬럼 타입에
 * 맞춰 `Double` 을 사용한다 (`clipping_category_rules.*_threshold` 는 DOUBLE PRECISION,
 * `batch_summaries.importance_score` 는 FLOAT).
 *
 * 필드 의미:
 *  - [autoApproveThreshold]: INCLUDE 제안을 자동 승인할 importance 임계값. NULL = 비활성.
 *  - [reviewThreshold]: REVIEW 버킷 하한 임계값. NULL = 규칙이 아직 등록되지 않음.
 *  - [pendingReviewCount]: 현재 상태가 `REVIEW` 인 리뷰 항목 수(전체 누적, 기간 제한 없음).
 *  - [last7DaysProcessed]: 최근 7일간 `reviewed_at` 이 채워진 항목 수 (INCLUDE + EXCLUDE 합계).
 *  - [last7DaysAutoApproved]: 최근 7일간 `reviewed_by = 'policy-auto'` 로 처리된 항목 수.
 *  - [last7DaysManuallyReviewed]: 최근 7일간 policy-auto 가 아닌 사람/토큰이 처리한 항목 수.
 *  - [avgScore]: 해당 카테고리의 리뷰 항목(clipping_review_items) 에 연결된 요약 importance_score 평균.
 *    데이터가 없으면 0.0f.
 *  - [eventTypeDistribution]: 동일 요약 기반 `batch_summaries.event_type` 분포. NULL/빈 문자열은 "NULL".
 *  - [lastReviewedAt]: 해당 카테고리의 가장 최근 `reviewed_at`. 처리 이력이 없으면 null.
 */
data class ReviewPolicyStatus(
    val categoryId: String,
    val categoryName: String,
    val autoApproveThreshold: Double?,
    val reviewThreshold: Double?,
    val pendingReviewCount: Int,
    val last7DaysProcessed: Int,
    val last7DaysAutoApproved: Int,
    val last7DaysManuallyReviewed: Int,
    val avgScore: Float,
    val eventTypeDistribution: Map<String, Int>,
    val lastReviewedAt: Instant?,
)

/**
 * 여러 카테고리 리뷰 정책 현황 응답 래퍼.
 *
 * [generatedAt] 은 응답 조립 시점이며, UI 에서 캐시 신선도 표시에 사용한다.
 */
data class ReviewPolicyStatusResponse(
    val categories: List<ReviewPolicyStatus>,
    val generatedAt: Instant,
)
