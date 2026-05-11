package com.ohmyclipping.store

import com.ohmyclipping.model.CompetitorRssFeed
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.time.Instant

/**
 * JdbcCompetitorRssFeedStore 단위 테스트.
 * JdbcTemplate을 MockK로 대체하여 각 메서드의 SQL 호출 로직을 검증한다.
 */
class JdbcCompetitorRssFeedStoreTest {

    private val jdbc: JdbcTemplate = mockk(relaxed = true)
    private val store = JdbcCompetitorRssFeedStore(jdbc)

    @Nested
    inner class `findByCompetitorId` {

        @Test
        fun `competitorId로 조회 쿼리를 실행한다`() {
            every { jdbc.query(any<String>(), any<RowMapper<*>>(), any()) } returns emptyList<Any>()

            val result = store.findByCompetitorId("comp-1")

            result shouldBe emptyList()
            verify(exactly = 1) { jdbc.query(any<String>(), any<RowMapper<*>>(), "comp-1") }
        }
    }

    @Nested
    inner class `findAllActive` {

        @Test
        fun `JOIN 쿼리로 활성 경쟁사의 피드를 조회한다`() {
            every { jdbc.query(any<String>(), any<RowMapper<*>>()) } returns emptyList<Any>()

            val result = store.findAllActive()

            result shouldBe emptyList()
            verify(exactly = 1) { jdbc.query(any<String>(), any<RowMapper<*>>()) }
        }
    }

    @Nested
    inner class `save` {

        @Test
        fun `id가 비어 있으면 UUID를 생성해서 저장한다`() {
            every { jdbc.update(any<String>(), *anyVararg()) } returns 1

            val feed = CompetitorRssFeed(
                id = "",
                competitorId = "comp-1",
                feedUrl = "https://example.com/feed.xml",
                label = "메인 피드"
            )
            val saved = store.save(feed)

            saved.id.shouldNotBeEmpty()
            saved.competitorId shouldBe "comp-1"
            saved.feedUrl shouldBe "https://example.com/feed.xml"
            saved.label shouldBe "메인 피드"
            saved.createdAt shouldNotBe null
        }

        @Test
        fun `id가 있으면 그대로 사용한다`() {
            every { jdbc.update(any<String>(), *anyVararg()) } returns 1

            val feed = CompetitorRssFeed(
                id = "fixed-id",
                competitorId = "comp-1",
                feedUrl = "https://example.com/feed.xml"
            )
            val saved = store.save(feed)

            saved.id shouldBe "fixed-id"
        }

        @Test
        fun `label이 null이어도 저장된다`() {
            every { jdbc.update(any<String>(), *anyVararg()) } returns 1

            val feed = CompetitorRssFeed(
                id = "",
                competitorId = "comp-1",
                feedUrl = "https://example.com/feed.xml",
                label = null
            )
            val saved = store.save(feed)

            saved.label shouldBe null
        }
    }

    @Nested
    inner class `delete` {

        @Test
        fun `id로 DELETE 쿼리를 실행한다`() {
            every { jdbc.update(any<String>(), any()) } returns 1

            store.delete("feed-1")

            verify(exactly = 1) { jdbc.update(any<String>(), "feed-1") }
        }
    }

    @Nested
    inner class `deleteByCompetitorId` {

        @Test
        fun `competitorId로 관련 모든 피드를 삭제한다`() {
            every { jdbc.update(any<String>(), any()) } returns 3

            store.deleteByCompetitorId("comp-1")

            verify(exactly = 1) { jdbc.update(any<String>(), "comp-1") }
        }
    }

    @Nested
    inner class `countByCompetitorId` {

        @Test
        fun `COUNT 쿼리를 실행하고 결과를 반환한다`() {
            every {
                jdbc.queryForObject(any<String>(), Int::class.java, any())
            } returns 3

            val result = store.countByCompetitorId("comp-1")

            result shouldBe 3
        }

        @Test
        fun `쿼리 결과가 null이면 0을 반환한다`() {
            every {
                jdbc.queryForObject(any<String>(), Int::class.java, any())
            } returns null

            val result = store.countByCompetitorId("comp-1")

            result shouldBe 0
        }
    }
}
