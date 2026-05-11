package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.ErrorResponse
import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.StaleEditInfo
import com.clipping.mcpserver.observability.ErrorSlackNotifier
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

/**
 * GlobalExceptionHandler가 낙관적 잠금 충돌 응답에 staleEditInfo를 포함하는지 검증한다.
 */
class GlobalExceptionHandlerStaleEditTest {

    private val notifier = mockk<ErrorSlackNotifier>(relaxed = true)
    private val handler = GlobalExceptionHandler(notifier)
    private val mapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @Test
    fun `ConflictException with staleEditInfo includes nested info in 409 body`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.method(HttpMethod.PUT, "/api/admin/categories/cat-1")
        )
        val staleEditInfo = StaleEditInfo(
            code = "STALE_EDIT",
            latestUpdatedAt = java.time.Instant.parse("2026-04-10T00:00:00Z"),
            latestEditorName = "관리자B",
            changedFieldNames = listOf("name", "description")
        )
        val ex = ConflictException(
            message = "다른 관리자가 먼저 저장했습니다.",
            staleEditInfo = staleEditInfo
        )

        val response = handler.handleServiceException(ex, exchange)

        response.statusCode.value() shouldBe 409
        val body = response.body!!
        body.code shouldBe "CONFLICT"
        body.staleEditInfo shouldBe staleEditInfo
        // JSON 직렬화에서 staleEditInfo 필드와 중요 값들이 포함돼야 한다.
        val json = mapper.writeValueAsString(body)
        json shouldContain "\"staleEditInfo\""
        json shouldContain "\"STALE_EDIT\""
        json shouldContain "\"관리자B\""
        json shouldContain "\"name\""
    }

    @Test
    fun `ConflictException without staleEditInfo omits field from JSON`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.method(HttpMethod.PUT, "/api/admin/categories/cat-2")
        )
        val ex = ConflictException(message = "일반 충돌")

        val response = handler.handleServiceException(ex, exchange)

        val json = mapper.writeValueAsString(response.body!!)
        // @JsonInclude(NON_NULL) 덕에 staleEditInfo 필드가 완전히 생략돼야 한다.
        json shouldNotContain "\"staleEditInfo\""
    }

    @Test
    fun `unrelated ErrorResponse construction produces a correct nested data class`() {
        val info = StaleEditInfo(
            latestUpdatedAt = java.time.Instant.parse("2026-01-01T00:00:00Z"),
            latestEditorName = "관리자",
            changedFieldNames = emptyList()
        )
        val response = ErrorResponse(
            code = "CONFLICT",
            message = "테스트",
            staleEditInfo = info
        )
        response.staleEditInfo shouldBe info
    }
}
