package com.ohmyclipping.rss

import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.service.source.SourceVerificationClient
import com.ohmyclipping.service.source.VerificationResult
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * HTTP 기반 RSS 소스 접근 가능 여부를 검증하는 클라이언트.
 * robots.txt 차단 여부를 먼저 확인한 뒤, RSS 피드 파싱 가능 여부를 검사한다.
 * Google News 등 RSS 구독 전용 도메인은 robots.txt 검사를 건너뛴다.
 *
 * SSRF 방어: 자동 리다이렉트를 비활성화하고, 리다이렉트 대상을 UrlSafetyValidator로
 * 재검증한 뒤 수동 추적한다 (최대 3홉). 응답 크기를 1MB로 제한하여 OOM DoS를 방지한다.
 */
@Component
class HttpSourceVerificationClient(
    private val urlSafetyValidator: UrlSafetyValidator
) : SourceVerificationClient {

    companion object {
        /** robots.txt 검사를 건너뛰는 도메인 목록 — RSS 구독 전용이므로 크롤링 차단과 무관하다. */
        internal val ROBOTS_BYPASS_HOSTS = setOf("news.google.com")

        /** 리다이렉트 최대 추적 횟수 — DNS rebinding 방어를 위해 매 홉마다 SSRF 검증을 수행한다. */
        private const val MAX_REDIRECTS = 3

        /** 응답 본문 최대 크기 (1MB) — OOM DoS 방지. */
        private const val MAX_RESPONSE_BYTES = 1_048_576L
    }

    override fun verify(sourceUri: URI): VerificationResult {
        // Google News RSS 피드처럼 구독 전용 도메인은 robots.txt 차단과 무관하므로 검사를 건너뛴다.
        val robotsBlocked = if (sourceUri.host in ROBOTS_BYPASS_HOSTS) {
            false
        } else {
            val robotsUri = sourceUri.resolve("/robots.txt")
            isBlockedByRobots(robotsUri, sourceUri.path.ifBlank { "/" })
        }
        return if (robotsBlocked) {
            VerificationResult.ROBOTS_BLOCKED
        } else if (canFetchRss(sourceUri)) {
            VerificationResult.VERIFIED
        } else {
            VerificationResult.FEED_ERROR
        }
    }

    /**
     * 안전한 HTTP 연결을 열고 리다이렉트를 수동 추적한다.
     * 자동 리다이렉트를 비활성화하여 DNS rebinding 공격을 방지하고,
     * 리다이렉트 대상마다 UrlSafetyValidator로 SSRF 검증을 재수행한다.
     */
    private fun openSafeConnection(
        uri: URI,
        remainingRedirects: Int = MAX_REDIRECTS
    ): HttpURLConnection {
        val conn = (uri.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ClippingBot/1.0")
        }

        val status = conn.responseCode
        // 리다이렉트 응답이면 Location 헤더를 SSRF 검증 후 수동 추적한다.
        if (status in 301..308 && remainingRedirects > 0) {
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (location == null) return conn
            val redirectUri = urlSafetyValidator.validatePublicHttpUrl(location)
            return openSafeConnection(redirectUri, remainingRedirects - 1)
        }
        return conn
    }

    /**
     * 응답 본문을 최대 [maxBytes]까지만 읽어 OOM DoS를 방지한다.
     */
    private fun readLimited(
        conn: HttpURLConnection,
        maxBytes: Long = MAX_RESPONSE_BYTES
    ): String {
        return conn.inputStream.use { input ->
            val reader = input.buffered().reader()
            val sb = StringBuilder()
            val buf = CharArray(8192)
            var totalRead = 0L
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                totalRead += n
                if (totalRead > maxBytes) break
                sb.append(buf, 0, n)
            }
            sb.toString()
        }
    }

    private fun isBlockedByRobots(robotsUri: URI, path: String): Boolean {
        return try {
            val conn = openSafeConnection(robotsUri)
            try {
                if (conn.responseCode != 200) return false
                val content = readLimited(conn)
                parseRobotsTxt(content, path)
            } finally {
                conn.disconnect()
            }
        } catch (_: IOException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    internal fun parseRobotsTxt(content: String, path: String): Boolean {
        var inUserAgentAll = false
        for (line in content.lines()) {
            val trimmed = line.trim().lowercase()
            if (trimmed.startsWith("user-agent:")) {
                val agent = trimmed.substringAfter("user-agent:").trim()
                inUserAgentAll = agent == "*"
            } else if (inUserAgentAll && trimmed.startsWith("disallow:")) {
                val disallowed = trimmed.substringAfter("disallow:").trim()
                if (disallowed.isNotEmpty() && path.startsWith(disallowed)) {
                    return true
                }
            }
        }
        return false
    }

    private fun canFetchRss(uri: URI): Boolean {
        val conn = openSafeConnection(uri)
        try {
            val code = conn.responseCode
            if (code != 200) return false

            val contentType = conn.contentType ?: ""
            // 응답 크기를 제한하여 읽되, RSS 판별에는 앞부분만 필요하다.
            val content = readLimited(conn)
            return content.contains("<rss") || content.contains("<feed") ||
                content.contains("<channel") || contentType.contains("xml") ||
                contentType.contains("rss")
        } finally {
            conn.disconnect()
        }
    }
}
