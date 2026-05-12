package com.ohmyclipping.service.source

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.KnownNewsSource
import com.ohmyclipping.service.port.SourceUrlSafetyPort
import com.ohmyclipping.store.KnownNewsSourceStore
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * RSS 피드 자동 탐색 서비스.
 * 사이트명/도메인/URL 입력 → 매핑 테이블 매칭 + 도메인 RSS 경로 크롤링.
 */
@Service
class RssFeedDiscoveryService(
    private val knownNewsSourceStore: KnownNewsSourceStore,
    private val urlSafetyValidator: SourceUrlSafetyPort
) {
    data class DiscoveredFeed(val url: String, val title: String)
    data class DiscoverResult(
        val knownMatch: KnownNewsSource?,
        val discoveredFeeds: List<DiscoveredFeed>
    )

    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val feedPaths = listOf("/rss", "/feed", "/rss.xml", "/atom.xml", "/feed.xml", "/index.xml")

    /**
     * 사이트명/도메인/URL을 입력받아 RSS 피드를 탐색한다.
     * 1. known_news_sources 매핑 테이블 검색
     * 2. 도메인 추출 → 알려진 RSS 경로 순회 크롤링
     */
    fun discover(query: String): DiscoverResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return DiscoverResult(knownMatch = null, discoveredFeeds = emptyList())
        }

        val knownMatches = knownNewsSourceStore.search(trimmed)
        val knownMatch = knownMatches.firstOrNull()

        val domain = extractDomain(trimmed)
        val discoveredFeeds = if (domain != null) discoverFromDomain(domain) else emptyList()

        return DiscoverResult(knownMatch = knownMatch, discoveredFeeds = discoveredFeeds)
    }

    /** 입력에서 도메인을 추출한다. URL이면 호스트, 도메인 형태면 그대로 반환. */
    private fun extractDomain(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        return try {
            val candidate = if (trimmed.startsWith("http", ignoreCase = true)) trimmed else "https://$trimmed"
            URI.create(candidate).host
                ?.trim()
                ?.trimEnd('.')
                ?.lowercase()
                ?.removePrefix("www.")
                ?.takeIf { it.contains(".") }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** 도메인의 알려진 RSS 경로를 순회하며 유효한 피드를 찾는다. */
    private fun discoverFromDomain(domain: String): List<DiscoveredFeed> {
        val feeds = mutableListOf<DiscoveredFeed>()
        for (path in feedPaths) {
            val url = "https://$domain$path"
            try {
                // SSRF 방어: 내부 네트워크/localhost 접근을 차단한다.
                urlSafetyValidator.validatePublicHttpUrl(url)

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                val contentType = response.headers().firstValue("content-type").orElse("")
                val body = response.body()?.take(500) ?: ""
                if (response.statusCode() == 200 && looksLikeFeed(contentType, body)) {
                    val title = extractFeedTitle(body) ?: "$domain RSS"
                    feeds.add(DiscoveredFeed(url = url, title = title))
                }
            } catch (_: InvalidInputException) {
                // 경로 실패는 무시
            } catch (_: IllegalArgumentException) {
                // 경로 실패는 무시
            } catch (_: IOException) {
                // 경로 실패는 무시
            } catch (_: SecurityException) {
                // 경로 실패는 무시
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return feeds
            }
        }
        return feeds
    }

    private fun looksLikeFeed(contentType: String, body: String): Boolean {
        val ct = contentType.lowercase()
        if (ct.contains("xml") || ct.contains("rss") || ct.contains("atom")) return true
        val b = body.trimStart()
        return b.startsWith("<?xml") || b.startsWith("<rss") || b.startsWith("<feed") || b.startsWith("<atom")
    }

    private fun extractFeedTitle(body: String): String? {
        val titleRegex = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
        return titleRegex.find(body)?.groupValues?.get(1)?.trim()
    }
}
