package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.BatchSummaryEntity
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Language
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.repository.BatchSummaryRepository
import com.clipping.mcpserver.repository.RssItemRepository
import com.clipping.mcpserver.support.SqlUtils
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * 기사 요약 JPA 구현. JdbcBatchSummaryStore를 대체한다.
 * 전문 검색 및 복합 동적 쿼리는 JdbcTemplate을 병용한다.
 */
@Repository
@Primary
class JpaBatchSummaryStore(
    private val repository: BatchSummaryRepository,
    private val jdbc: JdbcTemplate,
    private val rssItemRepository: RssItemRepository
) : BatchSummaryStore,
    CategoryOverviewStatsStore,
    SummaryRetentionStore,
    SummarySearchStore,
    DigestCandidateStore,
    SummaryEnrichmentStore,
    SummaryDeliveryStore,
    SentArticleStore,
    SummaryKeywordLookupStore,
    SummaryCategoryCountStore {

    companion object {
        /** 미발송 요약 조회 시 안전 상한. 이 수에 도달하면 경고를 남긴다. */
        private const val MAX_UNSENT_SUMMARIES = 1000
    }

    private val mapper = jacksonObjectMapper()

    override fun findById(id: String): BatchSummary? =
        repository.findById(id).orElse(null)?.toModel()

    override fun findByIds(ids: List<String>): List<BatchSummary> {
        if (ids.isEmpty()) return emptyList()
        return repository.findAllById(ids).map { it.toModel() }
    }

    override fun findByCategoryId(categoryId: String, limit: Int): List<BatchSummary> {
        val safeLimit = limit.coerceIn(1, 10000)
        // DB에서 최신순 정렬과 LIMIT을 함께 적용해 임의 row 선제 제한을 방지한다.
        return repository.findByCategoryIdOrderByCreatedAtDesc(categoryId, PageRequest.of(0, safeLimit))
            .map { it.toModel() }
    }

    override fun findUnsent(categoryId: String?, limit: Int): List<BatchSummary> {
        val safeLimit = limit.coerceIn(1, MAX_UNSENT_SUMMARIES)
        // DB 단에서 LIMIT을 적용해 미발송 요약 전체 로딩을 방지한다.
        val pageable = PageRequest.of(0, safeLimit)
        val entities = if (categoryId != null) {
            repository.findByCategoryIdAndIsSentToSlackFalseOrderByCreatedAtAsc(categoryId, pageable)
        } else {
            repository.findByIsSentToSlackFalse(pageable)
        }
        val results = entities.map { it.toModel() }
        // 안전 상한에 도달하면 경고를 남겨 운영자가 배치 크기를 조정할 수 있게 한다.
        if (results.size >= safeLimit) {
            log.warn { "findUnsent hit safety limit=$safeLimit (categoryId=$categoryId). Some unsent summaries may be deferred." }
        }
        return results
    }

    override fun findByDateRange(
        from: Instant,
        to: Instant,
        categoryId: String?,
        limit: Int?,
    ): List<BatchSummary> {
        val safeLimit = limit?.coerceIn(1, 10000)
        val entities = if (safeLimit != null && categoryId != null) {
            repository.findByCategoryIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                categoryId,
                from,
                to,
                PageRequest.of(0, safeLimit),
            )
        } else if (safeLimit != null) {
            repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, PageRequest.of(0, safeLimit))
        } else if (categoryId != null) {
            repository.findByCategoryIdAndCreatedAtBetween(categoryId, from, to)
        } else {
            repository.findByCreatedAtBetween(from, to)
        }
        val sorted = entities.map { it.toModel() }.sortedByDescending { it.createdAt }
        return sorted
    }

    override fun findTopArticles(
        from: Instant,
        to: Instant,
        categoryId: String?,
        sentiment: String?,
        eventType: String?,
        keyword: String?,
        limit: Int,
    ): List<BatchSummary> {
        val conditions = mutableListOf("created_at >= ?", "created_at < ?")
        val params = mutableListOf<Any>(
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to),
        )
        // 선택 필터는 SQL 조건으로 내려 보내 기간 내 전체 row materialize를 피한다.
        if (!categoryId.isNullOrBlank()) {
            conditions += "category_id = ?"
            params += categoryId
        }
        if (!sentiment.isNullOrBlank()) {
            conditions += "sentiment = ?"
            params += sentiment
        }
        if (!eventType.isNullOrBlank()) {
            conditions += "event_type = ?"
            params += eventType
        }
        if (!keyword.isNullOrBlank()) {
            val pattern = "%${SqlUtils.escapeLike(keyword.trim().lowercase())}%"
            conditions += "LOWER(coalesce(keywords, '')) LIKE ? ESCAPE '\\'"
            params += pattern
        }
        val safeLimit = limit.coerceIn(1, 100)
        params += safeLimit
        val sql = """
            SELECT * FROM batch_summaries
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY importance_score DESC, created_at DESC
            LIMIT ?
        """.trimIndent()
        return jdbc.query(sql, { rs, _ -> mapRowToModel(rs) }, *params.toTypedArray())
    }

    override fun findByCategoryIdsAndDateRange(
        categoryIds: List<String>,
        from: Instant,
        to: Instant,
        limitPerCategory: Int,
    ): List<BatchSummary> {
        if (categoryIds.isEmpty()) return emptyList()
        require(categoryIds.size <= 200) {
            "categoryIds is capped at 200 to avoid pathological IN clauses (got ${categoryIds.size})"
        }
        val safeLimit = limitPerCategory.coerceIn(1, 200)
        // @Primary 라서 이 구현이 prod 기본 경로다. N+1 을 피하기 위해 JpaRepository 반복 대신
        // JdbcTemplate 으로 윈도 함수 native SQL 을 직접 실행한다 (search 등 다른 메서드도 같은 패턴).
        val rowMapper = org.springframework.jdbc.core.RowMapper<BatchSummary> { rs, _ ->
            BatchSummary(
                id = rs.getString("id"),
                originalTitle = rs.getString("original_title"),
                translatedTitle = rs.getString("translated_title"),
                summary = rs.getString("summary"),
                keywords = rs.getString("keywords")
                    ?.let { runCatching { mapper.readValue<List<String>>(it) }.getOrDefault(emptyList()) }
                    ?: emptyList(),
                insights = rs.getString("insights"),
                importanceScore = rs.getFloat("importance_score"),
                sourceLink = rs.getString("source_link"),
                isSentToSlack = rs.getBoolean("is_sent_to_slack"),
                categoryId = rs.getString("category_id"),
                rssItemId = rs.getString("rss_item_id"),
                sentiment = rs.getString("sentiment"),
                eventType = rs.getString("event_type"),
                isFallback = rs.getBoolean("is_fallback"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        }
        val placeholders = categoryIds.joinToString(",") { "?" }
        val fromTs = java.sql.Timestamp.from(from)
        val toTs = java.sql.Timestamp.from(to)
        val sql = """
            SELECT * FROM (
                SELECT bs.*,
                       ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY created_at DESC) AS rn
                FROM batch_summaries bs
                WHERE category_id IN ($placeholders)
                  AND created_at >= ? AND created_at < ?
            ) ranked
            WHERE rn <= ?
            ORDER BY category_id, created_at DESC
        """.trimIndent()
        val params = categoryIds.toMutableList<Any>().apply {
            add(fromTs)
            add(toTs)
            add(safeLimit)
        }
        return jdbc.query(sql, rowMapper, *params.toTypedArray())
    }

    override fun findLatestSentByCategoryId(categoryId: String): BatchSummary? =
        repository.findFirstByCategoryIdAndIsSentToSlackTrueOrderByCreatedAtDesc(categoryId)?.toModel()

    override fun search(categoryId: String, query: String, limit: Int): List<BatchSummary> {
        // 전문 검색은 PostgreSQL tsvector/websearch_to_tsquery를 사용하므로 JdbcTemplate으로 처리한다.
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val safeLimit = limit.coerceIn(1, 100)

        return try {
            jdbc.query(
                """
                SELECT * FROM batch_summaries
                WHERE category_id = ?
                  AND to_tsvector('simple',
                      coalesce(original_title, '') || ' ' ||
                      coalesce(translated_title, '') || ' ' ||
                      coalesce(summary, '') || ' ' ||
                      coalesce(keywords, '')
                  ) @@ websearch_to_tsquery('simple', ?)
                ORDER BY
                  ts_rank(
                      to_tsvector('simple',
                          coalesce(original_title, '') || ' ' ||
                          coalesce(translated_title, '') || ' ' ||
                          coalesce(summary, '') || ' ' ||
                          coalesce(keywords, '')
                      ),
                      websearch_to_tsquery('simple', ?)
                  ) DESC,
                  importance_score DESC,
                  created_at DESC
                LIMIT ?
                """.trimIndent(),
                { rs, _ -> mapRowToModel(rs) },
                categoryId, trimmed, trimmed, safeLimit
            )
        } catch (e: DataAccessException) {
            if (!shouldFallbackFromFullTextSearch(e)) throw e
            // tsvector 미지원 시 LIKE 폴백
            val pattern = "%${SqlUtils.escapeLike(trimmed.lowercase())}%"
            jdbc.query(
                """
                SELECT * FROM batch_summaries
                WHERE category_id = ?
                  AND (
                    LOWER(coalesce(original_title, '')) LIKE ?
                    OR LOWER(coalesce(translated_title, '')) LIKE ?
                    OR LOWER(coalesce(summary, '')) LIKE ?
                    OR LOWER(coalesce(keywords, '')) LIKE ?
                  )
                ORDER BY importance_score DESC, created_at DESC
                LIMIT ?
                """.trimIndent(),
                { rs, _ -> mapRowToModel(rs) },
                categoryId, pattern, pattern, pattern, pattern, safeLimit
            )
        }
    }

    override fun findByKeywordsInRange(
        from: Instant,
        to: Instant,
        keywords: List<String>,
        orderByImportance: Boolean,
        limit: Int
    ): List<BatchSummary> {
        if (keywords.isEmpty()) return emptyList()
        val safeLimit = limit.coerceIn(1, 200)
        val fromTs = java.sql.Timestamp.from(from)
        val toTs = java.sql.Timestamp.from(to)

        val orderBy = if (orderByImportance) {
            "importance_score DESC, created_at DESC"
        } else {
            "created_at DESC"
        }

        // PostgreSQL tsvector 쿼리를 우선 시도하고, 문법 미지원 환경에서만 LIKE 폴백한다.
        return try {
            val tsQuery = buildWebsearchAnyQuery(keywords)
            val sql = """
                SELECT * FROM batch_summaries
                WHERE created_at >= ? AND created_at < ?
                  AND to_tsvector('simple',
                      coalesce(original_title,'') || ' ' ||
                      coalesce(translated_title,'') || ' ' ||
                      coalesce(summary,'') || ' ' ||
                      coalesce(keywords,'')
                  ) @@ websearch_to_tsquery('simple', ?)
                ORDER BY $orderBy
                LIMIT ?
            """.trimIndent()
            jdbc.query(sql, { rs, _ -> mapRowToModel(rs) }, fromTs, toTs, tsQuery, safeLimit)
        } catch (e: DataAccessException) {
            if (!shouldFallbackFromFullTextSearch(e)) throw e
            // H2 등 tsvector 미지원 환경에서는 LIKE 폴백을 사용한다.
            val searchField = "LOWER(COALESCE(original_title,'') || ' ' || " +
                "COALESCE(translated_title,'') || ' ' || COALESCE(summary,'') || ' ' || " +
                "COALESCE(keywords,''))"
            val likeClauses = keywords.map { "$searchField LIKE ? ESCAPE '\\'" }
            val keywordCondition = likeClauses.joinToString(" OR ")

            val sql = """
                SELECT * FROM batch_summaries
                WHERE created_at >= ? AND created_at < ?
                  AND ($keywordCondition)
                ORDER BY $orderBy
                LIMIT ?
            """.trimIndent()

            val params = mutableListOf<Any>(fromTs, toTs)
            keywords.forEach { params += "%${SqlUtils.escapeLike(it.lowercase())}%" }
            params += safeLimit

            jdbc.query(sql, { rs, _ -> mapRowToModel(rs) }, *params.toTypedArray())
        }
    }

    override fun countByItemOlderThan(cutoff: Instant, categoryId: String?): Int {
        // 서브쿼리 JOIN이므로 JdbcTemplate으로 처리한다.
        val cutoffTs = java.sql.Timestamp.from(cutoff)
        return if (categoryId != null) {
            jdbc.queryForObject(
                """SELECT COUNT(*) FROM batch_summaries
                   WHERE rss_item_id IN (SELECT id FROM rss_items WHERE created_at < ? AND category_id = ?)""",
                Int::class.java, cutoffTs, categoryId
            ) ?: 0
        } else {
            jdbc.queryForObject(
                """SELECT COUNT(*) FROM batch_summaries
                   WHERE rss_item_id IN (SELECT id FROM rss_items WHERE created_at < ?)""",
                Int::class.java, cutoffTs
            ) ?: 0
        }
    }

    override fun deleteByItemOlderThan(cutoff: Instant, categoryId: String?): Int {
        // EXISTS 서브쿼리로 IN 서브쿼리보다 효율적으로 삭제한다 (H2/PostgreSQL 모두 호환).
        val cutoffTs = java.sql.Timestamp.from(cutoff)
        return if (categoryId != null) {
            jdbc.update(
                """DELETE FROM batch_summaries bs
                   WHERE EXISTS (
                       SELECT 1 FROM rss_items ri
                       WHERE ri.id = bs.rss_item_id AND ri.created_at < ? AND ri.category_id = ?
                   )""",
                cutoffTs, categoryId
            )
        } else {
            jdbc.update(
                """DELETE FROM batch_summaries bs
                   WHERE EXISTS (
                       SELECT 1 FROM rss_items ri
                       WHERE ri.id = bs.rss_item_id AND ri.created_at < ?
                   )""",
                cutoffTs
            )
        }
    }

    override fun deleteOlderThanExcludingAnchored(cutoff: Instant, limit: Int): Int {
        // chunked DELETE via IN (subquery with LIMIT). H2/PG 양쪽에서 DELETE ... LIMIT
        // 구문 비호환이므로 IN (SELECT ... LIMIT) 패턴 사용 (cleanupReviewItemAudits 와 동일).
        // NOT EXISTS anti-join. NOT IN 은 NULL-safe 하지 않고 PG 플래너가
        // anti-join 으로 변환하지 못해 성능 저하.
        // 두 anchor 테이블 모두 보호 — V117 이 bookmark FK 를 CASCADE 로
        // 재추가했으므로 bookmark 도 NOT EXISTS 로 감싸지 않으면 사용자 북마크 손실.
        val cutoffTs = java.sql.Timestamp.from(cutoff)
        return jdbc.update(
            """
            DELETE FROM batch_summaries
            WHERE id IN (
                SELECT bs.id FROM batch_summaries bs
                WHERE bs.created_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM summary_feedback sf
                      WHERE sf.summary_id = bs.id
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM bookmarked_articles ba
                      WHERE ba.summary_id = bs.id
                  )
                ORDER BY bs.created_at
                LIMIT ?
            )
            """.trimIndent(),
            cutoffTs, limit
        )
    }

    override fun save(summary: BatchSummary): BatchSummary {
        // id 공백 여부를 미리 판정해 UUID 할당 전 신규/기존 구분에 사용한다.
        val isNew = summary.id.isBlank()
        val id = if (isNew) UUID.randomUUID().toString() else summary.id
        val saved = summary.copy(id = id, createdAt = Instant.now())
        // rss_item과 카테고리 일치 여부를 검증한다.
        validateSummaryCategoryConsistency(saved, isNew)
        val entity = saved.toEntity()
        repository.save(entity)
        return saved
    }

    @Transactional
    override fun markSent(ids: List<String>) {
        if (ids.isEmpty()) return
        repository.markSent(ids)
    }

    override fun findSentArticles(
        categoryIds: List<String>,
        keyword: String?,
        dateFrom: Instant?,
        dateTo: Instant?,
        offset: Int,
        limit: Int
    ): List<BatchSummary> {
        // 동적 조건 조합이므로 JdbcTemplate으로 처리한다.
        val (whereClause, params) = buildSentArticlesQuery(
            categoryIds, keyword, dateFrom, dateTo
        )
        val sql = """
            SELECT * FROM batch_summaries
            WHERE $whereClause
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        val allParams = params + listOf(limit, offset)
        return jdbc.query(sql, { rs, _ -> mapRowToModel(rs) }, *allParams.toTypedArray())
    }

    override fun countSentArticles(
        categoryIds: List<String>,
        keyword: String?,
        dateFrom: Instant?,
        dateTo: Instant?
    ): Int {
        val (whereClause, params) = buildSentArticlesQuery(
            categoryIds, keyword, dateFrom, dateTo
        )
        val sql = "SELECT COUNT(*) FROM batch_summaries WHERE $whereClause"
        return jdbc.queryForObject(sql, Int::class.java, *params.toTypedArray()) ?: 0
    }

    override fun countByCategory(from: Instant, to: Instant): Map<String, Int> {
        val rows = jdbc.query(
            """SELECT category_id, COUNT(*) AS cnt
               FROM batch_summaries
               WHERE created_at >= ? AND created_at < ?
               GROUP BY category_id""",
            { rs, _ -> rs.getString("category_id") to rs.getInt("cnt") },
            java.sql.Timestamp.from(from), java.sql.Timestamp.from(to)
        )
        return rows.toMap()
    }

    override fun findBySourceLink(link: String): BatchSummary? =
        repository.findBySourceLink(link)?.toModel()

    override fun findBySourceLinkAndCategoryId(link: String, categoryId: String): BatchSummary? =
        repository.findFirstBySourceLinkAndCategoryId(link, categoryId)?.toModel()

    // ── private helpers ──

    /**
     * 요약이 연결된 rss_item과 같은 카테고리/제목/링크를 가리키는지 검증한다.
     *
     * - 신규 요약(isNew=true)에 rssItemId가 없으면 즉시 거부한다 (INSERT 불변식).
     * - 기존 요약(isNew=false)에 rssItemId가 null이면 V139 ON DELETE SET NULL에 의해
     *   원본 기사가 삭제된 정상 케이스이므로 검증을 건너뛴다.
     */
    private fun validateSummaryCategoryConsistency(summary: BatchSummary, isNew: Boolean) {
        // 신규 요약은 반드시 rss_item 참조가 있어야 한다.
        val rssItemId = summary.rssItemId
        if (rssItemId.isNullOrBlank()) {
            if (isNew) {
                throw InvalidInputException("새 batch_summary 는 rss_item_id 필수")
            }
            // 기존 요약이 retention으로 rss_item이 제거된 경우 — 검증 생략.
            return
        }
        val linkedItem = findLinkedItem(rssItemId)
        if (!summary.isLinkedTo(linkedItem)) {
            throw DataIntegrityViolationException(
                "batch_summary linkage mismatch: summary=${summary.id.ifBlank { "<new>" }}, rssItemId=$rssItemId"
            )
        }
    }

    /** JPA 리포지토리로 rss_item을 조회하여 같은 영속성 컨텍스트에서 일관된 데이터를 참조한다. */
    private fun findLinkedItem(rssItemId: String): RssItem {
        val entity = rssItemRepository.findById(rssItemId).orElse(null)
            ?: throw DataIntegrityViolationException("rss_item not found: $rssItemId")
        return RssItem(
            id = entity.id,
            title = entity.title,
            link = entity.link,
            language = Language.FOREIGN,
            categoryId = entity.categoryId
        )
    }

    private fun buildSentArticlesQuery(
        categoryIds: List<String>,
        keyword: String?,
        dateFrom: Instant?,
        dateTo: Instant?
    ): Pair<String, List<Any>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        conditions += "is_sent_to_slack = TRUE"

        if (categoryIds.isEmpty()) {
            conditions += "1 = 0"
        } else {
            val ph = categoryIds.joinToString(",") { "?" }
            conditions += "category_id IN ($ph)"
            params.addAll(categoryIds)
        }

        if (!keyword.isNullOrBlank()) {
            val pattern = "%${SqlUtils.escapeLike(keyword.trim().lowercase())}%"
            conditions += """(
                LOWER(coalesce(original_title, '')) LIKE ?
                OR LOWER(coalesce(translated_title, '')) LIKE ?
                OR LOWER(coalesce(summary, '')) LIKE ?
                OR LOWER(coalesce(keywords, '')) LIKE ?
            )"""
            params.addAll(listOf(pattern, pattern, pattern, pattern))
        }

        if (dateFrom != null) {
            conditions += "created_at >= ?"
            params += java.sql.Timestamp.from(dateFrom)
        }
        if (dateTo != null) {
            conditions += "created_at <= ?"
            params += java.sql.Timestamp.from(dateTo)
        }

        return conditions.joinToString(" AND ") to params
    }

    /** ResultSet에서 BatchSummary 모델로 직접 매핑한다. */
    private fun mapRowToModel(rs: java.sql.ResultSet): BatchSummary =
        BatchSummary(
            id = rs.getString("id"),
            originalTitle = rs.getString("original_title"),
            translatedTitle = rs.getString("translated_title"),
            summary = rs.getString("summary"),
            insights = rs.getString("insights"),
            keywords = parseKeywords(rs.getString("id"), rs.getString("keywords")),
            importanceScore = rs.getFloat("importance_score"),
            sourceLink = rs.getString("source_link"),
            isSentToSlack = rs.getBoolean("is_sent_to_slack"),
            categoryId = rs.getString("category_id"),
            rssItemId = rs.getString("rss_item_id"),
            sentiment = rs.getString("sentiment"),
            eventType = rs.getString("event_type"),
            isFallback = rs.getBoolean("is_fallback"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )

    private fun parseKeywords(summaryId: String, raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val trimmed = raw.trim()
        return runCatching {
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                mapper.readValue(trimmed)
            } else {
                trimmed.split(",").map { it.trim() }.filter { it.isNotBlank() }
            }
        }.getOrElse { e ->
            log.warn(e) { "Failed to parse keywords for summary=$summaryId, raw=$trimmed" }
            emptyList()
        }
    }

    private fun BatchSummaryEntity.toModel() = BatchSummary(
        id = id,
        originalTitle = originalTitle,
        translatedTitle = translatedTitle,
        summary = summary,
        insights = insights,
        keywords = parseKeywords(id, keywords),
        importanceScore = importanceScore,
        sourceLink = sourceLink,
        isSentToSlack = isSentToSlack,
        categoryId = categoryId,
        rssItemId = rssItemId,
        sentiment = sentiment,
        eventType = eventType,
        isFallback = isFallback,
        createdAt = createdAt
    )

    private fun BatchSummary.toEntity() = BatchSummaryEntity(
        id = id,
        originalTitle = originalTitle,
        translatedTitle = translatedTitle,
        summary = summary,
        insights = insights,
        keywords = mapper.writeValueAsString(keywords),
        importanceScore = importanceScore,
        sourceLink = sourceLink,
        isSentToSlack = isSentToSlack,
        categoryId = categoryId,
        rssItemId = rssItemId,
        sentiment = sentiment,
        eventType = eventType,
        isFallback = isFallback,
        createdAt = createdAt
    )

    override fun findDigestCandidatesWithSource(
        categoryId: String,
        since: Instant,
        limit: Int,
    ): Pair<List<BatchSummary>, Map<String, String?>> {
        val sourceMap = mutableMapOf<String, String?>()
        // LEFT JOIN으로 rss_source_id를 사이드카로 읽어 digest 공정 배분에 사용한다.
        val summaries = jdbc.query(
            """
            SELECT bs.*, ri.rss_source_id AS src_id
              FROM batch_summaries bs
              LEFT JOIN rss_items ri ON ri.id = bs.rss_item_id
             WHERE bs.category_id = ?
               AND bs.created_at >= ?
             ORDER BY bs.created_at DESC
             LIMIT ?
            """.trimIndent(),
            { rs, rowNum ->
                val summary = mapRowToModel(rs)
                // RSS item 이 없는 수동/SearchCo 기사도 도메인 버킷으로 묶어 소스 다양성 로직을 유지한다.
                sourceMap[summary.id] = rs.getString("src_id") ?: sourceBucketFromLink(summary.sourceLink)
                summary
            },
            categoryId,
            java.sql.Timestamp.from(since),
            limit,
        )
        return summaries to sourceMap
    }

    /** source_link 에서 digest 소스 다양성용 호스트 버킷을 추출한다. */
    private fun sourceBucketFromLink(sourceLink: String): String? =
        runCatching {
            URI(sourceLink.trim()).host
                ?.lowercase()
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    override fun updateAiSummary(
        id: String,
        summary: String,
        keywords: String?,
        insights: String?,
        importanceScore: Float,
        sentiment: String?,
        eventType: String?
    ) {
        jdbc.update(
            """UPDATE batch_summaries
               SET summary = ?, keywords = ?, insights = ?,
                   importance_score = ?, sentiment = ?, event_type = ?
               WHERE id = ?""",
            summary, keywords, insights, importanceScore, sentiment, eventType, id
        )
    }

    override fun findFallbacksWithin24h(limit: Int): List<BatchSummary> {
        // H2/PostgreSQL 호환: 파라미터 바인딩으로 INTERVAL 문법 회피
        return jdbc.query(
            """
            SELECT * FROM batch_summaries
            WHERE is_fallback = TRUE
              AND created_at > ?
            ORDER BY created_at ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> mapRowToModel(rs) },
            java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofHours(24))),
            limit
        )
    }

    override fun updateSummaryContent(
        summaryId: String,
        translatedTitle: String?,
        summary: String,
        keywords: List<String>,
        importanceScore: Float,
        sentiment: String?,
        eventType: String?,
    ) {
        // 재요약 성공 시 내용을 갱신하고 is_fallback을 FALSE로 전환한다.
        val keywordsJson = if (keywords.isNotEmpty()) {
            mapper.writeValueAsString(keywords)
        } else null

        jdbc.update(
            """
            UPDATE batch_summaries
            SET translated_title = ?, summary = ?, keywords = ?,
                importance_score = ?, sentiment = ?, event_type = ?,
                is_fallback = FALSE
            WHERE id = ?
            """.trimIndent(),
            translatedTitle, summary, keywordsJson,
            importanceScore, sentiment, eventType,
            summaryId
        )
    }

    override fun findUnsummarizedCompetitorArticles(maxSummaryLength: Int, limit: Int): List<BatchSummary> {
        val rowMapper = org.springframework.jdbc.core.RowMapper<BatchSummary> { rs, _ ->
            BatchSummary(
                id = rs.getString("id"),
                originalTitle = rs.getString("original_title"),
                translatedTitle = rs.getString("translated_title"),
                summary = rs.getString("summary"),
                keywords = rs.getString("keywords")?.let { runCatching { mapper.readValue<List<String>>(it) }.getOrDefault(emptyList()) } ?: emptyList(),
                insights = rs.getString("insights"),
                importanceScore = rs.getFloat("importance_score"),
                sourceLink = rs.getString("source_link"),
                isSentToSlack = rs.getBoolean("is_sent_to_slack"),
                categoryId = rs.getString("category_id"),
                rssItemId = rs.getString("rss_item_id"),
                sentiment = rs.getString("sentiment"),
                eventType = rs.getString("event_type"),
                isFallback = rs.getBoolean("is_fallback"),
                createdAt = rs.getTimestamp("created_at").toInstant()
            )
        }
        return jdbc.query(
            """SELECT * FROM batch_summaries
               WHERE category_id = '__competitor__'
                 AND (summary IS NULL OR LENGTH(summary) <= ?)
                 AND (keywords IS NULL OR keywords = '[]')
               ORDER BY created_at DESC
               LIMIT ?""",
            rowMapper, maxSummaryLength, limit
        )
    }

    override fun searchAcrossCategories(query: String, limit: Int): List<BatchSummary> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val safeLimit = limit.coerceIn(1, 100)

        // PostgreSQL tsvector 전문 검색을 우선 시도하고, 문법 미지원 환경에서만 LIKE 폴백한다.
        return try {
            jdbc.query(
                """
                SELECT * FROM batch_summaries
                WHERE to_tsvector('simple',
                    coalesce(original_title, '') || ' ' ||
                    coalesce(translated_title, '') || ' ' ||
                    coalesce(summary, '') || ' ' ||
                    coalesce(keywords, '')
                ) @@ websearch_to_tsquery('simple', ?)
                ORDER BY importance_score DESC, created_at DESC
                LIMIT ?
                """.trimIndent(),
                { rs, _ -> mapRowToModel(rs) },
                trimmed,
                safeLimit,
            )
        } catch (e: DataAccessException) {
            if (!shouldFallbackFromFullTextSearch(e)) throw e
            val pattern = "%${SqlUtils.escapeLike(trimmed.lowercase())}%"
            jdbc.query(
                """
                SELECT * FROM batch_summaries
                WHERE (
                    LOWER(coalesce(original_title, '')) LIKE ? ESCAPE '\'
                    OR LOWER(coalesce(translated_title, '')) LIKE ? ESCAPE '\'
                    OR LOWER(coalesce(summary, '')) LIKE ? ESCAPE '\'
                    OR LOWER(coalesce(keywords, '')) LIKE ? ESCAPE '\'
                )
                ORDER BY importance_score DESC, created_at DESC
                LIMIT ?
                """.trimIndent(),
                { rs, _ -> mapRowToModel(rs) },
                pattern, pattern, pattern, pattern,
                safeLimit,
            )
        }
    }

    fun findByLocalDateRange(
        categoryId: String?,
        from: LocalDate,
        to: LocalDate,
        limit: Int,
    ): List<BatchSummary> {
        val safeLimit = limit.coerceIn(1, 1000)
        val fromTs = java.sql.Timestamp.from(
            from.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
        )
        val toTs = java.sql.Timestamp.from(
            to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
        )

        return if (categoryId != null) {
            jdbc.query(
                """SELECT * FROM batch_summaries
                   WHERE category_id = ?
                     AND created_at >= ? AND created_at < ?
                   ORDER BY created_at DESC
                   LIMIT ?""",
                { rs, _ -> mapRowToModel(rs) },
                categoryId, fromTs, toTs, safeLimit,
            )
        } else {
            jdbc.query(
                """SELECT * FROM batch_summaries
                   WHERE created_at >= ? AND created_at < ?
                   ORDER BY created_at DESC
                   LIMIT ?""",
                { rs, _ -> mapRowToModel(rs) },
                fromTs, toTs, safeLimit,
            )
        }
    }

    override fun searchInDateRange(
        categoryId: String?,
        query: String,
        from: LocalDate,
        to: LocalDate,
        limit: Int,
    ): List<BatchSummary> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val safeLimit = limit.coerceIn(1, 100)
        val fromTs = java.sql.Timestamp.from(
            from.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
        )
        val toTs = java.sql.Timestamp.from(
            to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
        )

        return try {
            searchInDateRangeWithTsVector(categoryId, trimmed, fromTs, toTs, safeLimit)
        } catch (e: DataAccessException) {
            if (!shouldFallbackFromFullTextSearch(e)) throw e
            searchInDateRangeWithLike(categoryId, trimmed, fromTs, toTs, safeLimit)
        }
    }

    private fun searchInDateRangeWithTsVector(
        categoryId: String?,
        query: String,
        fromTs: java.sql.Timestamp,
        toTs: java.sql.Timestamp,
        limit: Int,
    ): List<BatchSummary> {
        val categoryClause = if (categoryId != null) "AND category_id = ?" else ""
        val sql = """
            SELECT * FROM batch_summaries
            WHERE created_at >= ? AND created_at < ?
              $categoryClause
              AND to_tsvector('simple',
                  coalesce(original_title, '') || ' ' ||
                  coalesce(translated_title, '') || ' ' ||
                  coalesce(summary, '') || ' ' ||
                  coalesce(keywords, '')
              ) @@ websearch_to_tsquery('simple', ?)
            ORDER BY
              ts_rank(
                  to_tsvector('simple',
                      coalesce(original_title, '') || ' ' ||
                      coalesce(translated_title, '') || ' ' ||
                      coalesce(summary, '') || ' ' ||
                      coalesce(keywords, '')
                  ),
                  websearch_to_tsquery('simple', ?)
              ) DESC,
              importance_score DESC,
              created_at DESC
            LIMIT ?
        """.trimIndent()
        val params = mutableListOf<Any>(fromTs, toTs)
        if (categoryId != null) params += categoryId
        params.addAll(listOf(query, query, limit))
        return jdbc.query(sql, { rs, _ -> mapRowToModel(rs) }, *params.toTypedArray())
    }

    private fun searchInDateRangeWithLike(
        categoryId: String?,
        query: String,
        fromTs: java.sql.Timestamp,
        toTs: java.sql.Timestamp,
        limit: Int,
    ): List<BatchSummary> {
        val categoryClause = if (categoryId != null) "AND category_id = ?" else ""
        val pattern = "%${SqlUtils.escapeLike(query.lowercase())}%"
        val sql = """
            SELECT * FROM batch_summaries
            WHERE created_at >= ? AND created_at < ?
              $categoryClause
              AND (
                LOWER(coalesce(original_title, '')) LIKE ? ESCAPE '\'
                OR LOWER(coalesce(translated_title, '')) LIKE ? ESCAPE '\'
                OR LOWER(coalesce(summary, '')) LIKE ? ESCAPE '\'
                OR LOWER(coalesce(keywords, '')) LIKE ? ESCAPE '\'
              )
            ORDER BY importance_score DESC, created_at DESC
            LIMIT ?
        """.trimIndent()
        val params = mutableListOf<Any>(fromTs, toTs)
        if (categoryId != null) params += categoryId
        params.addAll(listOf(pattern, pattern, pattern, pattern, limit))
        return jdbc.query(sql, { rs, _ -> mapRowToModel(rs) }, *params.toTypedArray())
    }

    override fun findByImportanceScoreGreaterThan(
        categoryId: String,
        minScore: Double,
        sinceDate: LocalDate,
        limit: Int,
    ): List<BatchSummary> {
        val safeLimit = limit.coerceIn(1, 100)
        val sinceTs = java.sql.Timestamp.from(
            sinceDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
        )
        return jdbc.query(
            """SELECT * FROM batch_summaries
               WHERE category_id = ?
                 AND importance_score >= ?
                 AND created_at >= ?
               ORDER BY importance_score DESC, created_at DESC
               LIMIT ?""",
            { rs, _ -> mapRowToModel(rs) },
            categoryId, minScore, sinceTs, safeLimit,
        )
    }

    override fun fetchCategoryOverviewStats(
        categoryId: String,
        since7Days: LocalDate,
    ): CategoryOverviewStatsStore.CategoryOverviewRow? {
        val sinceTs = java.sql.Timestamp.from(
            since7Days.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()
        )
        // 단일 쿼리로 소스 수, 구독자 수, 최근 7일 기사 수, 평균 중요도, 마지막 갱신을 조회한다
        return jdbc.query(
            """
            SELECT
                (SELECT COUNT(*) FROM rss_sources
                 WHERE category_id = ? AND is_active = TRUE) AS source_count,
                (SELECT COUNT(*) FROM clipping_user_requests
                 WHERE approved_category_id = ?
                   AND status = 'APPROVED') AS subscriber_count,
                COUNT(*) AS recent_item_count_7d,
                COALESCE(AVG(importance_score), 0.0) AS avg_importance_7d,
                MAX(created_at) AS last_updated_at
            FROM batch_summaries
            WHERE category_id = ? AND created_at >= ?
            """.trimIndent(),
            { rs, _ ->
                CategoryOverviewStatsStore.CategoryOverviewRow(
                    sourceCount = rs.getInt("source_count"),
                    subscriberCount = rs.getInt("subscriber_count"),
                    recentItemCount7Days = rs.getInt("recent_item_count_7d"),
                    avgImportance7Days = rs.getDouble("avg_importance_7d"),
                    lastUpdatedAt = rs.getTimestamp("last_updated_at")?.toInstant(),
                )
            },
            categoryId, categoryId, categoryId, sinceTs,
        ).firstOrNull()
    }
}
