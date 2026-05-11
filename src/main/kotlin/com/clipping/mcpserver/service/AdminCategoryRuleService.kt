package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.StaleEditInfo
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.model.DeliveryPreset
import com.clipping.mcpserver.model.EntityRevisionResourceType
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AdminCategoryRuleService(
    private val categoryStore: CategoryStore,
    private val categoryRuleStore: CategoryRuleStore,
    private val entityRevisionRecorder: EntityRevisionRecorder
) {

    /**
     * 카테고리 운영 규칙을 조회합니다.
     * 규칙이 아직 저장되지 않았다면 기본값을 반환합니다.
     */
    fun getCategoryRule(categoryId: String): CategoryRule {
        ensureCategoryExists(categoryId)
        return categoryRuleStore.findByCategoryId(categoryId) ?: defaultRule(categoryId)
    }

    /**
     * 카테고리 운영 규칙을 저장합니다.
     * 저장 시 revision을 1 증가시켜 운영 변경 이력을 추적합니다.
     *
     * [expectedUpdatedAt]이 주어지면 낙관적 잠금을 적용해 다른 관리자의 저장과 충돌할 때
     * [ConflictException]을 던집니다. null이면 기존 upsert 경로로 저장합니다.
     */
    @Suppress("LongParameterList")
    fun updateCategoryRule(
        categoryId: String,
        includeKeywords: List<String>?,
        excludeKeywords: List<String>?,
        riskTags: List<String>?,
        includeThreshold: Double?,
        reviewThreshold: Double?,
        uncertainToReview: Boolean?,
        autoExcludeEnabled: Boolean?,
        updatedBy: String?,
        deliveryDays: List<String>? = null,
        deliveryHour: Int? = null,
        deliveryPreset: String? = null,
        autoApproveThreshold: Double? = null,
        clearAutoApproveThreshold: Boolean = false,
        // V132: 자동 EXCLUDE 대상 event_type 블랙리스트. null 이면 기존 값 유지.
        excludeEventTypes: List<String>? = null,
        expectedUpdatedAt: Instant? = null
    ): CategoryRule {
        val current = getCategoryRule(categoryId)
        val normalizedInclude = includeKeywords?.let { normalizeTokens(it) } ?: current.includeKeywords
        val normalizedExclude = excludeKeywords?.let { normalizeTokens(it) } ?: current.excludeKeywords
        val normalizedRiskTags = riskTags?.let { normalizeTokens(it) } ?: current.riskTags
        // event_type 은 enum 유사 토큰이라 대소문자까지 정규화한다 (기존 키워드는 원문 보존).
        val normalizedExcludeEventTypes = excludeEventTypes?.let { normalizeEventTypes(it) }
            ?: current.excludeEventTypes
        val nextIncludeThreshold = includeThreshold ?: current.includeThreshold
        val nextReviewThreshold = reviewThreshold ?: current.reviewThreshold

        ensureValid(nextIncludeThreshold in 0.0..1.0) { "includeThreshold must be between 0 and 1" }
        ensureValid(nextReviewThreshold in 0.0..1.0) { "reviewThreshold must be between 0 and 1" }
        ensureValid(nextIncludeThreshold >= nextReviewThreshold) {
            "includeThreshold must be greater than or equal to reviewThreshold"
        }

        // 자동 승인 임계값은 명시적으로 clear 플래그가 올 때만 null로 되돌린다.
        // 그렇지 않고 값이 들어오면 범위 검증 후 적용한다. 둘 다 아니면 current 값을 유지한다.
        val resolvedAutoApprove = when {
            clearAutoApproveThreshold -> null
            autoApproveThreshold != null -> {
                ensureValid(autoApproveThreshold in 0.0..1.0) {
                    "autoApproveThreshold must be between 0 and 1"
                }
                autoApproveThreshold
            }
            else -> current.autoApproveThreshold
        }

        // 구독별 발송 스케줄 설정이 전달되면 반영한다.
        val resolvedDeliveryPreset = deliveryPreset
            ?.let { runCatching { DeliveryPreset.valueOf(it) }.getOrNull() }
            ?: current.deliveryPreset
        val resolvedDeliveryDays = deliveryDays ?: current.deliveryDays
        val resolvedDeliveryHour = deliveryHour ?: current.deliveryHour

        // revision은 store.updateWithExpectedUpdatedAt 내부에서 +1 된다.
        // upsert 경로에서는 아래에서 명시적으로 +1 한다.
        val nextRule = current.copy(
            includeKeywords = normalizedInclude,
            excludeKeywords = normalizedExclude,
            riskTags = normalizedRiskTags,
            excludeEventTypes = normalizedExcludeEventTypes,
            includeThreshold = nextIncludeThreshold,
            reviewThreshold = nextReviewThreshold,
            uncertainToReview = uncertainToReview ?: current.uncertainToReview,
            autoExcludeEnabled = autoExcludeEnabled ?: current.autoExcludeEnabled,
            deliveryDays = resolvedDeliveryDays,
            deliveryHour = resolvedDeliveryHour,
            deliveryPreset = resolvedDeliveryPreset,
            autoApproveThreshold = resolvedAutoApprove,
            updatedBy = updatedBy?.trim()?.ifBlank { null }
        )

        // 편집 충돌 감지를 위한 변경 필드 목록.
        val changedFields = collectChangedFieldNames(
            current = current,
            includeKeywords = includeKeywords,
            excludeKeywords = excludeKeywords,
            riskTags = riskTags,
            excludeEventTypes = excludeEventTypes,
            normalizedExcludeEventTypes = normalizedExcludeEventTypes,
            includeThreshold = includeThreshold,
            reviewThreshold = reviewThreshold,
            uncertainToReview = uncertainToReview,
            autoExcludeEnabled = autoExcludeEnabled,
            deliveryDays = deliveryDays,
            deliveryHour = deliveryHour,
            deliveryPreset = resolvedDeliveryPreset,
            autoApproveThreshold = resolvedAutoApprove,
            autoApproveTouched = clearAutoApproveThreshold || autoApproveThreshold != null
        )

        val saved = if (expectedUpdatedAt == null) {
            // 기대 시각이 없으면 기존 upsert(원자적 insert-or-update)로 저장하고 revision만 +1 시킨다.
            categoryRuleStore.upsert(nextRule.copy(revision = current.revision + 1))
        } else {
            categoryRuleStore.updateWithExpectedUpdatedAt(nextRule, expectedUpdatedAt)
                ?: run {
                    // 낙관적 잠금 실패 시 최신 상태를 다시 읽어 프론트로 전달한다.
                    val latest = categoryRuleStore.findByCategoryId(categoryId) ?: current
                    throw ConflictException(
                        message = "카테고리 규칙이 다른 관리자에 의해 변경되었습니다. " +
                            "새로고침 후 다시 저장해주세요.",
                        staleEditInfo = StaleEditInfo(
                            latestUpdatedAt = latest.updatedAt,
                            latestEditorName = latest.updatedBy?.takeIf { it.isNotBlank() } ?: "관리자",
                            changedFieldNames = changedFields
                        )
                    )
                }
        }
        // CategoryRule은 `categoryId`가 식별자 역할. 변경 이력도 해당 ID로 기록해 GET history와 일관시킨다.
        if (changedFields.isNotEmpty()) {
            entityRevisionRecorder.record(
                resourceType = EntityRevisionResourceType.CATEGORY_RULE,
                resourceId = saved.categoryId,
                editorId = updatedBy?.takeIf { it.isNotBlank() } ?: "system",
                editorDisplayName = null,
                changedFields = changedFields,
                entity = saved
            )
        }
        return saved
    }

    /**
     * 카테고리 규칙을 특정 revision snapshot 값으로 복원한다.
     */
    fun restoreFromSnapshot(
        categoryId: String,
        snapshot: CategoryRule,
        expectedUpdatedAt: Instant,
        actorUsername: String
    ): CategoryRule {
        ensureCategoryExists(categoryId)
        val current = getCategoryRule(categoryId)
        val changedFields = diffRuleFields(current, snapshot)
        if (changedFields.isEmpty()) return current

        // 스냅샷 기반 값으로 후보를 구성하되, revision/updatedAt 메타는 store가 관리한다.
        val candidate = current.copy(
            includeKeywords = snapshot.includeKeywords,
            excludeKeywords = snapshot.excludeKeywords,
            riskTags = snapshot.riskTags,
            excludeEventTypes = snapshot.excludeEventTypes,
            includeThreshold = snapshot.includeThreshold,
            reviewThreshold = snapshot.reviewThreshold,
            uncertainToReview = snapshot.uncertainToReview,
            autoExcludeEnabled = snapshot.autoExcludeEnabled,
            deliveryDays = snapshot.deliveryDays,
            deliveryHour = snapshot.deliveryHour,
            deliveryPreset = snapshot.deliveryPreset,
            autoApproveThreshold = snapshot.autoApproveThreshold,
            updatedBy = actorUsername
        )
        val saved = categoryRuleStore.updateWithExpectedUpdatedAt(candidate, expectedUpdatedAt)
            ?: run {
                val latest = categoryRuleStore.findByCategoryId(categoryId) ?: current
                throw ConflictException(
                    message = "카테고리 규칙이 다른 관리자에 의해 변경되었습니다. " +
                        "새로고침 후 다시 저장해주세요.",
                    staleEditInfo = StaleEditInfo(
                        latestUpdatedAt = latest.updatedAt,
                        latestEditorName = latest.updatedBy?.takeIf { it.isNotBlank() } ?: "관리자",
                        changedFieldNames = changedFields
                    )
                )
            }
        entityRevisionRecorder.record(
            resourceType = EntityRevisionResourceType.CATEGORY_RULE,
            resourceId = saved.categoryId,
            editorId = actorUsername,
            editorDisplayName = null,
            changedFields = changedFields,
            entity = saved
        )
        return saved
    }

    /** 복원 시 실제 바뀌는 필드만 뽑아 이력에 기록한다. */
    private fun diffRuleFields(current: CategoryRule, snapshot: CategoryRule): List<String> {
        val changes = mutableListOf<String>()
        if (current.includeKeywords != snapshot.includeKeywords) changes += "includeKeywords"
        if (current.excludeKeywords != snapshot.excludeKeywords) changes += "excludeKeywords"
        if (current.riskTags != snapshot.riskTags) changes += "riskTags"
        if (current.excludeEventTypes != snapshot.excludeEventTypes) changes += "excludeEventTypes"
        if (current.includeThreshold != snapshot.includeThreshold) changes += "includeThreshold"
        if (current.reviewThreshold != snapshot.reviewThreshold) changes += "reviewThreshold"
        if (current.uncertainToReview != snapshot.uncertainToReview) changes += "uncertainToReview"
        if (current.autoExcludeEnabled != snapshot.autoExcludeEnabled) changes += "autoExcludeEnabled"
        if (current.deliveryDays != snapshot.deliveryDays) changes += "deliveryDays"
        if (current.deliveryHour != snapshot.deliveryHour) changes += "deliveryHour"
        if (current.deliveryPreset != snapshot.deliveryPreset) changes += "deliveryPreset"
        if (current.autoApproveThreshold != snapshot.autoApproveThreshold) changes += "autoApproveThreshold"
        return changes
    }

    /** 변경된 규칙 필드 이름 목록을 추출한다. StaleEditInfo.changedFieldNames로 사용. */
    @Suppress("LongParameterList", "ComplexMethod")
    private fun collectChangedFieldNames(
        current: CategoryRule,
        includeKeywords: List<String>?,
        excludeKeywords: List<String>?,
        riskTags: List<String>?,
        excludeEventTypes: List<String>?,
        normalizedExcludeEventTypes: List<String>,
        includeThreshold: Double?,
        reviewThreshold: Double?,
        uncertainToReview: Boolean?,
        autoExcludeEnabled: Boolean?,
        deliveryDays: List<String>?,
        deliveryHour: Int?,
        deliveryPreset: DeliveryPreset?,
        autoApproveThreshold: Double?,
        autoApproveTouched: Boolean
    ): List<String> {
        val changes = mutableListOf<String>()
        if (includeKeywords != null) changes += "includeKeywords"
        if (excludeKeywords != null) changes += "excludeKeywords"
        if (riskTags != null) changes += "riskTags"
        // 정규화 이후 실제 변경 여부를 비교해 noop 저장을 변경 이력에서 제외한다.
        if (excludeEventTypes != null && normalizedExcludeEventTypes != current.excludeEventTypes) {
            changes += "excludeEventTypes"
        }
        if (includeThreshold != null && includeThreshold != current.includeThreshold) {
            changes += "includeThreshold"
        }
        if (reviewThreshold != null && reviewThreshold != current.reviewThreshold) {
            changes += "reviewThreshold"
        }
        if (uncertainToReview != null && uncertainToReview != current.uncertainToReview) {
            changes += "uncertainToReview"
        }
        if (autoExcludeEnabled != null && autoExcludeEnabled != current.autoExcludeEnabled) {
            changes += "autoExcludeEnabled"
        }
        if (deliveryDays != null) changes += "deliveryDays"
        if (deliveryHour != null && deliveryHour != current.deliveryHour) changes += "deliveryHour"
        if (deliveryPreset != null && deliveryPreset != current.deliveryPreset) changes += "deliveryPreset"
        if (autoApproveTouched && autoApproveThreshold != current.autoApproveThreshold) {
            changes += "autoApproveThreshold"
        }
        return changes
    }

    private fun ensureCategoryExists(categoryId: String) {
        categoryStore.findById(categoryId) ?: throw NotFoundException("Category not found: $categoryId")
    }

    private fun normalizeTokens(values: List<String>): List<String> =
        values
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(80)
            .toList()

    /**
     * event_type 블랙리스트 정규화.
     * - trim + UPPERCASE 로 대소문자 변이(`Other` vs `OTHER`)를 단일 형태로 수렴
     * - blank 제거 후 distinct 로 중복 제거
     * - enum 검증은 하지 않는다 — evaluator 가 unknown 값을 안전하게 무시하므로
     *   관리자가 신규 event_type 을 실험적으로 입력해도 API 는 거부하지 않는다.
     * - 상한은 keyword 정규화와 동일하게 80 개로 방어적으로 둔다.
     */
    private fun normalizeEventTypes(values: List<String>): List<String> =
        values
            .asSequence()
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(80)
            .toList()

    private fun defaultRule(categoryId: String): CategoryRule =
        CategoryRule(
            categoryId = categoryId,
            includeKeywords = emptyList(),
            excludeKeywords = emptyList(),
            riskTags = emptyList(),
            includeThreshold = 0.55,
            reviewThreshold = 0.35,
            uncertainToReview = true,
            autoExcludeEnabled = true,
            revision = 0,
            updatedBy = null
        )
}
