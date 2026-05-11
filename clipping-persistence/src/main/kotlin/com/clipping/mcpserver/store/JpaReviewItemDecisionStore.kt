package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.ReviewItemDecisionEntity
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.model.ReviewItemDecision
import com.clipping.mcpserver.repository.ReviewItemDecisionRepository
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 리뷰 의사결정 JPA 구현. JdbcReviewItemDecisionStore를 대체한다.
 * 조인 집계와 upsert 동시성 보정은 JdbcTemplate을 병용한다.
 */
@Repository
@Primary
class JpaReviewItemDecisionStore(
    private val repository: ReviewItemDecisionRepository,
    private val jdbc: JdbcTemplate
) : ReviewItemDecisionStore {

    override fun findBySummaryId(summaryId: String): ReviewItemDecision? =
        repository.findBySummaryId(summaryId)?.toModel()

    override fun findBySummaryIds(summaryIds: List<String>): List<ReviewItemDecision> {
        if (summaryIds.isEmpty()) return emptyList()
        return repository.findBySummaryIdIn(summaryIds).map { it.toModel() }
    }

    override fun findReviewedBetween(from: Instant, to: Instant, categoryId: String?): List<ReviewItemDecision> {
        val entities = if (categoryId.isNullOrBlank()) {
            repository.findByReviewedAtBetween(from, to)
        } else {
            repository.findByReviewedAtBetweenAndCategoryId(from, to, categoryId)
        }
        return entities.map { it.toModel() }.sortedBy { it.reviewedAt }
    }

    @Transactional
    override fun upsert(decision: ReviewItemDecision): ReviewItemDecision {
        val now = Instant.now()
        val persisted = decision.copy(
            reviewedAt = decision.reviewedAt ?: now,
            updatedAt = now
        )

        // 기존 row가 있으면 update-first
        val existing = repository.findBySummaryId(persisted.summaryId)
        if (existing != null) {
            existing.categoryId = persisted.categoryId
            existing.status = persisted.status.name
            existing.reason = persisted.reason
            existing.reviewedBy = persisted.reviewedBy
            existing.reviewedAt = persisted.reviewedAt
            existing.updatedAt = now
            repository.save(existing)
            return repository.findBySummaryId(persisted.summaryId)?.toModel()
                ?: throw IllegalStateException("Failed to load clipping_review_items after upsert for summaryId=${persisted.summaryId}")
        }

        // 신규 insert
        return try {
            repository.save(persisted.toEntity())
            repository.findBySummaryId(persisted.summaryId)?.toModel()
                ?: throw IllegalStateException("Failed to load clipping_review_items after insert for summaryId=${persisted.summaryId}")
        } catch (_: org.springframework.dao.DataIntegrityViolationException) {
            // 동시 insert 경합 시 update로 보정한다.
            val retryEntity = repository.findBySummaryId(persisted.summaryId)
                ?: throw IllegalStateException("Failed to upsert clipping_review_items for summaryId=${persisted.summaryId}")
            retryEntity.categoryId = persisted.categoryId
            retryEntity.status = persisted.status.name
            retryEntity.reason = persisted.reason
            retryEntity.reviewedBy = persisted.reviewedBy
            retryEntity.reviewedAt = persisted.reviewedAt
            retryEntity.updatedAt = now
            repository.save(retryEntity)
            retryEntity.toModel()
        }
    }

    override fun countByStatusGroupedByCategory(from: Instant, to: Instant): List<CategoryStatusCount> {
        // GROUP BY 집계는 JdbcTemplate으로 처리한다.
        return jdbc.query(
            """
            SELECT category_id, status, COUNT(*) AS cnt
            FROM clipping_review_items
            WHERE created_at >= ? AND created_at < ?
            GROUP BY category_id, status
            """.trimIndent(),
            { rs, _ ->
                val categoryId = rs.getString("category_id") ?: return@query null
                val status = rs.getString("status") ?: return@query null
                CategoryStatusCount(
                    categoryId = categoryId,
                    status = status,
                    count = rs.getInt("cnt")
                )
            },
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to)
        ).mapNotNull { it }
            .filter { it.categoryId.isNotBlank() && it.status.isNotBlank() }
    }

    override fun findExcludedItems(categoryId: String, limit: Int): List<ExcludedItemRow> {
        // JOIN 쿼리는 JdbcTemplate으로 처리한다.
        val safeLimit = limit.coerceIn(1, 50)
        return jdbc.query(
            """
            SELECT
                COALESCE(bs.translated_title, bs.original_title) AS title,
                ri.reason,
                bs.importance_score AS score,
                ri.created_at AS excluded_at
            FROM clipping_review_items ri
            JOIN batch_summaries bs ON bs.id = ri.summary_id
            WHERE ri.category_id = ? AND ri.status = 'EXCLUDE'
            ORDER BY ri.created_at DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                val excludedAt = rs.getTimestamp("excluded_at")?.toInstant() ?: return@query null
                ExcludedItemRow(
                    title = rs.getString("title") ?: "",
                    reason = rs.getString("reason"),
                    score = rs.getFloat("score"),
                    excludedAt = excludedAt
                )
            },
            categoryId,
            safeLimit
        ).mapNotNull { it }
    }

    override fun countByCategory(): List<ReviewCategoryCounts> {
        // 미발송 batch_summaries 기준으로 카테고리별 상태를 집계한다.
        // H2 호환: COUNT(*) FILTER(WHERE ...) 대신 COUNT(CASE WHEN ... THEN 1 END) 사용
        return jdbc.query(
            """
            SELECT
                ri.category_id,
                c.name AS category_name,
                COUNT(*) AS total_count,
                COUNT(CASE WHEN ri.status = 'REVIEW' THEN 1 END) AS review_count,
                COUNT(CASE WHEN ri.status = 'INCLUDE' THEN 1 END) AS include_count,
                COUNT(CASE WHEN ri.status = 'EXCLUDE' THEN 1 END) AS exclude_count,
                COUNT(CASE WHEN ri.suggested_status = 'INCLUDE' THEN 1 END) AS suggested_include_count
            FROM clipping_review_items ri
            JOIN batch_summaries bs ON bs.id = ri.summary_id
            JOIN batch_categories c ON c.id = ri.category_id
            WHERE bs.is_sent_to_slack = false
            GROUP BY ri.category_id, c.name
            """.trimIndent(),
            { rs, _ ->
                val categoryId = rs.getString("category_id") ?: return@query null
                ReviewCategoryCounts(
                    categoryId = categoryId,
                    categoryName = rs.getString("category_name") ?: "",
                    totalCount = rs.getInt("total_count"),
                    reviewCount = rs.getInt("review_count"),
                    includeCount = rs.getInt("include_count"),
                    excludeCount = rs.getInt("exclude_count"),
                    suggestedIncludeCount = rs.getInt("suggested_include_count")
                )
            },
            *emptyArray<Any>()
        ).mapNotNull { it }
    }

    override fun getAccuracyStats(from: Instant, to: Instant): List<AccuracyRow> {
        // 사람이 검토한 항목만 대상으로 AI 제안 vs 실제 결과를 카테고리별로 집계한다.
        return jdbc.query(
            """
            SELECT
                ri.category_id,
                c.name AS category_name,
                ri.suggested_status,
                ri.status,
                COUNT(*) AS cnt
            FROM clipping_review_items ri
            JOIN batch_categories c ON c.id = ri.category_id
            WHERE ri.suggested_status IS NOT NULL
              AND ri.reviewed_at >= ?
              AND ri.reviewed_at < ?
              AND ri.reviewed_by != 'policy-auto'
            GROUP BY ri.category_id, c.name, ri.suggested_status, ri.status
            """.trimIndent(),
            { rs, _ ->
                val categoryId = rs.getString("category_id") ?: return@query null
                val suggestedStatus = rs.getString("suggested_status") ?: return@query null
                val actualStatus = rs.getString("status") ?: return@query null
                AccuracyRow(
                    categoryId = categoryId,
                    categoryName = rs.getString("category_name") ?: "",
                    suggestedStatus = suggestedStatus,
                    actualStatus = actualStatus,
                    count = rs.getInt("cnt")
                )
            },
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to)
        ).mapNotNull { it }
    }

    override fun findAutoExcluded(
        since: Instant,
        categoryId: String?,
        reasonPrefix: String?,
        limit: Int,
        offset: Int,
    ): List<AutoExcludedRow> {
        // JOIN + 동적 WHERE 는 JdbcTemplate 로 처리 — JPQL 보다 명시적이고 PG/H2 공통.
        // rss_items / rss_sources 는 LEFT JOIN — FK 는 있지만 CASCADE 미설정인 orphan 가능성 방어.
        val conditions = autoExcludedWhere(categoryId, reasonPrefix)
        val args = autoExcludedArgs(since, categoryId, reasonPrefix) + listOf(limit, offset)
        return jdbc.query(
            """
            SELECT
                ri.summary_id                                    AS summary_id,
                COALESCE(bs.translated_title, bs.original_title) AS title,
                bs.original_title                                AS original_title,
                bs.translated_title                              AS translated_title,
                bs.category_id                                   AS category_id,
                c.name                                           AS category_name,
                bs.importance_score                              AS score,
                ri.reason                                        AS reason,
                ri.reviewed_at                                   AS excluded_at,
                bs.summary                                       AS summary,
                ri2.link                                         AS source_url,
                rs.name                                          AS source_name,
                ri2.published_at                                 AS published_at,
                bs.event_type                                    AS event_type,
                bs.sentiment                                     AS sentiment
            FROM clipping_review_items ri
            JOIN batch_summaries bs          ON bs.id = ri.summary_id
            JOIN batch_categories c          ON c.id = ri.category_id
            LEFT JOIN rss_items ri2          ON ri2.id = bs.rss_item_id
            LEFT JOIN rss_sources rs         ON rs.id = ri2.rss_source_id
            WHERE ri.status = 'EXCLUDE'
              AND ri.reviewed_by = 'policy-auto'
              AND ri.reviewed_at >= ?
              $conditions
            ORDER BY ri.reviewed_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ ->
                val summaryId = rs.getString("summary_id") ?: return@query null
                val excludedAt = rs.getTimestamp("excluded_at")?.toInstant() ?: return@query null
                AutoExcludedRow(
                    summaryId = summaryId,
                    title = rs.getString("title") ?: "",
                    originalTitle = rs.getString("original_title") ?: "",
                    translatedTitle = rs.getString("translated_title"),
                    categoryId = rs.getString("category_id") ?: "",
                    categoryName = rs.getString("category_name") ?: "",
                    score = rs.getFloat("score"),
                    reason = rs.getString("reason") ?: "",
                    excludedAt = excludedAt,
                    summary = rs.getString("summary") ?: "",
                    sourceUrl = rs.getString("source_url"),
                    sourceName = rs.getString("source_name"),
                    publishedAt = rs.getTimestamp("published_at")?.toInstant(),
                    eventType = rs.getString("event_type"),
                    sentiment = rs.getString("sentiment"),
                )
            },
            *args.toTypedArray(),
        ).mapNotNull { it }
    }

    override fun countAutoExcluded(
        since: Instant,
        categoryId: String?,
        reasonPrefix: String?,
    ): Int {
        val conditions = autoExcludedWhere(categoryId, reasonPrefix)
        val args = autoExcludedArgs(since, categoryId, reasonPrefix)
        return jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM clipping_review_items ri
            WHERE ri.status = 'EXCLUDE'
              AND ri.reviewed_by = 'policy-auto'
              AND ri.reviewed_at >= ?
              $conditions
            """.trimIndent(),
            Int::class.java,
            *args.toTypedArray(),
        ) ?: 0
    }

    override fun breakdownAutoExcludedByReason(
        since: Instant,
        categoryId: String?,
        reasonPrefix: String?,
    ): Map<String, Int> {
        val conditions = autoExcludedWhere(categoryId, reasonPrefix)
        val args = autoExcludedArgs(since, categoryId, reasonPrefix)
        val rows = jdbc.query(
            """
            SELECT ri.reason AS reason, COUNT(*) AS cnt
            FROM clipping_review_items ri
            WHERE ri.status = 'EXCLUDE'
              AND ri.reviewed_by = 'policy-auto'
              AND ri.reviewed_at >= ?
              $conditions
            GROUP BY ri.reason
            """.trimIndent(),
            { rs, _ -> (rs.getString("reason") ?: "") to rs.getInt("cnt") },
            *args.toTypedArray(),
        )
        // NULL reason 행은 집계에서 제외 — 방어적 필터
        return rows.filter { it.first.isNotBlank() }.toMap()
    }

    /** 동적 WHERE 조각 — categoryId / reasonPrefix 가 있을 때만 조건을 추가. */
    private fun autoExcludedWhere(categoryId: String?, reasonPrefix: String?): String = buildString {
        if (!categoryId.isNullOrBlank()) append(" AND ri.category_id = ?")
        if (!reasonPrefix.isNullOrBlank()) append(" AND ri.reason LIKE ?")
    }

    /** 동적 바인드 인자 — [autoExcludedWhere] 의 ? 순서와 정렬해야 한다. */
    private fun autoExcludedArgs(
        since: Instant,
        categoryId: String?,
        reasonPrefix: String?,
    ): List<Any> {
        val args = mutableListOf<Any>(java.sql.Timestamp.from(since))
        if (!categoryId.isNullOrBlank()) args.add(categoryId)
        // LIKE prefix — 사용자 입력에서 와일드카드 메타문자는 제거하고 suffix 로 `%` 만 허용
        if (!reasonPrefix.isNullOrBlank()) args.add("${reasonPrefix.replace("%", "").replace("_", "")}%")
        return args
    }

    // ── private helpers ──

    private fun ReviewItemDecisionEntity.toModel() = ReviewItemDecision(
        summaryId = summaryId,
        categoryId = categoryId,
        status = ReviewDecisionStatus.valueOf(status),
        reason = reason,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ReviewItemDecision.toEntity() = ReviewItemDecisionEntity(
        summaryId = summaryId,
        categoryId = categoryId,
        status = status.name,
        reason = reason,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
