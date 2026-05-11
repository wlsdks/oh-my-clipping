package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.LocalDevSupportService
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

/**
 * 로컬 profile에서 개발용 shortcut과 bootstrap 데이터가 정상 동작하는지 검증합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test", "local")
class LocalDevPublicControllerIntegrationTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var localDevSupportService: LocalDevSupportService

    @Test
    fun `local login shortcuts should expose fixed seeded entries`() {
        webClient.get().uri("/api/public/dev/login-shortcuts")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(true)
            .jsonPath("$.shortcuts.length()").isEqualTo(3)
            .jsonPath("$.shortcuts[0].label").isEqualTo("관리자 로그인")
            .jsonPath("$.shortcuts[0].username").isEqualTo("dev.admin@clipping.local")
            .jsonPath("$.shortcuts[1].label").isEqualTo("회원 로그인")
            .jsonPath("$.shortcuts[1].username").isEqualTo("dev.user@clipping.local")
            .jsonPath("$.shortcuts[2].label").isEqualTo("신규 가입자 로그인")
            .jsonPath("$.shortcuts[2].username").isEqualTo("dev.user.fresh@clipping.local")
    }

    @Test
    fun `local bootstrap should remain idempotent when reapplied`() {
        // 로컬 runner 이후에도 수동 재실행 시 row 수가 늘어나지 않아야 한다.
        localDevSupportService.bootstrap()

        val devUserCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_users WHERE username LIKE 'dev.%'",
            Int::class.java
        ) ?: 0
        val requestCount = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM clipping_user_requests
            WHERE id IN (
                '00000000-0000-0000-0000-000000001001',
                '00000000-0000-0000-0000-000000001002',
                '00000000-0000-0000-0000-000000001003'
            )
            """.trimIndent(),
            Int::class.java
        ) ?: 0
        val summaryCount = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM batch_summaries
            WHERE id IN (
                '00000000-0000-0000-0000-000000000501',
                '00000000-0000-0000-0000-000000000502',
                '00000000-0000-0000-0000-000000000503',
                '00000000-0000-0000-0000-000000000504',
                '00000000-0000-0000-0000-000000000505',
                '00000000-0000-0000-0000-000000000506'
            )
            """.trimIndent(),
            Int::class.java
        ) ?: 0

        // LocalDevSupportService 의 seed 계정 8종(admin + user + pending + rejected + fresh + analyst + finance + ops)을 보장한다.
        devUserCount shouldBe 8
        requestCount shouldBe 3
        summaryCount shouldBe 6
    }

    @Test
    fun `local bootstrap should align placeholder slack channels to ops log channel`() {
        // 런타임 설정에 운영 로그 채널을 직접 삽입한다.
        jdbc.update(
            "MERGE INTO clipping_runtime_settings (setting_key, setting_value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
            "ops_log_channel_id", "C123LOCAL99"
        )
        // 로컬 seed 승인 테스트가 항상 가능하도록 placeholder 채널을 현재 운영 로그 채널로 치환한다.
        localDevSupportService.bootstrap()

        val categoryChannel = jdbc.queryForObject(
            "SELECT slack_channel_id FROM batch_categories WHERE id = ?",
            String::class.java,
            "00000000-0000-0000-0000-000000000201"
        )
        val requestChannel = jdbc.queryForObject(
            "SELECT slack_channel_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            "00000000-0000-0000-0000-000000001001"
        )

        categoryChannel shouldBe "C123LOCAL99"
        requestChannel shouldBe "C123LOCAL99"
    }

    @Test
    fun `local bootstrap should keep dev user subscriptions within policy limit`() {
        localDevSupportService.bootstrap()

        val approvedCount = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM clipping_user_requests
            WHERE requester_user_id = ?
              AND status = 'APPROVED'
            """.trimIndent(),
            Int::class.java,
            "00000000-0000-0000-0000-000000000002"
        ) ?: 0

        // 부트스트랩 SQL: 5건 APPROVED 중 2건(1006, 1007)이 WITHDRAWN → 3건 유지
        approvedCount shouldBe 3
    }

    @Test
    fun `local bootstrap should remove duplicate canonical category names`() {
        localDevSupportService.bootstrap()

        val duplicateCanonicalCount = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM (
                SELECT name
                FROM batch_categories
                WHERE is_active = TRUE
                  AND name IN ('AI/테크', 'HR/L&D', '투자/금융', '정책/규제', '보안/인프라', '마케팅/이커머스')
                GROUP BY name
                HAVING COUNT(*) > 1
            ) duplicates
            """.trimIndent(),
            Int::class.java
        ) ?: 0

        duplicateCanonicalCount shouldBe 0
    }

    @Test
    fun `local bootstrap should align batch summaries with rss items`() {
        localDevSupportService.bootstrap()

        val mismatchCount = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM batch_summaries bs
            JOIN rss_items ri ON ri.id = bs.rss_item_id
            WHERE bs.source_link LIKE 'https://local-seed.example.com/%'
              AND (
                  bs.category_id <> ri.category_id
                  OR bs.original_title <> ri.title
                  OR bs.source_link <> ri.link
              )
            """.trimIndent(),
            Int::class.java
        ) ?: 0

        mismatchCount shouldBe 0
    }

    @Test
    fun `local bootstrap should align seeded approved requests to intended categories`() {
        localDevSupportService.bootstrap()

        val aiTechCategoryId = jdbc.queryForObject(
            "SELECT approved_category_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            "00000000-0000-0000-0000-000000001004"
        )
        val hrCategoryId = jdbc.queryForObject(
            "SELECT approved_category_id FROM clipping_user_requests WHERE id = ?",
            String::class.java,
            "00000000-0000-0000-0000-000000001006"
        )

        aiTechCategoryId shouldBe "00000000-0000-0000-0000-000000000201"
        hrCategoryId shouldBe "00000000-0000-0000-0000-000000000202"
    }

    @Test
    fun `seeded admin and user accounts should login with fixed password`() {
        login("/login", "dev.admin@clipping.local", "/admin")
        login("/login", "dev.user@clipping.local", "/user")
        login("/login", "dev.user.fresh@clipping.local", "/user")
    }

    private fun login(path: String, username: String, expectedRedirect: String) {
        val loginResult = webClient.post().uri(path)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("username", username)
                    .with("password", "LocalPass123!")
            )
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().value(HttpHeaders.LOCATION) { location ->
                location.contains(expectedRedirect) shouldBe true
            }
            .returnResult(Void::class.java)

        val sessionCookie = loginResult.responseHeaders[HttpHeaders.SET_COOKIE]
            ?.asSequence()
            ?.map { it.substringBefore(";") }
            ?.lastOrNull {
                it.startsWith("SESSION=") && it.substringAfter("SESSION=").isNotBlank()
            }

        (sessionCookie != null) shouldBe true
    }
}
