package com.ohmyclipping.admin

import com.ohmyclipping.model.Category
import com.ohmyclipping.store.CategoryStore
import org.junit.jupiter.api.BeforeEach
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
class CategoryRuleAdminControllerTest {

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var categoryStore: CategoryStore

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        val category = categoryStore.save(Category(id = "", name = "RuleTestCat-${System.nanoTime()}"))
        categoryId = category.id
    }

    @Test
    fun `GET category rule should return defaults when no rule exists`() {
        adminClient().get().uri("/api/admin/category-rules/$categoryId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.categoryId").isEqualTo(categoryId)
            .jsonPath("$.includeKeywords").isArray
            .jsonPath("$.excludeKeywords").isArray
            .jsonPath("$.riskTags").isArray
            .jsonPath("$.excludeEventTypes").isArray
            .jsonPath("$.excludeEventTypes.length()").isEqualTo(0)
            .jsonPath("$.includeThreshold").isEqualTo(0.55)
            .jsonPath("$.reviewThreshold").isEqualTo(0.35)
            .jsonPath("$.uncertainToReview").isEqualTo(true)
            .jsonPath("$.autoExcludeEnabled").isEqualTo(true)
            .jsonPath("$.revision").isEqualTo(0)
            .jsonPath("$.version").doesNotExist()
    }

    @Test
    fun `PUT category rule should save and increase revision`() {
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "includeKeywords": ["학습", "리더십"],
                  "excludeKeywords": ["채용"],
                  "riskTags": ["노사", "소송"],
                  "includeThreshold": 0.70,
                  "reviewThreshold": 0.45,
                  "uncertainToReview": true,
                  "autoExcludeEnabled": true,
                  "updatedBy": "admin"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.includeKeywords[0]").isEqualTo("학습")
            .jsonPath("$.excludeKeywords[0]").isEqualTo("채용")
            .jsonPath("$.riskTags[0]").isEqualTo("노사")
            .jsonPath("$.includeThreshold").isEqualTo(0.7)
            .jsonPath("$.reviewThreshold").isEqualTo(0.45)
            .jsonPath("$.revision").isEqualTo(1)
            .jsonPath("$.version").doesNotExist()
            .jsonPath("$.updatedBy").isEqualTo("admin")

        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reviewThreshold":0.5,"updatedBy":"admin-2"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.revision").isEqualTo(2)
            .jsonPath("$.version").doesNotExist()
            .jsonPath("$.reviewThreshold").isEqualTo(0.5)
            .jsonPath("$.updatedBy").isEqualTo("admin-2")
    }

    @Test
    fun `PUT category rule should reject invalid thresholds`() {
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"includeThreshold":0.4,"reviewThreshold":0.6}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_INPUT")
            .jsonPath("$.traceId").exists()
    }

    @Test
    fun `GET category rule should return 404 when category does not exist`() {
        adminClient().get().uri("/api/admin/category-rules/not-found")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `PUT category rule should persist autoApproveThreshold and expose it on GET`() {
        // 자동 승인 임계값이 저장되고 GET으로 읽을 때 그대로 반환되는지 검증한다
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"autoApproveThreshold":0.85,"updatedBy":"admin"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.autoApproveThreshold").isEqualTo(0.85)

        adminClient().get().uri("/api/admin/category-rules/$categoryId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.autoApproveThreshold").isEqualTo(0.85)
    }

    @Test
    fun `PUT category rule with autoApproveThreshold out of range should return 400`() {
        // 임계값 범위는 [0,1]. 1.2는 거부되어야 한다
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"autoApproveThreshold":1.2}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `PUT category rule should persist excludeEventTypes and expose it on GET`() {
        // PR-3-lite: event_type 블랙리스트가 PUT 으로 저장되고 GET 응답에 그대로 나와야 한다.
        // 정규화: 소문자/공백/중복이 들어와도 서비스에서 uppercase + trim + distinct 로 수렴.
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "excludeEventTypes": ["other", " OTHER ", "FUNDING", "", "funding"],
                  "updatedBy": "admin"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.excludeEventTypes").isArray
            .jsonPath("$.excludeEventTypes[0]").isEqualTo("OTHER")
            .jsonPath("$.excludeEventTypes[1]").isEqualTo("FUNDING")
            .jsonPath("$.excludeEventTypes.length()").isEqualTo(2)

        // GET 도 같은 정규화된 값을 돌려줘야 한다 (저장이 실제로 됐는지 확인).
        adminClient().get().uri("/api/admin/category-rules/$categoryId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.excludeEventTypes[0]").isEqualTo("OTHER")
            .jsonPath("$.excludeEventTypes[1]").isEqualTo("FUNDING")
            .jsonPath("$.excludeEventTypes.length()").isEqualTo(2)
    }

    @Test
    fun `PUT category rule with null excludeEventTypes should preserve existing value`() {
        // 먼저 값을 세팅.
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"excludeEventTypes":["OTHER"],"updatedBy":"admin"}""")
            .exchange()
            .expectStatus().isOk

        // 이후 다른 필드만 수정하면서 excludeEventTypes 는 생략 — 기존 값이 유지돼야 한다.
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reviewThreshold":0.4,"updatedBy":"admin-2"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.excludeEventTypes[0]").isEqualTo("OTHER")
            .jsonPath("$.excludeEventTypes.length()").isEqualTo(1)
    }

    @Test
    fun `PUT category rule with empty excludeEventTypes should disable the rule`() {
        // 값 세팅.
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"excludeEventTypes":["OTHER","FUNDING"]}""")
            .exchange()
            .expectStatus().isOk

        // 명시적 빈 배열 → 룰 비활성화.
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"excludeEventTypes":[]}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.excludeEventTypes").isArray
            .jsonPath("$.excludeEventTypes.length()").isEqualTo(0)
    }

    @Test
    fun `PUT category rule with clearAutoApproveThreshold should reset value to null`() {
        // 먼저 값을 설정한 뒤 clear 플래그로 해제한다 — GET 시 null이 반환되어야 한다
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"autoApproveThreshold":0.75}""")
            .exchange()
            .expectStatus().isOk

        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"clearAutoApproveThreshold":true}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.autoApproveThreshold").doesNotExist()
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
