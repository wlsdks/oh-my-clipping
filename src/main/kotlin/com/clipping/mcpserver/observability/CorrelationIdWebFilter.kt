package com.clipping.mcpserver.observability

import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 모든 HTTP 요청에 고유 상관 ID를 부여하는 WebFilter.
 * 요청 헤더에 X-Request-Id가 있으면 재사용하고, 없으면 UUID를 생성한다.
 * MDC에 `requestId`로 설정하여 모든 로그에 자동 포함되고,
 * 응답 헤더에도 X-Request-Id를 추가하여 클라이언트가 추적할 수 있다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdWebFilter : WebFilter {

    companion object {
        const val HEADER_NAME = "X-Request-Id"
        const val MDC_KEY = "requestId"
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // 클라이언트가 이미 전달한 상관 ID가 있으면 재사용한다
        val requestId = exchange.request.headers.getFirst(HEADER_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        // 응답 헤더에 상관 ID를 추가해 클라이언트가 에러 추적에 활용할 수 있게 한다
        exchange.response.headers.add(HEADER_NAME, requestId)

        return chain.filter(exchange)
            .contextWrite { ctx -> ctx.put(MDC_KEY, requestId) }
            .doOnEach { signal ->
                if (!signal.isOnComplete && !signal.isOnError) return@doOnEach
                signal.contextView.getOrEmpty<String>(MDC_KEY).ifPresent { MDC.remove(it) }
            }
            .transformDeferred { original ->
                Mono.deferContextual { ctx ->
                    val id = ctx.getOrDefault(MDC_KEY, requestId) ?: requestId
                    MDC.put(MDC_KEY, id)
                    original
                        .doFinally { MDC.remove(MDC_KEY) }
                }
            }
    }
}
