package com.clipping.mcpserver.store

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.time.Instant

class SummaryCacheStoreTest {

    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val store = JdbcSummaryCacheStore(jdbc)

    @Nested
    inner class `findByKey` {

        @Test
        fun `캐시 키가 존재하면 CachedSummary를 반환한다`() {
            every {
                jdbc.query(
                    match<String> { it.contains("summary_cache") && it.contains("cache_key") },
                    any<RowMapper<CachedSummary>>(),
                    eq("abc123")
                )
            } returns listOf(
                CachedSummary(
                    cacheKey = "abc123",
                    summary = "요약 내용",
                    keywords = "[\"AI\"]",
                    importanceScore = 0.8f,
                    sentiment = "NEUTRAL",
                    eventType = "PRODUCT_LAUNCH",
                    translatedTitle = "AI 기술 동향"
                )
            )

            val result = store.findByKey("abc123")
            result shouldNotBe null
            result!!.summary shouldBe "요약 내용"
            result.importanceScore shouldBe 0.8f
            result.translatedTitle shouldBe "AI 기술 동향"
        }

        @Test
        fun `translatedTitle이 null인 캐시도 정상 반환한다`() {
            every {
                jdbc.query(
                    match<String> { it.contains("summary_cache") && it.contains("cache_key") },
                    any<RowMapper<CachedSummary>>(),
                    eq("xyz")
                )
            } returns listOf(
                CachedSummary(
                    cacheKey = "xyz",
                    summary = "한국어 요약",
                    keywords = null,
                    importanceScore = 0.6f,
                    sentiment = null,
                    eventType = null,
                    translatedTitle = null
                )
            )

            val result = store.findByKey("xyz")
            result shouldNotBe null
            result!!.translatedTitle shouldBe null
        }

        @Test
        fun `캐시 키가 없으면 null을 반환한다`() {
            every {
                jdbc.query(any<String>(), any<RowMapper<CachedSummary>>(), any<String>())
            } returns emptyList()

            store.findByKey("nonexistent") shouldBe null
        }
    }

    @Nested
    inner class `save` {

        @Test
        fun `캐시 엔트리를 vendor neutral INSERT로 저장한다`() {
            val entry = CachedSummary(
                cacheKey = "key1",
                summary = "요약",
                keywords = null,
                importanceScore = 0.5f,
                sentiment = null,
                eventType = null,
                translatedTitle = null
            )

            store.save(entry)

            verify {
                jdbc.update(
                    match<String> {
                        it.contains("INSERT INTO summary_cache") &&
                            !it.contains("ON CONFLICT") &&
                            it.contains("translated_title")
                    },
                    eq("key1"), eq("요약"), isNull(),
                    eq(0.5f), isNull(), isNull(), isNull()
                )
            }
        }

        @Test
        fun `translatedTitle이 있으면 함께 저장한다`() {
            val entry = CachedSummary(
                cacheKey = "key2",
                summary = "Foreign article summary",
                keywords = null,
                importanceScore = 0.9f,
                sentiment = "POSITIVE",
                eventType = null,
                translatedTitle = "번역된 제목"
            )

            store.save(entry)

            verify {
                jdbc.update(
                    match<String> {
                        it.contains("INSERT INTO summary_cache") &&
                        it.contains("translated_title")
                    },
                    eq("key2"), eq("Foreign article summary"), isNull(),
                    eq(0.9f), eq("POSITIVE"), isNull(), eq("번역된 제목")
                )
            }
        }

        @Test
        fun `중복 캐시 키는 기존 캐시를 유지하기 위해 무시한다`() {
            val entry = CachedSummary(
                cacheKey = "key1",
                summary = "요약",
                keywords = null,
                importanceScore = 0.5f,
                sentiment = null,
                eventType = null,
                translatedTitle = null
            )
            every {
                jdbc.update(any<String>(), any<String>(), any<String>(), any(), any<Float>(), any(), any(), any())
            } throws DuplicateKeyException("duplicate cache key")

            store.save(entry)

            verify(exactly = 1) {
                jdbc.update(
                    match<String> { it.contains("INSERT INTO summary_cache") && !it.contains("ON CONFLICT") },
                    eq("key1"), eq("요약"), isNull(),
                    eq(0.5f), isNull(), isNull(), isNull()
                )
            }
        }
    }

    @Nested
    inner class `deleteOlderThan` {

        @Test
        fun `지정된 시점보다 오래된 엔트리를 삭제한다`() {
            val cutoff = Instant.parse("2026-04-08T00:00:00Z")
            every {
                jdbc.update(
                    match<String> { it.contains("DELETE FROM summary_cache") && it.contains("created_at <") },
                    any<java.sql.Timestamp>()
                )
            } returns 42

            val deleted = store.deleteOlderThan(cutoff)
            deleted shouldBe 42
        }
    }
}
