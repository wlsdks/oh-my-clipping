package com.clipping.mcpserver.admin

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class CategoryAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Test
    fun `GET categories should return paginated response`() {
        adminClient().get().uri("/api/admin/categories")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.totalCount").isNumber
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(30)
    }

    @Test
    fun `GET categories with search should filter results`() {
        // 먼저 카테고리를 생성한다
        adminClient().post().uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"SearchableCategory"}""")
            .exchange()
            .expectStatus().isCreated

        adminClient().get().uri("/api/admin/categories?search=Searchable")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content").isArray
            .jsonPath("$.totalCount").isNumber
    }

    @Test
    fun `GET categories with pagination params should respect page and size`() {
        adminClient().get().uri("/api/admin/categories?page=0&size=5")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(5)
    }

    @Test
    fun `GET categories should normalize negative page to first page`() {
        adminClient().get().uri("/api/admin/categories?page=-1&size=5")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(5)
    }

    @Test
    fun `POST create and GET category`() {
        val result = adminClient().post().uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Test Category","description":"Test desc","maxItems":3}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("Test Category")
            .jsonPath("$.maxItems").isEqualTo(3)
            .returnResult()

        val body = String(result.responseBody!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().get().uri("/api/admin/categories/$id")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Test Category")
    }

    @Test
    fun `PUT should update category`() {
        // Create
        val result = adminClient().post().uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"UpdateMe"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        // Update
        adminClient().put().uri("/api/admin/categories/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Updated Name","maxItems":5}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Updated Name")
            .jsonPath("$.maxItems").isEqualTo(5)
    }

    @Test
    fun `PUT should clear channel and persona when blank values are provided`() {
        val created = adminClient().post().uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"MappingCategory","slackChannelId":"C123MAP","personaId":"persona-A"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val createdBody = String(created.responseBodyContent!!)
        val id = createdBody.substringAfter("\"id\":\"").substringBefore("\"")

        val updated = adminClient().put().uri("/api/admin/categories/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"slackChannelId":"","personaId":""}""")
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)

        val updatedBody = String(updated.responseBodyContent!!)
        assertTrue(updatedBody.contains(""""slackChannelId":null"""))
        assertTrue(updatedBody.contains(""""personaId":null"""))
    }

    @Test
    fun `DELETE should remove category`() {
        val result = adminClient().post().uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"ToDelete"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().delete().uri("/api/admin/categories/$id")
            .exchange()
            .expectStatus().isNoContent

        adminClient().get().uri("/api/admin/categories/$id")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `GET nonexistent category should return 404`() {
        adminClient().get().uri("/api/admin/categories/nonexistent")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `admin endpoint should require bearer token`() {
        webClient.get().uri("/api/admin/categories")
            .exchange()
            .expectStatus().isUnauthorized
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
