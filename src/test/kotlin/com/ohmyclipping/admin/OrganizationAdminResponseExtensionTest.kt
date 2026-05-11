package com.ohmyclipping.admin

import org.junit.jupiter.api.AfterEach
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

/**
 * OrganizationResponse 에 추가된 stockCode / aliases / origin / usageCount 필드가
 * GET /api/admin/organizations 응답에 포함되는지 확인하는 통합 테스트.
 *
 * - 어드민 API 로 조직·카테고리를 생성하고, JDBC 로 stockCode/aliases/origin 을 보강 후
 *   category 링크를 삽입하여 usageCount=1 상태를 만든다.
 * - @Transactional 없이 explicit cleanup 으로 격리 (@AfterEach + @BeforeEach).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class OrganizationAdminResponseExtensionTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private var testOrgId: String = ""
    private var testCatId: String = ""

    @BeforeEach
    fun setup() {
        // 조직 생성 — API 경유로 ID 를 확보한다.
        testOrgId = createOrgViaApi()
        // JDBC 로 stockCode / aliases / origin 을 보강한다.
        jdbc.update(
            "UPDATE organizations SET stock_code = ?, aliases = ?, origin = ? WHERE id = ?",
            "999930", """["MegaCorp"]""", "admin_created", testOrgId
        )
        // 카테고리 생성 — API 경유로 ID 를 확보한다.
        testCatId = createCategoryViaApi()
        // 카테고리 ↔ 조직 링크 삽입.
        jdbc.update(
            """
            INSERT INTO category_organizations (category_id, organization_id, tenant_id, created_at)
            VALUES (?, ?, 'default', NOW())
            """.trimIndent(),
            testCatId, testOrgId
        )
    }

    @AfterEach
    fun cleanup() {
        if (testOrgId.isNotEmpty()) {
            jdbc.update("DELETE FROM category_organizations WHERE organization_id = ?", testOrgId)
        }
        if (testCatId.isNotEmpty()) {
            jdbc.update("DELETE FROM category_organizations WHERE category_id = ?", testCatId)
        }
        if (testOrgId.isNotEmpty()) {
            jdbc.update("DELETE FROM organizations WHERE id = ?", testOrgId)
        }
        if (testCatId.isNotEmpty()) {
            jdbc.update("DELETE FROM batch_categories WHERE id = ?", testCatId)
        }
    }

    @Test
    fun `GET organizations - stockCode, aliases, origin, usageCount 필드가 응답에 포함된다`() {
        adminClient().get().uri("/api/admin/organizations")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            // aliases 는 JSON 배열이어야 한다.
            .jsonPath("$.content[?(@.id == '$testOrgId')].aliases").isArray()
            // aliases 첫 번째 원소 값 검증.
            .jsonPath("$.content[?(@.id == '$testOrgId')].aliases[0]").isEqualTo("MegaCorp")
            // origin 필드가 존재하고 값이 맞는지 확인.
            .jsonPath("$.content[?(@.id == '$testOrgId')].origin").isEqualTo("admin_created")
            // stockCode 필드가 존재하고 값이 맞는지 확인.
            .jsonPath("$.content[?(@.id == '$testOrgId')].stockCode").isEqualTo("999930")
            // usageCount 는 카테고리 링크 1개 → 1.
            .jsonPath("$.content[?(@.id == '$testOrgId')].usageCount").isEqualTo(1)
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    /**
     * 어드민 API 로 조직을 생성하고 부여된 ID 를 반환한다.
     */
    private fun createOrgViaApi(): String {
        val result = adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"ExtTestOrg-${System.nanoTime()}","type":"COMPETITOR"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)
        val body = String(result.responseBodyContent!!)
        return body.substringAfter("\"id\":\"").substringBefore("\"")
    }

    /**
     * 어드민 API 로 카테고리를 생성하고 부여된 ID 를 반환한다.
     */
    private fun createCategoryViaApi(): String {
        val result = adminClient().post().uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"ExtTestCat-${System.nanoTime()}"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)
        val body = String(result.responseBodyContent!!)
        return body.substringAfter("\"id\":\"").substringBefore("\"")
    }
}
