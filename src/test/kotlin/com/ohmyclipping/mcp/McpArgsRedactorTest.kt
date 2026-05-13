package com.ohmyclipping.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class McpArgsRedactorTest {

    private val objectMapper = jacksonObjectMapper()
    private val sut = McpArgsRedactor(objectMapper)

    /** JSON-RPC 형식의 요청 본문 바이트 배열을 생성한다. */
    private fun jsonRpcBody(arguments: Map<String, Any>): ByteArray {
        val body = mapOf(
            "jsonrpc" to "2.0",
            "method" to "tools/call",
            "params" to mapOf(
                "name" to "test_tool",
                "arguments" to arguments
            )
        )
        return objectMapper.writeValueAsBytes(body)
    }

    @Nested
    inner class `인자 레드액션` {

        @Test
        fun `SlackUserId를 포함하는 키는 SHA-256 해시로 치환된다`() {
            val bodyBytes = jsonRpcBody(mapOf("slackUserId" to "U12345678"))

            val result = sut.redact("test_tool", bodyBytes)
            val resultNode = objectMapper.readTree(result)

            val redactedValue = resultNode.get("slackUserId").asText()
            // SHA-256 해시 접두어 12자 — 원본 값이 아님을 검증
            redactedValue shouldNotContain "U12345678"
            redactedValue.length shouldBe 12
        }

        @Test
        fun `query 값이 200자 초과이면 잘라내고 truncated를 표시한다`() {
            val longQuery = "a".repeat(250)
            val bodyBytes = jsonRpcBody(mapOf("query" to longQuery))

            val result = sut.redact("test_tool", bodyBytes)
            val resultNode = objectMapper.readTree(result)

            val queryValue = resultNode.get("query").asText()
            queryValue.length shouldBe 200 + "...(truncated)".length
            queryValue shouldContain "...(truncated)"
            queryValue shouldStartWith "a".repeat(200)
        }

        @Test
        fun `password 키는 REDACTED로 마스킹된다`() {
            val bodyBytes = jsonRpcBody(mapOf("password" to "super-secret-123"))

            val result = sut.redact("test_tool", bodyBytes)
            val resultNode = objectMapper.readTree(result)

            resultNode.get("password").asText() shouldBe "[REDACTED]"
        }

        @Test
        fun `확장된 민감 키들은 모두 REDACTED로 마스킹된다`() {
            val sensitiveKeys = listOf(
                "password",
                "passwd",
                "mySecret",
                "accessToken",
                "apiKey",
                "api_key",
                "Authorization",
                "credential",
                "credentials",
                "slackWebhook",
                "webhookUrl",
                "bearerToken",
                "privateKey",
                "private_key",
                "USER_API_KEY",
            )
            sensitiveKeys.forEach { key ->
                val bodyBytes = jsonRpcBody(mapOf(key to "leak-me"))

                val result = sut.redact("test_tool", bodyBytes)
                val resultNode = objectMapper.readTree(result)

                resultNode.get(key).asText() shouldBe "[REDACTED]"
            }
        }

        @Test
        fun `민감하지 않은 키는 원본을 유지한다`() {
            val bodyBytes = jsonRpcBody(mapOf("categoryId" to "cat-1", "userId" to "u-1"))

            val result = sut.redact("test_tool", bodyBytes)
            val resultNode = objectMapper.readTree(result)

            resultNode.get("categoryId").asText() shouldBe "cat-1"
            resultNode.get("userId").asText() shouldBe "u-1"
        }

        @Test
        fun `값 안의 key-value secret 패턴은 REDACTED 마커로 치환된다`() {
            val payload = "please run: export password=p@ssw0rd && echo ok"
            val bodyBytes = jsonRpcBody(mapOf("note" to payload))

            val result = sut.redact("test_tool", bodyBytes)
            val resultNode = objectMapper.readTree(result)

            val masked = resultNode.get("note").asText()
            masked shouldContain "password=***REDACTED***"
            masked shouldNotContain "p@ssw0rd"
        }

        @Test
        fun `query 안의 secret 도 마스킹되고 길이 제한이 적용된다`() {
            // `api_key=xyz123` 스타일의 키-밸류 유출은 그대로 캡처되어야 한다.
            val raw = "fetch https://x?api_key=xyz123supersecret&q=test " + "x".repeat(300)
            val bodyBytes = jsonRpcBody(mapOf("query" to raw))

            val result = sut.redact("test_tool", bodyBytes)
            val resultNode = objectMapper.readTree(result)

            val out = resultNode.get("query").asText()
            out shouldNotContain "xyz123supersecret"
            out shouldContain "api_key=***REDACTED***"
            out shouldContain "...(truncated)"
        }

        @Test
        fun `authorization bearer 형태의 임베디드 토큰은 실제 토큰까지 마스킹된다`() {
            val bodyBytes = jsonRpcBody(
                mapOf("note" to "curl -H 'authorization bearer sk-live-secret-token' https://example.com")
            )

            val result = sut.redact("test_tool", bodyBytes)
            val resultNode = objectMapper.readTree(result)

            val masked = resultNode.get("note").asText()
            masked shouldContain "authorization=***REDACTED***"
            masked shouldNotContain "bearer sk-live-secret-token"
            masked shouldNotContain "sk-live-secret-token"
        }

        @Test
        fun `slackChannelId는 원본을 유지한다`() {
            val bodyBytes = jsonRpcBody(mapOf("slackChannelId" to "C0123456789"))

            val result = sut.redact("test_tool", bodyBytes)
            val resultNode = objectMapper.readTree(result)

            resultNode.get("slackChannelId").asText() shouldBe "C0123456789"
        }

        @Test
        fun `빈 인자는 빈 JSON을 반환한다`() {
            val body = mapOf(
                "jsonrpc" to "2.0",
                "method" to "tools/call",
                "params" to mapOf(
                    "name" to "test_tool"
                    // arguments 키 없음
                )
            )
            val bodyBytes = objectMapper.writeValueAsBytes(body)

            val result = sut.redact("test_tool", bodyBytes)

            result shouldBe "{}"
        }

        @Test
        fun `잘못된 JSON은 에러 마커를 반환한다`() {
            val bodyBytes = "not-valid-json{{{".toByteArray()

            val result = sut.redact("test_tool", bodyBytes)

            result shouldContain "_redactError"
            result shouldContain "invalid-json"
        }
    }
}
