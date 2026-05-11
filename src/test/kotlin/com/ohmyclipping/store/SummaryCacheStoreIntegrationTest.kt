package com.ohmyclipping.store

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SummaryCacheStoreIntegrationTest {

    @Autowired lateinit var store: SummaryCacheStore

    @Test
    fun `save 후 findByKey로 translatedTitle까지 조회한다`() {
        val key = "summary-cache-${System.nanoTime()}"

        store.save(
            CachedSummary(
                cacheKey = key,
                summary = "요약",
                keywords = "[\"AI\"]",
                importanceScore = 0.8f,
                sentiment = "NEUTRAL",
                eventType = "PRODUCT_LAUNCH",
                translatedTitle = "번역된 제목",
            )
        )

        val result = store.findByKey(key)

        result!!.cacheKey shouldBe key
        result.summary shouldBe "요약"
        result.keywords shouldBe "[\"AI\"]"
        result.importanceScore shouldBe 0.8f
        result.sentiment shouldBe "NEUTRAL"
        result.eventType shouldBe "PRODUCT_LAUNCH"
        result.translatedTitle shouldBe "번역된 제목"
    }

    @Test
    fun `같은 cacheKey를 다시 저장해도 기존 캐시를 유지한다`() {
        val key = "summary-cache-duplicate-${System.nanoTime()}"
        store.save(
            CachedSummary(
                cacheKey = key,
                summary = "첫 번째 요약",
                keywords = null,
                importanceScore = 0.5f,
                sentiment = null,
                eventType = null,
                translatedTitle = "첫 번째 제목",
            )
        )

        store.save(
            CachedSummary(
                cacheKey = key,
                summary = "두 번째 요약",
                keywords = "[\"duplicate\"]",
                importanceScore = 0.9f,
                sentiment = "POSITIVE",
                eventType = "UPDATE",
                translatedTitle = "두 번째 제목",
            )
        )

        val result = store.findByKey(key)

        result!!.summary shouldBe "첫 번째 요약"
        result.keywords shouldBe null
        result.importanceScore shouldBe 0.5f
        result.sentiment shouldBe null
        result.eventType shouldBe null
        result.translatedTitle shouldBe "첫 번째 제목"
    }
}
