package com.ohmyclipping.admin

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.CategoryRule
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.model.RuntimeSetting
import com.ohmyclipping.service.ReviewPolicyRuleEvaluator
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryRuleStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssItemStore
import com.ohmyclipping.store.RuntimeSettingStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * POST `/api/admin/category-rules/{categoryId}/dry-run` 통합 테스트.
 *
 * [com.ohmyclipping.service.AdminReviewQueueService.dryRunRule] 가 HTTP 경로를 통해
 * 정상 호출되고, 응답 shape 이 `RuleDryRunResult` 와 일치하는지 검증한다. 서비스 단위 레벨의
 * 집계 로직은 `AdminReviewQueueServiceDryRunTest` 에서 mock 으로 상세 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class CategoryRuleDryRunControllerTest {

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var categoryRuleStore: CategoryRuleStore
    @Autowired lateinit var itemStore: RssItemStore
    @Autowired lateinit var summaryStore: BatchSummaryStore
    @Autowired lateinit var runtimeSettingStore: RuntimeSettingStore

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        // 카테고리 + 기본 룰 생성
        val category = categoryStore.save(Category(id = "", name = "DryRunCat-${System.nanoTime()}"))
        categoryId = category.id
        categoryRuleStore.upsert(
            CategoryRule(
                categoryId = categoryId,
                excludeEventTypes = emptyList(),
            )
        )
        // zero_signal 기본값 on 보정 (다른 테스트가 off 로 남겨놓을 가능성 차단)
        runtimeSettingStore.save(
            RuntimeSetting(
                key = ReviewPolicyRuleEvaluator.ZERO_SIGNAL_KEY,
                value = "true",
            )
        )
    }

    private fun createSummary(title: String, eventType: String?, importanceScore: Float = 0.7f): BatchSummary {
        val item = itemStore.save(
            RssItem(
                id = "",
                title = title,
                content = title,
                link = "https://example.com/dry-${System.nanoTime()}",
                categoryId = categoryId,
            )
        )
        return summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = title,
                summary = title,
                sourceLink = item.link,
                categoryId = categoryId,
                rssItemId = item.id,
                eventType = eventType,
                importanceScore = importanceScore,
            )
        )
    }

    @Test
    fun `POST dry-run returns aggregated counts and sample summaries`() {
        // Given: 3 건 — 1 건 OPINION 은 제안된 blacklist 로 걸리고 나머지는 PassThrough
        createSummary("의견 기사", eventType = "OPINION")
        createSummary("일반 뉴스", eventType = "NEWS")
        createSummary("다른 일반 뉴스", eventType = "NEWS")

        adminClient().post().uri("/api/admin/category-rules/$categoryId/dry-run")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"excludeEventTypes":["OPINION"]}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.analyzedCount").isEqualTo(3)
            .jsonPath("$.wouldAutoExclude").isEqualTo(1)
            .jsonPath("$.wouldStayUnchanged").isEqualTo(2)
            .jsonPath("$.samples").isArray
            .jsonPath("$.samples[0].reason").isEqualTo("event_type_blacklist")
            .jsonPath("$.samples[0].eventType").isEqualTo("OPINION")
            .jsonPath("$.samples[0].title").isEqualTo("의견 기사")
    }

    @Test
    fun `POST dry-run with empty excludeEventTypes returns zero auto-exclude`() {
        // 룰 비활성 시뮬레이션 — 아무것도 걸리지 않아야 한다
        createSummary("의견 기사", eventType = "OPINION", importanceScore = 0.9f)

        adminClient().post().uri("/api/admin/category-rules/$categoryId/dry-run")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"excludeEventTypes":[]}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.wouldAutoExclude").isEqualTo(0)
            .jsonPath("$.samples").isEmpty
    }

    @Test
    fun `POST dry-run returns 404 when category does not exist`() {
        adminClient().post().uri("/api/admin/category-rules/not-a-real-category/dry-run")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"excludeEventTypes":["OPINION"]}""")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `POST dry-run returns 400 when days is out of range`() {
        adminClient().post().uri("/api/admin/category-rules/$categoryId/dry-run")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"excludeEventTypes":["OPINION"],"days":200}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_INPUT")
    }

    @Test
    fun `POST dry-run without Bearer token returns 401`() {
        // 인증 헤더 없이 요청 — security filter 가 401 로 차단해야 한다
        webClient.post().uri("/api/admin/category-rules/$categoryId/dry-run")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"excludeEventTypes":["OPINION"]}""")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `POST dry-run respects maxSamples override`() {
        // 4 건 모두 OPINION 으로 생성 → 제안 blacklist 에 걸리지만 maxSamples=2 로 샘플은 2 건만
        repeat(4) { idx -> createSummary("의견 $idx", eventType = "OPINION") }

        adminClient().post().uri("/api/admin/category-rules/$categoryId/dry-run")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"excludeEventTypes":["OPINION"],"maxSamples":2}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.wouldAutoExclude").isEqualTo(4)
            .jsonPath("$.samples.length()").isEqualTo(2)
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
