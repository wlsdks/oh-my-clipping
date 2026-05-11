package com.ohmyclipping.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

private val log = KotlinLogging.logger {}

/**
 * 로그인 엔드포인트(`/login POST`)에 대한 IP 기반 brute force 방지 필터.
 * Redis 기반 슬라이딩 윈도우로 동일 IP에서의 로그인 시도를 제한한다.
 * Redis 장애 시 fail-open으로 요청을 허용한다.
 *
 * 분당 최대 시도 횟수는 `clipping-mcp-server.rate-limit.max-login-attempts-per-minute`로 설정한다.
 * 프로덕션 기본값: 10, 테스트 프로파일: 60.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class LoginRateLimitFilter(
    private val redisRateLimitService: RedisRateLimitService,
    private val properties: ClippingMcpServerProperties
) : WebFilter {

    companion object {
        const val WINDOW_SECONDS = 60L
        private const val KEY_PREFIX = "rl:login:"
        private const val SIGNUP_KEY_PREFIX = "rl:signup:"

        /** 회원가입 엔드포인트 경로 목록. */
        private val SIGNUP_PATHS = setOf("/admin/signup", "/user/signup")
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        val path = request.path.value()

        // 로그인 Rate Limit
        if (path == "/login" && request.method == HttpMethod.POST) {
            val maxAttempts = properties.rateLimit.maxLoginAttemptsPerMinute
            val clientIp = request.remoteAddress?.address?.hostAddress ?: "unknown"
            if (redisRateLimitService.isRateLimited("$KEY_PREFIX$clientIp", maxAttempts, WINDOW_SECONDS)) {
                log.warn { "Login rate limit exceeded for IP: $clientIp (limit=$maxAttempts/min)" }
                exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                return exchange.response.setComplete()
            }
        }

        // 회원가입 Rate Limit (같은 IP에서 분당 최대 시도 수 제한)
        if (path in SIGNUP_PATHS && request.method == HttpMethod.POST) {
            val maxAttempts = properties.rateLimit.maxLoginAttemptsPerMinute
            val clientIp = request.remoteAddress?.address?.hostAddress ?: "unknown"
            if (redisRateLimitService.isRateLimited("$SIGNUP_KEY_PREFIX$clientIp", maxAttempts, WINDOW_SECONDS)) {
                log.warn { "Signup rate limit exceeded for IP: $clientIp (limit=$maxAttempts/min)" }
                exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                return exchange.response.setComplete()
            }
        }

        return chain.filter(exchange)
    }
}
