package com.ohmyclipping.admin

import com.ohmyclipping.config.RedisRateLimitService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * [ClientErrorController] HTTP 레벨 검증.
 *
 * 기본 요청은 Bearer 관리자 토큰으로 인증하여 실제 엔드포인트 경로까지 도달하도록 한다
 * — `/api/client-errors` 자체는 익명 허용이 아니므로 세션 또는 Bearer 인증이 필요하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ClientErrorControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @MockitoSpyBean
    lateinit var redisRateLimitService: RedisRateLimitService

    private fun authedClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    @Nested
    inner class `정상 요청 처리` {

        @Test
        fun `필수 메시지만 있으면 202를 반환한다`() {
            authedClient().post()
                .uri("/api/client-errors")
                .bodyValue(mapOf("message" to "boom"))
                .exchange()
                .expectStatus().isAccepted
                .expectBody().isEmpty
        }

        @Test
        fun `전체 필드가 들어와도 202를 반환한다`() {
            authedClient().post()
                .uri("/api/client-errors")
                .bodyValue(
                    mapOf(
                        "message" to "Minified React error #185",
                        "stack" to "Error: boom\n  at App (app.tsx:10)",
                        "componentStack" to "in App\n  in ErrorBoundary",
                        "url" to "/admin/review-queue",
                        "userAgent" to "Mozilla/5.0",
                        "reactErrorCode" to "185",
                        "tags" to mapOf("feature" to "review-batch", "build" to "1234")
                    )
                )
                .exchange()
                .expectStatus().isAccepted
        }
    }

    @Nested
    inner class `입력 검증 실패` {

        @Test
        fun `message가 비어 있으면 400을 반환한다`() {
            authedClient().post()
                .uri("/api/client-errors")
                .bodyValue(mapOf("message" to "   "))
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `message가 2001자 이상이면 400을 반환한다`() {
            val tooLong = "x".repeat(2001)
            authedClient().post()
                .uri("/api/client-errors")
                .bodyValue(mapOf("message" to tooLong))
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `tags가 21개 이상이면 400을 반환한다`() {
            val tooManyTags = (1..21).associate { "k$it" to "v$it" }
            authedClient().post()
                .uri("/api/client-errors")
                .bodyValue(
                    mapOf(
                        "message" to "boom",
                        "tags" to tooManyTags
                    )
                )
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    inner class `rate limit 초과` {

        @Test
        fun `분당 한도를 초과하면 429를 반환한다`() {
            // given: 다음 호출이 IP 분당 한도에 걸렸다고 RedisRateLimitService가 true를 반환
            `when`(
                redisRateLimitService.isRateLimited(anyString(), any(Int::class.java), anyLong())
            ).thenReturn(true)

            // when & then
            authedClient().post()
                .uri("/api/client-errors")
                .bodyValue(mapOf("message" to "boom"))
                .exchange()
                .expectStatus().isEqualTo(429)
        }
    }
}
