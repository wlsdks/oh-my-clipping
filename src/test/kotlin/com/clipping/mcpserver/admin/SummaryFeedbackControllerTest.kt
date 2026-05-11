package com.clipping.mcpserver.admin

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class FeedbackAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    @Nested
    inner class `인기 피드백 조회` {
        @Test
        fun `기본값으로 조회한다`() {
            adminClient().get()
                .uri("/api/admin/feedback/hot")
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `limit이 범위를 벗어나면 400을 반환한다`() {
            adminClient().get()
                .uri("/api/admin/feedback/hot?limit=0")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `days가 범위를 벗어나면 400을 반환한다`() {
            adminClient().get()
                .uri("/api/admin/feedback/hot?days=0")
                .exchange()
                .expectStatus().isBadRequest
        }
    }
}
