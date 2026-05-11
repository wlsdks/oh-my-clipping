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
 * 통합 히스토리 + restore 컨트롤러 E2E 테스트.
 *
 * 4개 도메인 중 페르소나를 기본 시나리오로 사용한다:
 *   1. 페르소나 생성
 *   2. 2회 업데이트 → revision 2건 append
 *   3. GET /history → 2건 반환, 최신순
 *   4. POST /restore → 최초 버전 snapshot으로 복원
 *   5. stale expectedUpdatedAt → 409
 *   6. 존재하지 않는 revisionId → 404
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class AdminHistoryControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Test
    fun `GET personas history returns revisions in newest-first order`() {
        val id = createPersona("HistoryTarget", "prompt-v0")
        updatePersona(id, """{"systemPrompt":"prompt-v1"}""")
        updatePersona(id, """{"systemPrompt":"prompt-v2"}""")

        adminClient().get().uri("/api/admin/personas/$id/history")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].revisionNumber").isEqualTo(2)
            .jsonPath("$[1].revisionNumber").isEqualTo(1)
            .jsonPath("$[0].changedFields[0]").isEqualTo("systemPrompt")
    }

    @Test
    fun `GET history respects limit query param`() {
        val id = createPersona("LimitTarget", "prompt-v0")
        repeat(3) { i ->
            updatePersona(id, """{"systemPrompt":"prompt-$i"}""")
        }

        adminClient().get().uri("/api/admin/personas/$id/history?limit=2")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
    }

    @Test
    fun `POST restore with valid expectedUpdatedAt restores snapshot`() {
        val id = createPersona("RestoreTarget", "v0-prompt")
        updatePersona(id, """{"systemPrompt":"v1-prompt"}""")
        updatePersona(id, """{"systemPrompt":"v2-prompt"}""")

        val historyBody = adminClient().get().uri("/api/admin/personas/$id/history")
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBodyContent!!
        val historyJson = String(historyBody)
        // revisionNumber=1이 최초 업데이트 스냅샷 — snapshot이 v0-prompt → v1-prompt 변환을 담고 있음.
        val targetRevisionId = extractRevisionIdByNumber(historyJson, revisionNumber = 1)

        val currentUpdatedAt = adminClient().get().uri("/api/admin/personas/$id")
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBodyContent!!
            .let { String(it) }
            .let { it.substringAfter("\"updatedAt\":\"").substringBefore("\"") }

        adminClient().post().uri("/api/admin/personas/$id/restore")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"revisionId":"$targetRevisionId","expectedUpdatedAt":"$currentUpdatedAt"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.systemPrompt").isEqualTo("v1-prompt")
    }

    @Test
    fun `POST restore with stale expectedUpdatedAt returns 409`() {
        // 2번 업데이트해야 revision 1 snapshot이 현재 상태와 달라진다. (v1-prompt vs v2-prompt)
        val id = createPersona("StaleRestoreTarget", "v0-prompt")
        updatePersona(id, """{"systemPrompt":"v1-prompt"}""")
        updatePersona(id, """{"systemPrompt":"v2-prompt"}""")

        val historyBody = adminClient().get().uri("/api/admin/personas/$id/history")
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBodyContent!!
        val revisionId = extractRevisionIdByNumber(String(historyBody), revisionNumber = 1)

        adminClient().post().uri("/api/admin/personas/$id/restore")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"revisionId":"$revisionId","expectedUpdatedAt":"2020-01-01T00:00:00Z"}"""
            )
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `POST restore with unknown revisionId returns 404`() {
        val id = createPersona("NotFoundTarget", "v0-prompt")
        val currentUpdatedAt = adminClient().get().uri("/api/admin/personas/$id")
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBodyContent!!
            .let { String(it) }
            .let { it.substringAfter("\"updatedAt\":\"").substringBefore("\"") }

        adminClient().post().uri("/api/admin/personas/$id/restore")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"revisionId":"non-existent-uuid","expectedUpdatedAt":"$currentUpdatedAt"}"""
            )
            .exchange()
            .expectStatus().isNotFound
    }

    private fun createPersona(name: String, prompt: String): String {
        val result = adminClient().post().uri("/api/admin/personas")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"$name","systemPrompt":"$prompt"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        return String(result.responseBodyContent!!).substringAfter("\"id\":\"").substringBefore("\"")
    }

    private fun updatePersona(id: String, body: String) {
        val currentUpdatedAt = adminClient().get().uri("/api/admin/personas/$id")
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBodyContent!!
            .let { String(it) }
            .let { it.substringAfter("\"updatedAt\":\"").substringBefore("\"") }

        // expectedUpdatedAt 없이 저장 — 낙관적 잠금을 쓰지 않아야 본 테스트의 append 경로를 직접 검증한다.
        adminClient().put().uri("/api/admin/personas/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk
    }

    /**
     * history JSON 응답에서 revisionNumber에 해당하는 revisionId를 추출한다.
     * 단순 문자열 파싱이지만 테스트 가독성을 위해 헬퍼로 분리.
     */
    private fun extractRevisionIdByNumber(json: String, revisionNumber: Int): String {
        val marker = "\"revisionNumber\":$revisionNumber"
        val entryStart = json.lastIndexOf("{", json.indexOf(marker))
        val entryEnd = json.indexOf("}", entryStart)
        val entry = json.substring(entryStart, entryEnd + 1)
        return entry.substringAfter("\"revisionId\":\"").substringBefore("\"")
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
