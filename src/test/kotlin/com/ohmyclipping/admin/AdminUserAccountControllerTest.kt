package com.ohmyclipping.admin

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
class AdminUserAccountControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    @Nested
    inner class `계정 목록 조회` {
        @Test
        fun `전체 목록을 조회한다`() {
            adminClient().get()
                .uri("/api/admin/user-accounts")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$").isArray
        }

        @Test
        fun `PENDING 상태로 필터링한다`() {
            adminClient().get()
                .uri("/api/admin/user-accounts?status=PENDING")
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `잘못된 상태값이면 400을 반환한다`() {
            adminClient().get()
                .uri("/api/admin/user-accounts?status=INVALID")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `personaId 쿼리파라미터를 허용한다`() {
            // 존재하지 않는 페르소나면 빈 배열, 존재하는 페르소나면 해당 구독자 목록을 반환한다.
            adminClient().get()
                .uri("/api/admin/user-accounts?personaId=non-existent-persona")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$").isArray
        }
    }
}
