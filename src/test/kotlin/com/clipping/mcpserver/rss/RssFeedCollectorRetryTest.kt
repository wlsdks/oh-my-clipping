package com.clipping.mcpserver.rss

import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.content.ArticleContentExtractor
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SchedulerRunTracker
import com.clipping.mcpserver.security.UrlSafetyValidator
import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

class RssFeedCollectorRetryTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `should retry and recover when source becomes available`() {
        val requestCount = AtomicInteger(0)
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        server = httpServer
        val port = httpServer.address.port
        httpServer.createContext("/feed.xml") { exchange ->
            val count = requestCount.incrementAndGet()
            if (count < 3) {
                val body = "temporary failure"
                exchange.sendResponseHeaders(500, body.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.toByteArray()) }
                return@createContext
            }

            val body = """
                <rss version="2.0">
                  <channel>
                    <title>Recovered Feed</title>
                    <item>
                      <title>Recovered item</title>
                      <link>http://127.0.0.1:$port/article-1</link>
                      <description>This description is long enough to skip fallback extraction path.</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        httpServer.start()

        val urlValidator = mockk<UrlSafetyValidator>()
        every { urlValidator.validatePublicHttpUrl(any()) } answers { URI(firstArg()) }
        val extractor = mockk<ArticleContentExtractor>()
        every { extractor.extract(any()) } returns null

        val collector = RssFeedCollector(
            properties = ClippingMcpServerProperties(
                rssConnectionTimeoutMs = 500,
                rssReadTimeoutMs = 500,
                rssMaxAttempts = 3,
                rssRetryBackoffMs = 1,
                rssFallbackMinContentLength = 20
            ),
            urlSafetyValidator = urlValidator,
            articleContentExtractor = extractor,
            metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        )

        val source = RssSource(
            id = "source-1",
            name = "Retryable Source",
            url = "http://127.0.0.1:$port/feed.xml",
            categoryId = "cat-1"
        )

        val items = collector.collect(source, 24)

        requestCount.get() shouldBe 3
        items shouldHaveSize 1
        items.first().title shouldBe "Recovered item"
    }

    @Test
    fun `should throw after max attempts when source keeps failing`() {
        val requestCount = AtomicInteger(0)
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        server = httpServer
        val port = httpServer.address.port
        httpServer.createContext("/feed.xml") { exchange ->
            requestCount.incrementAndGet()
            val body = "always failing"
            exchange.sendResponseHeaders(500, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        httpServer.start()

        val urlValidator = mockk<UrlSafetyValidator>()
        every { urlValidator.validatePublicHttpUrl(any()) } answers { URI(firstArg()) }
        val extractor = mockk<ArticleContentExtractor>()
        every { extractor.extract(any()) } returns null

        val collector = RssFeedCollector(
            properties = ClippingMcpServerProperties(
                rssConnectionTimeoutMs = 500,
                rssReadTimeoutMs = 500,
                rssMaxAttempts = 3,
                rssRetryBackoffMs = 1
            ),
            urlSafetyValidator = urlValidator,
            articleContentExtractor = extractor,
            metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        )

        val source = RssSource(
            id = "source-2",
            name = "Always Fail Source",
            url = "http://127.0.0.1:$port/feed.xml",
            categoryId = "cat-1"
        )

        val thrown = assertThrows<IllegalStateException> {
            collector.collect(source, 24)
        }

        thrown.message.shouldContain("after 3 attempts")
        requestCount.get() shouldBe 3
    }

    @TestFactory
    fun `should reject non positive hoursBack before url validation`(): List<DynamicTest> =
        listOf(0, -1, -24).map { invalidHours ->
            DynamicTest.dynamicTest("hoursBack=$invalidHours") {
                val urlValidator = mockk<UrlSafetyValidator>()
                val extractor = mockk<ArticleContentExtractor>()
                val collector = RssFeedCollector(
                    properties = ClippingMcpServerProperties(),
                    urlSafetyValidator = urlValidator,
                    articleContentExtractor = extractor,
                    metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
                )
                val source = RssSource(
                    id = "source-invalid",
                    name = "Invalid Hours Source",
                    url = "https://example.com/feed.xml",
                    categoryId = "cat-1"
                )

                val collectError = assertThrows<InvalidInputException> {
                    collector.collect(source, invalidHours)
                }
                val collectByUrlError = assertThrows<InvalidInputException> {
                    collector.collectByUrl("https://example.com/feed.xml", invalidHours)
                }

                collectError.message.shouldContain("hoursBack must be greater than 0")
                collectByUrlError.message.shouldContain("hoursBack must be greater than 0")
                verify(exactly = 0) { urlValidator.validatePublicHttpUrl(any()) }
            }
        }
}
