package com.ohmyclipping.rss

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.content.ArticleContentExtractor
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.Language
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.support.InterruptibleSleep
import com.ohmyclipping.support.UrlCanonicalizer
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.net.HttpURLConnection
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}
private val KOREAN_PATTERN = Regex("[가-힣]")

@Component
class RssFeedCollector(
    private val properties: ClippingMcpServerProperties,
    private val urlSafetyValidator: UrlSafetyValidator,
    private val articleContentExtractor: ArticleContentExtractor,
    private val metrics: ClippingMetrics
) {

    /**
     * URL 기반 RSS fetch 캐시.
     * 같은 URL을 여러 카테고리/구독에서 요청해도 TTL 이내엔 1번만 HTTP fetch한다.
     */
    private data class CachedFeed(
        val items: List<RssItem>,
        val fetchedAt: Instant
    ) {
        /** 캐시 유효 시간 (30분) */
        fun isValid(): Boolean =
            Duration.between(fetchedAt, Instant.now()).toMinutes() < CACHE_TTL_MINUTES
    }

    private val fetchCache = ConcurrentHashMap<String, CachedFeed>()

    @PostConstruct
    fun registerCacheSizeGauge() {
        // RSS fetch 캐시 크기를 실시간으로 노출하여 운영 대시보드에서 캐시 활용률을 모니터링한다.
        metrics.registerRssCacheSizeGauge { fetchCache.size }
    }

    companion object {
        /** RSS fetch 캐시 TTL (분). 같은 URL에 대한 중복 HTTP 요청을 제거한다. */
        const val CACHE_TTL_MINUTES = 30L
        /** RSS fetch 캐시 최대 크기. 초과 시 가장 오래된 항목부터 제거한다. */
        const val MAX_CACHE_SIZE = 500
    }

    /**
     * RSS 수집 실패 시 지정된 재시도 횟수만큼 반복하고, 최종 실패는 상위 계층으로 전파한다.
     * 같은 URL이 TTL 이내에 이미 수집되었으면 캐시된 결과를 반환한다.
     */
    fun collect(
        source: RssSource,
        hoursBack: Int,
        enrichShortContent: Boolean = true,
    ): List<RssItem> {
        requirePositiveHoursBack(hoursBack)
        val url = urlSafetyValidator.validatePublicHttpUrl(source.url).toURL()
        val cutoff = Instant.now().minus(hoursBack.toLong(), ChronoUnit.HOURS)

        // 캐시 히트: 같은 URL이 최근에 수집되었으면 결과를 재사용한다.
        val cacheKey = canonicalFeedCacheKey(source.url, url)
        val cached = fetchCache[cacheKey]
        if (cached != null && cached.isValid()) {
            metrics.recordRssCacheHit()
            log.debug { "RSS cache hit: ${source.name} (${source.url})" }
            // 캐시된 아이템을 현재 소스/카테고리에 맞게 복제한다.
            val items = cached.items
                .filter {
                    val publishedAt = it.publishedAt
                    publishedAt == null || publishedAt.isAfter(cutoff)
                }
                .map { it.copy(id = UUID.randomUUID().toString(), categoryId = source.categoryId, rssSourceId = source.id) }
            return if (enrichShortContent) items.map { enrichShortContent(it) } else items
        }

        metrics.recordRssCacheMiss()
        val maxAttempts = properties.rssMaxAttempts.coerceIn(1, 5)
        var lastError: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                val items = collectOnce(source, url, cutoff, enrichShortContent)
                // 수집 성공 시 캐시에 저장한다.
                fetchCache[cacheKey] = CachedFeed(items = items, fetchedAt = Instant.now())
                evictExpiredCache()
                return items
            } catch (e: Exception) {
                lastError = e
                if (attempt >= maxAttempts) {
                    break
                }
                log.warn(e) {
                    "Failed to collect RSS from ${source.name} (${source.url}), retry=$attempt/$maxAttempts"
                }
                sleepBeforeRetry(source, attempt)
            }
        }

        val failure = lastError ?: IllegalStateException("Unknown RSS collect failure")
        throw IllegalStateException(
            "Failed to collect RSS from ${source.name} (${source.url}) after $maxAttempts attempts: ${failure.message}",
            failure
        )
    }

    /**
     * URL만으로 RSS 피드를 수집한다. RssSource 없이도 사용 가능하며,
     * 경쟁사 모니터링처럼 소스 엔티티가 불필요한 경우에 사용한다.
     * 반환되는 RssItem의 categoryId는 빈 문자열, rssSourceId는 null이다.
     */
    fun collectByUrl(url: String, hoursBack: Int): List<RssItem> {
        requirePositiveHoursBack(hoursBack)
        val validatedUrl = urlSafetyValidator.validatePublicHttpUrl(url).toURL()
        val cutoff = Instant.now().minus(hoursBack.toLong(), ChronoUnit.HOURS)

        // 캐시 히트: 같은 URL이 최근에 수집되었으면 결과를 재사용한다.
        val cacheKey = canonicalFeedCacheKey(url, validatedUrl)
        val cached = fetchCache[cacheKey]
        if (cached != null && cached.isValid()) {
            metrics.recordRssCacheHit()
            log.debug { "RSS cache hit (by URL): $url" }
            return cached.items
                .filter {
                    val publishedAt = it.publishedAt
                    publishedAt == null || publishedAt.isAfter(cutoff)
                }
                .map { it.copy(id = UUID.randomUUID().toString(), categoryId = "", rssSourceId = null) }
                .map { enrichShortContent(it) }
        }

        metrics.recordRssCacheMiss()
        val maxAttempts = properties.rssMaxAttempts.coerceIn(1, 5)
        var lastError: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                val items = fetchAndParse(
                    validatedUrl,
                    cutoff,
                    categoryId = "",
                    rssSourceId = null,
                    enrichShortContent = true,
                )
                // 수집 성공 시 캐시에 저장한다.
                fetchCache[cacheKey] = CachedFeed(items = items, fetchedAt = Instant.now())
                evictExpiredCache()
                return items
            } catch (e: Exception) {
                lastError = e
                if (attempt >= maxAttempts) break
                log.warn(e) { "Failed to collect RSS from $url, retry=$attempt/$maxAttempts" }
                val baseBackoff = properties.rssRetryBackoffMs.coerceAtLeast(0)
                val sleepMillis = (baseBackoff * attempt.toLong()).coerceAtMost(5_000L)
                if (sleepMillis > 0) {
                    InterruptibleSleep.sleep(delayMs = sleepMillis, context = "RSS retry for URL $url")
                }
            }
        }

        val failure = lastError ?: IllegalStateException("Unknown RSS collect failure")
        throw IllegalStateException(
            "Failed to collect RSS from $url after $maxAttempts attempts: ${failure.message}",
            failure
        )
    }

    /** 만료된 캐시 엔트리를 제거하고, 최대 크기를 초과하면 가장 오래된 항목부터 제거한다. */
    private fun evictExpiredCache() {
        fetchCache.entries.removeIf { !it.value.isValid() }
        // 캐시 크기 상한은 properties.rssCacheMaxSize를 사용하고, MAX_CACHE_SIZE는 폴백 참고값이다.
        val maxSize = properties.rssCacheMaxSize
        if (fetchCache.size > maxSize) {
            val sortedKeys = fetchCache.entries
                .sortedBy { it.value.fetchedAt }
                .map { it.key }
            val toRemove = sortedKeys.take(fetchCache.size - maxSize)
            toRemove.forEach { fetchCache.remove(it) }
        }
    }

    private fun requirePositiveHoursBack(hoursBack: Int) {
        if (hoursBack <= 0) {
            throw InvalidInputException("hoursBack must be greater than 0")
        }
    }

    private fun canonicalFeedCacheKey(rawUrl: String, validatedUrl: java.net.URL): String =
        runCatching {
            UrlCanonicalizer.canonicalize(validatedUrl.toURI()).toString()
        }.getOrDefault(rawUrl.trim()).lowercase()

    private fun collectOnce(
        source: RssSource,
        url: java.net.URL,
        cutoff: Instant,
        enrichShortContent: Boolean,
    ): List<RssItem> =
        fetchAndParse(
            url,
            cutoff,
            categoryId = source.categoryId,
            rssSourceId = source.id,
            enrichShortContent = enrichShortContent,
        )

    /**
     * HTTP fetch + RSS parse + 본문 추출 공통 로직.
     * collect()와 collectByUrl() 모두 이 메서드를 호출한다.
     */
    private fun fetchAndParse(
        url: java.net.URL,
        cutoff: Instant,
        categoryId: String,
        rssSourceId: String?,
        enrichShortContent: Boolean,
    ): List<RssItem> {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = properties.rssConnectionTimeoutMs
        connection.readTimeout = properties.rssReadTimeoutMs
        connection.setRequestProperty("User-Agent", "ClippingBot/1.0")

        val input = SyndFeedInput()
        val feed = connection.inputStream.use { stream ->
            XmlReader(stream, connection.contentType).use { reader ->
                input.build(reader)
            }
        }

        return feed.entries
            .filter { entry ->
                val published = entry.publishedDate?.toInstant() ?: entry.updatedDate?.toInstant()
                published == null || published.isAfter(cutoff)
            }
            .mapNotNull { entry ->
                val rawLink = entry.link?.trim() ?: return@mapNotNull null
                val canonicalLink = UrlCanonicalizer.canonicalizeToString(rawLink)
                val title = entry.title?.trim().orEmpty().ifBlank { "Untitled" }
                val rssContent = entry.description?.value
                    ?: entry.contents?.firstOrNull()?.value
                    ?: ""
                val rawContent = rssContent.trim()
                val content = org.jsoup.Jsoup.parse(rawContent).text()
                    .take(properties.maxContentLength * 2)
                val published = entry.publishedDate?.toInstant()
                    ?: entry.updatedDate?.toInstant()

                RssItem(
                    id = UUID.randomUUID().toString(),
                    title = title.trim().take(1000),
                    content = content,
                    link = canonicalLink,
                    publishedAt = published,
                    language = detectLanguage(title + " " + content),
                    categoryId = categoryId,
                    rssSourceId = rssSourceId
                )
            }
            .distinctBy { it.link }
            .map { item ->
                if (enrichShortContent) enrichShortContent(item) else item
            }
    }

    fun enrichShortContent(item: RssItem): RssItem {
        val currentContent = item.content.orEmpty()
        if (currentContent.trim().length >= properties.rssFallbackMinContentLength) {
            return item
        }

        val startedAt = Instant.now()
        val extracted = runCatching {
            articleContentExtractor.extract(item.link)
        }.onFailure { e ->
            log.debug(e) { "Failed to enrich short RSS item: ${item.link}" }
        }.getOrNull()
        metrics.recordExtraction(
            context = "rss_fallback",
            success = extracted != null,
            durationMs = Duration.between(startedAt, Instant.now()).toMillis()
        )
        if (extracted == null) return item

        val content = org.jsoup.Jsoup.parse(extracted.content.trim()).text()
            .take(properties.maxContentLength * 2)
        val title = if (item.title == "Untitled") {
            extracted.title.takeIf { it.isNotBlank() } ?: item.title
        } else {
            item.title
        }
        return item.copy(
            title = title.trim().take(1000),
            content = content,
            language = extracted.language,
        )
    }

    private fun sleepBeforeRetry(source: RssSource, attempt: Int) {
        val baseBackoff = properties.rssRetryBackoffMs.coerceAtLeast(0)
        val sleepMillis = (baseBackoff * attempt.toLong()).coerceAtMost(5_000L)
        if (sleepMillis <= 0) return
        InterruptibleSleep.sleep(
            delayMs = sleepMillis,
            context = "RSS retry for ${source.name}"
        )
    }

    private fun detectLanguage(text: String): Language {
        val koreanCount = KOREAN_PATTERN.findAll(text).count()
        return if (koreanCount > 5) Language.KOREAN else Language.FOREIGN
    }
}
