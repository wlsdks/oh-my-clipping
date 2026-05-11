package com.ohmyclipping.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * SearchCo 뉴스 검색 API 연동 설정.
 * client-id/client-secret이 비어 있으면 SearchCo 수집을 건너뛴다.
 */
@ConfigurationProperties(prefix = "clipping-mcp-server.naver")
data class NaverProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val apiBaseUrl: String = "https://openapi.naver.com/v1/search/news.json",
    val connectTimeoutMs: Int = 10000,
    val readTimeoutMs: Int = 15000,
    val defaultDisplay: Int = 20,
    val maxDisplay: Int = 100
)
