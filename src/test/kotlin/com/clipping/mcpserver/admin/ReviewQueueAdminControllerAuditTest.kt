package com.clipping.mcpserver.admin

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.model.Language
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.service.AdminReviewQueueService
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.ReviewItemAuditStore
import com.clipping.mcpserver.store.ReviewItemDecisionStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.RssSourceStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * `/api/admin/review-items/auto-excluded` + `/{summaryId}/restore-to-review` 통합 테스트.
 *
 * 픽스처는 `AdminReviewQueueService.ensurePolicyReviewDecisions` 경로를 통해 실제 룰 엔진
 * event_type_blacklist 를 발동시켜 `rule:event_type_blacklist` reason 의 policy-auto EXCLUDE 를
 * 만든다 — 이렇게 해야 `reviewed_by='policy-auto'` 필터 + reason 접두어 동작을 end-to-end 로 잠글 수 있다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ReviewQueueAdminControllerAuditTest {

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var categoryRuleStore: CategoryRuleStore
    @Autowired lateinit var sourceStore: RssSourceStore
    @Autowired lateinit var itemStore: RssItemStore
    @Autowired lateinit var summaryStore: BatchSummaryStore
    @Autowired lateinit var reviewItemDecisionStore: ReviewItemDecisionStore
    @Autowired lateinit var reviewItemAuditStore: ReviewItemAuditStore
    @Autowired lateinit var adminReviewQueueService: AdminReviewQueueService

    private lateinit var categoryId: String
    private lateinit var autoExcludedSummaryId: String

    @BeforeEach
    fun setUp() {
        // event_type_blacklist 룰이 걸린 카테고리 생성
        val category = categoryStore.save(Category(id = "", name = "AuditCat-${System.nanoTime()}"))
        categoryId = category.id
        categoryRuleStore.upsert(
            CategoryRule(
                categoryId = category.id,
                excludeEventTypes = listOf("OPINION"),
            )
        )

        // OPINION 이벤트 타입 summary 를 만든 뒤 ensurePolicyReviewDecisions 로 정책 자동 EXCLUDE 경로 발동
        val source = sourceStore.save(
            RssSource(
                id = "",
                name = "AuditSrc",
                url = "https://93.184.216.41/audit",
                categoryId = category.id,
            )
        )
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "OPINION 기반 자동 제외 대상",
                content = "본문",
                link = "https://example.com/audit-${System.nanoTime()}",
                language = Language.KOREAN,
                categoryId = category.id,
                rssSourceId = source.id,
            )
        )
        val summary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = item.title,
                summary = "OPINION 본문",
                keywords = listOf("audit"),
                importanceScore = 0.9f,
                sourceLink = item.link,
                categoryId = category.id,
                rssItemId = item.id,
                eventType = "OPINION",
            )
        )
        autoExcludedSummaryId = summary.id

        // 정책 자동 EXCLUDE 경로 발동 — reason = rule:event_type_blacklist, reviewedBy = policy-auto
        adminReviewQueueService.ensurePolicyReviewDecisions(listOf(summary))
    }

    @Test
    fun `GET auto-excluded 는 policy-auto EXCLUDE 항목과 breakdown 을 반환한다`() {
        adminClient().get().uri("/api/admin/review-items/auto-excluded?categoryId=$categoryId&days=7")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items").isArray
            .jsonPath("$.items[0].summaryId").isEqualTo(autoExcludedSummaryId)
            .jsonPath("$.items[0].reason").isEqualTo("rule:event_type_blacklist")
            .jsonPath("$.items[0].categoryName").isNotEmpty
            .jsonPath("$.totalCount").isNumber
            .jsonPath("$.reasonBreakdown").exists()
    }

    @Test
    fun `POST restore-to-review 는 200 과 REVIEW 상태를 반환한다`() {
        adminClient().post().uri("/api/admin/review-items/$autoExcludedSummaryId/restore-to-review")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.summaryId").isEqualTo(autoExcludedSummaryId)
            .jsonPath("$.newStatus").isEqualTo("REVIEW")

        // DB 에 REVIEW 로 반영됐는지 + audit 이력 기록됐는지 확인
        val refreshed = reviewItemDecisionStore.findBySummaryId(autoExcludedSummaryId)
        requireNotNull(refreshed) { "restore 후 결정이 조회되지 않음" }
        assert(refreshed.status == ReviewDecisionStatus.REVIEW) {
            "상태가 REVIEW 로 바뀌지 않음: ${refreshed.status}"
        }
        val audits = reviewItemAuditStore.listBySummaryId(autoExcludedSummaryId, limit = 10)
        assert(audits.any { it.reason == "manual_restore_from_auto_exclude" }) {
            "manual_restore_from_auto_exclude audit 이 기록되지 않음"
        }
    }

    @Test
    fun `인증 없이 auto-excluded 호출 시 401 을 반환한다`() {
        webClient.get().uri("/api/admin/review-items/auto-excluded")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `존재하지 않는 summary 에 restore 호출 시 404`() {
        adminClient().post().uri("/api/admin/review-items/non-existent-id/restore-to-review")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `이미 REVIEW 상태인 항목을 다시 restore 하면 409 Conflict`() {
        // 먼저 성공적으로 REVIEW 복구
        adminClient().post().uri("/api/admin/review-items/$autoExcludedSummaryId/restore-to-review")
            .exchange()
            .expectStatus().isOk

        // 같은 id 로 한 번 더 호출 — 더이상 EXCLUDE 가 아니므로 ConflictException
        adminClient().post().uri("/api/admin/review-items/$autoExcludedSummaryId/restore-to-review")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `page size 쿼리 파라미터로 페이지네이션 제어가 가능하다`() {
        adminClient().get()
            .uri("/api/admin/review-items/auto-excluded?categoryId=$categoryId&page=0&size=50")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items").isArray
            .jsonPath("$.totalCount").isNumber
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
