package com.clipping.mcpserver.admin

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.store.CategoryStore
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
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class SourceAdminControllerTest {

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var jdbc: JdbcTemplate

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        val cat = categoryStore.save(Category(id = "", name = "SourceTestCat-${System.nanoTime()}"))
        categoryId = cat.id
    }

    @Test
    fun `POST create source and GET`() {
        val result = adminClient().post().uri("/api/admin/sources")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Test RSS","url":"https://93.184.216.34/rss","categoryId":"$categoryId"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("Test RSS")
            .jsonPath("$.crawlApproved").isEqualTo(false)
            .jsonPath("$.legalBasis").isEqualTo("QUOTATION_ONLY")
            .jsonPath("$.summaryAllowed").isEqualTo(true)
            .jsonPath("$.fulltextAllowed").isEqualTo(false)
            .jsonPath("$.verificationStatus").isEqualTo("PENDING")
            .returnResult()

        val body = String(result.responseBody!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().get().uri("/api/admin/sources/$id")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `POST approve source`() {
        val result = adminClient().post().uri("/api/admin/sources")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Approve Me","url":"https://93.184.216.35/rss","categoryId":"$categoryId"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().post().uri("/api/admin/sources/$id/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"approved":true,"approvedBy":"admin","legalBasis":"OPEN_LICENSE","summaryAllowed":true}
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.crawlApproved").isEqualTo(true)
            .jsonPath("$.approvedBy").isEqualTo("admin")
            .jsonPath("$.legalBasis").isEqualTo("OPEN_LICENSE")
            .jsonPath("$.fulltextAllowed").isEqualTo(false)
    }

    @Test
    fun `POST approve source should reject fulltext request`() {
        val result = adminClient().post().uri("/api/admin/sources")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Approve PolicyFail","url":"https://93.184.216.38/rss","categoryId":"$categoryId"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().post().uri("/api/admin/sources/$id/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"approved":true,"approvedBy":"admin","legalBasis":"OPEN_LICENSE","summaryAllowed":true,"fulltextAllowed":true}
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `GET sources filtered by category should return paginated response`() {
        adminClient().post().uri("/api/admin/sources")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Cat Source","url":"https://93.184.216.36/rss","categoryId":"$categoryId"}""")
            .exchange()
            .expectStatus().isCreated

        adminClient().get().uri("/api/admin/sources?categoryId=$categoryId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.totalCount").isNumber
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(30)
    }

    @Test
    fun `GET sources with search should filter results`() {
        adminClient().post().uri("/api/admin/sources")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"SearchRSS","url":"https://93.184.216.50/rss","categoryId":"$categoryId"}""")
            .exchange()
            .expectStatus().isCreated

        adminClient().get().uri("/api/admin/sources?search=SearchRSS")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.totalCount").isNumber
    }

    @Test
    fun `GET sources with pagination params should respect page and size`() {
        adminClient().get().uri("/api/admin/sources?page=0&size=5")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(5)
    }

    @Test
    fun `GET sources should normalize negative page to first page`() {
        adminClient().get().uri("/api/admin/sources?page=-1&size=5")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(5)
    }

    @Test
    fun `DELETE source`() {
        val result = adminClient().post().uri("/api/admin/sources")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Delete Me","url":"https://93.184.216.37/rss","categoryId":"$categoryId"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().delete().uri("/api/admin/sources/$id")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `DELETE source with collected items returns conflict instead of server error`() {
        val result = adminClient().post().uri("/api/admin/sources")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Delete Protected","url":"https://93.184.216.41/rss","categoryId":"$categoryId"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")
        insertRssItem(sourceId = id)

        adminClient().delete().uri("/api/admin/sources/$id")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("CONFLICT")
            .jsonPath("$.error").isEqualTo("CONFLICT")
            .jsonPath("$.message").isEqualTo("연결된 데이터가 있어 삭제할 수 없어요. 관련 데이터를 먼저 정리해 주세요.")
            .jsonPath("$.traceId").exists()
    }

    @Test
    fun `POST create source should reject private network URL`() {
        adminClient().post().uri("/api/admin/sources")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Blocked","url":"http://127.0.0.1/rss","categoryId":"$categoryId"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_INPUT")
            .jsonPath("$.error").isEqualTo("INVALID_INPUT")
            .jsonPath("$.traceId").exists()
    }

    @Test
    fun `POST create source should reject fulltext request`() {
        adminClient().post().uri("/api/admin/sources")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"name":"PolicyFail","url":"https://93.184.216.39/rss","categoryId":"$categoryId","legalBasis":"OPEN_LICENSE","fulltextAllowed":true}
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_INPUT")
            .jsonPath("$.error").isEqualTo("INVALID_INPUT")
            .jsonPath("$.traceId").exists()
    }

    @Test
    fun `GET list with invalid complianceStatus returns 400`() {
        adminClient().get().uri("/api/admin/sources?complianceStatus=GARBAGE")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_INPUT")
    }

    @Test
    fun `GET list without complianceStatus returns all sources`() {
        adminClient().get().uri("/api/admin/sources")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.totalCount").isNumber
    }

    @Test
    fun `GET compliance-summary returns attention count`() {
        adminClient().get().uri("/api/admin/sources/compliance-summary")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.attentionCount").isNumber
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    private fun insertRssItem(sourceId: String) {
        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        jdbc.update(
            """
            INSERT INTO rss_items
                (id, title, content, link, language, is_processed, category_id, rss_source_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            "Protected source item",
            "Test content",
            "https://example.com/protected-source/$id",
            "FOREIGN",
            false,
            categoryId,
            sourceId,
            Timestamp.from(now)
        )
    }
}
