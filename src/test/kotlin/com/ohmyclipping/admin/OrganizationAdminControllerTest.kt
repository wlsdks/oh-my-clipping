package com.ohmyclipping.admin

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
 * OrganizationAdminController HTTP 계약 검증.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class OrganizationAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun cleanup() {
        // 테스트 간 상호 오염 방지.
        jdbc.update("DELETE FROM category_organizations")
        jdbc.update("DELETE FROM organizations")
    }

    @Test
    fun `POST organizations - 정상 생성 시 201 응답`() {
        adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Acme","type":"COMPETITOR","domain":"acme.com"}""")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("Acme")
            .jsonPath("$.type").isEqualTo("COMPETITOR")
            .jsonPath("$.domain").isEqualTo("acme.com")
            .jsonPath("$.tenantId").isEqualTo("default")
    }

    @Test
    fun `POST organizations - 동일 이름 중복 시 409 응답`() {
        adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"DupCo","type":"OTHER"}""")
            .exchange()
            .expectStatus().isCreated

        adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"DupCo","type":"CUSTOMER"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `POST organizations - 잘못된 type 이면 400 응답`() {
        adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Foo","type":"INVESTOR"}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `GET organizations - type 필터 동작`() {
        adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"CompA","type":"COMPETITOR"}""")
            .exchange().expectStatus().isCreated
        adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"CustB","type":"CUSTOMER"}""")
            .exchange().expectStatus().isCreated

        adminClient().get().uri("/api/admin/organizations?type=CUSTOMER")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalCount").isEqualTo(1)
            .jsonPath("$.content[0].name").isEqualTo("CustB")
    }

    @Test
    fun `PATCH organizations - 부분 수정 동작`() {
        val result = adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"PatchMe","type":"PARTNER","domain":"patchme.com"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().patch().uri("/api/admin/organizations/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"CUSTOMER","domain":""}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.type").isEqualTo("CUSTOMER")
            .jsonPath("$.domain").isEqualTo(null)
            .jsonPath("$.name").isEqualTo("PatchMe")
    }

    @Test
    fun `DELETE organizations - 삭제 후 GET 이 404`() {
        val result = adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"ToDelete","type":"OTHER"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)

        val body = String(result.responseBodyContent!!)
        val id = body.substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().delete().uri("/api/admin/organizations/$id")
            .exchange()
            .expectStatus().isNoContent

        adminClient().get().uri("/api/admin/organizations/$id")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `GET organizations without auth - 401`() {
        webClient.get().uri("/api/admin/organizations")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `PUT categories organizations - 링크 교체 동작`() {
        // 카테고리 생성
        val catResult = adminClient().post().uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"LinkCat"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)
        val catId = String(catResult.responseBodyContent!!)
            .substringAfter("\"id\":\"").substringBefore("\"")

        // 조직 2개 생성
        val orgA = adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"LinkOrgA","type":"COMPETITOR"}""")
            .exchange().expectStatus().isCreated
            .returnResult(String::class.java)
        val orgAId = String(orgA.responseBodyContent!!)
            .substringAfter("\"id\":\"").substringBefore("\"")
        val orgB = adminClient().post().uri("/api/admin/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"LinkOrgB","type":"PARTNER"}""")
            .exchange().expectStatus().isCreated
            .returnResult(String::class.java)
        val orgBId = String(orgB.responseBodyContent!!)
            .substringAfter("\"id\":\"").substringBefore("\"")

        // 링크 교체
        adminClient().put().uri("/api/admin/categories/$catId/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"organizationIds":["$orgAId","$orgBId"]}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalCount").isEqualTo(2)

        // 조회 확인
        adminClient().get().uri("/api/admin/categories/$catId/organizations")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalCount").isEqualTo(2)

        // 빈 리스트로 해제
        adminClient().put().uri("/api/admin/categories/$catId/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"organizationIds":[]}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalCount").isEqualTo(0)
    }

    @Test
    fun `PUT categories organizations - 존재하지 않는 orgId 섞이면 400`() {
        val catResult = adminClient().post().uri("/api/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"InvalidLinkCat"}""")
            .exchange()
            .expectStatus().isCreated
            .returnResult(String::class.java)
        val catId = String(catResult.responseBodyContent!!)
            .substringAfter("\"id\":\"").substringBefore("\"")

        adminClient().put().uri("/api/admin/categories/$catId/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"organizationIds":["nonexistent-id"]}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
