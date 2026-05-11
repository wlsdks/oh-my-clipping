package com.ohmyclipping.admin

import io.kotest.matchers.shouldBe
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class AdminAuthFlowIntegrationTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private val passwordEncoder = BCryptPasswordEncoder()

    /** V129: signup 에 departmentId 가 필요하므로 테스트용 부서 row 를 시드한다. UNIQUE 격리를 위해 suffix 부여. */
    private fun seedDepartment(name: String): String {
        val id = UUID.randomUUID().toString()
        val suffix = id.takeLast(8)
        jdbc.update(
            """
            INSERT INTO departments (id, name, name_normalized, display_order, is_active, created_at, updated_at)
            VALUES (?, ?, ?, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            id, "$name-$suffix", "$name-$suffix".trim().lowercase()
        )
        return id
    }

    @Test
    fun `login page should be public`() {
        webClient.get().uri("/admin/login")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `first signup then login should allow admin api without bearer header`() {
        val username = "auth${(System.nanoTime() % 1_000_000)}"
        val password = "StrongPass123!"

        webClient.post().uri("/admin/signup")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("displayName", "Ops")
                    .with("password", password)
                    .with("confirmPassword", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().value(HttpHeaders.LOCATION) { location ->
                location.contains("/admin/login?registered=1") shouldBe true
            }

        val userCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_users WHERE username = ?",
            Int::class.java,
            username
        ) ?: 0
        userCount shouldBe 1

        val loginResult = webClient.post().uri("/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().value(HttpHeaders.LOCATION) { location ->
                location.contains("/admin") shouldBe true
            }
            .returnResult(Void::class.java)

        val sessionCookie = loginResult.responseHeaders[HttpHeaders.SET_COOKIE]
            ?.asSequence()
            ?.map { it.substringBefore(";") }
            ?.lastOrNull {
                it.startsWith("SESSION=") && it.substringAfter("SESSION=").isNotBlank()
            }
            ?: throw IllegalStateException("SESSION cookie not found")
        val sessionId = sessionCookie.substringAfter("SESSION=")

        webClient.get().uri("/admin")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus().isOk

        webClient.get().uri("/admin/sources")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                body.contains("<div id=\"root\"></div>") shouldBe true
            }

        webClient.get().uri("/api/admin/categories")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
    }

    @Test
    fun `user signup and login should allow only user api`() {
        val adminSessionId = createAdminSession()
        val username = "user${(System.nanoTime() % 1_000_000)}"
        val password = "StrongPass123!"
        val department = "전략기획팀"
        val departmentId = seedDepartment(department)

        webClient.post().uri("/user/signup")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("displayName", "Requester")
                    .with("departmentId", departmentId)
                    .with("password", password)
                    .with("confirmPassword", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().value(HttpHeaders.LOCATION) { location ->
                location.contains("/user/login?pending_approval=1") shouldBe true
            }

        val approvalStatus = jdbc.queryForObject(
            "SELECT approval_status FROM admin_users WHERE username = ?",
            String::class.java,
            username
        )
        approvalStatus shouldBe "PENDING"

        // Legacy 이름 캐시가 서비스에서 JOIN 결과로 동기화됐는지 확인한다.
        val savedDepartment = jdbc.queryForObject(
            "SELECT department FROM admin_users WHERE username = ?",
            String::class.java,
            username
        )
        // seedDepartment 가 UNIQUE 격리를 위해 suffix 를 붙이므로 prefix 만 확인한다.
        (savedDepartment?.startsWith(department) == true) shouldBe true

        webClient.post().uri("/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().value(HttpHeaders.LOCATION) { location ->
                location.contains("/login?pending_approval=1") shouldBe true
            }

        val userId = jdbc.queryForObject(
            "SELECT id FROM admin_users WHERE username = ?",
            String::class.java,
            username
        ) ?: throw IllegalStateException("user id not found")

        webClient.get().uri("/api/admin/user-accounts?status=PENDING")
            .cookie("SESSION", adminSessionId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isNotEmpty

        webClient.post().uri("/api/admin/user-accounts/$userId/approve")
            .cookie("SESSION", adminSessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reviewNote":"현업 확인 완료"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.approvalStatus").isEqualTo("APPROVED")

        val loginResult = webClient.post().uri("/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().value(HttpHeaders.LOCATION) { location ->
                location.contains("/user") shouldBe true
            }
            .returnResult(Void::class.java)

        val sessionCookie = loginResult.responseHeaders[HttpHeaders.SET_COOKIE]
            ?.asSequence()
            ?.map { it.substringBefore(";") }
            ?.lastOrNull {
                it.startsWith("SESSION=") && it.substringAfter("SESSION=").isNotBlank()
            }
            ?: throw IllegalStateException("SESSION cookie not found")
        val sessionId = sessionCookie.substringAfter("SESSION=")

        webClient.get().uri("/user")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus().isOk

        webClient.get().uri("/api/user/requests")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray

        webClient.get().uri("/api/admin/categories")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `rejected user should not be able to login`() {
        val adminSessionId = createAdminSession()
        val username = "rejected${(System.nanoTime() % 1_000_000)}"
        val password = "StrongPass123!"

        val deptId = seedDepartment("테스트팀")
        // 사용자 가입 → PENDING 상태
        webClient.post().uri("/user/signup")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("displayName", "거절 대상")
                    .with("departmentId", deptId)
                    .with("password", password)
                    .with("confirmPassword", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection

        val userId = jdbc.queryForObject(
            "SELECT id FROM admin_users WHERE username = ?",
            String::class.java,
            username
        ) ?: throw IllegalStateException("user id not found")

        // 관리자가 거절 처리
        webClient.post().uri("/api/admin/user-accounts/$userId/reject")
            .cookie("SESSION", adminSessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reviewNote":"정책 위반"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.approvalStatus").isEqualTo("REJECTED")

        // 거절된 사용자 로그인 시도 → 차단 확인
        webClient.post().uri("/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().value(HttpHeaders.LOCATION) { location ->
                location.contains("rejected=1") shouldBe true
            }
    }

    @Test
    fun `me endpoint should return approvalStatus`() {
        val sessionId = createAdminSession()

        webClient.get().uri("/api/me")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.approvalStatus").isEqualTo("APPROVED")
            .jsonPath("$.role").isEqualTo("ADMIN")
            .jsonPath("$.username").isNotEmpty
    }

    @Test
    fun `api logout should return no content and invalidate session`() {
        val sessionId = createAdminSession()

        webClient.post().uri("/logout")
            .cookie("SESSION", sessionId)
            .header("X-Logout-Mode", "api")
            .exchange()
            .expectStatus().isNoContent

        webClient.get().uri("/api/admin/categories")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus().isUnauthorized
    }

    private fun createAdminSession(): String {
        val username = "approver${(System.nanoTime() % 1_000_000)}"
        val password = "StrongPass123!"
        val id = UUID.randomUUID().toString()
        val now = java.sql.Timestamp.from(java.time.Instant.now())
        jdbc.update(
            """
            INSERT INTO admin_users
            (id, username, password_hash, display_name, is_active, last_login_at, created_at, updated_at, role, department, approval_status, approval_note, approved_by_user_id, approved_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            username,
            passwordEncoder.encode(password),
            "승인 담당자",
            true,
            null,
            now,
            now,
            "ADMIN",
            "운영팀",
            "APPROVED",
            null,
            null,
            now
        )

        val loginResult = webClient.post().uri("/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().value(HttpHeaders.LOCATION) { location ->
                location.contains("/admin") shouldBe true
            }
            .returnResult(Void::class.java)

        return loginResult.responseHeaders[HttpHeaders.SET_COOKIE]
            ?.asSequence()
            ?.map { it.substringBefore(";") }
            ?.lastOrNull { it.startsWith("SESSION=") && it.substringAfter("SESSION=").isNotBlank() }
            ?.substringAfter("SESSION=")
            ?: throw IllegalStateException("admin session cookie not found")
    }
}
