package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.UserEventEntity
import com.clipping.mcpserver.model.UserEvent
import com.clipping.mcpserver.repository.UserEventRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val log = KotlinLogging.logger {}

/**
 * 사용자 행동 이벤트 JPA 구현. JdbcUserEventStore를 대체한다.
 * 집계, 조인, 일괄 INSERT는 JdbcTemplate을 병용한다.
 */
@Repository
@Primary
class JpaUserEventStore(
    private val repository: UserEventRepository,
    private val jdbc: JdbcTemplate
) : UserEventStore {

    override fun save(event: UserEvent) {
        repository.save(event.toEntity())
    }

    override fun saveBatch(events: List<UserEvent>) {
        if (events.isEmpty()) return
        // 일괄 INSERT는 JdbcTemplate batchUpdate로 네트워크 왕복을 줄인다.
        // V75 이후 summary_id, V127 이후 target_channel_id / slack_message_ts 컬럼 포함.
        val batchArgs = events.map { e ->
            arrayOf<Any?>(
                e.userId, e.eventType, e.eventData,
                e.pagePath, e.sessionId, e.summaryId,
                e.targetChannelId, e.slackMessageTs,
                Timestamp.from(e.createdAt)
            )
        }
        jdbc.batchUpdate(
            """INSERT INTO user_events
               (user_id, event_type, event_data, page_path, session_id, summary_id,
                target_channel_id, slack_message_ts, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            batchArgs
        )
        log.debug { "사용자 이벤트 ${events.size}건 일괄 저장 완료" }
    }

    override fun countByEventType(eventType: String, from: Instant, to: Instant): Long =
        repository.countByEventTypeAndCreatedAtBetween(eventType, from, to)

    override fun countByEventTypeForDays(eventType: String, days: List<LocalDate>): Map<LocalDate, Long> {
        val distinctDays = days.distinct()
        if (distinctDays.isEmpty()) return emptyMap()

        val kst = ZoneId.of("Asia/Seoul")
        val ranges = distinctDays.map { day ->
            day to (day.atStartOfDay(kst).toInstant() to day.plusDays(1).atStartOfDay(kst).toInstant())
        }
        val selectColumns = ranges.indices.joinToString(",\n") { index ->
            "SUM(CASE WHEN created_at >= ? AND created_at < ? THEN 1 ELSE 0 END) AS c$index"
        }
        val params = mutableListOf<Any?>()
        ranges.forEach { (_, range) ->
            // 날짜별 KST 경계를 파라미터로 넘겨 DB 타임존 설정과 무관하게 집계한다.
            params += Timestamp.from(range.first)
            params += Timestamp.from(range.second)
        }
        params += eventType
        params += Timestamp.from(ranges.minOf { it.second.first })
        params += Timestamp.from(ranges.maxOf { it.second.second })

        val row = jdbc.queryForMap(
            """
            SELECT $selectColumns
            FROM user_events
            WHERE event_type = ?
              AND created_at >= ?
              AND created_at < ?
            """.trimIndent(),
            *params.toTypedArray()
        )
        return ranges.mapIndexed { index, (day, _) ->
            day to ((row["c$index"] as? Number)?.toLong() ?: 0L)
        }.toMap()
    }

    override fun countEventsByTypeForUsers(
        userIds: List<String>,
        from: Instant,
        to: Instant
    ): Map<String, Map<String, Int>> {
        if (userIds.isEmpty()) return emptyMap()
        val placeholders = userIds.joinToString(",") { "?" }
        val params = arrayOf<Any?>(*userIds.toTypedArray(), Timestamp.from(from), Timestamp.from(to))
        val rows = jdbc.query(
            """SELECT user_id, event_type, COUNT(*) AS cnt
               FROM user_events
               WHERE user_id IN ($placeholders)
                 AND created_at >= ? AND created_at < ?
               GROUP BY user_id, event_type""",
            { rs, _ ->
                Triple(
                    rs.getString("user_id"),
                    rs.getString("event_type"),
                    rs.getInt("cnt")
                )
            },
            *params
        )
        return rows.groupBy { it.first }
            .mapValues { (_, triples) ->
                triples.associate { it.second to it.third }
            }
    }

    override fun countDistinctUsers(from: Instant, to: Instant): Long =
        repository.countDistinctUsersByCreatedAtBetween(from, to)

    override fun findByUserAndDateRange(
        userId: String,
        from: Instant,
        to: Instant,
        limit: Int
    ): List<UserEvent> =
        repository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            userId, from, to, PageRequest.of(0, limit)
        ).map { it.toModel() }

    override fun dailyActiveUsers(from: Instant, to: Instant): List<DailyCount> {
        // DATE 캐스팅 + GROUP BY 집계는 JdbcTemplate으로 처리한다.
        return jdbc.query(
            """SELECT CAST(created_at AS DATE) AS d,
                      COUNT(DISTINCT user_id) AS cnt
               FROM user_events
               WHERE created_at >= ? AND created_at < ?
               GROUP BY CAST(created_at AS DATE)
               ORDER BY d""",
            { rs, _ ->
                DailyCount(
                    date = rs.getString("d"),
                    count = rs.getLong("cnt")
                )
            },
            Timestamp.from(from),
            Timestamp.from(to)
        )
    }

    override fun findWizardStepEvents(from: Instant, to: Instant): List<WizardStepRow> {
        val entities = repository.findByEventTypeAndCreatedAtBetween("wizard_step", from, to)
        return entities.map { WizardStepRow(eventData = it.eventData, userId = it.userId) }
    }

    override fun findArticleEvents(from: Instant, to: Instant): List<ArticleEventRow> {
        // 두 이벤트 타입을 IN 절로 조회하므로 JdbcTemplate으로 처리한다.
        return jdbc.query(
            """SELECT event_type, event_data, user_id
               FROM user_events
               WHERE event_type IN ('article_impression', 'article_click')
                 AND created_at >= ? AND created_at < ?""",
            { rs, _ ->
                ArticleEventRow(
                    eventType = rs.getString("event_type"),
                    eventData = rs.getString("event_data"),
                    userId = rs.getString("user_id")
                )
            },
            Timestamp.from(from),
            Timestamp.from(to)
        )
    }

    @Transactional
    override fun deleteOlderThan(cutoff: Instant): Int =
        repository.deleteByCreatedAtBefore(cutoff)

    override fun findArticleMetadata(summaryIds: List<String>): List<ArticleMetadataRow> {
        if (summaryIds.isEmpty()) return emptyList()
        // 다중 테이블 조인이므로 JdbcTemplate으로 처리한다.
        val placeholders = summaryIds.joinToString(",") { "?" }
        return jdbc.query(
            """SELECT bs.id AS summary_id,
                      bs.original_title AS title,
                      bs.category_id,
                      bc.name AS category_name,
                      rs.name AS source_name,
                      ri.published_at
               FROM batch_summaries bs
               LEFT JOIN batch_categories bc ON bs.category_id = bc.id
               LEFT JOIN rss_items ri ON bs.rss_item_id = ri.id
               LEFT JOIN rss_sources rs ON ri.rss_source_id = rs.id
               WHERE bs.id IN ($placeholders)""",
            { rs, _ ->
                ArticleMetadataRow(
                    summaryId = rs.getString("summary_id"),
                    title = rs.getString("title"),
                    categoryId = rs.getString("category_id"),
                    categoryName = rs.getString("category_name"),
                    sourceName = rs.getString("source_name"),
                    publishedAt = rs.getTimestamp("published_at")?.toInstant()?.toString()
                )
            },
            *summaryIds.toTypedArray()
        )
    }

    override fun countBookmarksBySummaryIds(summaryIds: List<String>): Map<String, Long> {
        if (summaryIds.isEmpty()) return emptyMap()
        val placeholders = summaryIds.joinToString(",") { "?" }
        return jdbc.query(
            """SELECT summary_id, COUNT(*) AS cnt
               FROM bookmarked_articles
               WHERE summary_id IN ($placeholders)
               GROUP BY summary_id""",
            { rs, _ -> rs.getString("summary_id") to rs.getLong("cnt") },
            *summaryIds.toTypedArray()
        ).toMap()
    }

    override fun countFeedbackByDay(day: LocalDate): DailyFeedbackCount {
        // KST 기준 해당 일자의 시작과 종료를 UTC Instant로 변환한다.
        val kst = ZoneId.of("Asia/Seoul")
        val dayStart = day.atStartOfDay(kst).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(kst).toInstant()

        // feedback_positive 건수를 조회한다.
        val positive = jdbc.queryForObject(
            """SELECT COUNT(*) FROM user_events
               WHERE event_type = 'feedback_positive'
                 AND created_at >= ? AND created_at < ?""",
            Long::class.java,
            Timestamp.from(dayStart),
            Timestamp.from(dayEnd)
        ) ?: 0L

        // feedback_negative 건수를 조회한다.
        val negative = jdbc.queryForObject(
            """SELECT COUNT(*) FROM user_events
               WHERE event_type = 'feedback_negative'
                 AND created_at >= ? AND created_at < ?""",
            Long::class.java,
            Timestamp.from(dayStart),
            Timestamp.from(dayEnd)
        ) ?: 0L

        return DailyFeedbackCount(positive = positive, negative = negative)
    }

    // ── private helpers ──

    private fun UserEventEntity.toModel() = UserEvent(
        id = id,
        userId = userId,
        eventType = eventType,
        eventData = eventData,
        pagePath = pagePath,
        sessionId = sessionId,
        summaryId = summaryId,
        targetChannelId = targetChannelId,
        slackMessageTs = slackMessageTs,
        createdAt = createdAt
    )

    private fun UserEvent.toEntity() = UserEventEntity(
        id = id ?: 0,
        userId = userId,
        eventType = eventType,
        eventData = eventData,
        pagePath = pagePath,
        sessionId = sessionId,
        summaryId = summaryId,
        targetChannelId = targetChannelId,
        slackMessageTs = slackMessageTs,
        createdAt = createdAt
    )
}
