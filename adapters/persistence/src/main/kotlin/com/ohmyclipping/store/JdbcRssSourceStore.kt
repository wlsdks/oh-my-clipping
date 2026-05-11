package com.ohmyclipping.store

import com.ohmyclipping.model.RssSource
import com.ohmyclipping.model.SourceComplianceStatus
import com.ohmyclipping.model.SourceLegalBasis
import com.ohmyclipping.model.SourceRegionType
import com.ohmyclipping.support.SqlUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
class JdbcRssSourceStore(private val jdbc: JdbcTemplate) : RssSourceStore {

    private val rowMapper = RowMapper<RssSource> { rs, _ ->
        RssSource(
            id = rs.getString("id"),
            name = rs.getString("name"),
            url = rs.getString("url"),
            emoji = rs.getString("emoji"),
            isActive = rs.getBoolean("is_active"),
            crawlApproved = rs.getBoolean("crawl_approved"),
            approvedBy = rs.getString("approved_by"),
            approvedAt = rs.getTimestamp("approved_at")?.toInstant(),
            legalBasis = parseLegalBasis(rs.getString("legal_basis")),
            summaryAllowed = rs.getBoolean("summary_allowed"),
            fulltextAllowed = rs.getBoolean("fulltext_allowed"),
            termsReviewedAt = rs.getTimestamp("terms_reviewed_at")?.toInstant(),
            expectedReviewAt = rs.getTimestamp("expected_review_at")?.toInstant(),
            reviewNotes = rs.getString("review_notes"),
            verificationStatus = rs.getString("verification_status") ?: "PENDING",
            reliabilityScore = rs.getInt("reliability_score"),
            lastCrawlError = rs.getString("last_crawl_error"),
            crawlFailCount = rs.getInt("crawl_fail_count"),
            lastSuccessAt = rs.getTimestamp("last_success_at")?.toInstant(),
            sourceRegion = parseSourceRegion(rs.getString("source_region")),
            categoryId = rs.getString("category_id"),
            curated = rs.getBoolean("curated"),
            responsibilityAcknowledgedAt = rs.getTimestamp("responsibility_acknowledged_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            systemUpdatedAt = rs.getTimestamp("system_updated_at").toInstant(),
            origin = rs.getString("origin") ?: "manual"
        )
    }

    override fun list(limit: Int): List<RssSource> {
        val safeLimit = limit.coerceIn(1, 10000)
        return jdbc.query("SELECT * FROM rss_sources ORDER BY created_at LIMIT ?", rowMapper, safeLimit)
    }

    override fun listByCategoryId(categoryId: String): List<RssSource> =
        jdbc.query("SELECT * FROM rss_sources WHERE category_id = ? ORDER BY created_at", rowMapper, categoryId)

    override fun listApproved(categoryId: String?): List<RssSource> =
        if (categoryId != null) {
            jdbc.query(
                """SELECT * FROM rss_sources
                   WHERE crawl_approved = TRUE
                     AND is_active = TRUE
                     AND summary_allowed = TRUE
                     AND verification_status = 'VERIFIED'
                     AND legal_basis <> 'PROHIBITED'
                     AND category_id = ?
                   ORDER BY created_at""",
                rowMapper, categoryId
            )
        } else {
            jdbc.query(
                """SELECT * FROM rss_sources
                   WHERE crawl_approved = TRUE
                     AND is_active = TRUE
                     AND summary_allowed = TRUE
                     AND verification_status = 'VERIFIED'
                     AND legal_basis <> 'PROHIBITED'
                   ORDER BY created_at""",
                rowMapper
            )
        }

    override fun findById(id: String): RssSource? =
        jdbc.query("SELECT * FROM rss_sources WHERE id = ?", rowMapper, id).firstOrNull()

    override fun findPendingVerificationCreatedBefore(cutoff: Instant): List<RssSource> =
        jdbc.query(
            """SELECT * FROM rss_sources
               WHERE verification_status = 'PENDING'
                 AND created_at < ?
               ORDER BY created_at""",
            rowMapper,
            java.sql.Timestamp.from(cutoff),
        )

    override fun findByUrlAndCategoryId(url: String, categoryId: String): RssSource? =
        jdbc.query(
            "SELECT * FROM rss_sources WHERE url = ? AND category_id = ? LIMIT 1",
            rowMapper, url, categoryId
        ).firstOrNull()

    override fun save(source: RssSource): RssSource {
        val now = Instant.now()
        val id = source.id.ifBlank { UUID.randomUUID().toString() }
        val saved = source.copy(id = id, createdAt = now, updatedAt = now, systemUpdatedAt = now)
        jdbc.update(
            """INSERT INTO rss_sources (id, name, url, emoji, is_active, crawl_approved, approved_by, approved_at,
               legal_basis, summary_allowed, fulltext_allowed, terms_reviewed_at, expected_review_at, review_notes,
               verification_status, reliability_score, last_crawl_error, crawl_fail_count, last_success_at,
               source_region, category_id, curated, responsibility_acknowledged_at,
               created_at, updated_at, system_updated_at, origin)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            saved.id, saved.name, saved.url, saved.emoji, saved.isActive,
            saved.crawlApproved, saved.approvedBy,
            saved.approvedAt?.let { java.sql.Timestamp.from(it) },
            saved.legalBasis.name, saved.summaryAllowed, saved.fulltextAllowed,
            saved.termsReviewedAt?.let { java.sql.Timestamp.from(it) },
            saved.expectedReviewAt?.let { java.sql.Timestamp.from(it) },
            saved.reviewNotes,
            saved.verificationStatus, saved.reliabilityScore,
            saved.lastCrawlError, saved.crawlFailCount,
            saved.lastSuccessAt?.let { java.sql.Timestamp.from(it) },
            saved.sourceRegion.name,
            saved.categoryId, saved.curated,
            saved.responsibilityAcknowledgedAt?.let { java.sql.Timestamp.from(it) },
            java.sql.Timestamp.from(saved.createdAt),
            java.sql.Timestamp.from(saved.updatedAt),
            java.sql.Timestamp.from(saved.systemUpdatedAt),
            saved.origin
        )
        return saved
    }

    override fun update(source: RssSource): RssSource {
        val now = Instant.now()
        val updated = source.copy(updatedAt = now, systemUpdatedAt = now)
        jdbc.update(
            """UPDATE rss_sources
               SET name = ?, url = ?, emoji = ?, is_active = ?, category_id = ?,
                   crawl_approved = ?, approved_by = ?, approved_at = ?,
                   legal_basis = ?, summary_allowed = ?, fulltext_allowed = ?,
                   terms_reviewed_at = ?, expected_review_at = ?, review_notes = ?,
                   source_region = ?, curated = ?,
                   updated_at = ?, system_updated_at = ?
               WHERE id = ?""",
            updated.name, updated.url, updated.emoji, updated.isActive,
            updated.categoryId, updated.crawlApproved, updated.approvedBy,
            updated.approvedAt?.let { java.sql.Timestamp.from(it) },
            updated.legalBasis.name, updated.summaryAllowed, updated.fulltextAllowed,
            updated.termsReviewedAt?.let { java.sql.Timestamp.from(it) },
            updated.expectedReviewAt?.let { java.sql.Timestamp.from(it) },
            updated.reviewNotes, updated.sourceRegion.name, updated.curated,
            java.sql.Timestamp.from(updated.updatedAt),
            java.sql.Timestamp.from(updated.systemUpdatedAt), updated.id
        )
        return updated
    }

    override fun updateWithExpectedUpdatedAt(source: RssSource, expectedUpdatedAt: Instant): RssSource? {
        val now = Instant.now()
        val updated = source.copy(updatedAt = now, systemUpdatedAt = now)
        val affected = jdbc.update(
            """UPDATE rss_sources
               SET name = ?, url = ?, emoji = ?, is_active = ?, category_id = ?,
                   crawl_approved = ?, approved_by = ?, approved_at = ?,
                   legal_basis = ?, summary_allowed = ?, fulltext_allowed = ?,
                   terms_reviewed_at = ?, expected_review_at = ?, review_notes = ?,
                   source_region = ?, curated = ?,
                   updated_at = ?, system_updated_at = ?
               WHERE id = ? AND updated_at = ?""",
            updated.name, updated.url, updated.emoji, updated.isActive,
            updated.categoryId, updated.crawlApproved, updated.approvedBy,
            updated.approvedAt?.let { java.sql.Timestamp.from(it) },
            updated.legalBasis.name, updated.summaryAllowed, updated.fulltextAllowed,
            updated.termsReviewedAt?.let { java.sql.Timestamp.from(it) },
            updated.expectedReviewAt?.let { java.sql.Timestamp.from(it) },
            updated.reviewNotes, updated.sourceRegion.name, updated.curated,
            java.sql.Timestamp.from(updated.updatedAt),
            java.sql.Timestamp.from(updated.systemUpdatedAt),
            updated.id, java.sql.Timestamp.from(expectedUpdatedAt)
        )
        return if (affected == 1) updated else null
    }

    override fun delete(id: String) {
        jdbc.update("DELETE FROM rss_sources WHERE id = ?", id)
    }

    override fun updateApproval(id: String, approved: Boolean, approvedBy: String?) {
        val now = java.sql.Timestamp.from(Instant.now())
        val approvedAt = if (approved) now else null
        // 승인 플래그 토글은 관리자 편집 시각을 보존하기 위해 system_updated_at만 갱신한다.
        jdbc.update(
            "UPDATE rss_sources SET crawl_approved = ?, approved_by = ?, approved_at = ?, system_updated_at = ? WHERE id = ?",
            approved, approvedBy, approvedAt, now, id
        )
    }

    override fun updateVerificationStatus(id: String, status: String) {
        // verification_status는 SourceVerificationService 스케줄러가 갱신하므로 updated_at은 건드리지 않는다.
        jdbc.update(
            "UPDATE rss_sources SET verification_status = ?, system_updated_at = ? WHERE id = ?",
            status, java.sql.Timestamp.from(Instant.now()), id
        )
    }

    override fun incrementFailCount(id: String, error: String) {
        // CollectionService가 호출하는 경로이므로 updated_at은 건드리지 않는다.
        jdbc.update(
            "UPDATE rss_sources SET crawl_fail_count = crawl_fail_count + 1, last_crawl_error = ?, system_updated_at = ? WHERE id = ?",
            error, java.sql.Timestamp.from(Instant.now()), id
        )
    }

    override fun resetFailCount(id: String) {
        val now = java.sql.Timestamp.from(Instant.now())
        // 크롤 성공 시 통계 갱신은 system_updated_at만 갱신한다.
        jdbc.update(
            "UPDATE rss_sources SET crawl_fail_count = 0, last_crawl_error = NULL, last_success_at = ?, system_updated_at = ? WHERE id = ?",
            now, now, id
        )
    }

    override fun findFailedSources(minFailCount: Int): List<RssSource> =
        jdbc.query(
            "SELECT * FROM rss_sources WHERE is_active = TRUE AND crawl_fail_count >= ? ORDER BY crawl_fail_count DESC",
            rowMapper, minFailCount
        )

    override fun deactivate(id: String) {
        // SourceHealthScheduler가 호출하는 경로이므로 updated_at은 건드리지 않는다.
        jdbc.update(
            "UPDATE rss_sources SET is_active = FALSE, system_updated_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.now()), id
        )
    }

    override fun findDeactivated(): List<RssSource> =
        jdbc.query(
            "SELECT * FROM rss_sources WHERE is_active = FALSE ORDER BY system_updated_at DESC",
            rowMapper
        )

    override fun reactivate(id: String) {
        // SourceHealthScheduler가 호출하는 경로이므로 updated_at은 건드리지 않는다.
        jdbc.update(
            "UPDATE rss_sources SET is_active = TRUE, system_updated_at = ? WHERE id = ?",
            java.sql.Timestamp.from(Instant.now()), id
        )
    }

    override fun countErrorByCategoryId(categoryId: String): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM rss_sources WHERE category_id = ? AND crawl_fail_count > 0",
            Int::class.java,
            categoryId
        ) ?: 0

    override fun findAll(
        categoryId: String?,
        search: String?,
        complianceStatus: SourceComplianceStatus?,
        offset: Int,
        limit: Int
    ): List<RssSource> {
        val (whereClause, params) = buildSearchWhereClause(categoryId, search, complianceStatus)
        val sql = "SELECT * FROM rss_sources$whereClause ORDER BY created_at DESC LIMIT ? OFFSET ?"
        return jdbc.query(sql, rowMapper, *params.toTypedArray(), limit, offset)
    }

    override fun countAll(
        categoryId: String?,
        search: String?,
        complianceStatus: SourceComplianceStatus?
    ): Int {
        val (whereClause, params) = buildSearchWhereClause(categoryId, search, complianceStatus)
        val sql = "SELECT COUNT(*) FROM rss_sources$whereClause"
        return jdbc.queryForObject(sql, Int::class.java, *params.toTypedArray()) ?: 0
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
        return jdbc.queryForObject(
            sql,
            Int::class.java,
            java.sql.Timestamp.from(soonCutoff)
        ) ?: 0
    }

    /**
     * 카테고리 ID/검색어/저작권 상태로 동적 WHERE 절과 바인딩 파라미터를 생성한다.
     * 검색은 name 또는 url에 대해 대소문자 무시 LIKE 검색을 수행한다.
     */
    private fun buildSearchWhereClause(
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
            // LIKE 와일드카드 문자를 이스케이프하여 SQL 인젝션을 방지한다.
            val pattern = "%${SqlUtils.escapeLike(search.lowercase())}%"
            conditions += "(LOWER(name) LIKE ? ESCAPE '\\' OR LOWER(url) LIKE ? ESCAPE '\\')"
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
                    // 현재 시각 이후 ~ 30일 이내
                    val soonCutoff = now.plusSeconds(SourceComplianceStatus.EXPIRING_SOON_DAYS * 86400)
                    conditions += "expected_review_at IS NOT NULL AND expected_review_at >= ? AND expected_review_at <= ?"
                    params += java.sql.Timestamp.from(now)
                    params += java.sql.Timestamp.from(soonCutoff)
                }
                SourceComplianceStatus.NEVER_REVIEWED -> {
                    conditions += "terms_reviewed_at IS NULL AND crawl_approved = TRUE"
                }
                SourceComplianceStatus.VALID -> {
                    // 만료/만료 임박/미검토 중 어느 것도 아닌 상태
                    val soonCutoff = now.plusSeconds(SourceComplianceStatus.EXPIRING_SOON_DAYS * 86400)
                    conditions += "(terms_reviewed_at IS NOT NULL AND (expected_review_at IS NULL OR expected_review_at > ?))"
                    params += java.sql.Timestamp.from(soonCutoff)
                }
            }
        }

        val whereClause = if (conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}"
        return whereClause to params
    }

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
            WHERE rss_source_id = ? AND created_at >= ?
            GROUP BY CAST(created_at AS DATE)
            ORDER BY article_date DESC
        """.trimIndent()
        val mapper = RowMapper<Pair<LocalDate, Int>> { rs, _ ->
            rs.getDate("article_date").toLocalDate() to rs.getInt("cnt")
        }
        return jdbc.query(sql, mapper, sourceId, java.sql.Timestamp.from(cutoff))
    }

    override fun countArticlesBySourceId(sourceId: String): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM rss_items WHERE rss_source_id = ?",
            Int::class.java,
            sourceId
        ) ?: 0

    override fun countArticlesBySource(cutoff: Instant): Map<String, Int> {
        // cutoff 이후 수집된 기사 수를 소스별로 집계한다.
        val sql = """
            SELECT rss_source_id, COUNT(*) AS article_count
            FROM rss_items
            WHERE created_at >= ?
            GROUP BY rss_source_id
        """.trimIndent()
        val mapper = RowMapper<Pair<String, Int>> { rs, _ ->
            rs.getString("rss_source_id") to rs.getInt("article_count")
        }
        return jdbc.query(sql, mapper, java.sql.Timestamp.from(cutoff))
            .filter { it.first.isNotEmpty() }
            .associate { it }
    }

    override fun insert(
        id: String,
        categoryId: String,
        sourceUrl: String,
        sourceName: String,
        origin: String
    ) {
        // origin 포함 최소 필드로 소스를 신규 삽입한다.
        val now = java.sql.Timestamp.from(Instant.now())
        jdbc.update(
            """INSERT INTO rss_sources
               (id, name, url, category_id, origin, created_at, updated_at, system_updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            id, sourceName, sourceUrl, categoryId, origin, now, now, now
        )
    }

    override fun findByCategoryIdAndOrigin(categoryId: String, origin: String): List<RssSource> =
        jdbc.query(
            "SELECT * FROM rss_sources WHERE category_id = ? AND origin = ? ORDER BY created_at",
            rowMapper, categoryId, origin
        )

    override fun existsByCategoryIdAndUrl(categoryId: String, sourceUrl: String): Boolean =
        (jdbc.queryForObject(
            "SELECT COUNT(*) FROM rss_sources WHERE category_id = ? AND url = ?",
            Int::class.java, categoryId, sourceUrl
        ) ?: 0) > 0

    override fun updateReliabilityScores(scores: Map<String, Int>) {
        if (scores.isEmpty()) return
        // SourceHealthScheduler가 호출하는 경로이므로 updated_at은 건드리지 않는다.
        val now = java.sql.Timestamp.from(Instant.now())
        for ((id, score) in scores) {
            jdbc.update(
                "UPDATE rss_sources SET reliability_score = ?, system_updated_at = ? WHERE id = ?",
                score, now, id
            )
        }
    }
}
