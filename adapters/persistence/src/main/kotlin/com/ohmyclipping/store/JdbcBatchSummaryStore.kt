package com.ohmyclipping.store

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Language
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.support.SqlUtils
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private val log = KotlinLogging.logger {}

@Repository
class JdbcBatchSummaryStore(
    private val jdbc: JdbcTemplate
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

    private val mapper = jacksonObjectMapper()

    private val rowMapper = RowMapper<BatchSummary> { rs, _ ->
        val keywordsJson = rs.getString("keywords")
        BatchSummary(
            id = rs.getString("id"),
            originalTitle = rs.getString("original_title"),
            translatedTitle = rs.getString("translated_title"),
            summary = rs.getString("summary"),
            insights = rs.getString("insights"),
            keywords = parseKeywords(rs.getString("id"), keywordsJson),
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

    private fun parseKeywords(summaryId: String, raw: String?): List<String> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }

        val trimmed = raw.trim()
        return runCatching {
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                mapper.readValue(trimmed)
            } else {
                trimmed
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
        }.getOrElse { e ->
            log.warn(e) { "Failed to parse keywords for summary=$summaryId, raw=$trimmed" }
            emptyList()
        }
    }

    override fun findById(id: String): BatchSummary? =
        jdbc.query("SELECT * FROM batch_summaries WHERE id = ?", rowMapper, id).firstOrNull()

    override fun findByIds(ids: List<String>): List<BatchSummary> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        return jdbc.query(
            "SELECT * FROM batch_summaries WHERE id IN ($placeholders)",
            rowMapper, *ids.toTypedArray()
        )
    }

    override fun findByCategoryId(categoryId: String, limit: Int): List<BatchSummary> {
        val safeLimit = limit.coerceIn(1, 10000)
        return jdbc.query(
            "SELECT * FROM batch_summaries WHERE category_id = ? ORDER BY created_at DESC LIMIT ?",
            rowMapper, categoryId, safeLimit
        )
    }

    override fun findLatestSentByCategoryId(categoryId: String): BatchSummary? =
        jdbc.query(
            """
            SELECT * FROM batch_summaries
            WHERE category_id = ? AND is_sent_to_slack = TRUE
            ORDER BY created_at DESC
            LIMIT 1
            """.trimIndent(),
            rowMapper,
            categoryId
        ).firstOrNull()

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

        // 정렬 기준을 결정한다.
        val orderBy = if (orderByImportance) {
            "importance_score DESC, created_at DESC"
        } else {
            "created_at DESC"
        }

        // PostgreSQL tsvector 쿼리를 우선 시도하고, 문법 미지원 환경에서만 LIKE 폴백한다.
        return try {
            findByKeywordsInRangeWithTsVector(fromTs, toTs, keywords, orderBy, safeLimit)
        } catch (e: DataAccessException) {
            if (!shouldFallbackFromFullTextSearch(e)) throw e
            findByKeywordsInRangeWithLike(fromTs, toTs, keywords, orderBy, safeLimit)
        }
    }

    /** PostgreSQL to_tsvector/websearch_to_tsquery를 사용한 키워드 검색. GIN 인덱스를 활용할 수 있다. */
    private fun findByKeywordsInRangeWithTsVector(
        fromTs: java.sql.Timestamp,
        toTs: java.sql.Timestamp,
        keywords: List<String>,
        orderBy: String,
        limit: Int
    ): List<BatchSummary> {
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
        return jdbc.query(sql, rowMapper, fromTs, toTs, tsQuery, limit)
    }

    /** LIKE 폴백: H2 등 tsvector 미지원 환경에서 사용한다. */
    private fun findByKeywordsInRangeWithLike(
        fromTs: java.sql.Timestamp,
        toTs: java.sql.Timestamp,
        keywords: List<String>,
        orderBy: String,
        limit: Int
    ): List<BatchSummary> {
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
        params += limit

        return jdbc.query(sql, rowMapper, *params.toTypedArray())
    }

    override fun findByDateRange(
        from: Instant,
        to: Instant,
        categoryId: String?,
        limit: Int?,
    ): List<BatchSummary> {
        val fromTs = java.sql.Timestamp.from(from)
        val toTs = java.sql.Timestamp.from(to)
        // limit 가 주어지면 [1, 10000] 으로 clamp. 과도한 pageful 요청을 차단해 OOM 을 막는다.
        val safeLimit = limit?.coerceIn(1, 10000)
        val limitClause = safeLimit?.let { " LIMIT $it" } ?: ""
        return if (categoryId != null) {
            jdbc.query(
                """SELECT * FROM batch_summaries
                   WHERE created_at >= ? AND created_at < ?
                     AND category_id = ?
                   ORDER BY created_at DESC$limitClause""",
                rowMapper, fromTs, toTs, categoryId
            )
        } else {
            jdbc.query(
                """SELECT * FROM batch_summaries
                   WHERE created_at >= ? AND created_at < ?
                   ORDER BY created_at DESC$limitClause""",
                rowMapper, fromTs, toTs
            )
        }
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
        // 운영 조회 필터를 SQL에 반영해 기간 전체 row를 JVM으로 가져오지 않는다.
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
        params += limit.coerceIn(1, 100)
        val sql = """
            SELECT * FROM batch_summaries
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY importance_score DESC, created_at DESC
            LIMIT ?
        """.trimIndent()
        return jdbc.query(sql, rowMapper, *params.toTypedArray())
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
        val fromTs = java.sql.Timestamp.from(from)
        val toTs = java.sql.Timestamp.from(to)
        val placeholders = categoryIds.joinToString(",") { "?" }
        // 윈도 함수로 카테고리별 top-N 을 한 쿼리에 뽑는다. H2(MODE=PostgreSQL) + Postgres 양쪽
        // 지원. rowMapper 는 컬럼명 기반이므로 SELECT * 로 rn 이 포함돼도 무해하다.
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

    override fun findUnsent(categoryId: String?, limit: Int): List<BatchSummary> {
        val safeLimit = limit.coerceIn(1, 10000)
        return if (categoryId != null) {
            jdbc.query(
                "SELECT * FROM batch_summaries WHERE is_sent_to_slack = FALSE AND category_id = ? ORDER BY created_at LIMIT ?",
                rowMapper, categoryId, safeLimit
            )
        } else {
            jdbc.query(
                "SELECT * FROM batch_summaries WHERE is_sent_to_slack = FALSE ORDER BY created_at LIMIT ?",
                rowMapper, safeLimit
            )
        }
    }

    override fun search(categoryId: String, query: String, limit: Int): List<BatchSummary> {
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
                rowMapper,
                categoryId,
                trimmed,
                trimmed,
                safeLimit
            )
        } catch (e: DataAccessException) {
            if (!shouldFallbackFromFullTextSearch(e)) throw e
            // LIKE 와일드카드 문자를 이스케이프하여 SQL 인젝션을 방지한다.
            val pattern = "%${SqlUtils.escapeLike(trimmed.lowercase())}%"
            jdbc.query(
                """
                SELECT * FROM batch_summaries
                WHERE category_id = ?
                  AND (
                    LOWER(coalesce(original_title, '')) LIKE ? ESCAPE '\'
                    OR LOWER(coalesce(translated_title, '')) LIKE ? ESCAPE '\'
                    OR LOWER(coalesce(summary, '')) LIKE ? ESCAPE '\'
                    OR LOWER(coalesce(keywords, '')) LIKE ? ESCAPE '\'
                  )
                ORDER BY importance_score DESC, created_at DESC
                LIMIT ?
                """.trimIndent(),
                rowMapper,
                categoryId,
                pattern,
                pattern,
                pattern,
                pattern,
                safeLimit
            )
        }
    }

    override fun countByItemOlderThan(cutoff: Instant, categoryId: String?): Int {
        val cutoffTs = java.sql.Timestamp.from(cutoff)
        return if (categoryId != null) {
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM batch_summaries
                WHERE rss_item_id IN (
                    SELECT id
                    FROM rss_items
                    WHERE created_at < ? AND category_id = ?
                )
                """.trimIndent(),
                Int::class.java,
                cutoffTs,
                categoryId
            ) ?: 0
        } else {
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM batch_summaries
                WHERE rss_item_id IN (
                    SELECT id
                    FROM rss_items
                    WHERE created_at < ?
                )
                """.trimIndent(),
                Int::class.java,
                cutoffTs
            ) ?: 0
        }
    }

    override fun deleteByItemOlderThan(cutoff: Instant, categoryId: String?): Int {
        // EXISTS 서브쿼리로 IN 서브쿼리보다 효율적으로 삭제한다 (H2/PostgreSQL 모두 호환).
        val cutoffTs = java.sql.Timestamp.from(cutoff)
        return if (categoryId != null) {
            jdbc.update(
                """
                DELETE FROM batch_summaries bs
                WHERE EXISTS (
                    SELECT 1 FROM rss_items ri
                    WHERE ri.id = bs.rss_item_id
                      AND ri.created_at < ?
                      AND ri.category_id = ?
                )
                """.trimIndent(),
                cutoffTs,
                categoryId
            )
        } else {
            jdbc.update(
                """
                DELETE FROM batch_summaries bs
                WHERE EXISTS (
                    SELECT 1 FROM rss_items ri
                    WHERE ri.id = bs.rss_item_id
                      AND ri.created_at < ?
                )
                """.trimIndent(),
                cutoffTs
            )
        }
    }

    override fun deleteOlderThanExcludingAnchored(cutoff: Instant, limit: Int): Int {
        // NOTE: JpaBatchSummaryStore is @Primary — this JDBC impl is retained for consistency
        // but not exercised in production. Tests cover the JPA variant. Keep SQL identical to
        // prevent divergence if JPA is ever disabled.

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
        // rss_item과 다른 카테고리로 저장되면 조회/통계가 오염되므로 저장 전에 즉시 차단한다.
        validateSummaryCategoryConsistency(saved, isNew)
        jdbc.update(
            """INSERT INTO batch_summaries (id, original_title, translated_title, summary, keywords, importance_score,
               source_link, is_sent_to_slack, category_id, rss_item_id, sentiment, event_type, is_fallback, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            saved.id, saved.originalTitle, saved.translatedTitle, saved.summary,
            mapper.writeValueAsString(saved.keywords), saved.importanceScore, saved.sourceLink,
            saved.isSentToSlack, saved.categoryId, saved.rssItemId,
            saved.sentiment, saved.eventType, saved.isFallback,
            java.sql.Timestamp.from(saved.createdAt)
        )
        return saved
    }

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
        // rss_item이 없거나 category가 다르면 저장 시점에 명시적으로 실패시켜 데이터 오염을 막는다.
        val linkedItem = findLinkedItem(rssItemId)
        if (!summary.isLinkedTo(linkedItem)) {
            throw DataIntegrityViolationException(
                "batch_summary linkage mismatch: summary=${summary.id.ifBlank { "<new>" }}, rssItemId=$rssItemId"
            )
        }
    }

    /** 연결된 rss_item을 최소 필드만 읽어와 요약 무결성 검증에 사용한다. */
    private fun findLinkedItem(rssItemId: String): RssItem =
        jdbc.query(
            """
            SELECT id, title, link, category_id
            FROM rss_items
            WHERE id = ?
            """.trimIndent(),
            { rs, _ ->
                RssItem(
                    id = rs.getString("id"),
                    title = rs.getString("title"),
                    link = rs.getString("link"),
                    language = Language.FOREIGN,
                    categoryId = rs.getString("category_id")
                )
            },
            rssItemId
        ).firstOrNull() ?: throw DataIntegrityViolationException("rss_item not found: $rssItemId")

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
            rowMapper,
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

    override fun markSent(ids: List<String>) {
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        jdbc.update(
            "UPDATE batch_summaries SET is_sent_to_slack = TRUE WHERE id IN ($placeholders)",
            *ids.toTypedArray()
        )
    }

    override fun findSentArticles(
        categoryIds: List<String>,
        keyword: String?,
        dateFrom: Instant?,
        dateTo: Instant?,
        offset: Int,
        limit: Int
    ): List<BatchSummary> {
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
        return jdbc.query(sql, rowMapper, *allParams.toTypedArray())
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
        // 날짜 범위 내 카테고리별 기사 총 건수를 집계한다.
        val rows = jdbc.query(
            """
            SELECT category_id, COUNT(*) AS cnt
            FROM batch_summaries
            WHERE created_at >= ? AND created_at < ?
            GROUP BY category_id
            """.trimIndent(),
            { rs, _ -> rs.getString("category_id") to rs.getInt("cnt") },
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to)
        )
        return rows.toMap()
    }

    override fun findBySourceLink(link: String): BatchSummary? =
        jdbc.query("SELECT * FROM batch_summaries WHERE source_link = ?", rowMapper, link).firstOrNull()

    override fun findBySourceLinkAndCategoryId(link: String, categoryId: String): BatchSummary? =
        jdbc.query(
            "SELECT * FROM batch_summaries WHERE source_link = ? AND category_id = ? LIMIT 1",
            rowMapper, link, categoryId
        ).firstOrNull()

    /**
     * 발행 기사 조건 절과 파라미터를 생성한다.
     */
    private fun buildSentArticlesQuery(
        categoryIds: List<String>,
        keyword: String?,
        dateFrom: Instant?,
        dateTo: Instant?
    ): Pair<String, List<Any>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        // 슬랙 발행된 기사만 대상으로 한다.
        conditions += "is_sent_to_slack = TRUE"

        // 사용자가 접근 가능한 카테고리로 제한한다. 빈 목록은 "권한 없음"이므로 전체 조회로 열리면 안 된다.
        if (categoryIds.isEmpty()) {
            conditions += "1 = 0"
        } else {
            val ph = categoryIds.joinToString(",") { "?" }
            conditions += "category_id IN ($ph)"
            params.addAll(categoryIds)
        }

        // 키워드 검색 — LIKE 와일드카드를 이스케이프하여 안전하게 처리한다.
        if (!keyword.isNullOrBlank()) {
            val pattern = "%${SqlUtils.escapeLike(keyword.trim().lowercase())}%"
            conditions += """(
                LOWER(coalesce(original_title, '')) LIKE ? ESCAPE '\'
                OR LOWER(coalesce(translated_title, '')) LIKE ? ESCAPE '\'
                OR LOWER(coalesce(summary, '')) LIKE ? ESCAPE '\'
                OR LOWER(coalesce(keywords, '')) LIKE ? ESCAPE '\'
            )"""
            params.addAll(listOf(pattern, pattern, pattern, pattern))
        }

        // 날짜 범위 필터를 적용한다.
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

    override fun findDigestCandidatesWithSource(
        categoryId: String,
        since: Instant,
        limit: Int,
    ): Pair<List<BatchSummary>, Map<String, String?>> {
        val sourceMap = mutableMapOf<String, String?>()
        // LEFT JOIN으로 rss_source_id를 사이드카로 읽어 digest 공정 배분에 사용한다.
        // rss_source_id가 null(수동 URL 또는 SearchCo 뉴스)인 경우 source_link의 도메인을
        // 가상 버킷 키로 사용하여 press(언론사)별 공정 배분이 작동하게 한다.
        val summaries = jdbc.query(
            """
            SELECT bs.*,
                   COALESCE(
                       ri.rss_source_id,
                       CASE
                           WHEN bs.source_link IS NOT NULL AND bs.source_link != ''
                           THEN SUBSTRING(bs.source_link FROM '(?:https?://)?([^/?#]+)')
                           ELSE NULL
                       END
                   ) AS src_id
              FROM batch_summaries bs
              LEFT JOIN rss_items ri ON ri.id = bs.rss_item_id
             WHERE bs.category_id = ?
               AND bs.created_at >= ?
             ORDER BY bs.created_at DESC
             LIMIT ?
            """.trimIndent(),
            { rs, rowNum ->
                val summary = rowMapper.mapRow(rs, rowNum)!!
                sourceMap[summary.id] = rs.getString("src_id")
                summary
            },
            categoryId,
            java.sql.Timestamp.from(since),
            limit,
        )
        return summaries to sourceMap
    }

    override fun findUnsummarizedCompetitorArticles(maxSummaryLength: Int, limit: Int): List<BatchSummary> =
        jdbc.query(
            """SELECT * FROM batch_summaries
               WHERE category_id = '__competitor__'
                 AND (summary IS NULL OR LENGTH(summary) <= ?)
                 AND (keywords IS NULL OR keywords = '[]')
               ORDER BY created_at DESC
               LIMIT ?""",
            rowMapper, maxSummaryLength, limit
        )

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
                rowMapper,
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
                rowMapper,
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
        // LocalDate를 해당 일자의 시작/종료 Instant로 변환한다
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
                rowMapper, categoryId, fromTs, toTs, safeLimit,
            )
        } else {
            jdbc.query(
                """SELECT * FROM batch_summaries
                   WHERE created_at >= ? AND created_at < ?
                   ORDER BY created_at DESC
                   LIMIT ?""",
                rowMapper, fromTs, toTs, safeLimit,
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
        return jdbc.query(sql, rowMapper, *params.toTypedArray())
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
        return jdbc.query(sql, rowMapper, *params.toTypedArray())
    }

    override fun findByImportanceScoreGreaterThan(
        categoryId: String,
        minScore: Double,
        sinceDate: LocalDate,
        limit: Int,
    ): List<BatchSummary> {
        val safeLimit = limit.coerceIn(1, 100)
        // sinceDate를 해당 일자의 시작 Instant로 변환한다
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
            rowMapper, categoryId, minScore, sinceTs, safeLimit,
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
