package com.ohmyclipping.store

import com.ohmyclipping.entity.DeliveryLogEntity
import com.ohmyclipping.model.DeliveryDaySummary
import com.ohmyclipping.model.DeliveryLog
import com.ohmyclipping.service.dto.clipping.DigestResult
import com.ohmyclipping.repository.DeliveryLogRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 발송 이력 JPA 구현. JdbcDeliveryLogStore를 대체한다.
 * FILTER 집계와 동적 WHERE 등 복합 쿼리는 JdbcTemplate을 병용한다.
 */
@Repository
@Primary
class JpaDeliveryLogStore(
    private val repository: DeliveryLogRepository,
    private val jdbc: JdbcTemplate
) : DeliveryLogStore {

    companion object {
        /** 재시도 대상 조회 시 안전 상한. 이 수에 도달하면 경고를 남긴다. */
        private const val MAX_PENDING_RETRIES = 500
    }

    private val log = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
    private val mapper = jacksonObjectMapper().apply { findAndRegisterModules() }

    override fun tryReserve(
        categoryId: String,
        channelId: String,
        deliveryDate: LocalDate,
        deliveryHour: Int
    ): String? {
        // UNIQUE 제약 위반을 활용해 중복 발송을 방지한다.
        val existing = repository.findByCategoryIdAndChannelIdAndDeliveryDateAndDeliveryHour(
            categoryId, channelId, deliveryDate, deliveryHour
        )
        if (existing != null) return null

        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        val entity = DeliveryLogEntity(
            id = id,
            categoryId = categoryId,
            channelId = channelId,
            deliveryDate = deliveryDate,
            deliveryHour = deliveryHour,
            status = "RESERVED",
            itemCount = 0,
            createdAt = now,
            updatedAt = now
        )
        return try {
            repository.save(entity)
            id
        } catch (_: org.springframework.dao.DataIntegrityViolationException) {
            null
        }
    }

    override fun updateStatus(
        id: String,
        status: String,
        itemCount: Int,
        slackMessageTs: String?
    ) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.status = status
        entity.itemCount = itemCount
        entity.slackMessageTs = slackMessageTs
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }

    override fun savePreparedDigest(id: String, preparedDigest: DigestResult) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.preparedDigestJson = mapper.writeValueAsString(preparedDigest)
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }

    override fun markFallbackUsed(id: String, used: Boolean) {
        // JdbcTemplate 로 단일 컬럼만 갱신해 다른 속성(상태/타임스탬프)과의 경합을 피한다
        val now = Timestamp.from(Instant.now())
        jdbc.update(
            """
            UPDATE delivery_log
            SET fallback_used = ?, updated_at = ?
            WHERE id = ?
            """.trimIndent(),
            used, now, id
        )
    }

    @Transactional
    override fun deleteOlderThan(days: Int): Int {
        // CURRENT_DATE 연산은 SQL로 직접 처리한다.
        return jdbc.update(
            "DELETE FROM delivery_log WHERE delivery_date < (CURRENT_DATE - CAST(? AS INT))",
            days
        )
    }

    override fun findPendingRetries(maxRetries: Int): List<DeliveryLogStore.DeliveryRetryCandidate> {
        // next_retry_at 비교는 CURRENT_TIMESTAMP 로 H2/PG 양립. 안전 상한 LIMIT 을 적용한다.
        val results = jdbc.query(
            """
            SELECT id, category_id, channel_id, status, slack_message_ts,
                   prepared_digest_json, retry_count, created_at
            FROM delivery_log
            WHERE status IN ('FAILED', 'FINALIZATION_FAILED')
              AND retry_count < ?
              AND next_retry_at IS NOT NULL
              AND next_retry_at <= CURRENT_TIMESTAMP
            ORDER BY next_retry_at ASC
            LIMIT $MAX_PENDING_RETRIES
            """.trimIndent(),
            { rs, _ -> mapRetryCandidate(rs) },
            maxRetries
        ).mapNotNull { it }
        // 안전 상한에 도달하면 경고를 남겨 운영자가 배치 크기를 조정할 수 있게 한다.
        if (results.size >= MAX_PENDING_RETRIES) {
            log.warn { "findPendingRetries hit safety limit=$MAX_PENDING_RETRIES" }
        }
        return results
    }

    override fun claimForRetry(id: String): Boolean {
        val now = Timestamp.from(Instant.now())
        // 상태 조건을 만족하는 행만 RETRYING으로 원자적으로 전환한다.
        val updated = jdbc.update(
            """
            UPDATE delivery_log
            SET status = 'RETRYING', claimed_at = ?, updated_at = ?
            WHERE id = ? AND status IN ('FAILED', 'FINALIZATION_FAILED', 'ABANDONED')
              AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP)
            """.trimIndent(),
            now, now, id
        )
        return updated > 0
    }

    override fun recoverStuckClaims(timeoutMinutes: Long) {
        val now = Timestamp.from(Instant.now())
        // PG INTERVAL 리터럴 대신 vendor-neutral Timestamp 바인딩을 사용한다 (H2/PG 양립).
        val stuckBefore = Timestamp.from(Instant.now().minus(Duration.ofMinutes(timeoutMinutes)))
        // claimed_at이 timeoutMinutes 이상 경과한 RETRYING 행을 FAILED로 되돌린다.
        val recovered = jdbc.update(
            """
            UPDATE delivery_log
            SET status = 'FAILED', claimed_at = NULL, updated_at = ?
            WHERE status = 'RETRYING'
              AND claimed_at < ?
            """.trimIndent(),
            now, stuckBefore
        )
        if (recovered > 0) log.warn { "Recovered $recovered stuck RETRYING claims" }
    }

    override fun recordFailure(id: String, retryCount: Int, nextRetryAt: Instant?, status: String, lastError: String?) {
        val now = Timestamp.from(Instant.now())
        // 재시도 실패 결과를 단일 UPDATE로 기록하고 claimed_at을 해제한다.
        jdbc.update(
            """
            UPDATE delivery_log
            SET retry_count = ?, next_retry_at = ?, status = ?,
                last_error = ?, claimed_at = NULL, updated_at = ?
            WHERE id = ?
            """.trimIndent(),
            retryCount,
            nextRetryAt?.let { Timestamp.from(it) },
            status,
            lastError?.take(500),
            now,
            id
        )
    }

    override fun findAbandonedForMerge(
        categoryId: String,
        channelId: String,
        cutoffHours: Long
    ): List<DeliveryLogStore.DeliveryRetryCandidate> {
        // PG INTERVAL 리터럴 대신 Kotlin 산술 + 바인딩으로 vendor-neutral 처리.
        val mergeCutoff = Timestamp.from(Instant.now().minus(Duration.ofHours(cutoffHours)))
        return jdbc.query(
            """
            SELECT id, category_id, channel_id, status, slack_message_ts,
                   prepared_digest_json, retry_count, created_at
            FROM delivery_log
            WHERE category_id = ? AND channel_id = ? AND status = 'ABANDONED'
              AND created_at > ?
            ORDER BY delivery_date ASC, delivery_hour ASC
            """.trimIndent(),
            { rs, _ -> mapRetryCandidate(rs) },
            categoryId, channelId, mergeCutoff
        ).mapNotNull { it }
    }

    override fun resetForRetry(id: String) {
        val now = Timestamp.from(Instant.now())
        // ABANDONED 건의 retry_count를 0으로 초기화하고 next_retry_at을 현재 시각으로 설정한다.
        jdbc.update(
            """
            UPDATE delivery_log
            SET status = 'FAILED', retry_count = 0, next_retry_at = ?,
                claimed_at = NULL, updated_at = ?
            WHERE id = ? AND status = 'ABANDONED'
            """.trimIndent(),
            now, now, id
        )
    }

    override fun transitionToStale(cutoffHours: Long) {
        val now = Timestamp.from(Instant.now())
        // PG INTERVAL 리터럴 대신 vendor-neutral Timestamp 바인딩 사용 (H2/PG 양립).
        val staleCutoff = Timestamp.from(Instant.now().minus(Duration.ofHours(cutoffHours)))
        // cutoffHours 이상 경과한 ABANDONED 건을 STALE로 전이한다.
        val updated = jdbc.update(
            """
            UPDATE delivery_log SET status = 'STALE', updated_at = ?
            WHERE status = 'ABANDONED'
              AND created_at < ?
            """.trimIndent(),
            now, staleCutoff
        )
        if (updated > 0) log.info { "Transitioned $updated ABANDONED deliveries to STALE" }
    }

    override fun findUndeliveredForUser(categoryIds: List<String>): List<DeliveryLogStore.UndeliveredDigest> {
        if (categoryIds.isEmpty()) return emptyList()
        val placeholders = categoryIds.joinToString(",") { "?" }
        // PG INTERVAL 리터럴 대신 Timestamp 바인딩으로 H2/PG 양립을 보장한다.
        val failedCutoff = Timestamp.from(Instant.now().minusSeconds(15 * 60))
        // categoryIds 바인딩 뒤에 failedCutoff 바인딩을 한 번 추가한다.
        val args: Array<Any> = arrayOf(*categoryIds.toTypedArray(), failedCutoff)
        return jdbc.query(
            """
            SELECT id, category_id, delivery_date, delivery_hour, status, retry_count, prepared_digest_json
            FROM delivery_log
            WHERE category_id IN ($placeholders)
              AND (
                (status IN ('ABANDONED', 'STALE'))
                OR (status = 'FAILED' AND created_at < ?)
              )
              AND delivery_date >= CURRENT_DATE - 3
            ORDER BY delivery_date DESC, delivery_hour DESC
            LIMIT 20
            """.trimIndent(),
            { rs, _ ->
                val deliveryLogId = rs.getString("id") ?: return@query null
                val categoryId = rs.getString("category_id") ?: return@query null
                val deliveryDate = rs.getDate("delivery_date")?.toLocalDate() ?: return@query null
                val status = rs.getString("status") ?: return@query null
                DeliveryLogStore.UndeliveredDigest(
                    deliveryLogId = deliveryLogId,
                    categoryId = categoryId,
                    deliveryDate = deliveryDate,
                    deliveryHour = rs.getInt("delivery_hour"),
                    status = status,
                    retryCount = rs.getInt("retry_count"),
                    preparedDigest = parsePreparedDigest(rs.getString("prepared_digest_json"))
                )
            },
            *args
        ).mapNotNull { it }
    }

    override fun countByStatusOn(date: LocalDate): Map<String, Long> {
        // delivery_date = ? 로 상태별 카운트를 집계한다. 타임존 해석은 호출자가 이미 수행한 상태다.
        return jdbc.query(
            """
            SELECT status, COUNT(*) AS cnt
            FROM delivery_log
            WHERE delivery_date = ?
            GROUP BY status
            """.trimIndent(),
            { rs, _ ->
                val status = rs.getString("status") ?: return@query null
                status to rs.getLong("cnt")
            },
            Date.valueOf(date)
        ).mapNotNull { it }.toMap()
    }

    override fun summary(date: LocalDate): DeliveryDaySummary {
        // H2/PostgreSQL 양쪽에서 동작하도록 vendor-neutral SUM CASE 집계를 사용한다.
        return jdbc.queryForObject(
            """
            SELECT
                COUNT(*)                                                AS total_count,
                SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END)        AS sent_count,
                SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END)      AS failed_count,
                SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END)     AS skipped_count
            FROM delivery_log
            WHERE delivery_date = ?
            """.trimIndent(),
            { rs, _ ->
                val total = rs.getInt("total_count")
                val sent = rs.getInt("sent_count")
                val rate = if (total > 0) sent.toDouble() / total else 0.0
                DeliveryDaySummary(
                    totalCount = total,
                    sentCount = sent,
                    failedCount = rs.getInt("failed_count"),
                    skippedCount = rs.getInt("skipped_count"),
                    successRate = rate
                )
            },
            Date.valueOf(date)
        ) ?: DeliveryDaySummary(
            totalCount = 0,
            sentCount = 0,
            failedCount = 0,
            skippedCount = 0,
            successRate = 0.0
        )
    }

    override fun findAll(
        categoryId: String?,
        status: String?,
        from: LocalDate?,
        to: LocalDate?,
        since: Instant?,
        offset: Int,
        limit: Int
    ): List<DeliveryLog> {
        // 동적 WHERE 조합이므로 JdbcTemplate으로 처리한다.
        val (whereClause, params) = buildWhereClause(categoryId, status, from, to, since)
        val sql = """
            SELECT * FROM delivery_log
            $whereClause
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return jdbc.query(
            sql,
            { rs, _ -> mapRowToModel(rs) },
            *(params + limit + offset).toTypedArray()
        ).mapNotNull { it }
    }

    override fun countAll(
        categoryId: String?,
        status: String?,
        from: LocalDate?,
        to: LocalDate?,
        since: Instant?
    ): Int {
        val (whereClause, params) = buildWhereClause(categoryId, status, from, to, since)
        val sql = "SELECT COUNT(*) FROM delivery_log $whereClause"
        return jdbc.queryForObject(sql, Int::class.java, *params.toTypedArray()) ?: 0
    }

    override fun findById(id: String): DeliveryLog? =
        repository.findById(id).orElse(null)?.toModel()

    override fun dailyStats(from: LocalDate, to: LocalDate): List<DeliveryLogStore.DailyStat> {
        // 기간 내 일자별 상태 건수를 집계한다 (H2 호환 SUM CASE 사용).
        return jdbc.query(
            """
            SELECT delivery_date,
                   SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END) AS sent,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                   SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END) AS skipped
            FROM delivery_log
            WHERE delivery_date >= ? AND delivery_date <= ?
            GROUP BY delivery_date
            ORDER BY delivery_date
            """.trimIndent(),
            { rs, _ ->
                val date = rs.getDate("delivery_date")?.toLocalDate() ?: return@query null
                DeliveryLogStore.DailyStat(
                    date = date,
                    sent = rs.getInt("sent"),
                    failed = rs.getInt("failed"),
                    skipped = rs.getInt("skipped")
                )
            },
            Date.valueOf(from),
            Date.valueOf(to)
        ).mapNotNull { it }
    }

    override fun consecutiveZeroCategories(days: Int): List<DeliveryLogStore.ZeroCategoryAlert> {
        // 최근 N일 동안 SENT 건수가 0인 활성 카테고리를 조회한다.
        return jdbc.query(
            """
            SELECT c.id AS category_id,
                   c.name AS category_name,
                   ? - COALESCE(SUM(CASE WHEN dl.status = 'SENT' THEN 1 ELSE 0 END), 0) AS missed_slots
            FROM batch_categories c
            LEFT JOIN delivery_log dl
                ON dl.category_id = c.id
               AND dl.delivery_date >= CURRENT_DATE - CAST(? - 1 AS INT)
            WHERE c.is_active = TRUE
            GROUP BY c.id, c.name
            HAVING COALESCE(SUM(CASE WHEN dl.status = 'SENT' THEN 1 ELSE 0 END), 0) = 0
            ORDER BY c.name
            """.trimIndent(),
            { rs, _ ->
                DeliveryLogStore.ZeroCategoryAlert(
                    categoryId = rs.getString("category_id"),
                    categoryName = rs.getString("category_name"),
                    missedSlots = rs.getInt("missed_slots")
                )
            },
            days,
            days
        )
    }

    override fun findByCategoryIds(
        categoryIds: List<String>,
        from: LocalDate,
        to: LocalDate
    ): List<DeliveryLogStore.UserDeliveryLogEntry> {
        if (categoryIds.isEmpty()) return emptyList()

        // IN 절 플레이스홀더를 동적으로 생성한다.
        val placeholders = categoryIds.joinToString(", ") { "?" }
        val sql = """
            SELECT dl.delivery_date, dl.category_id, c.name AS category_name,
                   dl.item_count, dl.status, dl.created_at AS delivered_at
            FROM delivery_log dl
            JOIN batch_categories c ON c.id = dl.category_id
            WHERE dl.category_id IN ($placeholders)
              AND dl.delivery_date BETWEEN ? AND ?
            ORDER BY dl.delivery_date DESC, dl.created_at DESC
        """.trimIndent()

        val params = categoryIds.toMutableList<Any>().apply {
            add(Date.valueOf(from))
            add(Date.valueOf(to))
        }

        return jdbc.query(sql, { rs, _ ->
            val date = rs.getDate("delivery_date")?.toLocalDate() ?: return@query null
            val categoryId = rs.getString("category_id") ?: return@query null
            val status = rs.getString("status") ?: return@query null
            DeliveryLogStore.UserDeliveryLogEntry(
                date = date,
                categoryId = categoryId,
                categoryName = rs.getString("category_name") ?: "",
                itemCount = rs.getInt("item_count"),
                status = status,
                deliveredAt = rs.getTimestamp("delivered_at")?.toInstant()
            )
        }, *params.toTypedArray()).mapNotNull { it }
    }

    override fun existsSent(categoryId: String, date: LocalDate, hour: Int): Boolean {
        val count = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM delivery_log
            WHERE category_id = ? AND delivery_date = ? AND delivery_hour = ? AND status = 'SENT'
            """.trimIndent(),
            Int::class.java,
            categoryId, Date.valueOf(date), hour
        ) ?: 0
        return count > 0
    }

    override fun sumDeliveredItemsByCategoryDate(
        categoryIds: List<String>,
        from: LocalDate,
        to: LocalDate
    ): Map<Pair<String, LocalDate>, Int> {
        if (categoryIds.isEmpty()) return emptyMap()
        val placeholders = categoryIds.joinToString(",") { "?" }
        // 카테고리 IN 파라미터와 기간·상태 필터를 결합한다.
        val params: Array<Any> = (categoryIds + Date.valueOf(from) + Date.valueOf(to)).toTypedArray()
        val rows: List<Triple<String, LocalDate, Int>?> = jdbc.query(
            """
            SELECT category_id, delivery_date, COALESCE(SUM(item_count), 0) AS total
            FROM delivery_log
            WHERE category_id IN ($placeholders)
              AND delivery_date BETWEEN ? AND ?
              AND status = 'SENT'
            GROUP BY category_id, delivery_date
            """.trimIndent(),
            { rs, _ ->
                val categoryId = rs.getString("category_id") ?: return@query null
                val deliveryDate = rs.getDate("delivery_date")?.toLocalDate() ?: return@query null
                Triple(
                    categoryId,
                    deliveryDate,
                    rs.getInt("total")
                )
            },
            *params
        )
        return rows.mapNotNull { it }.associate { (categoryId, date, total) -> (categoryId to date) to total }
    }

    override fun findLastSentDate(channelId: String, categoryId: String): Instant? {
        // 해당 채널+카테고리의 마지막 SENT 발송 시각을 조회한다.
        val results = jdbc.query(
            """
            SELECT created_at FROM delivery_log
            WHERE channel_id = ? AND category_id = ? AND status = 'SENT'
            ORDER BY created_at DESC
            LIMIT 1
            """.trimIndent(),
            { rs, _ -> rs.getTimestamp("created_at")?.toInstant() },
            channelId, categoryId
        )
        return results.firstNotNullOfOrNull { it }
    }

    override fun findByChannelIds(channelIds: Collection<String>): List<DeliveryLog> {
        // 채널 목록이 비어 있으면 불필요한 IN () 쿼리를 만들지 않는다.
        if (channelIds.isEmpty()) return emptyList()
        val distinct = channelIds.toSet()
        return repository
            .findByChannelIdInOrderByDeliveryDateDescDeliveryHourDesc(distinct)
            .map { it.toModel() }
    }

    override fun hasNotifiedSinceLastSent(channelId: String, categoryId: String): Boolean {
        // 마지막 SENT 이후에 NOTIFIED_NO_CONTENT 기록이 있는지 확인한다.
        val count = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM delivery_log
            WHERE channel_id = ? AND category_id = ? AND status = 'NOTIFIED_NO_CONTENT'
              AND created_at > COALESCE(
                  (SELECT MAX(created_at) FROM delivery_log
                   WHERE channel_id = ? AND category_id = ? AND status = 'SENT'),
                  TIMESTAMP '1970-01-01 00:00:00')
            """.trimIndent(),
            Int::class.java,
            channelId, categoryId, channelId, categoryId
        ) ?: 0
        return count > 0
    }

    // ── private helpers ──

    private fun buildWhereClause(
        categoryId: String?,
        status: String?,
        from: LocalDate?,
        to: LocalDate?,
        since: Instant? = null
    ): Pair<String, List<Any>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (!categoryId.isNullOrBlank()) {
            conditions += "category_id = ?"
            params += categoryId
        }
        if (!status.isNullOrBlank()) {
            conditions += "status = ?"
            params += status
        }
        if (from != null) {
            conditions += "delivery_date >= ?"
            params += Date.valueOf(from)
        }
        if (to != null) {
            conditions += "delivery_date <= ?"
            params += Date.valueOf(to)
        }
        // within 파라미터에서 변환된 createdAt 하한 필터를 적용한다.
        if (since != null) {
            conditions += "created_at >= ?"
            params += Timestamp.from(since)
        }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        return whereClause to params
    }

    private fun parsePreparedDigest(raw: String?): DigestResult? {
        if (raw.isNullOrBlank()) return null
        return runCatching { mapper.readValue<DigestResult>(raw) }.getOrNull()
    }

    private fun mapRetryCandidate(rs: java.sql.ResultSet): DeliveryLogStore.DeliveryRetryCandidate? {
        val id = rs.getString("id") ?: return null
        val categoryId = rs.getString("category_id") ?: return null
        val channelId = rs.getString("channel_id") ?: return null
        val status = rs.getString("status") ?: return null
        val createdAt = rs.getTimestamp("created_at")?.toInstant() ?: return null
        return DeliveryLogStore.DeliveryRetryCandidate(
            id = id,
            categoryId = categoryId,
            channelId = channelId,
            status = status,
            slackMessageTs = rs.getString("slack_message_ts"),
            preparedDigest = parsePreparedDigest(rs.getString("prepared_digest_json")),
            retryCount = rs.getInt("retry_count"),
            createdAt = createdAt
        )
    }

    private fun mapRowToModel(rs: java.sql.ResultSet): DeliveryLog? {
        val id = rs.getString("id") ?: return null
        val categoryId = rs.getString("category_id") ?: return null
        val channelId = rs.getString("channel_id") ?: return null
        val deliveryDate = rs.getDate("delivery_date")?.toLocalDate() ?: return null
        val status = rs.getString("status") ?: return null
        val createdAt = rs.getTimestamp("created_at")?.toInstant() ?: return null
        val updatedAt = rs.getTimestamp("updated_at")?.toInstant() ?: return null
        return DeliveryLog(
            id = id,
            categoryId = categoryId,
            channelId = channelId,
            deliveryDate = deliveryDate,
            deliveryHour = rs.getInt("delivery_hour"),
            status = status,
            itemCount = rs.getInt("item_count"),
            slackMessageTs = rs.getString("slack_message_ts"),
            retryCount = rs.getInt("retry_count"),
            nextRetryAt = rs.getTimestamp("next_retry_at")?.toInstant(),
            claimedAt = rs.getTimestamp("claimed_at")?.toInstant(),
            lastError = rs.getString("last_error"),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun DeliveryLogEntity.toModel() = DeliveryLog(
        id = id,
        categoryId = categoryId,
        channelId = channelId,
        deliveryDate = deliveryDate,
        deliveryHour = deliveryHour,
        status = status,
        itemCount = itemCount,
        slackMessageTs = slackMessageTs,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        claimedAt = claimedAt,
        lastError = lastError,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
