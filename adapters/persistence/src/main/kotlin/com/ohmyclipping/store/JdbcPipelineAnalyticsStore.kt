package com.ohmyclipping.store

import com.ohmyclipping.store.pipeline.CategoryDeliveryStat
import com.ohmyclipping.store.pipeline.CategoryOwner
import com.ohmyclipping.store.pipeline.DeliveryDayAcc
import com.ohmyclipping.store.pipeline.DeliveryLogStatus
import com.ohmyclipping.store.pipeline.LlmRunStatus
import com.ohmyclipping.store.pipeline.PipelineAnalyticsStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * 파이프라인 분석용 집계 쿼리의 JDBC 구현.
 * vendor-neutral SQL을 사용하여 PostgreSQL과 H2 모두에서 동작한다.
 */
@Repository
class JdbcPipelineAnalyticsStore(
    private val jdbc: JdbcTemplate
) : PipelineAnalyticsStore {

    /** 기간 내 llm_runs 상태별 건수를 집계한다. [from, to) 반개구간. */
    override fun queryLlmStatusCounts(from: Instant, to: Instant): Map<LlmRunStatus, Int> {
        val result = mutableMapOf<LlmRunStatus, Int>()
        jdbc.query(
            """
            SELECT status, COUNT(*) AS cnt
            FROM llm_runs
            WHERE created_at >= ? AND created_at < ?
            GROUP BY status
            """.trimIndent(),
            { rs, _ ->
                val status = parseLlmRunStatus(rs.getString("status"))
                if (status != null) {
                    result[status] = rs.getInt("cnt")
                }
            },
            Timestamp.from(from),
            Timestamp.from(to)
        )
        return result
    }

    /** 기간 내 delivery_log 상태별 건수를 집계한다. [from, to] 폐구간. */
    override fun queryDeliveryStatusCounts(from: LocalDate, to: LocalDate): Map<DeliveryLogStatus, Int> {
        val result = mutableMapOf<DeliveryLogStatus, Int>()
        jdbc.query(
            """
            SELECT status, COUNT(*) AS cnt
            FROM delivery_log
            WHERE delivery_date >= ? AND delivery_date < ?
            GROUP BY status
            """.trimIndent(),
            { rs, _ ->
                val status = parseDeliveryLogStatus(rs.getString("status"))
                if (status != null) {
                    result[status] = rs.getInt("cnt")
                }
            },
            Date.valueOf(from),
            Date.valueOf(to.plusDays(1))
        )
        return result
    }

    /** 기간 내 delivery_log를 일자+상태별로 집계하여 날짜 맵으로 반환한다. */
    override fun queryDeliveryDailyMap(from: LocalDate, to: LocalDate): Map<LocalDate, DeliveryDayAcc> {
        val result = mutableMapOf<LocalDate, DeliveryDayAcc>()
        jdbc.query(
            """
            SELECT delivery_date, status, COUNT(*) AS cnt
            FROM delivery_log
            WHERE delivery_date >= ? AND delivery_date < ?
            GROUP BY delivery_date, status
            ORDER BY delivery_date
            """.trimIndent(),
            { rs, _ ->
                val date = rs.getDate("delivery_date")?.toLocalDate() ?: return@query
                val status = rs.getString("status")
                val cnt = rs.getInt("cnt")
                val acc = result.getOrPut(date) { DeliveryDayAcc() }
                when (status) {
                    DeliveryLogStatus.SENT.name -> acc.sent += cnt
                    DeliveryLogStatus.SKIPPED.name -> acc.skipped += cnt
                    DeliveryLogStatus.FAILED.name -> acc.failed += cnt
                }
            },
            Date.valueOf(from),
            Date.valueOf(to.plusDays(1))
        )
        return result
    }

    /**
     * 기간 내 EMPTY_RESULT 거절 사유를 error_message 접두어로 분류하여 집계한다.
     * CASE 식을 GROUP BY에서 반복하여 PostgreSQL/H2 양쪽 호환을 보장한다.
     */
    override fun queryRejectReasons(from: Instant, to: Instant): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        jdbc.query(
            """
            SELECT
              CASE
                WHEN error_message LIKE 'CHARS_TOO_SHORT%' THEN 'CHARS_TOO_SHORT'
                WHEN error_message LIKE 'PARAGRAPHS_TOO_FEW%' THEN 'PARAGRAPHS_TOO_FEW'
                WHEN error_message LIKE 'SENTENCES_TOO_FEW%' THEN 'SENTENCES_TOO_FEW'
                WHEN error_message LIKE 'SENTENCES_AFTER_CLAMP%' THEN 'SENTENCES_AFTER_CLAMP'
                ELSE 'OTHER'
              END AS reason,
              COUNT(*) AS cnt
            FROM llm_runs
            WHERE status = ? AND error_message IS NOT NULL
              AND created_at >= ? AND created_at < ?
            GROUP BY
              CASE
                WHEN error_message LIKE 'CHARS_TOO_SHORT%' THEN 'CHARS_TOO_SHORT'
                WHEN error_message LIKE 'PARAGRAPHS_TOO_FEW%' THEN 'PARAGRAPHS_TOO_FEW'
                WHEN error_message LIKE 'SENTENCES_TOO_FEW%' THEN 'SENTENCES_TOO_FEW'
                WHEN error_message LIKE 'SENTENCES_AFTER_CLAMP%' THEN 'SENTENCES_AFTER_CLAMP'
                ELSE 'OTHER'
              END
            """.trimIndent(),
            { rs, _ ->
                result[rs.getString("reason")] = rs.getInt("cnt")
            },
            LlmRunStatus.EMPTY_RESULT.name,
            Timestamp.from(from),
            Timestamp.from(to)
        )
        return result
    }

    /**
     * 기간 내 카테고리별 발송 통계를 집계한다.
     * delivery_log → batch_categories JOIN만 사용하고 user JOIN은 하지 않아
     * 다중 소유 시 건수 팬아웃이 발생하지 않는다.
     */
    override fun queryDeliveryMatrixByCategory(
        from: LocalDate,
        to: LocalDate
    ): List<CategoryDeliveryStat> {
        return jdbc.query(
            """
            SELECT bc.id AS category_id, bc.name AS category_name,
                   SUM(CASE WHEN dl.status = ? THEN 1 ELSE 0 END) AS sent,
                   SUM(CASE WHEN dl.status = ? THEN 1 ELSE 0 END) AS skipped,
                   SUM(CASE WHEN dl.status = ? THEN 1 ELSE 0 END) AS failed
            FROM delivery_log dl
            JOIN batch_categories bc ON bc.id = dl.category_id
            WHERE dl.delivery_date >= ? AND dl.delivery_date < ?
            GROUP BY bc.id, bc.name
            ORDER BY bc.name
            """.trimIndent(),
            { rs, _ ->
                CategoryDeliveryStat(
                    categoryId = rs.getString("category_id"),
                    categoryName = rs.getString("category_name"),
                    sent = rs.getInt("sent"),
                    skipped = rs.getInt("skipped"),
                    failed = rs.getInt("failed")
                )
            },
            DeliveryLogStatus.SENT.name,
            DeliveryLogStatus.SKIPPED.name,
            DeliveryLogStatus.FAILED.name,
            Date.valueOf(from),
            Date.valueOf(to.plusDays(1))
        )
    }

    /** 카테고리 ID 목록에 대한 소유자(구독자) 맵을 반환한다. */
    override fun queryCategoryOwners(categoryIds: List<String>): Map<String, List<CategoryOwner>> {
        if (categoryIds.isEmpty()) return emptyMap()

        val placeholders = categoryIds.joinToString(",") { "?" }
        val result = mutableMapOf<String, MutableList<CategoryOwner>>()

        jdbc.query(
            """
            SELECT uoc.category_id, u.id AS user_id, u.username
            FROM clipping_user_owned_categories uoc
            JOIN admin_users u ON u.id = uoc.user_id
            WHERE uoc.category_id IN ($placeholders)
            ORDER BY u.username
            """.trimIndent(),
            { rs, _ ->
                val catId = rs.getString("category_id")
                result.computeIfAbsent(catId) { mutableListOf() }.add(
                    CategoryOwner(
                        userId = rs.getString("user_id"),
                        username = rs.getString("username")
                    )
                )
            },
            *categoryIds.toTypedArray()
        )

        return result
    }

    // ── Private helpers ──

    private fun parseLlmRunStatus(value: String?): LlmRunStatus? {
        if (value.isNullOrBlank()) return null
        return try {
            LlmRunStatus.valueOf(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun parseDeliveryLogStatus(value: String?): DeliveryLogStatus? {
        if (value.isNullOrBlank()) return null
        return try {
            DeliveryLogStatus.valueOf(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
