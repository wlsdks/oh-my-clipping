package com.clipping.mcpserver.observability

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

private val log = KotlinLogging.logger {}

/**
 * HTTP 액세스 로그를 INFO 레벨로 기록하는 WebFilter.
 * `[METHOD /path] STATUS durationMs` 형식으로 출력한다.
 * 헬스체크 엔드포인트(/actuator/health)는 노이즈 방지를 위해 제외한다.
 * CorrelationIdWebFilter 다음 순서(HIGHEST_PRECEDENCE + 1)로 실행된다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class AccessLogWebFilter : WebFilter {

    companion object {
        /** 액세스 로그에서 제외할 경로 접두사 목록 */
        private val EXCLUDED_PREFIXES = listOf(
            "/actuator/health",
            "/actuator/prometheus"
        )
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()

        // 헬스체크/메트릭 엔드포인트는 로그 노이즈를 줄이기 위해 제외한다
        if (EXCLUDED_PREFIXES.any { path.startsWith(it) }) {
            return chain.filter(exchange)
        }

        val method = exchange.request.method
        val startNanos = System.nanoTime()

        return chain.filter(exchange)
            .doFinally {
                val durationMs = (System.nanoTime() - startNanos) / 1_000_000
                val status = exchange.response.statusCode?.value() ?: 0
                log.info { "[$method $path] $status ${durationMs}ms" }
            }
    }
}
