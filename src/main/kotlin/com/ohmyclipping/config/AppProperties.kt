package com.ohmyclipping.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 애플리케이션 전역 설정.
 * Slack 다이제스트의 클릭 추적 URL 등 절대 URL 생성에 사용된다.
 */
@ConfigurationProperties(prefix = "clipping-mcp-server.app")
data class AppProperties(
    /** 서버의 외부 접근 가능한 기본 URL (Slack 버튼 등 절대 URL 생성 시 사용). */
    val baseUrl: String = "http://localhost:8086"
)
