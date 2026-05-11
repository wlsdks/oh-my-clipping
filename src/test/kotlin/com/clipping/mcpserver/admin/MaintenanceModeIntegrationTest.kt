package com.clipping.mcpserver.admin

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.store.CategoryStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * 점검 모드가 사용자 쓰기 API만 차단하는지 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class MaintenanceModeIntegrationTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var categoryStore: CategoryStore

    private val passwordEncoder = BCryptPasswordEncoder()

    @BeforeEach
    fun resetMaintenanceSettings() {
        jdbc.update(
            """
            DELETE FROM clipping_runtime_settings
            WHERE setting_key IN ('maintenance_mode', 'maintenance_message')
            """.trimIndent()
        )
        ensureApprovedAdminExists()
    }

    @AfterEach
    fun cleanupMaintenanceSettings() {
        jdbc.update(
            """
            DELETE FROM clipping_runtime_settings
            WHERE setting_key IN ('maintenance_mode', 'maintenance_message')
            """.trimIndent()
        )
    }

    @Test
    fun `maintenance mode가 꺼져 있으면 사용자 쓰기 API가 정상 동작한다`() {
        val sessionId = signupApproveAndLoginUser("maintoff")
        val categoryId = createBrowsableCategory()

        webClient.post().uri("/api/user/categories/$categoryId/subscribe")
            .cookie("SESSION", sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"slackChannelId":""}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.categoryId").isEqualTo(categoryId)
            .jsonPath("$.status").isEqualTo("APPROVED")
    }

    @Test
    fun `maintenance mode가 켜지면 사용자 쓰기는 503으로 차단된다`() {
        val sessionId = signupApproveAndLoginUser("mainton")
        val categoryId = createBrowsableCategory()
        enableMaintenanceMode("서버 점검 중입니다")

        webClient.get().uri("/api/public/maintenance")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.active").isEqualTo(true)
            .jsonPath("$.message").isEqualTo("서버 점검 중입니다")

        webClient.get().uri("/api/user/categories/browse")
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus().isOk

        webClient.post().uri("/api/user/categories/$categoryId/subscribe")
            .cookie("SESSION", sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"slackChannelId":""}""")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.maintenanceMode").isEqualTo(true)
            .jsonPath("$.message").isEqualTo("서버 점검 중입니다")

        adminClient().get().uri("/api/admin/runtime-settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.maintenanceMode").isEqualTo(true)
    }

    /**
     * 테스트용 USER 계정을 생성하고 승인한 뒤 로그인 세션을 반환한다.
     */
    private fun signupApproveAndLoginUser(usernamePrefix: String): String {
        val username = "${usernamePrefix}_${System.nanoTime() % 1_000_000}"
        val password = "StrongPass123!"

        // V129: signup 에 departmentId 가 필요하므로 부서 row 를 선제 시드한다.
        val deptId = java.util.UUID.randomUUID().toString()
        val deptSuffix = deptId.takeLast(8)
        jdbc.update(
            """
            INSERT INTO departments (id, name, name_normalized, display_order, is_active, created_at, updated_at)
            VALUES (?, ?, ?, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            deptId,
            "전략기획팀-$deptSuffix",
            "전략기획팀-$deptSuffix"
        )

        webClient.post().uri("/user/signup")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("displayName", "Requester")
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
        ) ?: error("user id not found")

        adminClient().post().uri("/api/admin/user-accounts/$userId/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reviewNote":"maintenance test approval"}""")
            .exchange()
            .expectStatus().isOk

        jdbc.update(
            "UPDATE admin_users SET slack_dm_channel_id = ? WHERE username = ?",
            "D_MAINT_${username.uppercase()}",
            username
        )

        return loginAndExtractSessionId(username, password)
    }

    /**
     * 테스트용 점검 모드를 admin runtime settings API로 활성화한다.
     */
    private fun enableMaintenanceMode(message: String) {
        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"maintenanceMode":true,"maintenanceMessage":"$message"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.maintenanceMode").isEqualTo(true)
            .jsonPath("$.maintenanceMessage").isEqualTo(message)
    }

    /**
     * 폼 로그인 후 SESSION 쿠키 값을 추출한다.
     */
    private fun loginAndExtractSessionId(username: String, password: String): String {
        val loginResult = webClient.post().uri("/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", password)
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .returnResult(Void::class.java)

        val sessionCookie = loginResult.responseHeaders[HttpHeaders.SET_COOKIE]
            ?.asSequence()
            ?.map { it.substringBefore(";") }
            ?.lastOrNull {
                it.startsWith("SESSION=") && it.substringAfter("SESSION=").isNotBlank()
            }
            ?: error("SESSION cookie not found")

        return sessionCookie.substringAfter("SESSION=").also { it.isNotBlank() shouldBe true }
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    /**
     * browse 구독에 사용할 활성 카테고리를 하나 생성한다.
     */
    private fun createBrowsableCategory(): String =
        categoryStore.save(
            Category(
                id = "",
                name = "점검 테스트 ${System.nanoTime() % 1_000_000}",
                description = "maintenance test category",
                isActive = true,
                isPublic = true
            )
        ).id

    /**
     * admin-api Bearer principal이 승인된 ADMIN 계정으로 매핑되도록 테스트 관리자를 보장한다.
     */
    private fun ensureApprovedAdminExists() {
        val approvedAdminCount = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM admin_users
            WHERE role = 'ADMIN' AND approval_status = 'APPROVED' AND is_active = TRUE
            """.trimIndent(),
            Int::class.java
        ) ?: 0
        if (approvedAdminCount > 0) {
            return
        }

        val adminId = UUID.randomUUID().toString()
        val now = Timestamp.from(Instant.now())
        jdbc.update(
            """
            INSERT INTO admin_users
            (
                id,
                username,
                password_hash,
                display_name,
                is_active,
                last_login_at,
                created_at,
                updated_at,
                role,
                department,
                approval_status,
                approval_note,
                approved_by_user_id,
                approved_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            adminId,
            "maintenance-admin",
            passwordEncoder.encode("unused-password"),
            "점검 관리자",
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
    }
}
