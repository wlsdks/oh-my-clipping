package com.ohmyclipping.mcp

import com.ohmyclipping.error.DependencyFailureException
import com.ohmyclipping.error.RateLimitExceededException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ohmyclipping.service.digest.EngineInvalidInputException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
            error["type"] shouldBe "RATE_LIMITED"
            error["retryable"] shouldBe true
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
            error["type"] shouldBe "RATE_LIMITED"
            error["retryable"] shouldBe true
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
    inner class `DependencyFailureException 직렬화` {

        @Test
        fun `retryAfterSeconds 힌트가 있으면 MCP error payload 에 포함한다`() {
            val result = mcpToolCall {
                throw DependencyFailureException(
                    message = "upstream temporarily unavailable",
                    retryAfterSeconds = 120,
                )
            }

            val parsed: Map<String, Any> = mapper.readValue(result)
            val error = parsed["error"] as Map<*, *>
            error["code"] shouldBe McpErrorCode.DEPENDENCY_FAILURE.code
            error["type"] shouldBe "DEPENDENCY_FAILURE"
            error["retryable"] shouldBe true
            error["retryAfterSeconds"] shouldBe 120
            (error.containsKey("retryAt")) shouldBe false
        }

        @Test
        fun `retryAfterSeconds 힌트가 없으면 dependency error 에 재시도 시간을 붙이지 않는다`() {
            val result = mcpToolCall {
                throw DependencyFailureException("upstream failed")
            }

            val parsed: Map<String, Any> = mapper.readValue(result)
            val error = parsed["error"] as Map<*, *>
            error["type"] shouldBe "DEPENDENCY_FAILURE"
            error["retryable"] shouldBe true
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
            error["type"] shouldBe "INTERNAL_ERROR"
            error["retryable"] shouldBe false
            error["message"] shouldBe "Internal error"
        }
    }

    @Nested
    inner class `복구 힌트` {

        @Test
        fun `에러 payload 는 stable type 과 retryable 힌트를 포함한다`() {
            val result = mcpToolCall {
                throw IllegalArgumentException("limit must be positive")
            }

            val parsed: Map<String, Any> = mapper.readValue(result)
            val error = parsed["error"] as Map<*, *>
            error["code"] shouldBe McpErrorCode.INVALID_PARAMS.code
            error["type"] shouldBe "INVALID_PARAMS"
            error["retryable"] shouldBe false
            error["message"] shouldBe "limit must be positive"
        }

        @Test
        fun `엔진 입력 오류는 validation error 로 노출한다`() {
            val result = mcpToolCall {
                throw EngineInvalidInputException("maxItems must be greater than 0")
            }

            val parsed: Map<String, Any> = mapper.readValue(result)
            val error = parsed["error"] as Map<*, *>
            error["code"] shouldBe McpErrorCode.VALIDATION_ERROR.code
            error["type"] shouldBe "VALIDATION_ERROR"
            error["retryable"] shouldBe false
            error["message"] shouldBe "maxItems must be greater than 0"
        }

        @Test
        fun `사용자에게 노출되는 에러 메시지 안의 secret 은 마스킹한다`() {
            val result = mcpToolCall {
                throw IllegalArgumentException("invalid callback token=sk-live-secret")
            }

            val parsed: Map<String, Any> = mapper.readValue(result)
            val error = parsed["error"] as Map<*, *>
            error["type"] shouldBe "INVALID_PARAMS"
            val message = error["message"] as String
            message shouldContain "token=***REDACTED***"
            message shouldNotContain "sk-live-secret"
        }
    }
}
