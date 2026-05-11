package com.ohmyclipping.admin

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.Language
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssItemStore
import com.ohmyclipping.store.RssSourceStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * `/policy-status` + `/score-distribution` 대시보드 엔드포인트 통합 테스트.
 *
 * 기존 [ReviewQueueAdminControllerTest] 와 동일하게 `@SpringBootTest` + `WebTestClient` +
 * Bearer `test-admin-token` 패턴을 따른다 (spec 의 MockMvc 제안보다 repo convention 우선).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ReviewQueueAdminControllerDashboardTest {

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var sourceStore: RssSourceStore
    @Autowired lateinit var itemStore: RssItemStore
    @Autowired lateinit var summaryStore: BatchSummaryStore

    private lateinit var categoryId: String

    @BeforeEach
    fun setUp() {
        val category = categoryStore.save(Category(id = "", name = "DashboardCat-${System.nanoTime()}"))
        categoryId = category.id

        val source = sourceStore.save(
            RssSource(
                id = "",
                name = "DashboardSource",
                url = "https://93.184.216.41/dash",
                categoryId = categoryId,
            ),
        )

        // 점수 분포 집계가 0 건이어도 10 버킷을 반환하는지 확인하기 위해 최소 1개 summary 만 시드.
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "대시보드 테스트 아이템",
                content = "본문",
                link = "https://example.com/dash-${System.nanoTime()}",
                language = Language.KOREAN,
                categoryId = categoryId,
                rssSourceId = source.id,
            ),
        )
        summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = item.title,
                summary = "대시보드용 summary",
                keywords = listOf("dashboard"),
                importanceScore = 0.55f,
                sourceLink = item.link,
                categoryId = categoryId,
                rssItemId = item.id,
            ),
        )
    }

    @Test
    fun `policy-status 는 200 과 카테고리 리스트를 반환한다`() {
        adminClient().get().uri("/api/admin/review-items/policy-status")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.categories").isArray
            .jsonPath("$.generatedAt").isNotEmpty
    }

    @Test
    fun `score-distribution 은 10 버킷을 반환한다`() {
        adminClient().get().uri("/api/admin/review-items/score-distribution?days=7")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.buckets").isArray
            .jsonPath("$.buckets.length()").isEqualTo(10)
            .jsonPath("$.totalCount").isNumber
            .jsonPath("$.medianScore").isNumber
            .jsonPath("$.meanScore").isNumber
    }

    @Test
    fun `score-distribution 의 days 파라미터는 내부 clamp 되어 400 을 반환하지 않는다`() {
        // days=1000 도 서비스에서 90 으로 clamp — 응답은 200 OK 여야 한다.
        adminClient().get().uri("/api/admin/review-items/score-distribution?days=1000")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.buckets.length()").isEqualTo(10)
    }

    @Test
    fun `score-distribution 은 categoryId query param 을 전달받아 동작한다`() {
        adminClient().get()
            .uri("/api/admin/review-items/score-distribution?categoryId=$categoryId&days=7")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.buckets.length()").isEqualTo(10)
            // 시드한 summary(importance=0.55) 가 5번 버킷(0.5-0.6) 에 들어가므로 total 은 >= 1.
            .jsonPath("$.totalCount").value<Int> { count ->
                assert(count >= 1) { "expected at least 1 summary in category scope, got $count" }
            }
    }

    @Test
    fun `인증 헤더 없이 policy-status 를 호출하면 401`() {
        webClient.get().uri("/api/admin/review-items/policy-status")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `인증 헤더 없이 score-distribution 을 호출하면 401`() {
        webClient.get().uri("/api/admin/review-items/score-distribution")
            .exchange()
            .expectStatus().isUnauthorized
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
