package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.BulkRevertRequest
import com.ohmyclipping.admin.dto.BulkReviewRequest
import com.ohmyclipping.admin.dto.ReviewItemActionRequest
import com.ohmyclipping.admin.dto.ReviewItemAuditResponse
import com.ohmyclipping.admin.dto.ReviewItemResponse
import com.ohmyclipping.service.dto.AutoExcludedResponse
import com.ohmyclipping.service.dto.RestoreFromAutoExcludeResult
import com.ohmyclipping.service.dto.ReviewPolicyStatusResponse
import com.ohmyclipping.service.dto.ReviewStatsResponse
import com.ohmyclipping.service.dto.ReviewSummaryResponse
import com.ohmyclipping.service.dto.ScoreDistribution
import com.ohmyclipping.service.AdminReviewQueueService
import com.ohmyclipping.service.ReviewQueueAuditItem
import com.ohmyclipping.service.ReviewQueueItem
import com.ohmyclipping.service.dto.BulkActionResponse
import com.ohmyclipping.service.dto.ReviewItemActionResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 포함/검토/제외 운영을 위한 검토함 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/admin/review-items")
class ReviewQueueAdminController(
    private val adminReviewQueueService: AdminReviewQueueService
) {

    /**
     * 미발송 검토함의 카테고리별 상태 집계를 반환합니다.
     */
    @GetMapping("/summary")
    fun getSummary(): ReviewSummaryResponse = adminReviewQueueService.getSummary()

    /**
     * 지정 기간의 AI 정확도 통계를 반환합니다.
     * period: 7d(기본) | 30d
     */
    @GetMapping("/stats")
    fun getStats(@RequestParam(defaultValue = "7d") period: String): ReviewStatsResponse =
        adminReviewQueueService.getReviewStats(period)

    /**
     * 관리자 대시보드용 카테고리별 리뷰 정책 현황을 반환합니다.
     *
     * 응답에는 활성 카테고리 전체의 정책 임계값, pending 누적, 7일 처리 지표, 평균 점수,
     * event_type 분포, 마지막 처리 시각이 포함됩니다.
     */
    @GetMapping("/policy-status")
    fun getPolicyStatus(): ReviewPolicyStatusResponse =
        adminReviewQueueService.getPolicyStatus()

    /**
     * importance_score 의 10 버킷 히스토그램 분포를 반환합니다.
     *
     * @param categoryId 특정 카테고리 필터 (null 이면 전체)
     * @param days 집계 기간(일). 서비스 레이어에서 1~90 으로 clamp 합니다.
     */
    @GetMapping("/score-distribution")
    fun getScoreDistribution(
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false, defaultValue = "7") days: Int,
    ): ScoreDistribution =
        adminReviewQueueService.getScoreDistribution(categoryId, days)

    /**
     * 검토함 목록을 조회합니다.
     *
     * perCategory(옵션): 전체(categoryId 없음) 조회 시 카테고리별 top-N 샘플링을 적용한다.
     * 각 카테고리에서 priority DESC 순으로 최대 perCategory개씩 뽑은 뒤 limit까지 정렬한다.
     * categoryId가 지정된 경우 perCategory는 무시된다(단일 카테고리 범위 내 의미가 없음).
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
        @RequestParam(required = false) perCategory: Int?
    ): List<ReviewItemResponse> =
        adminReviewQueueService.listReviewItems(categoryId, status, limit, perCategory)
            .map { it.toResponse() }

    /**
     * 검토함 단건의 상태 변경 이력을 조회합니다.
     */
    @GetMapping("/{summaryId}/audits")
    fun listAudits(
        @PathVariable summaryId: String,
        @RequestParam(required = false, defaultValue = "30") limit: Int
    ): List<ReviewItemAuditResponse> =
        adminReviewQueueService.listAudits(summaryId, limit).map { it.toResponse() }

    /**
     * 검토 항목을 포함 상태로 승인합니다.
     */
    @PostMapping("/{summaryId}/approve")
    fun approve(
        @PathVariable summaryId: String,
        @RequestBody(required = false) request: ReviewItemActionRequest?
    ): ReviewItemActionResponse {
        adminReviewQueueService.approve(summaryId, request?.reason, request?.reviewedBy)
        return ReviewItemActionResponse(summaryId = summaryId, status = "INCLUDE")
    }

    /**
     * 검토 항목을 제외 상태로 처리합니다.
     */
    @PostMapping("/{summaryId}/exclude")
    fun exclude(
        @PathVariable summaryId: String,
        @RequestBody(required = false) request: ReviewItemActionRequest?
    ): ReviewItemActionResponse {
        adminReviewQueueService.exclude(summaryId, request?.reason, request?.reviewedBy)
        return ReviewItemActionResponse(summaryId = summaryId, status = "EXCLUDE")
    }

    /**
     * 검토 항목을 REVIEW 상태로 되돌립니다.
     */
    @PostMapping("/{summaryId}/review")
    fun markReview(
        @PathVariable summaryId: String,
        @RequestBody(required = false) request: ReviewItemActionRequest?
    ): ReviewItemActionResponse {
        adminReviewQueueService.markReview(summaryId, request?.reason, request?.reviewedBy)
        return ReviewItemActionResponse(summaryId = summaryId, status = "REVIEW")
    }

    /**
     * 정책 룰이 자동 EXCLUDE 한 항목을 감사용으로 조회합니다.
     *
     * 응답에는 기간/카테고리/사유 필터에 맞는 최신 항목과 전체 건수, reason 별 breakdown 이 포함됩니다.
     * 관리자는 이 뷰에서 오탐(잘못 걸린 기사) 을 발견하면 `/restore-to-review` 로 복구할 수 있습니다.
     *
     * @param categoryId 카테고리 필터 (옵션). blank/null 이면 전체.
     * @param reason reason 접두어 (예: `rule:` 또는 `rule:event_type_blacklist`). 옵션.
     * @param days 최근 N 일 (기본 7, 서비스 레이어에서 1..90 으로 clamp).
     * @param page 0-based 페이지 (기본 0).
     * @param size 페이지 크기 (기본 20, 최대 100).
     */
    @GetMapping("/auto-excluded")
    fun listAutoExcluded(
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false, defaultValue = "7") days: Int,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): AutoExcludedResponse =
        adminReviewQueueService.listAutoExcluded(
            categoryId = categoryId,
            reason = reason,
            days = days,
            page = page,
            size = size,
        )

    /**
     * 정책 룰이 자동 EXCLUDE 한 항목을 관리자가 REVIEW 로 복구합니다.
     *
     * 보호 조건:
     *  - 존재하지 않는 summary → 404.
     *  - `policy-auto` 가 아닌 항목이거나 EXCLUDE 가 아닌 항목 → 409 Conflict.
     *
     * 감사 이력에 reason = `manual_restore_from_auto_exclude` 로 전이가 기록됩니다.
     */
    @PostMapping("/{summaryId}/restore-to-review")
    fun restoreFromAutoExclude(
        @PathVariable summaryId: String,
        authentication: Authentication,
    ): RestoreFromAutoExcludeResult =
        adminReviewQueueService.restoreFromAutoExclude(summaryId, authentication.name)

    /**
     * 다수의 검토 항목을 한 번에 포함 상태로 승인합니다.
     */
    @PostMapping("/bulk-approve")
    fun bulkApprove(@RequestBody request: BulkReviewRequest): BulkActionResponse =
        adminReviewQueueService.bulkApprove(request.ids, request.reviewNote)

    /**
     * 다수의 검토 항목을 한 번에 제외 상태로 처리합니다.
     */
    @PostMapping("/bulk-exclude")
    fun bulkExclude(@RequestBody request: BulkReviewRequest): BulkActionResponse =
        adminReviewQueueService.bulkExclude(request.ids, request.reviewNote)

    /**
     * 다수의 검토 항목을 각자의 이전 상태로 되돌립니다.
     * 일괄 REVIEW 복원이 아닌 항목별 previousStatus를 각각 적용합니다.
     */
    @PostMapping("/bulk-revert")
    fun bulkRevert(@RequestBody request: BulkRevertRequest): BulkActionResponse =
        adminReviewQueueService.bulkRevert(request.reverts)

    private fun ReviewQueueItem.toResponse() = ReviewItemResponse(
        summaryId = summaryId,
        categoryId = categoryId,
        categoryName = categoryName,
        title = title,
        summary = summary,
        sourceLink = sourceLink,
        keywords = keywords,
        importanceScore = importanceScore,
        suggestedStatus = suggestedStatus.name,
        currentStatus = currentStatus.name,
        statusReason = statusReason,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt?.toString(),
        priorityScore = priorityScore,
        priorityLabel = priorityLabel,
        createdAt = createdAt.toString()
    )

    private fun ReviewQueueAuditItem.toResponse() = ReviewItemAuditResponse(
        id = id,
        summaryId = summaryId,
        categoryId = categoryId,
        fromStatus = fromStatus?.name,
        toStatus = toStatus.name,
        reason = reason,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt?.toString(),
        createdAt = createdAt.toString()
    )
}
