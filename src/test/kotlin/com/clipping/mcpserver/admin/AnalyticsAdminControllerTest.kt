package com.clipping.mcpserver.admin

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * AnalyticsAdminController의 from/to 날짜 범위 파라미터 지원을 검증하는 통합 테스트.
 *
 * 주요 검증 시나리오:
 * - from/to 모두 지정 시 정상 응답
 * - from 또는 to 하나만 지정 시 400 에러
 * - from/to 없이 days 파라미터로 폴백
 * - from이 to보다 이후인 경우 400 에러
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class AnalyticsAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    @Nested
    inner class `DAU 엔드포인트` {

        @Test
        fun `from과 to를 모두 지정하면 정상 응답한다`() {
            adminClient().get()
                .uri("/api/admin/analytics/dau?from=2026-03-03&to=2026-03-09")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data").isArray
        }

        @Test
        fun `from만 지정하면 400 에러를 반환한다`() {
            adminClient().get()
                .uri("/api/admin/analytics/dau?from=2026-03-03")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `to만 지정하면 400 에러를 반환한다`() {
            adminClient().get()
                .uri("/api/admin/analytics/dau?to=2026-03-09")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `from과 to 없이 days로 폴백한다`() {
            adminClient().get()
                .uri("/api/admin/analytics/dau?days=7")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data").isArray
        }

        @Test
        fun `파라미터 없이 호출하면 기본값 days=7로 동작한다`() {
            adminClient().get()
                .uri("/api/admin/analytics/dau")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data").isArray
        }

        @Test
        fun `days가 90일을 초과하면 400 에러를 반환한다`() {
            adminClient().get()
                .uri("/api/admin/analytics/dau?days=91")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `from이 to보다 이후이면 400 에러를 반환한다`() {
            adminClient().get()
                .uri("/api/admin/analytics/dau?from=2026-03-10&to=2026-03-03")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `from과 to 범위가 90일을 초과하면 400 에러를 반환한다`() {
            adminClient().get()
                .uri("/api/admin/analytics/dau?from=2026-01-01&to=2026-04-05")
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    inner class `위자드 퍼널 엔드포인트` {

        @Test
        fun `from과 to를 모두 지정하면 정상 응답한다`() {
            adminClient().get()
                .uri("/api/admin/analytics/wizard-funnel?from=2026-03-01&to=2026-03-10")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.data").isArray
        }

        @Test
        fun `from만 지정하면 400 에러를 반환한다`() {
            adminClient().get()
                .uri("/api/admin/analytics/wizard-funnel?from=2026-03-01")
                .exchange()
                .expectStatus().isBadRequest
        }
    }
}
