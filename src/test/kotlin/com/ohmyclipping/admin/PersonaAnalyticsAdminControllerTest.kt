package com.ohmyclipping.admin

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
class PersonaAnalyticsAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    @Test
    fun `admin 토큰으로 GET live 200 응답 + 응답 필드 모양`() {
        adminClient().get()
            .uri("/api/admin/analytics/personas/live")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totals.totalStyles").exists()
            .jsonPath("$.totals.activeSubscriptions").exists()
            .jsonPath("$.totals.presetUsageRate").exists()
            .jsonPath("$.totals.customStyleRatio").exists()
            .jsonPath("$.presetPortfolio").isArray
            .jsonPath("$.customSummary.totalCustomPersonas").exists()
            .jsonPath("$.asOf").exists()
    }

    @Test
    fun `인증 헤더 없이 호출하면 401`() {
        webClient.get()
            .uri("/api/admin/analytics/personas/live")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `응답에는 제거된 과거 필드가 포함되지 않는다`() {
        adminClient().get()
            .uri("/api/admin/analytics/personas/live")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totals.weeklySubscriptionDelta").doesNotExist()
            .jsonPath("$.totals.customConversionRate").doesNotExist()
            .jsonPath("$.totals.recentConversions").doesNotExist()
            .jsonPath("$.totals.mostChurnedPreset").doesNotExist()
    }
}
