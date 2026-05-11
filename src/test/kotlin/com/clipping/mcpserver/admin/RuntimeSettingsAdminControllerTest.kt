package com.clipping.mcpserver.admin

import com.clipping.mcpserver.config.RedisRateLimitService
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RuntimeSettingsAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @MockitoSpyBean
    lateinit var redisRateLimitService: RedisRateLimitService

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    @Nested
    inner class `설정 조회` {
        @Test
        fun `현재 설정을 조회한다`() {
            adminClient().get()
                .uri("/api/admin/runtime-settings")
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `retentionRssItemsDays, retentionBatchSummariesDays 반환`() {
            adminClient().get()
                .uri("/api/admin/runtime-settings")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.retentionRssItemsDays").isNumber
                .jsonPath("$.retentionBatchSummariesDays").isNumber
        }
    }

    @Nested
    inner class `retention 설정 수정` {
        @Test
        fun `retention 필드 업데이트가 성공한다`() {
            adminClient().put()
                .uri("/api/admin/runtime-settings")
                .bodyValue(mapOf("retentionRssItemsDays" to 60, "retentionBatchSummariesDays" to 180))
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.retentionRssItemsDays").isEqualTo(60)
                .jsonPath("$.retentionBatchSummariesDays").isEqualTo(180)
        }

        @Test
        fun `retentionRssItemsDays 6 (min 미만) 은 400`() {
            adminClient().put()
                .uri("/api/admin/runtime-settings")
                .bodyValue(mapOf("retentionRssItemsDays" to 6))
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `retentionBatchSummariesDays 731 (max 초과) 은 400`() {
            adminClient().put()
                .uri("/api/admin/runtime-settings")
                .bodyValue(mapOf("retentionBatchSummariesDays" to 731))
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `null 필드는 변경 없음 — 기존 값이 유지된다`() {
            // 먼저 특정 값으로 설정
            adminClient().put()
                .uri("/api/admin/runtime-settings")
                .bodyValue(mapOf("retentionRssItemsDays" to 45))
                .exchange()
                .expectStatus().isOk

            // retentionRssItemsDays를 null로 보내면 기존 값이 유지된다
            adminClient().put()
                .uri("/api/admin/runtime-settings")
                .bodyValue(mapOf("retentionBatchSummariesDays" to 200))
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.retentionRssItemsDays").isEqualTo(45)
        }
    }

    @Nested
    inner class `감사 로그` {
        @Test
        fun `기본 limit으로 감사 로그를 조회한다`() {
            adminClient().get()
                .uri("/api/admin/runtime-settings/audits")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$").isArray
        }

        @Test
        fun `limit 범위를 벗어나면 400을 반환한다`() {
            adminClient().get()
                .uri("/api/admin/runtime-settings/audits?limit=0")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `limit이 음수이면 400을 반환한다`() {
            adminClient().get()
                .uri("/api/admin/runtime-settings/audits?limit=-1")
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    inner class `Slack 검증 rate limit` {

        @Test
        fun `slack verify가 분당 한도를 초과하면 429를 반환한다`() {
            // given: 다음 호출이 rate limit에 걸렸다고 RedisRateLimitService가 true를 반환
            `when`(
                redisRateLimitService.isRateLimited(anyString(), any(Int::class.java), anyLong())
            ).thenReturn(true)

            // when & then
            adminClient().post()
                .uri("/api/admin/runtime-settings/slack/verify")
                .bodyValue(mapOf<String, String?>())
                .exchange()
                .expectStatus().isEqualTo(429)
        }
    }
}
