package com.clipping.mcpserver.admin

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
 * CategoryRuleBundleController HTTP 계약 + atomic 저장 통합 테스트.
 *
 * 어드민 API 로 카테고리·조직을 생성하고, PUT rule-bundle 호출 후
 * JDBC 로 DB 상태를 직접 검증한다.
 * @Transactional 없이 explicit @AfterEach cleanup 으로 격리.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class CategoryRuleBundleControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private var testCatId: String = ""
    private var testOrg1Id: String = ""
    private var testOrg2Id: String = ""

    @BeforeEach
    fun setup() {
        // 카테고리 생성
        testCatId = createCategoryViaApi()
        // 조직 2개 생성
        testOrg1Id = createOrgViaApi("BundleOrg1-${System.nanoTime()}")
        testOrg2Id = createOrgViaApi("BundleOrg2-${System.nanoTime()}")
    }

    @AfterEach
    fun cleanup() {
        if (testCatId.isNotEmpty()) {
            // syncSourcesForCategory 가 rss_sources 행을 생성할 수 있으므로 먼저 제거.
            jdbc.update("DELETE FROM rss_sources WHERE category_id = ?", testCatId)
            jdbc.update("DELETE FROM category_organizations WHERE category_id = ?", testCatId)
            jdbc.update("DELETE FROM category_feature_flags WHERE category_id = ?", testCatId)
            jdbc.update("DELETE FROM clipping_category_rules WHERE category_id = ?", testCatId)
        }
        if (testOrg1Id.isNotEmpty()) jdbc.update("DELETE FROM organizations WHERE id = ?", testOrg1Id)
        if (testOrg2Id.isNotEmpty()) jdbc.update("DELETE FROM organizations WHERE id = ?", testOrg2Id)
        if (testCatId.isNotEmpty()) jdbc.update("DELETE FROM batch_categories WHERE id = ?", testCatId)
    }

    @Test
    fun `PUT rule-bundle - 200 응답 및 includeKeywords, organizationIds, accountBasedDigestEnabled 가 원자적으로 저장된다`() {
        val body = """
            {
              "excludeEventTypes": [],
              "includeKeywords": ["AI", "RAG"],
              "organizationIds": ["$testOrg1Id", "$testOrg2Id"],
              "accountBasedDigestEnabled": true,
              "shadowModeEnabled": false
            }
        """.trimIndent()

        // when
        adminClient().put().uri("/api/admin/categories/$testCatId/rule-bundle")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk

        // then — includeKeywords 가 저장되었는지 JDBC 로 검증
        val includeKeywordsJson: String? = jdbc.queryForObject(
            "SELECT include_keywords FROM clipping_category_rules WHERE category_id = ?",
            String::class.java,
            testCatId
        )
        // JSON 배열에 두 키워드가 모두 포함되어야 한다
        assert(includeKeywordsJson != null) { "category_rules 행이 없다" }
        assert(includeKeywordsJson!!.contains("AI")) { "include_keywords 에 AI 가 없다: $includeKeywordsJson" }
        assert(includeKeywordsJson.contains("RAG")) { "include_keywords 에 RAG 가 없다: $includeKeywordsJson" }

        // then — category_organizations 가 정확히 2개 저장되었는지 검증
        val orgIds: List<String> = jdbc.queryForList(
            "SELECT organization_id FROM category_organizations WHERE category_id = ? ORDER BY organization_id",
            String::class.java,
            testCatId
        )
        assert(orgIds.size == 2) { "category_organizations 수가 2 가 아니다: $orgIds" }
        assert(orgIds.containsAll(listOf(testOrg1Id, testOrg2Id))) {
            "category_organizations 에 기대 조직이 없다: $orgIds"
        }

        // then — account_based_digest_enabled = true 인지 검증
        val flagEnabled: Boolean? = jdbc.queryForObject(
            "SELECT account_based_digest_enabled FROM category_feature_flags WHERE category_id = ?",
            Boolean::class.java,
            testCatId
        )
        assert(flagEnabled == true) { "account_based_digest_enabled 가 true 가 아니다: $flagEnabled" }

        // then — audit_log 에 RULE_BUNDLE_UPDATE 행이 생성되었는지 검증 (delta-based)
        val auditCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE action = 'RULE_BUNDLE_UPDATE' AND target_id = ?",
            Int::class.java,
            testCatId
        ) ?: 0
        assert(auditCount >= 1) { "audit_log 에 RULE_BUNDLE_UPDATE 행이 없다" }
    }

    @Test
    fun `PUT rule-bundle - 존재하지 않는 조직 ID 를 포함하면 400 응답`() {
        val body = """
            {
              "excludeEventTypes": [],
              "includeKeywords": [],
              "organizationIds": ["non-existent-org-id"],
              "accountBasedDigestEnabled": false,
              "shadowModeEnabled": false
            }
        """.trimIndent()

        adminClient().put().uri("/api/admin/categories/$testCatId/rule-bundle")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `PUT rule-bundle - 빈 organizationIds 로 조직 링크 전체 해제된다`() {
        // given — 먼저 조직 2개 링크
        adminClient().put().uri("/api/admin/categories/$testCatId/rule-bundle")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "excludeEventTypes": [],
                  "includeKeywords": [],
                  "organizationIds": ["$testOrg1Id", "$testOrg2Id"],
                  "accountBasedDigestEnabled": false,
                  "shadowModeEnabled": false
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk

        // when — 빈 목록으로 재호출
        adminClient().put().uri("/api/admin/categories/$testCatId/rule-bundle")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "excludeEventTypes": [],
                  "includeKeywords": [],
                  "organizationIds": [],
                  "accountBasedDigestEnabled": false,
                  "shadowModeEnabled": false
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk

        // then — 조직 링크가 0 개여야 한다
        val orgCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM category_organizations WHERE category_id = ?",
            Int::class.java,
            testCatId
        ) ?: 0
        assert(orgCount == 0) { "조직 링크가 남아있다: $orgCount" }
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    private fun createCategoryViaApi(): String {
        val result = adminClient().post().uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"BundleTestCat-${System.nanoTime()}"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)
        val body = String(result.responseBodyContent!!)
        return body.substringAfter("\"id\":\"").substringBefore("\"")
    }

    private fun createOrgViaApi(name: String): String {
        val result = adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"$name","type":"COMPETITOR"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)
        val body = String(result.responseBodyContent!!)
        return body.substringAfter("\"id\":\"").substringBefore("\"")
    }
}
