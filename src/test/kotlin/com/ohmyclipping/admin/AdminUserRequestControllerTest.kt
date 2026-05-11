package com.ohmyclipping.admin

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * AdminUserRequestController 통합 테스트.
 *
 * 주요 검증 시나리오:
 * - 전체/상태별 신청 목록 조회
 * - 잘못된 상태값 400 에러
 * - 통계 조회(pendingCount, totalCount)
 * - 빈 ID 목록 일괄 승인/반려 400 에러
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class AdminUserRequestControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    @Nested
    inner class `신청 목록 조회` {

        @Test
        fun `전체 목록을 조회한다`() {
            adminClient().get()
                .uri("/api/admin/user-requests")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$").isArray
        }

        @Test
        fun `PENDING 상태로 필터링한다`() {
            adminClient().get()
                .uri("/api/admin/user-requests?status=PENDING")
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `잘못된 상태값이면 400을 반환한다`() {
            adminClient().get()
                .uri("/api/admin/user-requests?status=INVALID")
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    inner class `신청 통계` {

        @Test
        fun `통계를 조회한다`() {
            adminClient().get()
                .uri("/api/admin/user-requests/stats")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.pendingCount").isNumber
                .jsonPath("$.totalCount").isNumber
        }
    }

    @Nested
    inner class `일괄 처리` {

        @Test
        fun `빈 ID 목록으로 일괄 승인하면 400을 반환한다`() {
            adminClient().post()
                .uri("/api/admin/user-requests/bulk-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """{"ids":[],"legalBasis":"QUOTATION_ONLY","summaryAllowed":true,"fulltextAllowed":false,"responsibilityAcknowledged":true}"""
                )
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `빈 ID 목록으로 일괄 반려하면 400을 반환한다`() {
            adminClient().post()
                .uri("/api/admin/user-requests/bulk-reject")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("ids" to emptyList<String>()))
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    inner class `법적 검토 필수 검증` {

        @Test
        fun `responsibilityAcknowledged false면 400`() {
            adminClient().post()
                .uri("/api/admin/user-requests/nonexistent-id/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """{"legalBasis":"QUOTATION_ONLY","summaryAllowed":true,"fulltextAllowed":false,"responsibilityAcknowledged":false}"""
                )
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `잘못된 legalBasis는 400`() {
            adminClient().post()
                .uri("/api/admin/user-requests/nonexistent-id/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """{"legalBasis":"INVALID","summaryAllowed":true,"fulltextAllowed":false,"responsibilityAcknowledged":true}"""
                )
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `legalBasis 누락은 400`() {
            adminClient().post()
                .uri("/api/admin/user-requests/nonexistent-id/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """{"summaryAllowed":true,"fulltextAllowed":false,"responsibilityAcknowledged":true}"""
                )
                .exchange()
                .expectStatus().isBadRequest
        }
    }
}
