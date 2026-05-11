package com.ohmyclipping.config

import com.ohmyclipping.resilience.TokenBucketRateLimiter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 외부 API Rate Limiter 빈 설정.
 * Gemini RPM과 Slack RPM을 Token Bucket으로 보호한다.
 */
@Configuration
class ResilienceConfig {

    @Bean
    fun geminiRateLimiter(properties: ClippingMcpServerProperties): TokenBucketRateLimiter {
        return TokenBucketRateLimiter(
            name = "gemini",
            permitsPerMinute = properties.geminiRpmLimit,
            maxBurst = (properties.geminiRpmLimit / 6).coerceAtLeast(5),
        )
    }

    @Bean
    fun slackRateLimiter(properties: ClippingMcpServerProperties): TokenBucketRateLimiter {
        return TokenBucketRateLimiter(
            name = "slack",
            permitsPerMinute = properties.slackRpmLimit,
            maxBurst = 3,
        )
    }
}
