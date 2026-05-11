package com.ohmyclipping.support

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Slack 채널 입력값(채널 ID, 채널 링크, '#채널', 'ID:Cxxxx')을
 * 표준 Slack ID로 정규화한다.
 * 채널(C/G), DM 채널(D), 사용자 ID(U) 모두 유효한 Slack 대상이다.
 */
object SlackChannelIdNormalizer {
    // 채널(C/G), DM 채널(D), 사용자 ID(U) 모두 유효한 Slack 대상이다.
    private val channelPattern = Regex("^[CGDU][A-Z0-9]{8,}$")

    /**
     * 다양한 형태의 Slack 채널 입력을 표준 채널 ID로 정규화한다.
     */
    fun normalize(raw: String?): String? {
        // null 입력은 정규화 불가로 그대로 null을 반환한다.
        if (raw == null) return null
        // URL/별칭 등 입력 형태를 먼저 채널 후보 문자열로 추출한다.
        val candidate = parseCandidate(raw.trim())
        // 접두어/대소문자/표기 흔들림을 채널 ID 형태로 정규화한다.
        val normalized = normalizeCandidate(candidate)
        return if (channelPattern.matches(normalized)) normalized else null
    }

    private fun parseCandidate(raw: String): String {
        // 빈 문자열은 즉시 빈 후보로 반환한다.
        if (raw.isBlank()) return ""
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            return raw
        }

        // URL 파싱 실패 시 원문을 그대로 반환해 후속 정규화에서 걸러낸다.
        val parsed = kotlin.runCatching { URI(raw) }.getOrNull() ?: return raw
        return parseCandidateFromUrl(parsed) ?: raw
    }

    private fun parseCandidateFromUrl(parsed: URI): String? {
        // archives/{channelId} 경로를 우선 탐색한다.
        val archiveChannel = extractArchiveChannel(parsed)
        if (!archiveChannel.isNullOrBlank()) return archiveChannel

        // query string의 channel 파라미터를 차순위로 탐색한다.
        val queryChannel = extractQueryChannel(parsed)
        if (!queryChannel.isNullOrBlank()) return queryChannel

        // 마지막으로 fragment 내 channel 파라미터를 확인한다.
        return extractFragmentChannel(parsed)
    }

    private fun extractArchiveChannel(parsed: URI): String? {
        val segments = parsed.path.split("/").filter { it.isNotBlank() }
        val archiveIdx = segments.indexOfFirst { it.equals("archives", ignoreCase = true) }
        return if (archiveIdx >= 0 && archiveIdx + 1 < segments.size) {
            segments[archiveIdx + 1]
        } else {
            null
        }
    }

    private fun extractQueryChannel(parsed: URI): String? =
        parsed.query?.split("&")
            ?.mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = pair.substring(0, separator)
                if (!key.equals("channel", ignoreCase = true)) return@mapNotNull null
                decodeComponent(pair.substring(separator + 1))
            }
            ?.firstOrNull()

    private fun extractFragmentChannel(parsed: URI): String? =
        parsed.fragment
            ?.substringAfter("channel=")
            ?.substringBefore("&")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun normalizeCandidate(candidate: String): String {
        // 화면 입력에서 흔히 섞이는 '#', 'ID:' 접두어를 제거한다.
        if (candidate.isBlank()) return ""
        val stripped = candidate
            .trim()
            .replace("#", "")
            .replaceFirst(Regex("^ID\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        return stripped.trim().uppercase()
    }

    private fun decodeComponent(value: String): String =
        // URL 인코딩된 query 값을 안전하게 복호화한다.
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8) }.getOrElse { value }
}
