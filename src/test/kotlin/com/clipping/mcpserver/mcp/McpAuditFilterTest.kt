package com.clipping.mcpserver.mcp

import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SchedulerRunTracker
import com.clipping.mcpserver.store.McpAuditEntry
import com.clipping.mcpserver.store.McpAuditLogStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean

class McpAuditFilterTest {

    private val objectMapper = jacksonObjectMapper()
    private val mcpArgsRedactor = McpArgsRedactor(objectMapper)
    private val mcpAuditLogStore = mockk<McpAuditLogStore>(relaxed = true)
    private val metrics = ClippingMetrics(null, SimpleMeterRegistry(), SchedulerRunTracker())
    private val sut = McpAuditFilter(objectMapper, mcpArgsRedactor, mcpAuditLogStore, metrics)

    /** JSON-RPC 형식의 도구 호출 요청 본문 문자열을 생성한다. */
    private fun toolCallBodyString(
        toolName: String = "test_tool",
        arguments: Map<String, Any> = emptyMap()
    ): String {
        val body = mapOf(
            "jsonrpc" to "2.0",
            "method" to "tools/call",
            "params" to mapOf(
                "name" to toolName,
                "arguments" to arguments
            )
        )
        return objectMapper.writeValueAsString(body)
    }

    @Nested
    inner class `감사 로그 기록` {

        @Test
        fun `정상 도구 호출은 감사 로그에 기록된다`() {
            val bodyString = toolCallBodyString(
                "list_categories", mapOf("query" to "테스트")
            )
            val request = MockServerHttpRequest
                .post("/mcp/message")
                .body(bodyString)
            val exchange = MockServerWebExchange.from(request)
            val chainCalled = AtomicBoolean(false)

            val chain = WebFilterChain { _ ->
                chainCalled.set(true)
                Mono.empty()
            }

            sut.filter(exchange, chain).block()

            chainCalled.get() shouldBe true

            // doFinally는 boundedElastic 스케줄러에서 비동기 실행 — timeout으로 대기
            val entrySlot = slot<McpAuditEntry>()
            verify(timeout = 3000) { mcpAuditLogStore.insert(capture(entrySlot)) }

            val entry = entrySlot.captured
            entry.toolName shouldBe "list_categories"
            entry.resultStatus shouldBe "OK"
            // tokenKid 가 없으면 actor 는 anonymous 로 폴백한다.
            entry.actor shouldBe "anonymous"
            entry.argsJson shouldContain "테스트"
        }

        @Test
        fun `인증 필터가 tokenKid 를 남긴 경우 actor 로 전달된다`() {
            val bodyString = toolCallBodyString("list_categories")
            val request = MockServerHttpRequest
                .post("/mcp/message")
                .body(bodyString)
            val exchange = MockServerWebExchange.from(request)
            // 상위 인증 필터가 성공했다면 exchange attribute 에 토큰 지문이 있다.
            exchange.attributes[McpBearerAuthFilter.ATTR_TOKEN_KID] = "deadbeef"
            val chain = WebFilterChain { Mono.empty() }

            sut.filter(exchange, chain).block()

            val slot = slot<McpAuditEntry>()
            verify(timeout = 3000) { mcpAuditLogStore.insert(capture(slot)) }
            slot.captured.actor shouldBe "deadbeef"
        }

        @Test
        fun `Content-Length 초과 요청은 413을 반환한다`() {
            // 10KB = 10_240 바이트를 초과하는 Content-Length 헤더를 설정한다
            val request = MockServerHttpRequest
                .post("/mcp/message")
                .header("Content-Length", "20000")
                .body("")
            val exchange = MockServerWebExchange.from(request)
            val chainCalled = AtomicBoolean(false)

            val chain = WebFilterChain { _ ->
                chainCalled.set(true)
                Mono.empty()
            }

            sut.filter(exchange, chain).block()

            exchange.response.statusCode shouldBe HttpStatus.PAYLOAD_TOO_LARGE
            chainCalled.get() shouldBe false
        }

        @Test
        fun `JSON-RPC 파싱 실패 시 toolName은 unknown으로 기록된다`() {
            val request = MockServerHttpRequest
                .post("/mcp/message")
                .body("not-json{{{")
            val exchange = MockServerWebExchange.from(request)

            val chain = WebFilterChain { Mono.empty() }

            sut.filter(exchange, chain).block()

            val entrySlot = slot<McpAuditEntry>()
            verify(timeout = 3000) { mcpAuditLogStore.insert(capture(entrySlot)) }

            entrySlot.captured.toolName shouldBe "unknown"
        }

        @Test
        fun `SSE 경로는 감사 필터를 건너뛴다`() {
            val request = MockServerHttpRequest.get("/sse").build()
            val exchange = MockServerWebExchange.from(request)
            val chainCalled = AtomicBoolean(false)

            val chain = WebFilterChain { _ ->
                chainCalled.set(true)
                Mono.empty()
            }

            sut.filter(exchange, chain).block()

            chainCalled.get() shouldBe true

            // 감사 로그가 기록되지 않음을 검증한다
            verify(exactly = 0) { mcpAuditLogStore.insert(any()) }
        }
    }
}
