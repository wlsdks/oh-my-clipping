package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.CategoryRuleDryRunRequest
import com.ohmyclipping.admin.dto.CategoryRuleResponse
import com.ohmyclipping.admin.dto.ExcludedItemsResponse
import com.ohmyclipping.admin.dto.RuleStatsResponse
import com.ohmyclipping.admin.dto.UpdateCategoryRuleRequest
import com.ohmyclipping.model.CategoryRule
import com.ohmyclipping.service.AdminCategoryRuleService
import com.ohmyclipping.service.AdminReviewQueueService
import com.ohmyclipping.service.CategoryRuleStatsService
import com.ohmyclipping.service.dto.RuleDryRunResult
import com.ohmyclipping.support.IdempotencyKeyService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 카테고리별 선별 운영 규칙 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/admin/category-rules")
class CategoryRuleAdminController(
    private val adminCategoryRuleService: AdminCategoryRuleService,
    private val categoryRuleStatsService: CategoryRuleStatsService,
    private val adminReviewQueueService: AdminReviewQueueService,
    private val idempotencyKeyService: IdempotencyKeyService
) {

    /**
     * 카테고리별 분류 통계를 조회합니다.
     */
    @GetMapping("/stats")
    fun getStats(@RequestParam(defaultValue = "7") days: Int): RuleStatsResponse {
        val result = categoryRuleStatsService.getRuleStats(days)
        return RuleStatsResponse(
            totalIncluded = result.totalIncluded,
            totalReview = result.totalReview,
            totalExcluded = result.totalExcluded,
            perCategory = result.perCategory.map { item ->
                RuleStatsResponse.CategoryStat(
                    categoryId = item.categoryId,
                    categoryName = item.categoryName,
                    included = item.included,
                    review = item.review,
                    excluded = item.excluded,
                    hasRule = item.hasRule
                )
            }
        )
    }

    /**
     * 특정 카테고리의 제외 항목을 조회합니다.
     */
    @GetMapping("/{categoryId}/excluded-items")
    fun getExcludedItems(
        @PathVariable categoryId: String,
        @RequestParam(defaultValue = "5") limit: Int
    ): ExcludedItemsResponse {
        val result = categoryRuleStatsService.getExcludedItems(categoryId, limit)
        return ExcludedItemsResponse(
            total = result.total,
            items = result.items.map { item ->
                ExcludedItemsResponse.ExcludedItem(
                    title = item.title,
                    reason = item.reason,
                    matchedKeyword = item.matchedKeyword,
                    score = item.score,
                    excludedAt = item.excludedAt
                )
            }
        )
    }

    /**
     * 카테고리 운영 규칙을 조회합니다.
     */
    @GetMapping("/{categoryId}")
    fun get(@PathVariable categoryId: String): CategoryRuleResponse =
        adminCategoryRuleService.getCategoryRule(categoryId).toResponse()

    /**
     * 카테고리 운영 규칙을 수정합니다.
     *
     * `Idempotency-Key` 헤더가 제공되면 같은 키의 재전송은 DB 를 다시 건드리지 않고 첫 응답을 그대로 재사용한다.
     */
    @PutMapping("/{categoryId}")
    fun update(
        @PathVariable categoryId: String,
        @RequestBody request: UpdateCategoryRuleRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        authentication: Authentication
    ): CategoryRuleResponse =
        idempotencyKeyService.executeIfKeyPresent(
            actor = authentication.name,
            key = idempotencyKey,
            resultClass = CategoryRuleResponse::class.java
        ) {
            adminCategoryRuleService.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = request.includeKeywords,
                excludeKeywords = request.excludeKeywords,
                riskTags = request.riskTags,
                includeThreshold = request.includeThreshold,
                reviewThreshold = request.reviewThreshold,
                uncertainToReview = request.uncertainToReview,
                autoExcludeEnabled = request.autoExcludeEnabled,
                updatedBy = request.updatedBy,
                autoApproveThreshold = request.autoApproveThreshold,
                clearAutoApproveThreshold = request.clearAutoApproveThreshold == true,
                excludeEventTypes = request.excludeEventTypes,
                expectedUpdatedAt = request.expectedUpdatedAt
            ).toResponse()
        }

    /**
     * 카테고리 운영 규칙 dry-run 시뮬레이션.
     *
     * "이 룰을 저장하면 최근 기간에 몇 건이 자동 제외될지" 를 관리자 UI 가 저장 전에 미리 확인하기 위한
     * read-only 엔드포인트. DB 에 어떤 결정/감사 이력도 쓰지 않는다.
     *
     * 요청 본문의 `excludeEventTypes` 는 null/미지정 시 빈 리스트로 해석 — 룰 비활성 상태의 효과를
     * 미리 보는 용도로도 쓰인다 (`wouldAutoExclude = 0` 이 기대값).
     */
    @PostMapping("/{categoryId}/dry-run")
    fun dryRun(
        @PathVariable categoryId: String,
        @RequestBody request: CategoryRuleDryRunRequest,
    ): RuleDryRunResult {
        // 서비스 기본값 활용을 위해 null 이면 기본 시그니처를 그대로 호출한다
        val excludeTypes = request.excludeEventTypes ?: emptyList()
        return if (request.days != null && request.maxSamples != null) {
            adminReviewQueueService.dryRunRule(
                categoryId = categoryId,
                proposedExcludeEventTypes = excludeTypes,
                days = request.days,
                maxSamples = request.maxSamples,
            )
        } else if (request.days != null) {
            adminReviewQueueService.dryRunRule(
                categoryId = categoryId,
                proposedExcludeEventTypes = excludeTypes,
                days = request.days,
            )
        } else if (request.maxSamples != null) {
            adminReviewQueueService.dryRunRule(
                categoryId = categoryId,
                proposedExcludeEventTypes = excludeTypes,
                maxSamples = request.maxSamples,
            )
        } else {
            adminReviewQueueService.dryRunRule(
                categoryId = categoryId,
                proposedExcludeEventTypes = excludeTypes,
            )
        }
    }

    private fun CategoryRule.toResponse() = CategoryRuleResponse(
        categoryId = categoryId,
        includeKeywords = includeKeywords,
        excludeKeywords = excludeKeywords,
        riskTags = riskTags,
        excludeEventTypes = excludeEventTypes,
        includeThreshold = includeThreshold,
        reviewThreshold = reviewThreshold,
        uncertainToReview = uncertainToReview,
        autoExcludeEnabled = autoExcludeEnabled,
        autoApproveThreshold = autoApproveThreshold,
        revision = revision,
        updatedBy = updatedBy,
        updatedAt = updatedAt.toString()
    )
}
