package com.ohmyclipping.admin

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * BackfillAdminController HTTP 계약 통합 테스트.
 *
 * JDBC 로 rss_sources + batch_categories 를 직접 시드하고
 * API 응답 구조와 validation 을 검증한다.
 * @Transactional 없이 explicit @AfterEach cleanup 으로 격리.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class BackfillAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private var testCatId: String = ""
    private var testSourceId: String = ""

    @BeforeEach
    fun setup() {
        // 카테고리 생성 (API 경유)
        testCatId = createCategoryViaApi()

        // rss_sources 직접 삽입
        testSourceId = "test-src-${System.nanoTime()}"
        jdbc.update(
            """
            INSERT INTO rss_sources
              (id, category_id, name, url, is_active, crawl_approved, origin,
               legal_basis, summary_allowed, fulltext_allowed,
               verification_status, reliability_score, crawl_fail_count,
               source_region, curated)
            VALUES (?, ?, ?, ?, true, false, 'manual',
                    'QUOTATION_ONLY', true, false,
                    'PENDING', 50, 0, 'UNKNOWN', false)
            """.trimIndent(),
            testSourceId,
            testCatId,
            "TestCorp Newsroom",
            "https://news.testcorp.example.com/kr/feed",
        )
    }

    @AfterEach
    fun cleanup() {
        if (testCatId.isNotEmpty()) {
            // syncSourcesForCategory 가 rss_sources 를 추가할 수 있으므로 먼저 삭제
            jdbc.update("DELETE FROM rss_sources WHERE category_id = ?", testCatId)
            jdbc.update("DELETE FROM category_organizations WHERE category_id = ?", testCatId)
            jdbc.update("DELETE FROM category_feature_flags WHERE category_id = ?", testCatId)
            jdbc.update("DELETE FROM clipping_category_rules WHERE category_id = ?", testCatId)
        }
        if (testCatId.isNotEmpty()) jdbc.update("DELETE FROM batch_categories WHERE id = ?", testCatId)
    }

    // ─── Preview 테스트 ───────────────────────────────────────────────────────

    @Nested
    inner class `GET 백필 미리보기` {

        @Test
        fun `GET preview - 200 응답 및 candidates, total, byConfidence 필드가 존재한다`() {
            adminClient().get()
                .uri("/api/admin/organizations/backfill/preview?confidence=low")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.candidates").isArray
                .jsonPath("$.total").isNumber
                .jsonPath("$.byConfidence").exists()
                .jsonPath("$.byConfidence.high").isNumber
                .jsonPath("$.byConfidence.medium").isNumber
                .jsonPath("$.byConfidence.low").isNumber
        }

        @Test
        fun `GET preview - categoryId 필터로 호출하면 200 응답`() {
            adminClient().get()
                .uri("/api/admin/organizations/backfill/preview?categoryId=$testCatId&confidence=low")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.candidates").isArray
        }

        @Test
        fun `GET preview - 파라미터 없이 호출하면 기본값 confidence=high 로 동작하고 200 응답`() {
            adminClient().get()
                .uri("/api/admin/organizations/backfill/preview")
                .exchange()
                .expectStatus().isOk
        }
    }

    // ─── Apply 테스트 ─────────────────────────────────────────────────────────

    @Nested
    inner class `POST 백필 적용` {

        @Test
        fun `빈 candidateIds 로 apply 호출하면 total=0 succeeded=0 응답`() {
            adminClient().post()
                .uri("/api/admin/organizations/backfill/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"candidateIds":[]}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(0)
                .jsonPath("$.succeeded").isEqualTo(0)
                .jsonPath("$.failed").isEqualTo(0)
                .jsonPath("$.errors").isArray
                .jsonPath("$.affectedCategoryIds").isArray
        }

        @Test
        fun `100개 초과 candidateIds 는 @Valid 에 의해 400 응답`() {
            // 101개의 id 생성
            val ids = (1..101).map { "\"src-$it\"" }.joinToString(",")
            val body = """{"candidateIds":[$ids]}"""

            adminClient().post()
                .uri("/api/admin/organizations/backfill/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `존재하지 않는 sourceId 로 apply 호출하면 succeeded=0 이고 errors 에 포함된다`() {
            adminClient().post()
                .uri("/api/admin/organizations/backfill/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"candidateIds":["non-existent-src-id"]}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.succeeded").isEqualTo(0)
                .jsonPath("$.failed").isEqualTo(1)
                .jsonPath("$.errors[0].candidateId").isEqualTo("non-existent-src-id")
        }

        @Test
        fun `인증 없이 apply 호출하면 401 또는 403 응답`() {
            webClient.post()
                .uri("/api/admin/organizations/backfill/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"candidateIds":[]}""")
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    private fun createCategoryViaApi(): String {
        val result = adminClient().post()
            .uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"BackfillTestCat-${System.nanoTime()}"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)
        val body = String(result.responseBodyContent!!)
        return body.substringAfter("\"id\":\"").substringBefore("\"")
    }
}
