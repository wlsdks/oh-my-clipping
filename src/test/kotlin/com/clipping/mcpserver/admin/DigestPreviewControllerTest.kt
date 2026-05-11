package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.digest.*

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * GET `/api/admin/categories/{categoryId}/digest-mode-preview` 및
 * POST `/api/admin/categories/{categoryId}/digest-dry-run` 통합 테스트.
 *
 * 응답 shape 과 인증 가드를 검증한다. 서비스 레벨 로직은 DigestPreviewService 단위 테스트에서 커버한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class DigestPreviewControllerTest {

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var categoryRuleStore: CategoryRuleStore

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    private fun seedCategory(): String {
        val category = categoryStore.save(
            Category(id = "", name = "dp-test-${System.nanoTime()}")
        )
        return category.id
    }

    @Test
    fun `GET digest-mode-preview - 키워드와 조직 없는 카테고리는 null mode 반환`() {
        val categoryId = seedCategory()

        adminClient().get()
            .uri("/api/admin/categories/$categoryId/digest-mode-preview")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentMode").doesNotExist()
            .jsonPath("$.keywordCount").isEqualTo(0)
            .jsonPath("$.orgCount").isEqualTo(0)
            .jsonPath("$.changed").isEqualTo(false)
    }

    @Test
    fun `GET digest-mode-preview - 키워드 있는 카테고리는 TOPIC_ONLY 반환`() {
        val categoryId = seedCategory()
        // 포함 키워드 1개 세팅
        categoryRuleStore.upsert(
            CategoryRule(
                categoryId = categoryId,
                includeKeywords = listOf("테스트키워드"),
                excludeEventTypes = emptyList(),
            )
        )

        adminClient().get()
            .uri("/api/admin/categories/$categoryId/digest-mode-preview")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.currentMode").isEqualTo("TOPIC_ONLY")
            .jsonPath("$.keywordCount").isEqualTo(1)
            .jsonPath("$.orgCount").isEqualTo(0)
    }

    @Test
    fun `POST digest-dry-run - 설정 없는 카테고리는 EMPTY mode와 빈 blocks 반환`() {
        val categoryId = seedCategory()

        adminClient().post()
            .uri("/api/admin/categories/$categoryId/digest-dry-run")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.mode").isEqualTo("EMPTY")
            .jsonPath("$.blocks").isEqualTo("[]")
    }

    @Test
    fun `POST digest-dry-run - 키워드 있는 카테고리는 mode와 blocks 반환`() {
        val categoryId = seedCategory()
        categoryRuleStore.upsert(
            CategoryRule(
                categoryId = categoryId,
                includeKeywords = listOf("AI"),
                excludeEventTypes = emptyList(),
            )
        )

        adminClient().post()
            .uri("/api/admin/categories/$categoryId/digest-dry-run")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.mode").isEqualTo("TOPIC_ONLY")
            .jsonPath("$.blocks").exists()
    }

    @Test
    fun `인증 없이 접근하면 401 반환`() {
        val categoryId = seedCategory()

        webClient.get()
            .uri("/api/admin/categories/$categoryId/digest-mode-preview")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
