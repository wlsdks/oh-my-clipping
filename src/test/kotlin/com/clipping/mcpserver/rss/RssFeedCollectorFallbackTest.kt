package com.clipping.mcpserver.rss

import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.content.ArticleContentExtractor
import com.clipping.mcpserver.content.ExtractedArticle
import com.clipping.mcpserver.model.Language
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SchedulerRunTracker
import com.clipping.mcpserver.security.UrlSafetyValidator
import com.sun.net.httpserver.HttpServer
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

class RssFeedCollectorFallbackTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `should use full page fallback when rss content is short`() {
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        server = httpServer
        val port = httpServer.address.port

        httpServer.createContext("/feed.xml") { exchange ->
            val body = """
                <rss version="2.0">
                  <channel>
                    <title>Test Feed</title>
                    <item>
                      <title>Short RSS item</title>
                      <link>http://127.0.0.1:$port/article-1</link>
                      <description>short</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        httpServer.start()

        val validator = mockk<UrlSafetyValidator>()
        every { validator.validatePublicHttpUrl(any()) } answers { URI(firstArg()) }

        val extractor = mockk<ArticleContentExtractor>()
        every { extractor.extract("http://127.0.0.1:$port/article-1") } returns
            ExtractedArticle(
                title = "Full Article",
                content = "This is a much longer full page article content used as fallback.",
                language = Language.FOREIGN
            )

        val collector = RssFeedCollector(
            properties = ClippingMcpServerProperties(maxContentLength = 5000),
            urlSafetyValidator = validator,
            articleContentExtractor = extractor,
            metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        )

        val source = RssSource(
            id = "source-1",
            name = "Test Source",
            url = "http://127.0.0.1:$port/feed.xml",
            categoryId = "cat-1"
        )

        val items = collector.collect(source, 24)
        items shouldHaveSize 1
        items[0].content shouldBe "This is a much longer full page article content used as fallback."
    }

    @Test
    fun `should canonicalize article links before storing and extracting`() {
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        server = httpServer
        val port = httpServer.address.port
        val canonicalArticleUrl = "http://127.0.0.1:$port/article-1?id=42"

        httpServer.createContext("/feed.xml") { exchange ->
            val body = """
                <rss version="2.0">
                  <channel>
                    <title>Test Feed</title>
                    <item>
                      <title>Tracked RSS item</title>
                      <link>http://127.0.0.1:$port/article-1?utm_source=slack&id=42&amp;fbclid=abc#comments</link>
                      <description>short</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        httpServer.start()

        val validator = mockk<UrlSafetyValidator>()
        every { validator.validatePublicHttpUrl(any()) } answers { URI(firstArg()) }

        val extractor = mockk<ArticleContentExtractor>()
        every { extractor.extract(canonicalArticleUrl) } returns
            ExtractedArticle(
                title = "Full Article",
                content = "Canonicalized article content.",
                language = Language.FOREIGN
            )

        val collector = RssFeedCollector(
            properties = ClippingMcpServerProperties(maxContentLength = 5000),
            urlSafetyValidator = validator,
            articleContentExtractor = extractor,
            metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        )

        val source = RssSource(
            id = "source-1",
            name = "Test Source",
            url = "http://127.0.0.1:$port/feed.xml",
            categoryId = "cat-1"
        )

        val items = collector.collect(source, 24)

        items shouldHaveSize 1
        items[0].link shouldBe canonicalArticleUrl
    }

