package com.clipping.mcpserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "clipping-mcp-server")
data class ClippingMcpServerProperties(
    val defaultHoursBack: Int = 24,
    val batchSize: Int = 50,
    val maxContentLength: Int = 5000,
    val rssConnectionTimeoutMs: Int = 10000,
    val rssReadTimeoutMs: Int = 15000,
    val rssMaxAttempts: Int = 3,
    val rssRetryBackoffMs: Long = 250,
    val rssFallbackMinContentLength: Int = 200,
    val pageConnectionTimeoutMs: Int = 10000,
    val pageReadTimeoutMs: Int = 15000,
    val maxExtractedContentLength: Int = 15000,
    val jobEnabled: Boolean = true,
    val jobPollDelayMs: Long = 3000,
    val jobWorkerBatchSize: Int = 5,
    val jobMaxAttempts: Int = 3,
    val jobInitialBackoffSeconds: Int = 30,
    val retentionDefaultDays: Int = 30,
    val digestMinImportanceScore: Float = 0.5f,
    val digestDefaultMaxItems: Int = 5,
    val digestMaxMessageChars: Int = 3500,
    val digestItemSummaryMaxChars: Int = 960,
    val digestKeywordMaxCount: Int = 6,
    val summaryMinChars: Int = 220,
    val summaryMaxChars: Int = 1200,
    val summaryMinParagraphCount: Int = 2,
    val summaryMinSentenceCount: Int = 3,
    val summaryKeywordMinCount: Int = 3,
    val summaryKeywordMaxCount: Int = 5,
    val ralphOrchestrationEnabled: Boolean = false,
    val ralphLoopEnabled: Boolean = true,
    val ralphLoopMaxIterations: Int = 4,
    val ralphLoopStopPhrase: String = "RALPH_STOP",
    val screeningThreshold: Float = 0.4f,
    val llmInputCostPerMillionUsd: Double = 0.10,
    val llmOutputCostPerMillionUsd: Double = 0.40,
    val geminiRpmLimit: Int = 800,
    val slackRpmLimit: Int = 18,
    val rssCacheMaxSize: Int = 1000,
    val rateLimit: RateLimitProperties = RateLimitProperties()
)

/**
 * 레이트 리밋 관련 설정.
 */
data class RateLimitProperties(
    /** 분당 최대 로그인 시도 횟수 (프로덕션 기본: 10, 테스트: 60). */
    val maxLoginAttemptsPerMinute: Int = 10,
    /** 분당 최대 사용자 API 요청 수. */
    val maxUserRequestsPerMinute: Int = 60,
    /** 분당 최대 관리자 API 쓰기 요청 수. */
    val maxAdminWriteRequestsPerMinute: Int = 100,
    /** 분당 최대 관리자 API 읽기 요청 수. */
    val maxAdminReadRequestsPerMinute: Int = 500,
    /** 분당 최대 익명 공개 API IP별 요청 수. */
    val maxPublicIpRequestsPerMinute: Int = 60
)
