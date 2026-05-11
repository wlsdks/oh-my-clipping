package com.ohmyclipping.store

import com.ohmyclipping.entity.CategoryRuleEntity
import com.ohmyclipping.model.CategoryRule
import com.ohmyclipping.model.DeliveryPreset
import com.ohmyclipping.repository.CategoryRuleRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 카테고리 규칙 JPA 구현. JdbcCategoryRuleStore를 대체한다.
 */
@Repository
@Primary
class JpaCategoryRuleStore(
    private val repository: CategoryRuleRepository
) : CategoryRuleStore {

    private val mapper = jacksonObjectMapper()

    override fun findByCategoryId(categoryId: String): CategoryRule? =
        repository.findByCategoryId(categoryId)?.toModel()

    override fun upsert(rule: CategoryRule): CategoryRule {
        val now = Instant.now()
        val existing = repository.findByCategoryId(rule.categoryId)

        val entity = if (existing != null) {
            // 기존 규칙 갱신
            applyRuleToEntity(rule, existing)
            existing.updatedAt = now
            existing.systemUpdatedAt = now
            existing
        } else {
            // 신규 규칙 생성
            CategoryRuleEntity(
                categoryId = rule.categoryId,
                includeKeywords = mapper.writeValueAsString(rule.includeKeywords),
                excludeKeywords = mapper.writeValueAsString(rule.excludeKeywords),
                riskTags = mapper.writeValueAsString(rule.riskTags),
                excludeEventTypes = mapper.writeValueAsString(rule.excludeEventTypes),
                includeThreshold = rule.includeThreshold,
                reviewThreshold = rule.reviewThreshold,
                uncertainToReview = rule.uncertainToReview,
                autoExcludeEnabled = rule.autoExcludeEnabled,
                deliveryDays = rule.deliveryDays?.joinToString(","),
                deliveryHour = rule.deliveryHour,
                deliveryPreset = rule.deliveryPreset?.name,
                autoApproveThreshold = rule.autoApproveThreshold,
                revision = rule.revision,
                updatedBy = rule.updatedBy,
                updatedAt = now,
                systemUpdatedAt = now
            )
        }
        return repository.save(entity).toModel()
    }

    override fun updateWithExpectedUpdatedAt(rule: CategoryRule, expectedUpdatedAt: Instant): CategoryRule? {
        val existing = repository.findByCategoryId(rule.categoryId) ?: return null
        // 낙관적 잠금: 기대 시각과 현재 엔티티 updated_at이 다르면 충돌로 간주한다.
        if (existing.updatedAt != expectedUpdatedAt) return null
        val now = Instant.now()
        applyRuleToEntity(rule, existing)
        // revision은 저장할 때마다 +1로 단조 증가시켜 감사 추적 보조 신호로 사용한다.
        existing.revision = rule.revision + 1
        existing.updatedAt = now
        existing.systemUpdatedAt = now
        return repository.save(existing).toModel()
    }

    /** CategoryRule 도메인 객체의 필드를 엔티티로 복사한다. 시간/리비전은 호출자가 별도 갱신. */
    private fun applyRuleToEntity(rule: CategoryRule, entity: CategoryRuleEntity) {
        entity.includeKeywords = mapper.writeValueAsString(rule.includeKeywords)
        entity.excludeKeywords = mapper.writeValueAsString(rule.excludeKeywords)
        entity.riskTags = mapper.writeValueAsString(rule.riskTags)
        entity.excludeEventTypes = mapper.writeValueAsString(rule.excludeEventTypes)
        entity.includeThreshold = rule.includeThreshold
        entity.reviewThreshold = rule.reviewThreshold
        entity.uncertainToReview = rule.uncertainToReview
        entity.autoExcludeEnabled = rule.autoExcludeEnabled
        entity.deliveryDays = rule.deliveryDays?.joinToString(",")
        entity.deliveryHour = rule.deliveryHour
        entity.deliveryPreset = rule.deliveryPreset?.name
        entity.autoApproveThreshold = rule.autoApproveThreshold
        entity.revision = rule.revision
        entity.updatedBy = rule.updatedBy
    }

    override fun findCategoryIdsWithCustomSchedule(): Set<String> =
        repository.findCategoryIdsWithCustomSchedule()

    override fun findIncludeKeywords(categoryId: String): List<String> =
        repository.findByCategoryId(categoryId)
            ?.let { parseJsonList(it.includeKeywords) }
            ?: emptyList()

    override fun setIncludeKeywords(categoryId: String, keywords: List<String>) {
        val now = Instant.now()
        val existing = repository.findByCategoryId(categoryId)
        if (existing != null) {
            // 기존 룰의 include_keywords 만 교체하고 나머지 필드는 보존한다.
            existing.includeKeywords = mapper.writeValueAsString(keywords)
            existing.updatedAt = now
            existing.systemUpdatedAt = now
            repository.save(existing)
        } else {
            // 룰이 없으면 include_keywords 만 채운 신규 룰을 생성한다.
            val entity = CategoryRuleEntity(
                categoryId = categoryId,
                includeKeywords = mapper.writeValueAsString(keywords),
                excludeKeywords = mapper.writeValueAsString(emptyList<String>()),
                riskTags = mapper.writeValueAsString(emptyList<String>()),
                excludeEventTypes = mapper.writeValueAsString(emptyList<String>()),
                updatedAt = now,
                systemUpdatedAt = now
            )
            repository.save(entity)
        }
    }

    override fun setKeywordsAndExcludeEventTypes(
        categoryId: String,
        includeKeywords: List<String>,
        excludeEventTypes: List<String>,
    ) {
        val now = Instant.now()
        val existing = repository.findByCategoryId(categoryId)
        if (existing != null) {
            // 기존 룰에서 두 필드만 교체하고 나머지 필드는 보존한다.
            applyBundleFields(existing, includeKeywords, excludeEventTypes, now)
            repository.save(existing)
            return
        }

        // 룰이 없으면 두 필드만 채운 신규 룰을 생성한다.
        // 동시성 race: 두 PUT 요청이 동시에 existing==null 을 보고 둘 다 INSERT 를 시도하면
        // 두 번째가 PK 제약에 걸려 DataIntegrityViolationException 이 난다.
        // JpaOrganizationStore.upsertByStockCodeOrName 과 동일한 패턴으로, catch 후 재조회 → UPDATE 로 복구.
        try {
            val entity = CategoryRuleEntity(
                categoryId = categoryId,
                includeKeywords = mapper.writeValueAsString(includeKeywords),
                excludeKeywords = mapper.writeValueAsString(emptyList<String>()),
                riskTags = mapper.writeValueAsString(emptyList<String>()),
                excludeEventTypes = mapper.writeValueAsString(excludeEventTypes),
                updatedAt = now,
                systemUpdatedAt = now
            )
            repository.save(entity)
        } catch (e: DataIntegrityViolationException) {
            // 다른 트랜잭션이 먼저 INSERT 한 경우 — 재조회 후 UPDATE 로 복구한다.
            val raced = repository.findByCategoryId(categoryId)
                ?: throw e
            applyBundleFields(raced, includeKeywords, excludeEventTypes, Instant.now())
            repository.save(raced)
        }
    }

    /** 번들 전용: include_keywords + exclude_event_types 만 엔티티에 반영한다. */
    private fun applyBundleFields(
        entity: CategoryRuleEntity,
        includeKeywords: List<String>,
        excludeEventTypes: List<String>,
        now: Instant,
    ) {
        entity.includeKeywords = mapper.writeValueAsString(includeKeywords)
        entity.excludeEventTypes = mapper.writeValueAsString(excludeEventTypes)
        entity.updatedAt = now
        entity.systemUpdatedAt = now
    }

    private fun CategoryRuleEntity.toModel() = CategoryRule(
        categoryId = categoryId,
        includeKeywords = parseJsonList(includeKeywords),
        excludeKeywords = parseJsonList(excludeKeywords),
        riskTags = parseJsonList(riskTags),
        excludeEventTypes = parseJsonList(excludeEventTypes),
        includeThreshold = includeThreshold,
        reviewThreshold = reviewThreshold,
        uncertainToReview = uncertainToReview,
        autoExcludeEnabled = autoExcludeEnabled,
        deliveryDays = parseDeliveryDays(deliveryDays),
        deliveryHour = deliveryHour,
        deliveryPreset = deliveryPreset
            ?.let { runCatching { DeliveryPreset.valueOf(it) }.getOrNull() },
        autoApproveThreshold = autoApproveThreshold,
        revision = revision,
        updatedBy = updatedBy,
        updatedAt = updatedAt,
        systemUpdatedAt = systemUpdatedAt
    )

    /** CSV 형태의 delivery_days 컬럼을 리스트로 변환한다. */
    private fun parseDeliveryDays(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        return raw.split(',').map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun parseJsonList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { mapper.readValue<List<String>>(raw) }
            .getOrElse {
                raw.split(',').map { token -> token.trim() }.filter { token -> token.isNotBlank() }
            }
    }
}
