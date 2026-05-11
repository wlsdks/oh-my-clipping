package com.ohmyclipping.mcp

import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.observability.SchedulerRunTracker
import com.ohmyclipping.store.McpAuditEntry
import com.ohmyclipping.store.McpAuditLogStore
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean

class McpBearerAuthFilterTest {

    /** 테스트용 32자 이상 토큰. */
    private val validToken = "a]3Fk9#mP!qW2xR7vLzY8nJ4cB6dT0sH"

    private val metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
    private val mcpAuditLogStore = mockk<McpAuditLogStore>(relaxed = true)

    /** 토큰을 직접 주입하여 필터를 생성한다 (@PostConstruct는 호출하지 않는다). */
    private fun createFilter(token: String): McpBearerAuthFilter {
        // 리플렉션 없이 생성자에 직접 토큰을 전달한다.
        // @PostConstruct는 Spring 컨테이너가 호출하므로 단위 테스트에서는 생략한다.
        return McpBearerAuthFilter(token, metrics, mcpAuditLogStore)
    }

    /** 필터를 실행하고 (exchange, chainCalled) 쌍을 반환한다. */
    private fun executeFilter(
        filter: McpBearerAuthFilter,
        path: String,
        authHeader: String? = null
    ): Pair<MockServerWebExchange, Boolean> {
        val requestBuilder = MockServerHttpRequest.post(path)
        if (authHeader != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, authHeader)
        }
        val exchange = MockServerWebExchange.from(requestBuilder.build())
        val chainCalled = AtomicBoolean(false)

        val chain = WebFilterChain { _ ->
            chainCalled.set(true)
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        return exchange to chainCalled.get()
    }

    @Nested
    inner class `MCP 경로 인증` {

        @Test
        fun `Bearer 토큰 없는 SSE 요청은 401을 반환한다`() {
            val filter = createFilter(validToken)

            val (exchange, chainCalled) = executeFilter(filter, "/sse")

            exchange.response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            chainCalled shouldBe false
        }

        @Test
        fun `빈 Authorization 헤더는 401을 반환한다`() {
            val filter = createFilter(validToken)

            val (exchange, chainCalled) = executeFilter(
                filter, "/mcp/message", authHeader = ""
            )

            exchange.response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            chainCalled shouldBe false
        }

        @Test
        fun `잘못된 토큰은 401을 반환한다`() {
            val filter = createFilter(validToken)

            val (exchange, chainCalled) = executeFilter(
                filter, "/mcp/message", authHeader = "Bearer wrong-token-value-that-is-definitely-invalid"
            )

            exchange.response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            chainCalled shouldBe false
        }

        @Test
        fun `올바른 토큰은 필터를 통과한다`() {
            val filter = createFilter(validToken)

            val (_, chainCalled) = executeFilter(
                filter, "/mcp/message", authHeader = "Bearer $validToken"
            )

            chainCalled shouldBe true
        }

        @Test
        fun `MCP 이외 경로는 인증 없이 통과한다`() {
            val filter = createFilter(validToken)

            val (_, chainCalled) = executeFilter(filter, "/api/admin/sources")

            chainCalled shouldBe true
        }
    }

    @Nested
    inner class `토큰 검증` {

        @Test
        fun `빈 토큰으로 초기화하면 IllegalStateException이 발생한다`() {
            val filter = createFilter("")

            assertThrows<IllegalStateException> {
                filter.validateToken()
            }
        }

        @Test
        fun `32자 미만 토큰으로 초기화하면 IllegalStateException이 발생한다`() {
            val filter = createFilter("short-token")

            assertThrows<IllegalStateException> {
                filter.validateToken()
            }
        }
    }

    @Nested
    inner class `인증 실패 감사 로그` {

        @Test
        fun `Bearer 누락 시 감사 로그에 __auth_failed__ 로 기록된다`() {
            val filter = createFilter(validToken)

            executeFilter(filter, "/mcp/message")

            val slot = slot<McpAuditEntry>()
            verify(timeout = 3000) { mcpAuditLogStore.insert(capture(slot)) }

            slot.captured.actor shouldBe "__auth_failed__"
            slot.captured.toolName shouldBe "__auth_failed__"
            slot.captured.resultStatus shouldBe "ERROR"
            slot.captured.httpStatusCode shouldBe 401
            slot.captured.errorMessage shouldBe "auth_failed:missing_bearer"
        }

        @Test
        fun `잘못된 토큰도 token_mismatch 이유로 감사 로그에 기록된다`() {
            val filter = createFilter(validToken)

            executeFilter(
                filter,
                "/mcp/message",
                authHeader = "Bearer wrong-token-value-that-is-definitely-invalid"
            )

            val slot = slot<McpAuditEntry>()
            verify(timeout = 3000) { mcpAuditLogStore.insert(capture(slot)) }
            slot.captured.errorMessage shouldBe "auth_failed:token_mismatch"
            slot.captured.httpStatusCode shouldBe 401
        }

        @Test
        fun `정상 인증은 감사 로그를 남기지 않는다`() {
            val filter = createFilter(validToken)

            executeFilter(
                filter,
                "/mcp/message",
                authHeader = "Bearer $validToken",
            )

            // 성공 경로는 downstream McpAuditFilter 가 처리하므로 여기선 남기지 않는다.
            verify(exactly = 0) { mcpAuditLogStore.insert(any()) }
        }
    }

    @Nested
    inner class `토큰 지문 exchange 속성` {

        @Test
        fun `올바른 토큰으로 통과하면 exchange 에 tokenKid 가 바인딩된다`() {
            val filter = createFilter(validToken)
            val requestBuilder = MockServerHttpRequest.post("/mcp/message")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $validToken")
            val exchange = MockServerWebExchange.from(requestBuilder.build())
            val chain = WebFilterChain { Mono.empty() }

            filter.filter(exchange, chain).block()

            val kid = exchange.attributes[McpBearerAuthFilter.ATTR_TOKEN_KID] as? String
            // 16진수 8자
            (kid != null && kid.matches(Regex("[0-9a-f]{8}"))) shouldBe true
        }

        @Test
        fun `서로 다른 토큰은 서로 다른 지문으로 매핑된다`() {
            val token1 = "a]3Fk9#mP!qW2xR7vLzY8nJ4cB6dT0sH"
            val token2 = "zX4Vn8#qP!wE2yR7vLzY8nJ4cB6dT0sH"

            val filter1 = createFilter(token1)
            val filter2 = createFilter(token2)

            val exch1 = MockServerWebExchange.from(
                MockServerHttpRequest.post("/mcp/message")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token1").build()
            )
            val exch2 = MockServerWebExchange.from(
                MockServerHttpRequest.post("/mcp/message")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token2").build()
            )
            val chain = WebFilterChain { Mono.empty() }

            filter1.filter(exch1, chain).block()
            filter2.filter(exch2, chain).block()

            val kid1 = exch1.attributes[McpBearerAuthFilter.ATTR_TOKEN_KID] as? String
            val kid2 = exch2.attributes[McpBearerAuthFilter.ATTR_TOKEN_KID] as? String
            (kid1 != null && kid2 != null && kid1 != kid2) shouldBe true
        }
    }
}
