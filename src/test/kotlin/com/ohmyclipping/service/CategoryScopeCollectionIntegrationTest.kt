package com.ohmyclipping.service

import com.ohmyclipping.store.CachedSummary
import com.ohmyclipping.store.JdbcRssItemStore
import com.ohmyclipping.store.JdbcSummaryCacheStore
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * 카테고리 스코프 수집의 핵심 불변식을 store 계약 수준에서 검증한다.
 *
 * 실제 PostgreSQL 통합 테스트는 Testcontainers가 필요하며 다음 라운드에서 추가한다.
 * 이 테스트는 [JdbcRssItemStore]와 [JdbcSummaryCacheStore]의 SQL이 올바른 카테고리
 * 스코프 조건과 vendor-neutral 문법을 사용하는지를 검증하여, 카테고리 간 링크 분리와
 * translatedTitle 보존 불변식을 잠근다.
 */
class CategoryScopeCollectionIntegrationTest {

    private val jdbc = mockk<JdbcTemplate>(relaxed = true)

    @Nested
    inner class `RssItemStore 카테고리 스코프 SQL` {

        private val store = JdbcRssItemStore(jdbc)

        @Test
        fun `findExistingLinks SQL에 category_id 조건이 포함된다`() {
            val sqlSlot = slot<String>()
            every {
                jdbc.queryForList(
                    capture(sqlSlot),
                    eq(String::class.java),
                    *anyVararg(),
                )
            } returns listOf("https://example.com/a")

            store.findExistingLinks(listOf("https://example.com/a"), "cat-1")

            sqlSlot.captured.contains("AND category_id = ?") shouldBe true
            sqlSlot.captured.contains("SELECT link FROM rss_items") shouldBe true
        }

        @Test
        fun `findByLink SQL에 category_id 조건이 포함된다`() {
            val sqlSlot = slot<String>()
            every {
                jdbc.query(
                    capture(sqlSlot),
                    any<RowMapper<*>>(),
                    *anyVararg(),
                )
            } returns emptyList<Any>()

            store.findByLink("https://example.com/a", "cat-1")

            sqlSlot.captured.contains("AND category_id = ?") shouldBe true
        }

        @Test
        fun `findExistingLinks는 빈 링크 목록에 대해 DB를 호출하지 않는다`() {
            store.findExistingLinks(emptyList(), "cat-1")
            verify(exactly = 0) {
                jdbc.queryForList(any<String>(), eq(String::class.java), *anyVararg())
            }
        }

        @Test
        fun `findExistingLinks는 중복 입력을 제거한 뒤 chunk 조회한다`() {
            every {
                jdbc.queryForList(any<String>(), eq(String::class.java), *anyVararg())
            } returns listOf("https://example.com/a")

            val links = List(2500) { "https://example.com/a" }
            val result = store.findExistingLinks(links, "cat-1")

            result shouldBe setOf("https://example.com/a")
            verify(exactly = 1) {
                jdbc.queryForList(any<String>(), eq(String::class.java), *anyVararg())
            }
        }

        @Test
        fun `findExistingLinks는 유니크 링크 2501개를 1000개 단위로 나눠 조회한다`() {
            every {
                jdbc.queryForList(any<String>(), eq(String::class.java), *anyVararg())
            } returns emptyList()

            val links = (1..2501).map { "https://example.com/$it" }
            store.findExistingLinks(links, "cat-1")

            verify(exactly = 3) {
                jdbc.queryForList(any<String>(), eq(String::class.java), *anyVararg())
            }
        }
    }

    @Nested
    inner class `SummaryCacheStore translatedTitle 보존 계약` {

        private val store = JdbcSummaryCacheStore(jdbc)

        @Test
        fun `save SQL에 translated_title 컬럼이 포함되고 PG 전용 ON CONFLICT를 사용하지 않는다`() {
            val sqlSlot = slot<String>()
            every { jdbc.update(capture(sqlSlot), *anyVararg()) } returns 1

            store.save(
                CachedSummary(
                    cacheKey = "k1",
                    summary = "s",
                    keywords = null,
                    importanceScore = 0.5f,
                    sentiment = null,
                    eventType = null,
                    translatedTitle = "한국어 제목",
                )
            )

            sqlSlot.captured.contains("INSERT INTO summary_cache") shouldBe true
            sqlSlot.captured.contains("translated_title") shouldBe true
            sqlSlot.captured.contains("ON CONFLICT") shouldBe false
        }

        @Test
        fun `findByKey SQL이 translated_title을 SELECT하고 rowMapper가 파싱한다`() {
            val sqlSlot = slot<String>()
            every {
                jdbc.query(
                    capture(sqlSlot),
                    any<RowMapper<CachedSummary>>(),
                    eq("k1"),
                )
            } returns listOf(
                CachedSummary(
                    cacheKey = "k1",
                    summary = "s",
                    keywords = null,
                    importanceScore = 0.5f,
                    sentiment = null,
                    eventType = null,
                    translatedTitle = "한국어 제목",
                )
            )

            val result = store.findByKey("k1")

            sqlSlot.captured.contains("translated_title") shouldBe true
            result?.translatedTitle shouldBe "한국어 제목"
        }

        @Test
        fun `save는 translatedTitle이 null이어도 정상 호출된다`() {
            every { jdbc.update(any<String>(), *anyVararg()) } returns 1

            store.save(
                CachedSummary(
                    cacheKey = "k2",
                    summary = "s",
                    keywords = null,
                    importanceScore = 0.5f,
                    sentiment = null,
                    eventType = null,
                    translatedTitle = null,
                )
            )

            verify(exactly = 1) { jdbc.update(any<String>(), *anyVararg()) }
        }
    }
}
