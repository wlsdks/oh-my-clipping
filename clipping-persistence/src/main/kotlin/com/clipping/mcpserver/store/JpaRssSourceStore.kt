package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.RssSourceEntity
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.model.SourceComplianceStatus
import com.clipping.mcpserver.model.SourceLegalBasis
import com.clipping.mcpserver.model.SourceRegionType
import com.clipping.mcpserver.repository.RssSourceRepository
import jakarta.persistence.EntityManager
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * RSS 소스 JPA 구현. JdbcRssSourceStore를 대체한다.
 * 단순 조회는 Repository 메서드, 부분 필드 업데이트는 EntityManager를 사용한다.
 */
@Repository
@Primary
class JpaRssSourceStore(
    private val repository: RssSourceRepository,
    private val em: EntityManager
) : RssSourceStore {

    override fun list(limit: Int): List<RssSource> {
        val safeLimit = limit.coerceIn(1, 10000)
        val sql = "SELECT * FROM rss_sources ORDER BY created_at LIMIT ?"
        val query = em.createNativeQuery(sql, RssSourceEntity::class.java)
        query.setParameter(1, safeLimit)
        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<RssSourceEntity>).map { it.toModel() }
    }

    override fun listByCategoryId(categoryId: String): List<RssSource> =
        repository.findByCategoryId(categoryId).map { it.toModel() }.sortedBy { it.createdAt }

    override fun listApproved(categoryId: String?): List<RssSource> {
        // 승인 + 활성 + 요약 허용 + VERIFIED + 금지 아닌 소스만 필터한다.
        val sql = buildString {
            append("SELECT * FROM rss_sources WHERE crawl_approved = TRUE")
            append(" AND is_active = TRUE AND summary_allowed = TRUE")
            append(" AND verification_status = 'VERIFIED' AND legal_basis <> 'PROHIBITED'")
            if (categoryId != null) append(" AND category_id = ?")
            append(" ORDER BY created_at")
        }
        val query = em.createNativeQuery(sql, RssSourceEntity::class.java)
        if (categoryId != null) query.setParameter(1, categoryId)
        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<RssSourceEntity>).map { it.toModel() }
    }

    override fun findById(id: String): RssSource? =
        repository.findById(id).orElse(null)?.toModel()

    override fun findPendingVerificationCreatedBefore(cutoff: Instant): List<RssSource> =
        repository.findByVerificationStatusAndCreatedAtBefore("PENDING", cutoff)
            .map { it.toModel() }
            .sortedBy { it.createdAt }

    override fun findByUrlAndCategoryId(url: String, categoryId: String): RssSource? =
        repository.findFirstByUrlAndCategoryId(url, categoryId)?.toModel()

    override fun save(source: RssSource): RssSource {
        val now = Instant.now()
        val id = source.id.ifBlank { UUID.randomUUID().toString() }
        val entity = RssSourceEntity(
            id = id,
            name = source.name,
            url = source.url,
            emoji = source.emoji,
            isActive = source.isActive,
            crawlApproved = source.crawlApproved,
            approvedBy = source.approvedBy,
            approvedAt = source.approvedAt,
            legalBasis = source.legalBasis.name,
            summaryAllowed = source.summaryAllowed,
            fulltextAllowed = source.fulltextAllowed,
            termsReviewedAt = source.termsReviewedAt,
            expectedReviewAt = source.expectedReviewAt,
            reviewNotes = source.reviewNotes,
            verificationStatus = source.verificationStatus,
            reliabilityScore = source.reliabilityScore,
            lastCrawlError = source.lastCrawlError,
            crawlFailCount = source.crawlFailCount,
            lastSuccessAt = source.lastSuccessAt,
            responsibilityAcknowledgedAt = source.responsibilityAcknowledgedAt,
            sourceRegion = source.sourceRegion.name,
            categoryId = source.categoryId,
            curated = source.curated,
            origin = source.origin,
            createdAt = now,
            updatedAt = now,
            systemUpdatedAt = now
        )
        return repository.save(entity).toModel()
    }

    override fun insert(
        id: String,
        categoryId: String,
        sourceUrl: String,
        sourceName: String,
        origin: String
    ) {
        // origin 포함 최소 필드로 소스를 신규 삽입한다.
        val now = Instant.now()
        val entity = RssSourceEntity(
            id = id,
            name = sourceName,
            url = sourceUrl,
            categoryId = categoryId,
            origin = origin,
            createdAt = now,
            updatedAt = now,
            systemUpdatedAt = now
        )
        repository.save(entity)
    }

    override fun findByCategoryIdAndOrigin(categoryId: String, origin: String): List<RssSource> =
        repository.findByCategoryIdAndOrigin(categoryId, origin).map { it.toModel() }

    override fun existsByCategoryIdAndUrl(categoryId: String, sourceUrl: String): Boolean =
        repository.countByCategoryIdAndUrl(categoryId, sourceUrl) > 0

    override fun update(source: RssSource): RssSource {
        val entity = repository.findById(source.id).orElseThrow {
            NotFoundException("RssSource not found: ${source.id}")
        }
        val now = Instant.now()
        applySourceToEntity(source, entity)
        entity.updatedAt = now
        entity.systemUpdatedAt = now
        return repository.save(entity).toModel()
    }

    override fun updateWithExpectedUpdatedAt(source: RssSource, expectedUpdatedAt: Instant): RssSource? {
        val entity = repository.findById(source.id).orElse(null) ?: return null
        // 낙관적 잠금: 기대 시각과 엔티티의 updatedAt이 일치하지 않으면 충돌로 간주한다.
        if (entity.updatedAt != expectedUpdatedAt) return null
        val now = Instant.now()
        applySourceToEntity(source, entity)
        entity.updatedAt = now
        entity.systemUpdatedAt = now
        return repository.save(entity).toModel()
    }

    /** RssSource 도메인 객체의 관리자 편집 필드를 엔티티로 복사한다. 시간은 호출자가 별도 갱신. */
    private fun applySourceToEntity(source: RssSource, entity: RssSourceEntity) {
        entity.name = source.name
        entity.url = source.url
        entity.emoji = source.emoji
        entity.isActive = source.isActive
        entity.categoryId = source.categoryId
        entity.crawlApproved = source.crawlApproved
        entity.approvedBy = source.approvedBy
        entity.approvedAt = source.approvedAt
        entity.legalBasis = source.legalBasis.name
        entity.summaryAllowed = source.summaryAllowed
        entity.fulltextAllowed = source.fulltextAllowed
        entity.termsReviewedAt = source.termsReviewedAt
        entity.expectedReviewAt = source.expectedReviewAt
        entity.reviewNotes = source.reviewNotes
        entity.sourceRegion = source.sourceRegion.name
        entity.curated = source.curated
    }

    override fun delete(id: String) {
        repository.deleteById(id)
        repository.flush()
    }

    @Transactional
    override fun updateApproval(id: String, approved: Boolean, approvedBy: String?) {
        val entity = repository.findById(id).orElse(null) ?: return
        val now = Instant.now()
        // 승인 플래그 토글은 관리자 편집 시각을 보존하기 위해 system_updated_at만 갱신한다.
        entity.crawlApproved = approved
        entity.approvedBy = approvedBy
        entity.approvedAt = if (approved) now else null
        entity.systemUpdatedAt = now
        repository.save(entity)
    }

    @Transactional
    override fun updateVerificationStatus(id: String, status: String) {
        val entity = repository.findById(id).orElse(null) ?: return
        // SourceVerificationService 스케줄러가 호출하므로 updated_at은 건드리지 않는다.
        entity.verificationStatus = status
        entity.systemUpdatedAt = Instant.now()
        repository.save(entity)
    }

    @Transactional
    override fun incrementFailCount(id: String, error: String) {
        val entity = repository.findById(id).orElse(null) ?: return
        // CollectionService가 호출하는 경로이므로 updated_at은 건드리지 않는다.
        entity.crawlFailCount += 1
        entity.lastCrawlError = error
        entity.systemUpdatedAt = Instant.now()
        repository.save(entity)
    }

    @Transactional
    override fun resetFailCount(id: String) {
        val entity = repository.findById(id).orElse(null) ?: return
        val now = Instant.now()
        // 크롤 성공 시 통계 갱신은 system_updated_at만 갱신한다.
        entity.crawlFailCount = 0
        entity.lastCrawlError = null
        entity.lastSuccessAt = now
        entity.systemUpdatedAt = now
        repository.save(entity)
    }

    override fun findFailedSources(minFailCount: Int): List<RssSource> =
        repository.findByCrawlFailCountGreaterThanEqualAndIsActiveTrue(minFailCount)
            .map { it.toModel() }
            .sortedByDescending { it.crawlFailCount }

    @Transactional
    override fun deactivate(id: String) {
        val entity = repository.findById(id).orElse(null) ?: return
        // SourceHealthScheduler가 호출하는 경로이므로 updated_at은 건드리지 않는다.
        entity.isActive = false
        entity.systemUpdatedAt = Instant.now()
        repository.save(entity)
    }

    override fun findDeactivated(): List<RssSource> =
        repository.findByIsActiveFalse()
            .map { it.toModel() }
            .sortedByDescending { it.systemUpdatedAt }

    @Transactional
    override fun reactivate(id: String) {
        val entity = repository.findById(id).orElse(null) ?: return
        // SourceHealthScheduler가 호출하는 경로이므로 updated_at은 건드리지 않는다.
        entity.isActive = true
        entity.systemUpdatedAt = Instant.now()
        repository.save(entity)
    }

    override fun countErrorByCategoryId(categoryId: String): Int =
        repository.countByCategoryIdAndCrawlFailCountGreaterThan(categoryId, 0)

    override fun findAll(
        categoryId: String?,
        search: String?,
        complianceStatus: SourceComplianceStatus?,
        offset: Int,
        limit: Int
    ): List<RssSource> {
        val (whereClause, params) = buildNativeSearchParams(categoryId, search, complianceStatus)
        val sql = "SELECT * FROM rss_sources$whereClause ORDER BY created_at DESC LIMIT ? OFFSET ?"
        val query = em.createNativeQuery(sql, RssSourceEntity::class.java)
        var idx = 1
        for (p in params) query.setParameter(idx++, p)
        query.setParameter(idx++, limit)
        query.setParameter(idx, offset)
        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<RssSourceEntity>).map { it.toModel() }
    }

    override fun countAll(
        categoryId: String?,
        search: String?,
        complianceStatus: SourceComplianceStatus?
    ): Int {
        val (whereClause, params) = buildNativeSearchParams(categoryId, search, complianceStatus)
        val sql = "SELECT COUNT(*) FROM rss_sources$whereClause"
        val query = em.createNativeQuery(sql)
        var idx = 1
        for (p in params) query.setParameter(idx++, p)
        return (query.singleResult as Number).toInt()
    }

    override fun countComplianceAttention(now: Instant): Int {
        // 재검토가 필요한 소스(만료 + 만료 임박 + 미검토)를 한 번에 집계한다.
        val soonCutoff = now.plusSeconds(SourceComplianceStatus.EXPIRING_SOON_DAYS * 86400)
        val sql = """
            SELECT COUNT(*) FROM rss_sources
            WHERE crawl_approved = TRUE
              AND (
                expected_review_at IS NOT NULL AND expected_review_at <= ?
                OR terms_reviewed_at IS NULL
              )
        """.trimIndent()
        val query = em.createNativeQuery(sql)
        query.setParameter(1, java.sql.Timestamp.from(soonCutoff))
        return (query.singleResult as Number).toInt()
    }

    /**
     * 카테고리 ID/검색어/저작권 상태로 동적 WHERE 절과 바인딩 파라미터를 생성한다.
     */
    private fun buildNativeSearchParams(
        categoryId: String?,
        search: String?,
        complianceStatus: SourceComplianceStatus?
    ): Pair<String, List<Any>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (categoryId != null) {
            conditions += "category_id = ?"
            params += categoryId
        }
        if (!search.isNullOrBlank()) {
            val pattern = "%${search.lowercase()}%"
            conditions += "(LOWER(name) LIKE ? OR LOWER(url) LIKE ?)"
            params += pattern
            params += pattern
        }
        // 저작권 필터: 상태별로 SQL 조건을 다르게 적용한다.
        if (complianceStatus != null) {
            val now = Instant.now()
            when (complianceStatus) {
                SourceComplianceStatus.EXPIRED -> {
                    conditions += "expected_review_at IS NOT NULL AND expected_review_at < ?"
                    params += java.sql.Timestamp.from(now)
                }
                SourceComplianceStatus.EXPIRING_SOON -> {
                    val soonCutoff = now.plusSeconds(SourceComplianceStatus.EXPIRING_SOON_DAYS * 86400)
                    conditions += "expected_review_at IS NOT NULL AND expected_review_at >= ? AND expected_review_at <= ?"
                    params += java.sql.Timestamp.from(now)
                    params += java.sql.Timestamp.from(soonCutoff)
                }
                SourceComplianceStatus.NEVER_REVIEWED -> {
                    conditions += "terms_reviewed_at IS NULL AND crawl_approved = TRUE"
                }
                SourceComplianceStatus.VALID -> {
                    val soonCutoff = now.plusSeconds(SourceComplianceStatus.EXPIRING_SOON_DAYS * 86400)
                    conditions += "(terms_reviewed_at IS NOT NULL AND (expected_review_at IS NULL OR expected_review_at > ?))"
                    params += java.sql.Timestamp.from(soonCutoff)
                }
            }
        }

        val whereClause = if (conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}"
        return whereClause to params
    }

    private fun RssSourceEntity.toModel() = RssSource(
        id = id,
        name = name,
        url = url,
        emoji = emoji,
        isActive = isActive,
        crawlApproved = crawlApproved,
        approvedBy = approvedBy,
        approvedAt = approvedAt,
        legalBasis = parseLegalBasis(legalBasis),
        summaryAllowed = summaryAllowed,
        fulltextAllowed = fulltextAllowed,
        termsReviewedAt = termsReviewedAt,
        expectedReviewAt = expectedReviewAt,
        reviewNotes = reviewNotes,
        verificationStatus = verificationStatus,
        reliabilityScore = reliabilityScore,
        lastCrawlError = lastCrawlError,
        crawlFailCount = crawlFailCount,
        lastSuccessAt = lastSuccessAt,
        sourceRegion = parseSourceRegion(sourceRegion),
        categoryId = categoryId,
        curated = curated,
        responsibilityAcknowledgedAt = responsibilityAcknowledgedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        systemUpdatedAt = systemUpdatedAt,
        origin = origin
    )

    private fun parseLegalBasis(raw: String?): SourceLegalBasis = try {
        SourceLegalBasis.valueOf(raw?.uppercase() ?: SourceLegalBasis.QUOTATION_ONLY.name)
    } catch (_: Exception) {
        SourceLegalBasis.QUOTATION_ONLY
    }

    private fun parseSourceRegion(raw: String?): SourceRegionType = try {
        SourceRegionType.valueOf(raw?.uppercase() ?: SourceRegionType.UNKNOWN.name)
    } catch (_: Exception) {
        SourceRegionType.UNKNOWN
    }

    override fun countDailyArticlesBySource(sourceId: String, cutoff: Instant): List<Pair<LocalDate, Int>> {
        // 특정 소스의 일별 기사 수집 건수를 집계한다 (vendor-neutral SQL).
        val sql = """
            SELECT CAST(created_at AS DATE) AS article_date, COUNT(*) AS cnt
            FROM rss_items
            WHERE rss_source_id = ?1 AND created_at >= ?2
            GROUP BY CAST(created_at AS DATE)
            ORDER BY article_date DESC
        """.trimIndent()
        val query = em.createNativeQuery(sql)
        query.setParameter(1, sourceId)
        query.setParameter(2, java.sql.Timestamp.from(cutoff))
        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>
        return rows.mapNotNull { row ->
            val date = coerceArticleDate(row[0]) ?: return@mapNotNull null
            val count = (row[1] as? Number)?.toInt() ?: return@mapNotNull null
            date to count
        }
    }

    private fun coerceArticleDate(raw: Any?): LocalDate? =
        when (raw) {
            is LocalDate -> raw
            is java.sql.Date -> raw.toLocalDate()
            else -> null
        }

    override fun countArticlesBySourceId(sourceId: String): Int {
        val query = em.createNativeQuery("SELECT COUNT(*) FROM rss_items WHERE rss_source_id = ?1")
        query.setParameter(1, sourceId)
        return (query.singleResult as Number).toInt()
    }

    @Transactional
    override fun updateReliabilityScores(scores: Map<String, Int>) {
        // SourceHealthScheduler가 호출하는 경로이므로 updated_at은 건드리지 않는다.
        if (scores.isEmpty()) return
        val now = Instant.now()
        for ((sourceId, score) in scores) {
            val entity = repository.findById(sourceId).orElse(null) ?: continue
            entity.reliabilityScore = score
            entity.systemUpdatedAt = now
            repository.save(entity)
        }
    }

    override fun countArticlesBySource(cutoff: Instant): Map<String, Int> {
        // cutoff 이후 수집된 기사 수를 소스별로 집계한다.
        // rss_items.rss_source_id 는 nullable (수동 URL 등 소스 없이 들어온 항목) 이므로 NULL 그룹은 제외한다.
        val sql = """
            SELECT rss_source_id, COUNT(*) AS article_count
            FROM rss_items
            WHERE created_at >= ?1
              AND rss_source_id IS NOT NULL
            GROUP BY rss_source_id
        """.trimIndent()
        val query = em.createNativeQuery(sql)
        query.setParameter(1, java.sql.Timestamp.from(cutoff))
        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>
        return rows.mapNotNull { row ->
            val sourceId = row[0] as? String ?: return@mapNotNull null
            val count = (row[1] as? Number)?.toInt() ?: return@mapNotNull null
            sourceId to count
        }.toMap()
    }
}
