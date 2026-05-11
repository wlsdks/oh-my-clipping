package com.clipping.mcpserver.observability

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CorrelationIdWebFilter 단위 테스트.
 *
 * 요청당 상관 ID 규칙을 검증한다:
 * 1) 클라이언트가 `X-Request-Id` 를 보내면 그대로 재사용한다.
 * 2) 헤더가 없거나 blank 면 UUID 를 생성한다.
 * 3) 응답 헤더에도 같은 ID 가 실려 클라이언트가 추적할 수 있다.
 */
class CorrelationIdWebFilterTest {

    private val filter = CorrelationIdWebFilter()

    private fun executeFilter(
        incoming: String? = null
    ): Pair<MockServerWebExchange, Boolean> {
        val builder = MockServerHttpRequest.get("/api/ping")
        if (incoming != null) builder.header(CorrelationIdWebFilter.HEADER_NAME, incoming)
        val exchange = MockServerWebExchange.from(builder.build())
        val chainCalled = AtomicBoolean(false)
        val chain = WebFilterChain { _ ->
            chainCalled.set(true)
            Mono.empty()
        }
        filter.filter(exchange, chain).block()
        return exchange to chainCalled.get()
    }

    @Nested
    inner class `상관 ID 생성과 재사용` {

        @Test
        fun `클라이언트 헤더가 비어있으면 UUID를 생성하고 응답 헤더에 실어준다`() {
            val (exchange, chainCalled) = executeFilter(incoming = null)

            chainCalled shouldBe true
            val responseId = exchange.response.headers.getFirst(CorrelationIdWebFilter.HEADER_NAME).shouldNotBeNull()
            // UUID v4 형태 (8-4-4-4-12)
            responseId shouldMatch Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        }

        @Test
        fun `클라이언트가 전달한 X-Request-Id 는 그대로 재사용된다`() {
            val clientId = "req-12345-from-client"

            val (exchange, chainCalled) = executeFilter(incoming = clientId)

            chainCalled shouldBe true
            exchange.response.headers.getFirst(CorrelationIdWebFilter.HEADER_NAME) shouldBe clientId
        }

        @Test
        fun `클라이언트가 blank 문자열을 보내면 새 UUID를 생성한다 (blank 폴백)`() {
            val (exchange, _) = executeFilter(incoming = "   ")

            val responseId = exchange.response.headers.getFirst(CorrelationIdWebFilter.HEADER_NAME).shouldNotBeNull()
            responseId shouldMatch Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        }

        @Test
        fun `연속된 두 요청은 서로 다른 상관 ID를 받는다 (충돌 방지)`() {
            val (e1, _) = executeFilter(incoming = null)
            val (e2, _) = executeFilter(incoming = null)

            val id1 = e1.response.headers.getFirst(CorrelationIdWebFilter.HEADER_NAME)
            val id2 = e2.response.headers.getFirst(CorrelationIdWebFilter.HEADER_NAME)
            id1.shouldNotBeNull()
            id2.shouldNotBeNull()
            (id1 != id2) shouldBe true
        }
    }
}
