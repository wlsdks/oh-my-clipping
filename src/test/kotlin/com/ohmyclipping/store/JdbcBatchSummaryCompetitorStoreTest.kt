package com.ohmyclipping.store

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.time.Instant

/**
 * JdbcBatchSummaryCompetitorStore 단위 테스트.
 * JdbcTemplate을 MockK로 대체하여 각 메서드의 SQL 호출과 중복 처리 로직을 검증한다.
 */
class JdbcBatchSummaryCompetitorStoreTest {

    private val jdbc: JdbcTemplate = mockk(relaxed = true)
    private val store = JdbcBatchSummaryCompetitorStore(jdbc)

    @Nested
    inner class `link` {

        @Test
        fun `정상적으로 INSERT를 수행한다`() {
            every { jdbc.update(any<String>(), *anyVararg()) } returns 1

            store.link("summary-1", "competitor-1")

            verify(exactly = 1) { jdbc.update(any<String>(), *anyVararg()) }
        }

        @Test
        fun `DuplicateKeyException 발생 시 예외를 삼키고 무시한다`() {
            every { jdbc.update(any<String>(), *anyVararg()) } throws DuplicateKeyException("duplicate")

            // 예외가 바깥으로 전파되지 않아야 한다
            store.link("summary-1", "competitor-1")
        }
    }

    @Nested
    inner class `linkAll` {

        @Test
        fun `경쟁사 목록에 대해 각각 link를 호출한다`() {
            every { jdbc.update(any<String>(), *anyVararg()) } returns 1

            store.linkAll("summary-1", listOf("comp-A", "comp-B", "comp-C"))

            verify(exactly = 3) { jdbc.update(any<String>(), *anyVararg()) }
        }

        @Test
        fun `중복 경쟁사 ID는 한 번만 link한다`() {
            every { jdbc.update(any<String>(), *anyVararg()) } returns 1

            store.linkAll("summary-1", listOf("comp-A", "comp-A", "comp-B", "comp-A"))

            verify(exactly = 1) { jdbc.update(any<String>(), "summary-1", "comp-A") }
            verify(exactly = 1) { jdbc.update(any<String>(), "summary-1", "comp-B") }
        }

        @Test
        fun `빈 목록이면 아무것도 호출하지 않는다`() {
            store.linkAll("summary-1", emptyList())

            verify(exactly = 0) { jdbc.update(any<String>(), *anyVararg()) }
        }
    }

    @Nested
    inner class `findBySummaryId` {

        @Test
        fun `summaryId로 조회 쿼리를 실행한다`() {
            every { jdbc.query(any<String>(), any<RowMapper<*>>(), any()) } returns emptyList<Any>()

            val result = store.findBySummaryId("summary-1")

            result shouldBe emptyList()
            verify(exactly = 1) { jdbc.query(any<String>(), any<RowMapper<*>>(), "summary-1") }
        }
    }

    @Nested
    inner class `findBySummaryIds` {

        @Test
        fun `빈 목록이면 DB를 호출하지 않고 빈 목록을 반환한다`() {
            val result = store.findBySummaryIds(emptyList())

            result shouldBe emptyList()
            verify(exactly = 0) { jdbc.query(any<String>(), any<RowMapper<*>>(), *anyVararg()) }
        }

        @Test
        fun `중복 summaryId는 IN 조건에서 제거한다`() {
            every { jdbc.query(any<String>(), any<RowMapper<*>>(), *anyVararg()) } returns emptyList<Any>()

            val result = store.findBySummaryIds(listOf("s1", "s1", "s2"))

            result shouldBe emptyList()
            verify(exactly = 1) {
                jdbc.query(
                    match<String> { it.contains("IN (?,?)") },
                    any<RowMapper<*>>(),
                    "s1",
                    "s2",
                )
            }
        }
    }

    @Nested
    inner class `findByCompetitorId` {

        @Test
        fun `competitorId와 limit으로 조회 쿼리를 실행한다`() {
            every { jdbc.query(any<String>(), any<RowMapper<*>>(), any(), any()) } returns emptyList<Any>()

            val result = store.findByCompetitorId("comp-1", 50)

            result shouldBe emptyList()
            verify(exactly = 1) { jdbc.query(any<String>(), any<RowMapper<*>>(), "comp-1", 50) }
        }

        @Test
        fun `limit이 0 이하이면 1로 보정된다`() {
            every { jdbc.query(any<String>(), any<RowMapper<*>>(), any(), any()) } returns emptyList<Any>()

            store.findByCompetitorId("comp-1", 0)

            val limitSlot = slot<Int>()
            verify { jdbc.query(any<String>(), any<RowMapper<*>>(), any(), capture(limitSlot)) }
            limitSlot.captured shouldBe 1
        }
    }

    @Nested
    inner class `countByCompetitorId` {

        @Test
        fun `COUNT 쿼리를 실행하고 결과를 반환한다`() {
            every {
                jdbc.queryForObject(any<String>(), Long::class.java, any())
            } returns 5L

            val result = store.countByCompetitorId("comp-1")

            result shouldBe 5L
        }

        @Test
        fun `쿼리 결과가 null이면 0을 반환한다`() {
            every {
                jdbc.queryForObject(any<String>(), Long::class.java, any())
            } returns null

            val result = store.countByCompetitorId("comp-1")

            result shouldBe 0L
        }
    }

    @Nested
    inner class `bulk competitor queries` {

        @Test
        fun `countByCompetitorIds는 중복 competitorId를 제거해 조회한다`() {
            every { jdbc.query(any<String>(), any<RowMapper<Pair<String, Long>>>(), *anyVararg()) } returns emptyList()

            val result = store.countByCompetitorIds(listOf("comp-1", "comp-1", "comp-2"))

            result shouldBe emptyMap()
            verify(exactly = 1) {
                jdbc.query(
                    match<String> { it.contains("IN (?,?)") },
                    any<RowMapper<Pair<String, Long>>>(),
                    "comp-1",
                    "comp-2",
                )
            }
        }

        @Test
        fun `findSummaryIdsByCompetitorIds는 중복 competitorId를 제거하고 limit을 보정한다`() {
            every { jdbc.queryForList(any<String>(), eq(String::class.java), *anyVararg()) } returns emptyList()
            val from = Instant.parse("2026-04-01T00:00:00Z")
            val to = Instant.parse("2026-04-08T00:00:00Z")

            val result = store.findSummaryIdsByCompetitorIds(
                competitorIds = listOf("comp-1", "comp-1", "comp-2"),
                from = from,
                to = to,
                limit = 10_000,
            )

            result shouldBe emptyList()
            verify(exactly = 1) {
                jdbc.queryForList(
                    match<String> { it.contains("IN (?,?)") },
                    eq(String::class.java),
                    "comp-1",
                    "comp-2",
                    java.sql.Timestamp.from(from),
                    java.sql.Timestamp.from(to),
                    1000,
                )
            }
        }
    }

    @Nested
    inner class `deleteBySummaryId` {

        @Test
        fun `summaryId로 DELETE 쿼리를 실행한다`() {
            every { jdbc.update(any<String>(), any()) } returns 1

            store.deleteBySummaryId("summary-1")

            verify(exactly = 1) { jdbc.update(any<String>(), "summary-1") }
        }
    }
}
