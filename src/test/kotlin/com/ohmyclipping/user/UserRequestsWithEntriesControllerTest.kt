package com.ohmyclipping.user

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters

/**
 * POST /api/user/requests/with-entries 컨트롤러 통합 테스트.
 * - 모든 entry 성공 시 201 submitted
 * - 모든 entry 실패 시 200 rejected + errors 배열
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class UserRequestsWithEntriesControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private lateinit var userSession: String
    private lateinit var deptId: String

    @BeforeEach
    fun setUp() {
        // 테스트 격리용 부서 생성
        deptId = java.util.UUID.randomUUID().toString()
        val suffix = deptId.takeLast(8)
        jdbc.update(
            """
            INSERT INTO departments (id, name, name_normalized, display_order, is_active, created_at, updated_at)
            VALUES (?, ?, ?, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            deptId,
            "테스트팀-$suffix",
            "테스트팀-$suffix"
        )

        // 관리자 계정 생성 + 로그인
        val adminUsername = "admin-ctrl-${System.nanoTime() % 1_000_000}@test.io"
        val adminPassword = "AdminPass123!"
        webClient.post().uri("/admin/signup")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", adminUsername)
                    .with("displayName", "TestAdmin")
                    .with("departmentId", deptId)
                    .with("password", adminPassword)
                    .with("confirmPassword", adminPassword)
            )
            .exchange()
            .expectStatus().is3xxRedirection

        val adminSession = loginAndGetSession(adminUsername, adminPassword)

        // 일반 사용자 계정 생성
        val username = "user-ctrl-${System.nanoTime() % 1_000_000}@test.io"
        val password = "UserPass123!"
        webClient.post().uri("/user/signup")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("displayName", "TestUser")
                    .with("departmentId", deptId)
                    .with("password", password)
                    .with("confirmPassword", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection

        // 관리자가 사용자 승인
        val userId = jdbc.queryForObject(
            "SELECT id FROM admin_users WHERE username = ?",
            String::class.java,
            username
        ) ?: error("user not found: $username")

        webClient.post().uri("/api/admin/user-accounts/$userId/approve")
            .cookie("SESSION", adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reviewNote":"테스트 승인"}""")
            .exchange()
            .expectStatus().isOk

        userSession = loginAndGetSession(username, password)
    }

    @Test
    fun `POST with-entries 모든 entry 성공 시 201 submitted`() {
        val body = """
            {
              "categoryName": "E2E-AB-Controller-${System.currentTimeMillis()}",
              "entries": [
                {"value": "리스킬링", "type": "keyword"},
                {"value": "MegaCorp", "type": "company", "stockCode": "999930"}
              ]
            }
        """.trimIndent()

        webClient.post().uri("/api/user/requests/with-entries")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.status").isEqualTo("submitted")
            .jsonPath("$.requestId").exists()
            .jsonPath("$.errors").isArray
    }

    @Test
    fun `rejected (모든 entry 실패) 시 200 + errors 배열`() {
        val body = """
            {
              "categoryName": "테스트",
              "entries": [
                {"value": "MegaCorp", "type": "company", "stockCode": "bad-format"}
              ]
            }
        """.trimIndent()

        webClient.post().uri("/api/user/requests/with-entries")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("rejected")
            .jsonPath("$.errors[0].reason").isEqualTo("INVALID_STOCK_CODE")
    }

    @Test
    fun `entries 항목이 50개를 초과하면 400 Bad Request 를 반환한다`() {
        // 51개 entry — @Size(max=50) 위반 → Spring @Valid 가 400 을 내려야 한다
        val entries = (1..51).joinToString(",") { i ->
            """{"value": "키워드$i", "type": "keyword"}"""
        }
        val body = """
            {
              "categoryName": "초과 테스트",
              "entries": [$entries]
            }
        """.trimIndent()

        webClient.post().uri("/api/user/requests/with-entries")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `categoryName 이 빈 문자열이면 400 Bad Request 를 반환한다`() {
        val body = """
            {
              "categoryName": "",
              "entries": [{"value": "리스킬링", "type": "keyword"}]
            }
        """.trimIndent()

        webClient.post().uri("/api/user/requests/with-entries")
            .cookie("SESSION", userSession)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    /** 폼 로그인 후 SESSION 쿠키 값을 반환한다. */
    private fun loginAndGetSession(username: String, password: String): String {
        val result = webClient.post().uri("/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .returnResult(Void::class.java)

        return result.responseHeaders[HttpHeaders.SET_COOKIE]
            ?.asSequence()
            ?.map { it.substringBefore(";") }
            ?.lastOrNull { it.startsWith("SESSION=") && it.substringAfter("SESSION=").isNotBlank() }
            ?.substringAfter("SESSION=")
            ?: error("SESSION cookie not found for $username")
    }
}
