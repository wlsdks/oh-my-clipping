package com.clipping.mcpserver.service.source

import java.net.URI
import java.net.URISyntaxException

/**
 * URL에서 등록 도메인(registered domain)을 추출하는 유틸리티.
 * ccSLD(co.kr, co.jp 등)를 고려하여 올바른 도메인 단위를 반환한다.
 */
object DomainExtractor {

    /** ccSLD 접미사 목록 — 이 접미사 뒤에 TLD가 붙으면 3세그먼트를 사용한다. */
    private val CC_SLDS = setOf(
        "co.kr", "or.kr", "go.kr", "ac.kr", "ne.kr", "re.kr",
        "co.jp", "or.jp", "ne.jp", "ac.jp",
        "co.uk", "org.uk", "ac.uk",
        "com.au", "org.au", "edu.au",
        "com.br", "org.br",
        "co.in", "org.in", "ac.in",
        "com.cn", "org.cn", "edu.cn"
    )

    /**
     * URL 문자열에서 등록 도메인을 추출한다.
     *
     * @param url 전체 URL (예: "https://news.example.co.kr/feed")
     * @return 등록 도메인 (예: "example.co.kr"), 추출 불가 시 null
     */
    fun extract(url: String): String? {
        val host = try {
            URI(url).host?.lowercase()
        } catch (_: URISyntaxException) {
            null
        } ?: return null

        return extractFromHost(host)
    }

    /**
     * 호스트명에서 등록 도메인을 추출한다.
     *
     * @param host 호스트명 (예: "news.example.co.kr")
     * @return 등록 도메인 (예: "example.co.kr")
     */
    fun extractFromHost(host: String): String? {
        val parts = host.split(".")
        if (parts.size < 2) return null

        // ccSLD에 해당하는지 확인 — 마지막 2세그먼트가 ccSLD 목록에 있으면 3세그먼트 사용
        if (parts.size >= 3) {
            val lastTwo = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
            if (lastTwo in CC_SLDS) {
                return if (parts.size >= 3) {
                    "${parts[parts.size - 3]}.$lastTwo"
                } else {
                    host
                }
            }
        }

        // 일반 도메인: 마지막 2세그먼트
        return "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
    }
}
