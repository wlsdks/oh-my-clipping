package com.ohmyclipping.store

import com.ohmyclipping.repository.DeliveryLogRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * Delivery log aggregate bad-row coercion tests.
 */
class JpaDeliveryLogStoreAggregationTest {

    private val repository = mockk<DeliveryLogRepository>(relaxed = true)
    private val jdbc = mockk<JdbcTemplate>()
    private val store = JpaDeliveryLogStore(repository, jdbc)

    @Test
    fun `countByStatusOn은 null status row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("GROUP BY status") }, any<RowMapper<Pair<String, Long>?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<Pair<String, Long>?>>()
            listOfNotNull(
                mapper.mapRow(statusRow(status = null, count = 9L), 0),
                mapper.mapRow(statusRow(status = "SENT", count = 2L), 1),
            )
        }

        store.countByStatusOn(LocalDate.of(2026, 4, 26)) shouldBe mapOf("SENT" to 2L)
    }

    @Test
    fun `dailyStats는 delivery_date가 null인 row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("SUM(CASE WHEN status = 'SENT'") }, any<RowMapper<DeliveryLogStore.DailyStat?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<DeliveryLogStore.DailyStat?>>()
            listOfNotNull(
                mapper.mapRow(dailyRow(date = null, sent = 3, failed = 1, skipped = 0), 0),
            )
        }

        store.dailyStats(
            LocalDate.of(2026, 4, 20),
            LocalDate.of(2026, 4, 26)
        ).shouldBeEmpty()
    }

    @Test
    fun `findPendingRetries는 필수 컬럼이 null인 row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("next_retry_at <= CURRENT_TIMESTAMP") }, any<RowMapper<DeliveryLogStore.DeliveryRetryCandidate?>>(), any<Int>())
        } answers {
            val mapper = secondArg<RowMapper<DeliveryLogStore.DeliveryRetryCandidate?>>()
            listOfNotNull(
                mapper.mapRow(retryCandidateRow(id = "retry-1", createdAt = null), 0),
                mapper.mapRow(retryCandidateRow(id = null, createdAt = NOW), 1),
                mapper.mapRow(retryCandidateRow(id = "retry-2", createdAt = NOW), 2),
            )
        }

        store.findPendingRetries(3) shouldBe listOf(
            DeliveryLogStore.DeliveryRetryCandidate(
                id = "retry-2",
                categoryId = "cat-1",
                channelId = "C123",
                status = "FAILED",
                slackMessageTs = null,
                preparedDigest = null,
                retryCount = 1,
                createdAt = NOW,
            )
        )
    }

    @Test
    fun `findUndeliveredForUser는 delivery_date가 null인 row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("delivery_date >= CURRENT_DATE - 3") }, any<RowMapper<DeliveryLogStore.UndeliveredDigest?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<DeliveryLogStore.UndeliveredDigest?>>()
            listOfNotNull(
                mapper.mapRow(undeliveredRow(id = "undelivered-1", deliveryDate = null), 0),
                mapper.mapRow(undeliveredRow(id = "undelivered-2", deliveryDate = LocalDate.of(2026, 4, 27)), 1),
            )
        }

        store.findUndeliveredForUser(listOf("cat-1")) shouldBe listOf(
            DeliveryLogStore.UndeliveredDigest(
                deliveryLogId = "undelivered-2",
                categoryId = "cat-1",
                deliveryDate = LocalDate.of(2026, 4, 27),
                deliveryHour = 9,
                status = "FAILED",
                retryCount = 1,
                preparedDigest = null,
            )
        )
    }

    @Test
    fun `findByCategoryIds는 delivery_date가 null인 row를 제외하고 categoryName null은 빈 문자열로 보정한다`() {
        every {
            jdbc.query(match { it.contains("JOIN batch_categories") && it.contains("dl.delivery_date BETWEEN") }, any<RowMapper<DeliveryLogStore.UserDeliveryLogEntry?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<DeliveryLogStore.UserDeliveryLogEntry?>>()
            listOfNotNull(
                mapper.mapRow(userDeliveryRow(deliveryDate = null, categoryName = "깨진 카테고리"), 0),
                mapper.mapRow(userDeliveryRow(deliveryDate = LocalDate.of(2026, 4, 27), categoryName = null), 1),
            )
        }

        store.findByCategoryIds(
            categoryIds = listOf("cat-1"),
            from = LocalDate.of(2026, 4, 20),
            to = LocalDate.of(2026, 4, 27)
        ) shouldBe listOf(
            DeliveryLogStore.UserDeliveryLogEntry(
                date = LocalDate.of(2026, 4, 27),
                categoryId = "cat-1",
                categoryName = "",
                itemCount = 5,
                status = "SENT",
                deliveredAt = NOW,
            )
        )
    }

    @Test
    fun `sumDeliveredItemsByCategoryDate는 category 또는 delivery_date가 null인 row를 제외한다`() {
        every {
            jdbc.query(match { it.contains("COALESCE(SUM(item_count), 0) AS total") }, any<RowMapper<Triple<String, LocalDate, Int>?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<Triple<String, LocalDate, Int>?>>()
            listOf(
                mapper.mapRow(deliveredItemsRow(categoryId = null, deliveryDate = LocalDate.of(2026, 4, 27), total = 10), 0),
                mapper.mapRow(deliveredItemsRow(categoryId = "cat-1", deliveryDate = null, total = 20), 1),
                mapper.mapRow(deliveredItemsRow(categoryId = "cat-1", deliveryDate = LocalDate.of(2026, 4, 27), total = 5), 2),
            )
        }

        store.sumDeliveredItemsByCategoryDate(
            categoryIds = listOf("cat-1"),
            from = LocalDate.of(2026, 4, 20),
            to = LocalDate.of(2026, 4, 27)
        ) shouldBe mapOf(("cat-1" to LocalDate.of(2026, 4, 27)) to 5)
    }

    @Test
    fun `findLastSentDate는 created_at이 null인 최신 row를 건너뛴다`() {
        every {
            jdbc.query(match { it.contains("ORDER BY created_at DESC") }, any<RowMapper<Instant?>>(), any<String>(), any<String>())
        } answers {
            val mapper = secondArg<RowMapper<Instant?>>()
            listOf(
                mapper.mapRow(lastSentRow(createdAt = null), 0),
                mapper.mapRow(lastSentRow(createdAt = NOW), 1),
            )
        }

        store.findLastSentDate("C123", "cat-1") shouldBe NOW
    }

    private fun statusRow(status: String?, count: Long): ResultSet =
        mockk {
            every { getString("status") } returns status
            every { getLong("cnt") } returns count
        }

    private fun dailyRow(date: LocalDate?, sent: Int, failed: Int, skipped: Int): ResultSet =
        mockk {
            every { getDate("delivery_date") } returns date?.let(Date::valueOf)
            every { getInt("sent") } returns sent
            every { getInt("failed") } returns failed
            every { getInt("skipped") } returns skipped
        }

    private fun retryCandidateRow(id: String?, createdAt: Instant?): ResultSet =
        mockk {
            every { getString("id") } returns id
            every { getString("category_id") } returns "cat-1"
            every { getString("channel_id") } returns "C123"
            every { getString("status") } returns "FAILED"
            every { getString("slack_message_ts") } returns null
            every { getString("prepared_digest_json") } returns null
            every { getInt("retry_count") } returns 1
            every { getTimestamp("created_at") } returns createdAt?.let(Timestamp::from)
        }

    private fun undeliveredRow(id: String?, deliveryDate: LocalDate?): ResultSet =
        mockk {
            every { getString("id") } returns id
            every { getString("category_id") } returns "cat-1"
            every { getDate("delivery_date") } returns deliveryDate?.let(Date::valueOf)
            every { getInt("delivery_hour") } returns 9
            every { getString("status") } returns "FAILED"
            every { getInt("retry_count") } returns 1
            every { getString("prepared_digest_json") } returns null
        }

    private fun userDeliveryRow(deliveryDate: LocalDate?, categoryName: String?): ResultSet =
        mockk {
            every { getDate("delivery_date") } returns deliveryDate?.let(Date::valueOf)
            every { getString("category_id") } returns "cat-1"
            every { getString("category_name") } returns categoryName
            every { getInt("item_count") } returns 5
            every { getString("status") } returns "SENT"
            every { getTimestamp("delivered_at") } returns Timestamp.from(NOW)
        }

    private fun deliveredItemsRow(categoryId: String?, deliveryDate: LocalDate?, total: Int): ResultSet =
        mockk {
            every { getString("category_id") } returns categoryId
            every { getDate("delivery_date") } returns deliveryDate?.let(Date::valueOf)
            every { getInt("total") } returns total
        }

    private fun lastSentRow(createdAt: Instant?): ResultSet =
        mockk {
            every { getTimestamp("created_at") } returns createdAt?.let(Timestamp::from)
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-04-27T00:00:00Z")
    }
}
