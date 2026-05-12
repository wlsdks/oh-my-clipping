package com.ohmyclipping.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [mcpToolCall] 에러 직렬화 단위 테스트.
 *
 * RateLimit 예외가 payload 에 retryAfterSeconds / retryAt 를 둘 다 싣는지 검증한다.
 * 이 두 필드가 없으면 LLM 이 즉시 재호출 loop 에 빠질 수 있다.
 */
class McpToolResponseTest {

    private val mapper = jacksonObjectMapper()

    @Nested
    inner class `RateLimitExceededException 직렬화` {

        @Test
        fun `retryAt 가 없으면 retryAfterSeconds 만 포함한다`() {
            val result = mcpToolCall {
                throw RateLimitExceededException(
                    message = "too many",
                    retryAfterSeconds = 60,
                    retryAt = null,
                )
            }

            val parsed: Map<String, Any> = mapper.readValue(result)
            val error = parsed["error"] as Map<*, *>
            error["retryAfterSeconds"] shouldBe 60
            (error.containsKey("retryAt")) shouldBe false
        }

        @Test
        fun `retryAt 가 지정되면 ISO-8601 문자열로 같이 노출된다`() {
            val at = Instant.parse("2026-04-21T10:00:00Z")
            val result = mcpToolCall {
                throw RateLimitExceededException(
                    message = "too many",
                    retryAfterSeconds = 3600,
                    retryAt = at,
                )
            }

            val parsed: Map<String, Any> = mapper.readValue(result)
            val error = parsed["error"] as Map<*, *>
            error["retryAfterSeconds"] shouldBe 3600
            (error["retryAt"] as String) shouldContain "2026-04-21T10:00:00"
        }

        @Test
        fun `다른 예외에는 retryAt 가 붙지 않는다`() {
            val result = mcpToolCall {
                throw IllegalStateException("boom")
            }

            val parsed: Map<String, Any> = mapper.readValue(result)
            val error = parsed["error"] as Map<*, *>
            (error.containsKey("retryAfterSeconds")) shouldBe false
            (error.containsKey("retryAt")) shouldBe false
        }
    }

    @Nested
    inner class `내부 오류 메시지 보호` {

        @Test
        fun `알 수 없는 내부 예외 메시지는 MCP 응답에 그대로 노출하지 않는다`() {
            val result = mcpToolCall {
                throw IllegalStateException("jdbc://internal-db.example/token=secret")
            }

            val parsed: Map<String, Any> = mapper.readValue(result)
            val error = parsed["error"] as Map<*, *>
            error["code"] shouldBe McpErrorCode.INTERNAL_ERROR.code
            error["message"] shouldBe "Internal error"
        }
    }
}
