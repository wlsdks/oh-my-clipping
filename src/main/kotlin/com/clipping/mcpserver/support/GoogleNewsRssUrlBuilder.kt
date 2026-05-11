package com.clipping.mcpserver.support

import java.net.URLEncoder

/**
 * 경쟁사 키워드로 Google News RSS 검색 URL을 생성한다.
 * 키워드를 OR로 연결하여 경쟁사당 1개의 URL을 만든다.
 */
object GoogleNewsRssUrlBuilder {

    private const val BASE_URL = "https://news.google.com/rss/search"

    /**
     * 키워드 리스트로 Google News KR RSS URL을 생성한다.
     * @param keywords 검색 키워드 (빈 문자열 무시)
     * @param language 언어 코드 (기본: ko)
     * @param country 국가 코드 (기본: KR)
     * @return RSS URL, 유효한 키워드가 없으면 null
     */
    fun buildUrl(
        keywords: List<String>,
        excludeKeywords: List<String> = emptyList(),
        language: String = "ko",
        country: String = "KR"
    ): String? {
        val filtered = keywords.map { it.trim() }.filter { it.isNotBlank() }
        if (filtered.isEmpty()) return null

        // 검색 키워드를 OR로 연결한다.
        val includeQuery = filtered.joinToString("+OR+") { encodeKeyword(it) }

        // 제외 키워드를 -접두어로 추가한다.
        val excludeFiltered = excludeKeywords.map { it.trim() }.filter { it.isNotBlank() }
        val excludeQuery = excludeFiltered.joinToString("+") { "-${encodeKeyword(it)}" }

        val query = if (excludeQuery.isNotBlank()) "$includeQuery+$excludeQuery" else includeQuery
        return "$BASE_URL?q=$query&hl=$language&gl=$country&ceid=$country:$language"
    }

    private fun encodeKeyword(keyword: String): String =
        URLEncoder.encode(keyword, Charsets.UTF_8)
            .replace("+", "%20")
}
