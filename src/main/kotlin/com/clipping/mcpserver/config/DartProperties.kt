package com.clipping.mcpserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * DART OpenAPI 연동 설정.
 * 기업 코드 목록을 주기적으로 갱신하여 기업명 자동완성에 활용한다.
 */
@ConfigurationProperties(prefix = "clipping-mcp-server.dart")
data class DartProperties(
    val apiKey: String = "",
    val corpCodeUrl: String = "https://opendart.fss.or.kr/api/corpCode.xml",
    val refreshCron: String = "0 0 3 * * MON",
    val connectTimeoutMs: Int = 10000,
    val readTimeoutMs: Int = 30000
)
