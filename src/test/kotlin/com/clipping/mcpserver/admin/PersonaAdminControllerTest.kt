package com.clipping.mcpserver.admin

import com.clipping.mcpserver.model.Persona
import com.clipping.mcpserver.store.PersonaStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class PersonaAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var personaStore: PersonaStore

    @Test
    fun `POST create persona and GET`() {
        val result = adminClient().post().uri("/api/admin/personas")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Tech Expert","systemPrompt":"You are a tech expert","maxItems":5}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("Tech Expert")
            .jsonPath("$.maxItems").isEqualTo(5)
            .returnResult()

        val body = String(result.responseBody!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().get().uri("/api/admin/personas/$id")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.systemPrompt").isEqualTo("You are a tech expert")
    }

    @Test
    fun `PUT update persona`() {
        val result = adminClient().post().uri("/api/admin/personas")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Original","systemPrompt":"original prompt"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().put().uri("/api/admin/personas/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Updated","systemPrompt":"new prompt"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Updated")
    }

    @Test
    fun `DELETE persona`() {
        val result = adminClient().post().uri("/api/admin/personas")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"ToDelete","systemPrompt":"p"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().delete().uri("/api/admin/personas/$id")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `GET list should return personas`() {
        adminClient().get().uri("/api/admin/personas")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    /**
     * Idempotency-Key 헤더를 같은 값으로 두 번 보내면 두 응답이 동일한 스냅샷이어야 하고,
     * 두 번째 PUT 의 body 가 다르더라도 첫 응답이 재사용되므로 DB 값이 바뀌지 않는다.
     *
     * 테스트 환경에선 Redis 가 mock 이라 실제 멱등성 enforcement 는 동작하지 않지만,
     * 적어도 Idempotency-Key 헤더 파싱과 컨트롤러 경로 자체가 깨지지 않는지를 확인한다.
     */
    @Test
    fun `PUT with Idempotency-Key header should be accepted and return success`() {
        val createResult = adminClient().post().uri("/api/admin/personas")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"IdempotencyTarget","systemPrompt":"original prompt"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val id = String(createResult.responseBodyContent!!)
            .substringAfter("\"id\":\"").substringBefore("\"")

        val idempotencyKey = java.util.UUID.randomUUID().toString()

        adminClient().put().uri("/api/admin/personas/$id")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Updated-v1","systemPrompt":"prompt-v1"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Updated-v1")
    }

    @Test
    fun `PATCH active returns 200 and reflects isActive`() {
        val result = adminClient().post().uri("/api/admin/personas")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Togglable","systemPrompt":"p"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        // 활성 → 비활성 전환
        adminClient().method(HttpMethod.PATCH).uri("/api/admin/personas/$id/active")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"isActive": false}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(id)
            .jsonPath("$.isActive").isEqualTo(false)

        // 비활성 → 활성 재전환도 같은 엔드포인트로 처리된다
        adminClient().method(HttpMethod.PATCH).uri("/api/admin/personas/$id/active")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"isActive": true}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.isActive").isEqualTo(true)
    }

    @Test
    fun `PATCH active without auth returns 401`() {
        webClient.method(HttpMethod.PATCH).uri("/api/admin/personas/any-id/active")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"isActive": false}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `PATCH active returns 404 when persona id does not exist`() {
        // 존재하지 않는 ID로 요청 시 서비스의 NotFoundException이 404로 매핑되는지 확인
        adminClient().method(HttpMethod.PATCH).uri("/api/admin/personas/nonexistent-persona/active")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"isActive": false}""")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `PATCH active returns 409 when deactivating a preset persona`() {
        // 프리셋 비활성화 보호 규칙이 HTTP 계층까지 전파되는지 확인한다.
        // 프리셋은 관리자 API로 생성할 수 없으므로 저장소에 직접 seed 한다.
        // VARCHAR(36) 컬럼에 맞추기 위해 UUID 문자열(36자)만 사용한다
        val presetId = UUID.randomUUID().toString()
        personaStore.save(
            Persona(
                id = presetId,
                name = "테스트 프리셋",
                systemPrompt = "preset prompt",
                isPreset = true,
                isActive = true
            )
        )

        adminClient().method(HttpMethod.PATCH).uri("/api/admin/personas/$presetId/active")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"isActive": false}""")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
