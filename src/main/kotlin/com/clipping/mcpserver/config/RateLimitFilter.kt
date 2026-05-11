package com.clipping.mcpserver.config

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
 * /api/user/, /api/admin/, 그리고 특정 /api/public/ 엔드포인트에 대한 주체별 요청 빈도 제한 필터.
 *
 * - 사용자 API: /api/user/ 하위 전체에 대해 사용자별 분당 [MAX_USER_REQUESTS_PER_MINUTE]회 제한.
 * - 관리자 API 쓰기: /api/admin/ 하위의 POST/PUT/PATCH/DELETE에 대해 관리자별
 *   분당 [MAX_ADMIN_WRITE_REQUESTS_PER_MINUTE]회 제한.
 * - 관리자 API 읽기: /api/admin/ 하위의 GET/HEAD/OPTIONS에 대해 관리자별
 *   분당 [MAX_ADMIN_READ_REQUESTS_PER_MINUTE]회 제한. 쓰기와 독립된 버킷을 사용해
 *   콘솔 폴링을 허용하면서도 토큰 탈취 시 분석/LLM 폭주를 막는다.
 * - 익명 공개 경로 ([PUBLIC_IP_LIMITED_PREFIXES]): 익명 유저가 접근 가능한 일부 엔드포인트
 *   (예: signup cascade 용 `/api/public/departments/tree`) 는 IP 별 분당
 *   [MAX_PUBLIC_IP_REQUESTS_PER_MINUTE]회 제한. 조직도 enumeration 방어 + 캐시 hammering
 *   방어 in depth. ngrok/공개 배포 환경 모두 커버.
 * - 화이트리스트 경로(`/actuator/` 하위, `/sse`, `/mcp/message`)는 rate limit 대상에서 제외한다.
 *   Prometheus scrape, SSE long-lived connection, MCP 전용 레이트리밋(McpRateLimiter)과
 *   중복 적용을 피하기 위함이다.
 *
 * Redis 슬라이딩 윈도우 방식이며, Redis 장애 시 [RedisRateLimitService]의 인메모리 폴백으로
 * 동작한다. 원칙적으로 fail-open이지만 폴백 경로도 슬라이딩 윈도우 제한을 유지한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RateLimitFilter(
    private val redisRateLimitService: RedisRateLimitService,
    private val properties: ClippingMcpServerProperties
) : WebFilter {

    companion object {
        const val WINDOW_SECONDS = 60L
        private const val USER_KEY_PREFIX = "rl:user:"
        private const val ADMIN_WRITE_KEY_PREFIX = "rl:admin:write:"
        private const val ADMIN_READ_KEY_PREFIX = "rl:admin:read:"
        private const val PUBLIC_IP_KEY_PREFIX = "rl:public:ip:"
        private const val ANONYMOUS_ADMIN_ACTOR = "anonymous-admin"
        private const val UNKNOWN_IP_ACTOR = "unknown-ip"

        private val WRITE_METHODS: Set<HttpMethod> = setOf(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE
        )

        /**
         * rate limit을 적용하지 않을 경로 목록. Prometheus scrape, SSE long-lived 연결,
         * MCP 자체 레이트리밋(McpRateLimiter)과의 중복을 피하기 위해 제외한다.
         */
        private val WHITELISTED_PATH_PREFIXES: List<String> = listOf(
            "/actuator/",
            "/sse",
            "/mcp/message"
        )

        /**
         * 익명 공개 엔드포인트 중 IP 기반 rate limit 을 적용할 경로 prefix 목록.
         * signup cascade 용 부서/팀 트리(/api/public/departments/tree) 가 대표적이며,
         * 조직도 enumeration + 캐시 hammering 방어 목적.
         * 나머지 /api/public 하위 경로는 기본 fail-open (현 정책 유지).
         */
        internal val PUBLIC_IP_LIMITED_PREFIXES: List<String> = listOf(
            "/api/public/departments"
        )
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        // 화이트리스트 경로는 관리자/사용자 네임스페이스보다 먼저 판별해 어떤 제한도 적용하지 않는다.
        if (isWhitelisted(path)) return chain.filter(exchange)
        return when {
            path.startsWith("/api/user/") -> applyUserLimit(exchange, chain)
            path.startsWith("/api/admin/") -> applyAdminLimit(exchange, chain)
            isPublicIpLimited(path) -> applyPublicIpLimit(exchange, chain)
            else -> chain.filter(exchange)
        }
    }

    /**
     * 사용자 API에 대한 사용자별 rate limit을 적용한다. 인증 Principal이 없으면 체인만 실행한다.
     */
    private fun applyUserLimit(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return exchange.getPrincipal<java.security.Principal>()
            .flatMap { principal ->
                val username = principal.name
                if (isLimited("$USER_KEY_PREFIX$username", properties.rateLimit.maxUserRequestsPerMinute)) {
                    log.warn { "Rate limit exceeded for user: $username" }
                    exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                    exchange.response.setComplete()
                } else {
                    chain.filter(exchange)
                }
            }
            .switchIfEmpty(chain.filter(exchange))
    }

    /**
     * 관리자 API 경로에 HTTP 메서드별로 분기해 rate limit을 적용한다.
     * 쓰기/읽기 버킷을 분리해 콘솔 폴링(읽기)이 쓰기 예산을 소진하지 않도록 한다.
     */
    private fun applyAdminLimit(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // 쓰기/읽기 버킷을 구분한다.
        val method = exchange.request.method
        return if (method in WRITE_METHODS) {
            applyAdminBucketLimit(
                exchange = exchange,
                chain = chain,
                keyPrefix = ADMIN_WRITE_KEY_PREFIX,
                maxPerMinute = properties.rateLimit.maxAdminWriteRequestsPerMinute,
                bucketLabel = "write"
            )
        } else {
            applyAdminBucketLimit(
                exchange = exchange,
                chain = chain,
                keyPrefix = ADMIN_READ_KEY_PREFIX,
                maxPerMinute = properties.rateLimit.maxAdminReadRequestsPerMinute,
                bucketLabel = "read"
            )
        }
    }

    /**
     * 관리자 쓰기/읽기 공용 버킷 적용 로직. 키 prefix와 한도만 달라진다.
     * Principal이 없으면 [ANONYMOUS_ADMIN_ACTOR]로 집계한다.
     */
    private fun applyAdminBucketLimit(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
        keyPrefix: String,
        maxPerMinute: Int,
        bucketLabel: String
    ): Mono<Void> {
        return exchange.getPrincipal<java.security.Principal>()
            .map { it.name ?: ANONYMOUS_ADMIN_ACTOR }
            .defaultIfEmpty(ANONYMOUS_ADMIN_ACTOR)
            .flatMap { actor ->
                if (isLimited("$keyPrefix$actor", maxPerMinute)) {
                    val requestPath = exchange.request.path.value()
                    val method = exchange.request.method
                    log.warn {
                        "Admin $bucketLabel rate limit exceeded: actor=$actor method=$method path=$requestPath"
                    }
                    exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                    exchange.response.setComplete()
                } else {
                    chain.filter(exchange)
                }
            }
    }

    /**
     * 익명 공개 경로에 IP 기반 rate limit 을 적용한다. Principal 없이 동작하므로
     * X-Forwarded-For > X-Real-IP > remote address 순으로 클라이언트 IP 를 추출한다.
     * IP 추출 실패 시 [UNKNOWN_IP_ACTOR] 로 fallback — 하나의 버킷에 몰려 전체 차단될 수
     * 있으나, 공개 경로 남용보다는 보수적으로 동작하는 편이 낫다.
     */
    private fun applyPublicIpLimit(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val ip = resolveClientIp(exchange)
        return if (isLimited("$PUBLIC_IP_KEY_PREFIX$ip", properties.rateLimit.maxPublicIpRequestsPerMinute)) {
            val requestPath = exchange.request.path.value()
            log.warn { "Public IP rate limit exceeded: ip=$ip path=$requestPath" }
            exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
            exchange.response.setComplete()
        } else {
            chain.filter(exchange)
        }
    }

    /**
     * 프록시/LB 체인을 고려해 클라이언트 IP 를 결정한다.
     * X-Forwarded-For 의 가장 왼쪽(원본 클라이언트) → X-Real-IP → remote address 순.
     */
    private fun resolveClientIp(exchange: ServerWebExchange): String {
        val headers = exchange.request.headers
        headers.getFirst("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        headers.getFirst("X-Real-IP")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return exchange.request.remoteAddress?.address?.hostAddress ?: UNKNOWN_IP_ACTOR
    }

    private fun isWhitelisted(path: String): Boolean =
        WHITELISTED_PATH_PREFIXES.any { path == it.trimEnd('/') || path.startsWith(it) }

    private fun isPublicIpLimited(path: String): Boolean =
        PUBLIC_IP_LIMITED_PREFIXES.any { path == it || path.startsWith("$it/") }

    private fun isLimited(key: String, maxRequests: Int): Boolean =
        redisRateLimitService.isRateLimited(key, maxRequests, WINDOW_SECONDS)
}
