package com.clipping.mcpserver.admin

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * EditingPresenceController HTTP 경계 테스트.
 *
 * 인증/권한(관리자 Bearer 필요) + 요청 바디 검증(허용되지 않는 resourceType 거부)을 중점적으로 확인한다.
 * Redis 는 TestRedisConfig 가 mock 으로 대체되어 있어 listActive 는 항상 빈 리스트를 반환한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class EditingPresenceControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Test
    fun `관리자 heartbeat 는 204 를 반환한다`() {
        adminClient().post().uri("/api/admin/editing-sessions/heartbeat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"resourceType":"persona","resourceId":"p-1"}""")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `Bearer 없는 요청은 401`() {
        webClient.post().uri("/api/admin/editing-sessions/heartbeat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"resourceType":"persona","resourceId":"p-1"}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `허용되지 않은 resourceType 은 400 거부된다`() {
        adminClient().post().uri("/api/admin/editing-sessions/heartbeat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"resourceType":"bogus","resourceId":"p-1"}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `release 요청은 204 를 반환한다`() {
        adminClient().method(org.springframework.http.HttpMethod.DELETE)
            .uri("/api/admin/editing-sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"resourceType":"persona","resourceId":"p-1"}""")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `list 는 빈 배열을 반환한다 — Redis 가 mock 이므로 세션 없음`() {
        adminClient().get()
            .uri { b -> b.path("/api/admin/editing-sessions").queryParam("resourceType", "persona").queryParam("resourceId", "p-1").build() }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
            .jsonPath("$.length()").isEqualTo(0)
    }

    @Test
    fun `list 는 허용되지 않은 resourceType 을 거부한다`() {
        adminClient().get()
            .uri { b -> b.path("/api/admin/editing-sessions").queryParam("resourceType", "bogus").queryParam("resourceId", "p-1").build() }
            .exchange()
            .expectStatus().isBadRequest
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
