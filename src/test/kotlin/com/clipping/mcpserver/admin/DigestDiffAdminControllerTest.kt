package com.clipping.mcpserver.admin

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
import java.time.LocalDate
import java.util.UUID

/**
 * DigestDiffAdminController HTTP 계약 통합 테스트.
 *
 * JDBC 로 batch_categories + digest_diff_log 를 직접 시드하고
 * GET /api/admin/digest-diff 의 응답 구조, 필터, 400 검증을 확인한다.
 * @AfterEach cleanup 으로 격리.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class DigestDiffAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private var testCatId: String = ""

    @BeforeEach
    fun setup() {
        // 카테고리 생성 (API 경유 — FK 충족)
        testCatId = createCategoryViaApi()
    }

    @AfterEach
    fun cleanup() {
        if (testCatId.isNotEmpty()) {
            jdbc.update("DELETE FROM digest_diff_log WHERE category_id = ?", testCatId)
            jdbc.update("DELETE FROM batch_categories WHERE id = ?", testCatId)
        }
    }

    // ─── 기본 조회 ───────────────────────────────────────────────────────────

    @Nested
    inner class `GET digest-diff 기본 조회` {

        @Test
        fun `categoryId 와 from-to 를 지정하면 해당 범위 행을 반환한다`() {
            // GIVEN — diff row 2개 삽입 (날짜 다름)
            val id1 = insertDiffRow(categoryId = testCatId, digestDate = LocalDate.of(2026, 4, 10))
            val id2 = insertDiffRow(categoryId = testCatId, digestDate = LocalDate.of(2026, 4, 11))

            // WHEN
            adminClient()
                .get()
                .uri("/api/admin/digest-diff?categoryId=$testCatId&from=2026-04-10&to=2026-04-11")
                .exchange()
                // THEN
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(50)
                .jsonPath("$.content").isArray
                .jsonPath("$.content[0].categoryId").isEqualTo(testCatId)
                .jsonPath("$.content[0].newMode").isEqualTo("DUAL_SECTION")
                .jsonPath("$.content[0].sectionsCount").isEqualTo(2)
                .jsonPath("$.content[0].articlesCount").isEqualTo(5)
                .jsonPath("$.content[0].crossMatchCount").isEqualTo(1)
        }

        @Test
        fun `from-to 범위 밖의 행은 포함되지 않는다`() {
            // GIVEN — 범위 안 1개, 범위 밖 1개
            insertDiffRow(categoryId = testCatId, digestDate = LocalDate.of(2026, 4, 15))
            insertDiffRow(categoryId = testCatId, digestDate = LocalDate.of(2026, 3, 1))

            // WHEN
            adminClient()
                .get()
                .uri("/api/admin/digest-diff?categoryId=$testCatId&from=2026-04-01&to=2026-04-30")
                .exchange()
                // THEN
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
        }

        @Test
        fun `결과가 없으면 content 가 빈 배열이고 totalElements 가 0 이다`() {
            adminClient()
                .get()
                .uri("/api/admin/digest-diff?categoryId=$testCatId&from=2020-01-01&to=2020-01-31")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.content").isEmpty
        }

        @Test
        fun `페이지 파라미터로 결과가 잘라진다`() {
            // GIVEN — 3개 삽입
            insertDiffRow(categoryId = testCatId, digestDate = LocalDate.of(2026, 4, 10))
            insertDiffRow(categoryId = testCatId, digestDate = LocalDate.of(2026, 4, 11))
            insertDiffRow(categoryId = testCatId, digestDate = LocalDate.of(2026, 4, 12))

            // WHEN — size=2, page=0
            adminClient()
                .get()
                .uri("/api/admin/digest-diff?categoryId=$testCatId&from=2026-04-01&to=2026-04-30&size=2&page=0")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.size").isEqualTo(2)
                .jsonPath("$.page").isEqualTo(0)
        }

        @Test
        fun `page=-1 은 page=0 과 동일한 슬라이스를 반환하고 응답 page 는 0 이다`() {
            // GIVEN — 2개 삽입
            insertDiffRow(categoryId = testCatId, digestDate = LocalDate.of(2026, 4, 10))
            insertDiffRow(categoryId = testCatId, digestDate = LocalDate.of(2026, 4, 11))

            // WHEN — 음수 page 는 서버에서 0 으로 강제한다.
            adminClient()
                .get()
                .uri("/api/admin/digest-diff?categoryId=$testCatId&from=2026-04-01&to=2026-04-30&size=2&page=-1")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.page").isEqualTo(0)
        }
    }

    // ─── 파라미터 검증 ────────────────────────────────────────────────────────

    @Nested
    inner class `categoryId 파라미터 검증` {

        @Test
        fun `categoryId 가 없으면 400 을 반환한다`() {
            adminClient()
                .get()
                .uri("/api/admin/digest-diff?from=2026-04-01&to=2026-04-30")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `categoryId 가 공백 문자열이면 400 을 반환한다`() {
            adminClient()
                .get()
                .uri("/api/admin/digest-diff?categoryId=+&from=2026-04-01&to=2026-04-30")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `인증 없이 호출하면 401 을 반환한다`() {
            webClient
                .get()
                .uri("/api/admin/digest-diff?categoryId=$testCatId")
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
        val result = adminClient()
            .post()
            .uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"DigestDiffTestCat-${System.nanoTime()}"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)
        val body = String(result.responseBodyContent!!)
        return body.substringAfter("\"id\":\"").substringBefore("\"")
    }

    private fun insertDiffRow(
        categoryId: String,
        digestDate: LocalDate,
        legacySummary: String = "Legacy summary text",
        newSummary: String = "New summary text",
        newMode: String = "DUAL_SECTION",
        sectionsCount: Int = 2,
        articlesCount: Int = 5,
        crossMatchCount: Int = 1,
    ): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            INSERT INTO digest_diff_log(
                id, category_id, digest_date, legacy_summary, new_summary, new_mode,
                sections_count, articles_count, cross_match_count
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            categoryId,
            java.sql.Date.valueOf(digestDate),
            legacySummary,
            newSummary,
            newMode,
            sectionsCount,
            articlesCount,
            crossMatchCount,
        )
        return id
    }
}
