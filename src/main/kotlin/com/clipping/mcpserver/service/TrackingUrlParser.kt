package com.clipping.mcpserver.service

import com.clipping.mcpserver.config.AppProperties
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Slack 에 공유된 URL 이 우리 tracking endpoint 인지 감지하고 summaryId 를 추출한다.
 *
 * 허용 경로:
 *   - /api/track/click/slack/{sid}           (path-based, PR3a)
 *   - /api/track/click?sid={sid}&url=...     (legacy query-based)
 *
 * host 검증: [AppProperties.baseUrl] 의 host 와 일치할 때만 accept.
 * baseUrl 이 비어있거나 파싱 불가면 host 검증을 skip 하여 테스트/로컬 환경에서도 동작한다.
 */
@Component
class TrackingUrlParser(
    private val appProperties: AppProperties,
) {

    /**
     * baseUrl 에서 추출한 host. null 이면 host 검증을 skip 한다.
     * 매 호출마다 URI 파싱을 피하려고 lazy 로 1회만 계산한다.
     */
    private val allowedHost: String? by lazy {
        runCatching { URI(appProperties.baseUrl).host }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * 주어진 URL 문자열이 tracking URL 이면 summaryId 를 반환한다.
     * 매칭 실패 / 허용 host 불일치 / 형식 오류는 모두 null.
     */
    fun extractSummaryId(url: String): String? {
        if (url.isBlank()) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null

        // host 검증: 설정된 host 가 있고 URI 에도 host 가 있으면 반드시 일치해야 한다.
        val host = uri.host
        if (allowedHost != null && host != null && !host.equals(allowedHost, ignoreCase = true)) {
            return null
        }

        val path = uri.path ?: return null

        // Path-based: /api/track/click/slack/{sid}
        val pathMatch = PATH_SID_REGEX.find(path)
        if (pathMatch != null) {
            return decodeSid(pathMatch.groupValues[1])
        }

        // Legacy query: /api/track/click?sid=...
        if (path == LEGACY_PATH) {
            val query = uri.rawQuery ?: return null
            val sid = query.split("&")
                .firstOrNull { it.startsWith(SID_QUERY_PREFIX) }
                ?.removePrefix(SID_QUERY_PREFIX)
                ?: return null
            return decodeSid(sid)
        }

        return null
    }

    /** URL-encoded summaryId 를 디코드. 빈 문자열은 null 처리. */
    private fun decodeSid(raw: String): String? {
        if (raw.isBlank()) return null
        return runCatching {
            URLDecoder.decode(raw, StandardCharsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        private val PATH_SID_REGEX = Regex("^/api/track/click/slack/([^/?#]+)$")
        private const val LEGACY_PATH = "/api/track/click"
        private const val SID_QUERY_PREFIX = "sid="
    }
}
