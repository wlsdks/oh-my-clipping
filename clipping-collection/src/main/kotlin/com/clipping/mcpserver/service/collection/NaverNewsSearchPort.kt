package com.clipping.mcpserver.service.collection

import java.time.Instant

/**
 * SearchCo 뉴스 검색을 수행하는 outbound port.
 */
interface NaverNewsSearchPort {

    fun isConfigured(): Boolean

    fun searchNews(query: String, display: Int = DEFAULT_DISPLAY): List<NaverNewsSearchItem>

    companion object {
        const val DEFAULT_DISPLAY = 20
    }
}

/**
 * SearchCo 뉴스 검색 결과 DTO.
 */
data class NaverNewsSearchItem(
    val title: String,
    val link: String,
    val description: String,
    val pubDate: String,
    val publishedAt: Instant? = null
)
