package com.ohmyclipping.store

import com.ohmyclipping.entity.CategoryEntity
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.CategoryPurpose
import com.ohmyclipping.model.CategoryStatus
import com.ohmyclipping.repository.CategoryRepository
import com.ohmyclipping.repository.RssSourceRepository
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 카테고리 JPA 구현. JdbcCategoryStore를 대체한다.
 */
@Repository
@Primary
class JpaCategoryStore(
    private val repository: CategoryRepository,
    private val rssSourceRepository: RssSourceRepository,
    private val em: EntityManager
) : CategoryStore {

    override fun list(): List<Category> =
        repository.findAll().map { it.toModel() }.sortedBy { it.createdAt }

    override fun listByIds(ids: Collection<String>): List<Category> {
        val normalizedIds = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalizedIds.isEmpty()) return emptyList()
        // 소유 카테고리 조회처럼 일부 ID만 필요한 경로에서 전체 카테고리 로드를 피한다.
        return repository.findAllById(normalizedIds)
            .map { it.toModel() }
            .sortedBy { it.createdAt }
    }

    override fun findOperational(): List<Category> =
        repository.findByStatusOrderByCreatedAtAsc(CategoryStatus.ACTIVE.name)
            .map { it.toModel() }

    override fun findPublicOperational(): List<Category> =
        repository.findByStatusAndIsPublicTrueOrderByCreatedAtAsc(CategoryStatus.ACTIVE.name)
            .map { it.toModel() }

    override fun findById(id: String): Category? =
        repository.findById(id).orElse(null)?.toModel()

    override fun findByName(name: String): Category? =
        repository.findByName(name)?.toModel()

    override fun save(category: Category): Category {
        val now = Instant.now()
        val id = category.id.ifBlank { UUID.randomUUID().toString() }
        val entity = CategoryEntity(
            id = id,
            name = category.name,
            description = category.description,
            slackChannelId = category.slackChannelId,
            isActive = category.isActive,
            isPublic = category.isPublic,
            maxItems = category.maxItems,
            personaId = category.personaId,
            createdAt = now,
            updatedAt = now,
            status = category.status.name,
            pausedAt = category.pausedAt,
            systemUpdatedAt = now,
            purpose = category.purpose?.name,
            background = category.background,
            problemStatement = category.problemStatement
        )
        return repository.save(entity).toModel()
    }

    override fun update(category: Category): Category {
        val entity = repository.findById(category.id).orElseThrow {
            NotFoundException("Category not found: ${category.id}")
        }
        val now = Instant.now()
        applyCategoryToEntity(category, entity)
        entity.updatedAt = now
        entity.systemUpdatedAt = now
        return repository.save(entity).toModel()
    }

    override fun updateWithExpectedUpdatedAt(category: Category, expectedUpdatedAt: Instant): Category? {
        val entity = repository.findById(category.id).orElse(null) ?: return null
        // 낙관적 잠금: 기대 시각과 현재 엔티티의 updatedAt이 다르면 충돌로 간주한다.
        if (entity.updatedAt != expectedUpdatedAt) return null
        val now = Instant.now()
        applyCategoryToEntity(category, entity)
        entity.updatedAt = now
        entity.systemUpdatedAt = now
        return repository.save(entity).toModel()
    }

    /** Category 도메인 객체의 필드를 엔티티로 복사한다. 시간은 호출자가 별도 갱신. */
    private fun applyCategoryToEntity(category: Category, entity: CategoryEntity) {
        entity.name = category.name
        entity.description = category.description
        entity.slackChannelId = category.slackChannelId
        entity.isActive = category.isActive
        entity.isPublic = category.isPublic
        entity.maxItems = category.maxItems
        entity.personaId = category.personaId
        entity.status = category.status.name
        entity.pausedAt = category.pausedAt
        // V123(Phase 3 PR1): 분석 목적 metadata 필드
        entity.purpose = category.purpose?.name
        entity.background = category.background
        entity.problemStatement = category.problemStatement
    }

    override fun delete(id: String) {
        repository.deleteById(id)
    }

    override fun countSources(categoryId: String): Int =
        rssSourceRepository.countByCategoryId(categoryId)

    override fun countSourcesByCategoryIds(categoryIds: List<String>): Map<String, Int> {
        val ids = categoryIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyMap()

        val placeholders = ids.joinToString(",") { "?" }
        val query = em.createNativeQuery(
            """
            SELECT category_id, COUNT(*) AS source_count
            FROM rss_sources
            WHERE category_id IN ($placeholders)
            GROUP BY category_id
            """.trimIndent()
        )
        ids.forEachIndexed { index, id -> query.setParameter(index + 1, id) }
        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>
        // 카테고리 목록 응답은 소스 수만 필요하므로 집계 row를 Map으로 축약한다.
        return rows.mapNotNull { row ->
            val categoryId = row[0] as? String ?: return@mapNotNull null
            val count = (row[1] as? Number)?.toInt() ?: return@mapNotNull null
            categoryId to count
        }.toMap()
    }

    override fun findAll(search: String?, offset: Int, limit: Int): List<Category> {
        val sql = buildString {
            append("SELECT * FROM batch_categories")
            if (!search.isNullOrBlank()) {
                append(" WHERE LOWER(name) LIKE ? OR LOWER(description) LIKE ?")
            }
            append(" ORDER BY created_at DESC LIMIT ? OFFSET ?")
        }
        val query = em.createNativeQuery(sql, CategoryEntity::class.java)
        var paramIndex = 1
        if (!search.isNullOrBlank()) {
            val pattern = "%${search.lowercase()}%"
            query.setParameter(paramIndex++, pattern)
            query.setParameter(paramIndex++, pattern)
        }
        query.setParameter(paramIndex++, limit)
        query.setParameter(paramIndex, offset)
        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<CategoryEntity>).map { it.toModel() }
    }

    override fun countAll(search: String?): Int {
        val sql = buildString {
            append("SELECT COUNT(*) FROM batch_categories")
            if (!search.isNullOrBlank()) {
                append(" WHERE LOWER(name) LIKE ? OR LOWER(description) LIKE ?")
            }
        }
        val query = em.createNativeQuery(sql)
        if (!search.isNullOrBlank()) {
            val pattern = "%${search.lowercase()}%"
            query.setParameter(1, pattern)
            query.setParameter(2, pattern)
        }
        return (query.singleResult as Number).toInt()
    }

    override fun findActiveByPersonaId(personaId: String): List<Category> =
        repository.findByPersonaIdAndStatusOrderByCreatedAtAsc(personaId, CategoryStatus.ACTIVE.name)
            .map { it.toModel() }

    override fun pause(id: String): Boolean {
        val entity = repository.findById(id).orElse(null) ?: return false
        val now = Instant.now()
        // 시스템 상태 변경이므로 updated_at은 보존하고 system_updated_at만 갱신한다.
        entity.status = "PAUSED"
        entity.isActive = false
        entity.pausedAt = now
        entity.systemUpdatedAt = now
        repository.save(entity)
        return true
    }

    override fun resume(id: String): Boolean {
        val entity = repository.findById(id).orElse(null) ?: return false
        val now = Instant.now()
        // AutoUnpauseScheduler도 호출하는 경로이므로 updated_at은 건드리지 않는다.
        entity.status = "ACTIVE"
        entity.isActive = true
        entity.pausedAt = null
        entity.systemUpdatedAt = now
        repository.save(entity)
        return true
    }

    override fun countActive(): Long = repository.countByIsActiveTrue()

    override fun countOperational(): Long = repository.countByStatus(CategoryStatus.ACTIVE.name)

    override fun countNewSince(since: Instant): Long =
        repository.countByCreatedAtGreaterThanEqual(since)

    override fun countDeactivatedSince(since: Instant): Long =
        repository.countByUpdatedAtGreaterThanEqualAndIsActiveFalse(since)

    override fun findExpiredPaused(maxDuration: Duration): List<Category> {
        val cutoff = Instant.now().minus(maxDuration)
        // pausedAt이 기준 시각보다 이전인 PAUSED 카테고리만 DB에서 조회한다.
        return repository.findByStatusAndPausedAtBeforeOrderByCreatedAtAsc(CategoryStatus.PAUSED.name, cutoff)
            .map { it.toModel() }
    }

    private fun CategoryEntity.toModel() = Category(
        id = id,
        name = name,
        description = description,
        slackChannelId = slackChannelId,
        isActive = isActive,
        isPublic = isPublic,
        maxItems = maxItems,
        personaId = personaId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = CategoryStatus.valueOf(status),
        pausedAt = pausedAt,
        systemUpdatedAt = systemUpdatedAt,
        // V123(Phase 3 PR1): DB 에 저장된 문자열을 enum 으로 복원. 허용 밖 값은 안전하게 null 처리.
        purpose = purpose?.let { raw -> runCatching { CategoryPurpose.valueOf(raw) }.getOrNull() },
        background = background,
        problemStatement = problemStatement
    )
}
