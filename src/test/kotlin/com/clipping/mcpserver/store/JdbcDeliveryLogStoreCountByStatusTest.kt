package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Category
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * JdbcDeliveryLogStore.countByStatusOn 통합 테스트.
 * 실제 H2(PostgreSQL 호환 모드) 스키마에 대해 SQL 이 정상 동작하는지,
 * 그리고 주어진 LocalDate 에 해당하는 delivery_date row 만 정확히 집계하는지 검증한다.
 *
 * 호출자(서비스 계층)가 타임존을 해석한 뒤 LocalDate 를 전달하는 포트 계약이므로,
 * 이 테스트는 "LocalDate 로 주어진 날짜의 row 만 카운트된다" 는 store 책임만 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcDeliveryLogStoreCountByStatusTest {

    @Autowired lateinit var deliveryLogStore: DeliveryLogStore
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var entityManager: EntityManager

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        // delivery_log → batch_categories FK 를 만족시키기 위해 카테고리를 미리 생성한다.
        categoryId = categoryStore.save(
            Category(id = "", name = "DeliveryCountByStatus-${System.nanoTime()}")
        ).id
        // JPA 세션을 플러시해야 raw JDBC INSERT 가 FK 참조를 볼 수 있다.
        entityManager.flush()
        // H2 `DB_CLOSE_DELAY=-1` 로 인한 cross-test pollution 방어:
        // 본 테스트에서 사용할 fixture 날짜 범위의 row 를 선제 삭제한다.
        jdbc.update(
            "DELETE FROM delivery_log WHERE delivery_date IN (?, ?, ?)",
            Date.valueOf(TEST_DATE),
            Date.valueOf(TEST_DATE.minusDays(1)),
            Date.valueOf(TEST_DATE.plusDays(1)),
        )
    }

    @Test
    fun `지정 날짜에 해당하는 delivery_log 만 status 별로 집계한다`() {
        val today = TEST_DATE
        val yesterday = today.minusDays(1)

        // 오늘 3건 SENT, 2건 FAILED, 1건 SKIPPED
        insertRow(today, "SENT", hour = 8)
        insertRow(today, "SENT", hour = 9)
        insertRow(today, "SENT", hour = 10)
        insertRow(today, "FAILED", hour = 11)
        insertRow(today, "FAILED", hour = 12)
        insertRow(today, "SKIPPED", hour = 13)
        // 어제 2건 SENT — today 집계에서 제외되어야 한다
        insertRow(yesterday, "SENT", hour = 8)
        insertRow(yesterday, "SENT", hour = 9)

        val result = deliveryLogStore.countByStatusOn(today)

        result["SENT"] shouldBe 3L
        result["FAILED"] shouldBe 2L
        result["SKIPPED"] shouldBe 1L
        result.size shouldBe 3
    }

    @Test
    fun `해당 날짜 row 가 없으면 빈 Map 을 반환한다`() {
        val today = TEST_DATE
        // 어제 row 만 있고 오늘은 없음
        insertRow(today.minusDays(1), "SENT", hour = 8)

        val result = deliveryLogStore.countByStatusOn(today)

        result.size shouldBe 0
    }

    @Test
    fun `다른 날짜 row 가 섞여 있어도 지정 날짜만 카운트한다`() {
        val today = TEST_DATE
        val tomorrow = today.plusDays(1)

        // 오늘 1건, 내일 2건 — 오늘 카운트에는 오늘분만 포함
        insertRow(today, "SENT", hour = 0)
        insertRow(tomorrow, "SENT", hour = 1)
        insertRow(tomorrow, "FAILED", hour = 2)

        val result = deliveryLogStore.countByStatusOn(today)

        result["SENT"] shouldBe 1L
        result.size shouldBe 1
    }

    @Test
    fun `summary는 vendor neutral 집계로 지정 날짜의 성공률을 계산한다`() {
        val today = TEST_DATE
        insertRow(today, "SENT", hour = 3)
        insertRow(today, "SENT", hour = 4)
        insertRow(today, "FAILED", hour = 5)
        insertRow(today, "SKIPPED", hour = 6)
        insertRow(today.plusDays(1), "FAILED", hour = 7)

        val result = deliveryLogStore.summary(today)

        result.totalCount shouldBe 4
        result.sentCount shouldBe 2
        result.failedCount shouldBe 1
        result.skippedCount shouldBe 1
        result.successRate shouldBe 0.5
    }

    @Test
    fun `summary는 해당 날짜 row가 없으면 0 카운트와 0 성공률을 반환한다`() {
        insertRow(TEST_DATE.minusDays(1), "SENT", hour = 14)

        val result = deliveryLogStore.summary(TEST_DATE)

        result.totalCount shouldBe 0
        result.sentCount shouldBe 0
        result.failedCount shouldBe 0
        result.skippedCount shouldBe 0
        result.successRate shouldBe 0.0
    }

    @Test
    fun `transitionToStale은 전달된 cutoffHours보다 오래된 ABANDONED만 STALE로 전이한다`() {
        val staleId = insertRow(
            date = TEST_DATE,
            status = "ABANDONED",
            hour = 15,
            createdAt = Instant.now().minusSeconds(3 * 3600)
        )
        val activeId = insertRow(
            date = TEST_DATE,
            status = "ABANDONED",
            hour = 16,
            createdAt = Instant.now().minusSeconds(1 * 3600)
        )

        deliveryLogStore.transitionToStale(cutoffHours = 2)

        statusOf(staleId) shouldBe "STALE"
        statusOf(activeId) shouldBe "ABANDONED"
    }

    @Test
    fun `findAbandonedForMerge는 전달된 cutoffHours 이내 ABANDONED만 반환한다`() {
        val includedId = insertRow(
            date = TEST_DATE,
            status = "ABANDONED",
            hour = 17,
            createdAt = Instant.now().minusSeconds(1 * 3600),
            channelId = "ch-merge"
        )
        insertRow(
            date = TEST_DATE,
            status = "ABANDONED",
            hour = 18,
            createdAt = Instant.now().minusSeconds(3 * 3600),
            channelId = "ch-merge"
        )
        insertRow(
            date = TEST_DATE,
            status = "STALE",
            hour = 19,
            createdAt = Instant.now().minusSeconds(30 * 60),
            channelId = "ch-merge"
        )

        val result = deliveryLogStore.findAbandonedForMerge(categoryId, "ch-merge", cutoffHours = 2)

        result.map { it.id } shouldBe listOf(includedId)
    }

    @Test
    fun `recoverStuckClaims는 전달된 timeoutMinutes보다 오래된 RETRYING만 FAILED로 복구한다`() {
        val recoveredId = insertRow(
            date = TEST_DATE,
            status = "RETRYING",
            hour = 20,
            channelId = "ch-retry"
        )
        val activeId = insertRow(
            date = TEST_DATE,
            status = "RETRYING",
            hour = 21,
            channelId = "ch-retry"
        )
        setClaimedAt(recoveredId, Instant.now().minusSeconds(20 * 60))
        setClaimedAt(activeId, Instant.now().minusSeconds(10 * 60))

        deliveryLogStore.recoverStuckClaims(timeoutMinutes = 15)

        statusOf(recoveredId) shouldBe "FAILED"
        statusOf(activeId) shouldBe "RETRYING"
    }

    /**
     * 테스트용 delivery_log row 를 삽입한다.
     * UNIQUE(category_id, channel_id, delivery_date, delivery_hour) 충돌을 피하려고 hour 로 구분한다.
     */
    private fun insertRow(
        date: LocalDate,
        status: String,
        hour: Int,
        createdAt: Instant = Instant.now(),
        channelId: String = "ch-test"
    ): String {
        val id = "t-${System.nanoTime()}-$hour"
        val createdAtTimestamp = Timestamp.from(createdAt)
        val updatedAtTimestamp = Timestamp.from(Instant.now())
        jdbc.update(
            """
            INSERT INTO delivery_log
            (id, category_id, channel_id, delivery_date, delivery_hour,
             status, item_count, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
            """.trimIndent(),
            id,
            categoryId,
            channelId,
            Date.valueOf(date),
            hour,
            status,
            createdAtTimestamp,
            updatedAtTimestamp,
        )
        return id
    }

    private fun statusOf(id: String): String =
        jdbc.queryForObject(
            "SELECT status FROM delivery_log WHERE id = ?",
            String::class.java,
            id
        )!!

    private fun setClaimedAt(id: String, claimedAt: Instant) {
        jdbc.update(
            "UPDATE delivery_log SET claimed_at = ? WHERE id = ?",
            Timestamp.from(claimedAt),
            id
        )
    }

    companion object {
        private val TEST_DATE: LocalDate = LocalDate.of(2026, 4, 18)
    }
}