    @Test
    fun `should keep rss item when full page fallback extraction throws`() {
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        server = httpServer
        val port = httpServer.address.port

        httpServer.createContext("/feed.xml") { exchange ->
            val body = """
                <rss version="2.0">
                  <channel>
                    <title>Test Feed</title>
                    <item>
                      <title>Short RSS item</title>
                      <link>http://127.0.0.1:$port/article-1</link>
                      <description>short</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        httpServer.start()

        val validator = mockk<UrlSafetyValidator>()
        every { validator.validatePublicHttpUrl(any()) } answers { URI(firstArg()) }

        val extractor = mockk<ArticleContentExtractor>()
        every { extractor.extract("http://127.0.0.1:$port/article-1") } throws RuntimeException("extractor unavailable")

        val collector = RssFeedCollector(
            properties = ClippingMcpServerProperties(maxContentLength = 5000),
            urlSafetyValidator = validator,
            articleContentExtractor = extractor,
            metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        )

        val source = RssSource(
            id = "source-1",
            name = "Test Source",
            url = "http://127.0.0.1:$port/feed.xml",
            categoryId = "cat-1"
        )

        val items = collector.collect(source, 24)

        items shouldHaveSize 1
        items[0].title shouldBe "Short RSS item"
        items[0].content shouldBe "short"
    }

    @Test
    fun `should keep rss item when full page fallback extraction returns null`() {
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        server = httpServer
        val port = httpServer.address.port

        httpServer.createContext("/feed.xml") { exchange ->
            val body = """
                <rss version="2.0">
                  <channel>
                    <title>Test Feed</title>
                    <item>
                      <title>Short RSS item</title>
                      <link>http://127.0.0.1:$port/article-1</link>
                      <description>short</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        httpServer.start()

        val validator = mockk<UrlSafetyValidator>()
        every { validator.validatePublicHttpUrl(any()) } answers { URI(firstArg()) }

        val extractor = mockk<ArticleContentExtractor>()
        every { extractor.extract("http://127.0.0.1:$port/article-1") } returns null

        val collector = RssFeedCollector(
            properties = ClippingMcpServerProperties(maxContentLength = 5000),
            urlSafetyValidator = validator,
            articleContentExtractor = extractor,
            metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        )

        val source = RssSource(
            id = "source-1",
            name = "Test Source",
            url = "http://127.0.0.1:$port/feed.xml",
            categoryId = "cat-1"
        )

        val items = collector.collect(source, 24)

        items shouldHaveSize 1
        items[0].title shouldBe "Short RSS item"
        items[0].content shouldBe "short"
    }

    @Test
    fun `should not call full page fallback when rss content is long enough`() {
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        server = httpServer
        val port = httpServer.address.port
        val longContent = "This RSS description is already long enough to avoid fallback extraction."

        httpServer.createContext("/feed.xml") { exchange ->
            val body = """
                <rss version="2.0">
                  <channel>
                    <title>Test Feed</title>
                    <item>
                      <title>Long RSS item</title>
                      <link>http://127.0.0.1:$port/article-1</link>
                      <description>$longContent</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        httpServer.start()

        val validator = mockk<UrlSafetyValidator>()
        every { validator.validatePublicHttpUrl(any()) } answers { URI(firstArg()) }

        val extractor = mockk<ArticleContentExtractor>()

        val collector = RssFeedCollector(
            properties = ClippingMcpServerProperties(
                maxContentLength = 5000,
                rssFallbackMinContentLength = 20,
            ),
            urlSafetyValidator = validator,
            articleContentExtractor = extractor,
            metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        )

        val source = RssSource(
            id = "source-1",
            name = "Test Source",
            url = "http://127.0.0.1:$port/feed.xml",
            categoryId = "cat-1"
        )

        val items = collector.collect(source, 24)

        items shouldHaveSize 1
        items[0].content shouldBe longContent
        verify(exactly = 0) { extractor.extract(any()) }
    }

    @Test
    fun `should reuse cached feed for shared source url without fallback extraction when enrichment is disabled`() {
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        server = httpServer
        val port = httpServer.address.port
        val requestCount = AtomicInteger(0)

        httpServer.createContext("/feed.xml") { exchange ->
            requestCount.incrementAndGet()
            val body = """
                <rss version="2.0">
                  <channel>
                    <title>Shared Feed</title>
                    <item>
                      <title>Shared short item</title>
                      <link>http://127.0.0.1:$port/article-1?utm_source=rss&id=1</link>
                      <description>short</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        httpServer.start()

        val validator = mockk<UrlSafetyValidator>()
        every { validator.validatePublicHttpUrl(any()) } answers { URI(firstArg()) }
        val extractor = mockk<ArticleContentExtractor>()

        val collector = RssFeedCollector(
            properties = ClippingMcpServerProperties(maxContentLength = 5000),
            urlSafetyValidator = validator,
            articleContentExtractor = extractor,
            metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        )
        val firstSource = RssSource(
            id = "source-1",
            name = "Shared Source A",
            url = "http://127.0.0.1:$port/feed.xml",
            categoryId = "cat-1"
        )
        val secondSource = firstSource.copy(id = "source-2", name = "Shared Source B", categoryId = "cat-2")

        val firstItems = collector.collect(firstSource, 24, enrichShortContent = false)
        val secondItems = collector.collect(secondSource, 24, enrichShortContent = false)

        requestCount.get() shouldBe 1
        firstItems shouldHaveSize 1
        secondItems shouldHaveSize 1
        firstItems[0].categoryId shouldBe "cat-1"
        firstItems[0].rssSourceId shouldBe "source-1"
        secondItems[0].categoryId shouldBe "cat-2"
        secondItems[0].rssSourceId shouldBe "source-2"
        secondItems[0].link shouldBe "http://127.0.0.1:$port/article-1?id=1"
        verify(exactly = 0) { extractor.extract(any()) }
    }

    @Test
    fun `should reuse cached feed when feed url differs only by tracking parameters`() {
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        server = httpServer
        val port = httpServer.address.port
        val requestCount = AtomicInteger(0)

        httpServer.createContext("/feed.xml") { exchange ->
            requestCount.incrementAndGet()
            val body = """
                <rss version="2.0">
                  <channel>
                    <title>Tracked Feed</title>
                    <item>
                      <title>Tracked feed item</title>
                      <link>http://127.0.0.1:$port/article-1?id=1</link>
                      <description>This description is long enough to avoid fallback extraction.</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        httpServer.start()

        val validator = mockk<UrlSafetyValidator>()
        every { validator.validatePublicHttpUrl(any()) } answers { URI(firstArg()) }
        val extractor = mockk<ArticleContentExtractor>()
        val collector = RssFeedCollector(
            properties = ClippingMcpServerProperties(maxContentLength = 5000),
            urlSafetyValidator = validator,
            articleContentExtractor = extractor,
            metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        )

        val firstSource = RssSource(
            id = "source-1",
            name = "Tracked Source A",
            url = "http://127.0.0.1:$port/feed.xml?utm_source=slack",
            categoryId = "cat-1"
        )
        val secondSource = firstSource.copy(
            id = "source-2",
            name = "Tracked Source B",
            url = "http://127.0.0.1:$port/feed.xml?utm_source=email&utm_campaign=daily",
            categoryId = "cat-2"
        )

        val firstItems = collector.collect(firstSource, 24, enrichShortContent = false)
        val secondItems = collector.collect(secondSource, 24, enrichShortContent = false)

        requestCount.get() shouldBe 1
        firstItems shouldHaveSize 1
        secondItems shouldHaveSize 1
        secondItems[0].categoryId shouldBe "cat-2"
        secondItems[0].rssSourceId shouldBe "source-2"
        verify(exactly = 0) { extractor.extract(any()) }
    }

    @Test
    fun `should deduplicate canonical article links before fallback extraction`() {
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        server = httpServer
        val port = httpServer.address.port
        val canonicalArticleUrl = "http://127.0.0.1:$port/article-1?id=1"

        httpServer.createContext("/feed.xml") { exchange ->
            val body = """
                <rss version="2.0">
                  <channel>
                    <title>Duplicate Link Feed</title>
                    <item>
                      <title>Tracked RSS item A</title>
                      <link>http://127.0.0.1:$port/article-1?utm_source=slack&id=1</link>
                      <description>short</description>
                    </item>
                    <item>
                      <title>Tracked RSS item B</title>
                      <link>http://127.0.0.1:$port/article-1?id=1&amp;utm_campaign=daily</link>
                      <description>short</description>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        httpServer.start()

        val validator = mockk<UrlSafetyValidator>()
        every { validator.validatePublicHttpUrl(any()) } answers { URI(firstArg()) }
        val extractor = mockk<ArticleContentExtractor>()
        every { extractor.extract(canonicalArticleUrl) } returns
            ExtractedArticle(
                title = "Full Article",
                content = "Canonical duplicate full article content.",
                language = Language.FOREIGN
            )
        val collector = RssFeedCollector(
            properties = ClippingMcpServerProperties(maxContentLength = 5000),
            urlSafetyValidator = validator,
            articleContentExtractor = extractor,
            metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
        )
        val source = RssSource(
            id = "source-1",
            name = "Duplicate Link Source",
            url = "http://127.0.0.1:$port/feed.xml",
            categoryId = "cat-1"
        )

        val items = collector.collect(source, 24)

        items shouldHaveSize 1
        items[0].link shouldBe canonicalArticleUrl
        items[0].content shouldBe "Canonical duplicate full article content."
        verify(exactly = 1) { extractor.extract(canonicalArticleUrl) }
    }
}
