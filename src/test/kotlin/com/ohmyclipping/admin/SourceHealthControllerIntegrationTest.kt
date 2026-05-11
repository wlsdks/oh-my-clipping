package com.ohmyclipping.admin

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * RSS 소스 헬스 엔드포인트 통합 테스트.
 * 어드민 대시보드의 RSS 소스 헬스 카드에서 사용한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class SourceHealthControllerIntegrationTest {

    @Autowired
    lateinit var webClient: WebTestClient

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    @Test
    fun `GET sources health 응답이 totalCount, healthyCount, unhealthy 필드를 가진다`() {
        adminClient().get()
            .uri("/api/admin/sources/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalCount").exists()
            .jsonPath("$.healthyCount").exists()
            .jsonPath("$.unhealthy").isArray
    }

    @Test
    fun `GET sources health는 인증 없으면 401을 반환한다`() {
        webClient.get()
            .uri("/api/admin/sources/health")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
