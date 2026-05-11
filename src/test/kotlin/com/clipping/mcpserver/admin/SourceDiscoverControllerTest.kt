package com.clipping.mcpserver.admin

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class SourceDiscoverControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    private fun adminClient() = webClient.mutate()
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
        .build()

    @Test
    fun `discover로 매핑 테이블을 검색한다`() {
        adminClient().post()
            .uri("/api/admin/sources/discover")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("query" to "Example Daily"))
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `discover로 빈 쿼리를 보내도 200을 반환한다`() {
        adminClient().post()
            .uri("/api/admin/sources/discover")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("query" to ""))
            .exchange()
            .expectStatus().isOk
    }
}
