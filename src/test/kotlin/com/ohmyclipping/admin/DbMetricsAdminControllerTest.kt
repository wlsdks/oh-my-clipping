package com.ohmyclipping.admin

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * DbMetricsAdminController 통합 테스트.
 *
 * 검증 시나리오:
 * - 관리자 인증이 있으면 200 + DbSizeSnapshot 반환
 * - 비인증 요청은 401/403
 * - forceRefresh=true 파라미터 패스스루
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30s")
@ActiveProfiles("test")
class DbMetricsAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    @Nested
    inner class `관리자 인증 있음` {

        @Test
        fun `GET api-admin-ops-db-metrics — 관리자 인증 있으면 200과 DbSizeSnapshot 반환`() {
            adminClient().get()
                .uri("/api/admin/ops/db-metrics")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.limitBytes").isEqualTo(1_073_741_824L)
                .jsonPath("$.thresholdLevel").isNotEmpty
                .jsonPath("$.topTables").isArray
                .jsonPath("$.retentionEligible").isNotEmpty
                .jsonPath("$.dailyGrowth").isNotEmpty
                .jsonPath("$.lastRefreshedAt").isNotEmpty
        }

        @Test
        fun `GET api-admin-ops-db-metrics forceRefresh=true — 서비스에 pass-through`() {
            adminClient().get()
                .uri("/api/admin/ops/db-metrics?forceRefresh=true")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.thresholdLevel").isNotEmpty
        }

        @Test
        fun `GET api-admin-ops-db-metrics forceRefresh=false — 기본 캐시 경로도 200`() {
            adminClient().get()
                .uri("/api/admin/ops/db-metrics?forceRefresh=false")
                .exchange()
                .expectStatus().isOk
        }
    }

    @Nested
    inner class `비인증 요청` {

        @Test
        fun `GET api-admin-ops-db-metrics — 비인증 요청은 401 또는 403`() {
            webClient.get()
                .uri("/api/admin/ops/db-metrics")
                .exchange()
                .expectStatus().value { status ->
                    assert(status == 401 || status == 403) {
                        "Expected 401 or 403 but got $status"
                    }
                }
        }
    }
}
