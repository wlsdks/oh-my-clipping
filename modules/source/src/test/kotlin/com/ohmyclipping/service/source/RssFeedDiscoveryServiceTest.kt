package com.ohmyclipping.service.source

import com.ohmyclipping.model.KnownNewsSource
import com.ohmyclipping.service.port.SourceUrlSafetyPort
import com.ohmyclipping.store.KnownNewsSourceStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class RssFeedDiscoveryServiceTest {

    private val knownStore = mockk<KnownNewsSourceStore>()
    private val urlSafetyValidator = mockk<SourceUrlSafetyPort>(relaxed = true)
    private val service = RssFeedDiscoveryService(knownStore, urlSafetyValidator)

    private val chosun = KnownNewsSource(
        id = "1", name = "Example Daily", aliases = listOf("Example", "example-press"),
        domain = "example-press.com", rssUrl = "https://www.example-press.com/arc/outboundfeeds/rss/",
        region = "DOMESTIC", createdAt = Instant.now()
    )

    @Nested
    inner class `매핑 테이블 검색` {
        @Test
        fun `한글 사이트명으로 매칭한다`() {
            every { knownStore.search("Example Daily") } returns listOf(chosun)
            val result = service.discover("Example Daily")
            result.knownMatch shouldNotBe null
            result.knownMatch!!.name shouldBe "Example Daily"
        }

        @Test
        fun `도메인으로 매칭한다`() {
            every { knownStore.search("example-press.com") } returns listOf(chosun)
            val result = service.discover("example-press.com")
            result.knownMatch shouldNotBe null
        }

        @Test
        fun `매칭이 없으면 knownMatch가 null이다`() {
            every { knownStore.search("없는사이트") } returns emptyList()
            val result = service.discover("없는사이트")
            result.knownMatch shouldBe null
        }
    }

    @Nested
    inner class `입력 정규화` {

        @Test
        fun `빈 검색어는 store 검색과 RSS 탐색을 실행하지 않는다`() {
            val result = service.discover("   ")

            result.knownMatch shouldBe null
            result.discoveredFeeds shouldBe emptyList()
            verify(exactly = 0) { knownStore.search(any()) }
            verify(exactly = 0) { urlSafetyValidator.validatePublicHttpUrl(any()) }
        }

        @Test
        fun `도메인 경로 입력은 host만 추출해 RSS 후보를 만든다`() {
            every { knownStore.search("example.com/news?utm=1") } returns emptyList()
            every { urlSafetyValidator.validatePublicHttpUrl(any()) } throws IllegalArgumentException("blocked in test")

            val result = service.discover("example.com/news?utm=1")

            result.knownMatch shouldBe null
            result.discoveredFeeds shouldBe emptyList()
            verify(exactly = 1) { urlSafetyValidator.validatePublicHttpUrl("https://example.com/rss") }
            verify(exactly = 0) { urlSafetyValidator.validatePublicHttpUrl(match { it.contains("/news") }) }
        }

        @Test
        fun `대문자 HTTP URL도 host를 추출해 RSS 후보를 만든다`() {
            every { knownStore.search("HTTP://WWW.EXAMPLE.COM/news") } returns emptyList()
            every { urlSafetyValidator.validatePublicHttpUrl(any()) } throws IllegalArgumentException("blocked in test")

            val result = service.discover("HTTP://WWW.EXAMPLE.COM/news")

            result.knownMatch shouldBe null
            result.discoveredFeeds shouldBe emptyList()
            verify(exactly = 1) { urlSafetyValidator.validatePublicHttpUrl("https://example.com/rss") }
        }
    }
}
