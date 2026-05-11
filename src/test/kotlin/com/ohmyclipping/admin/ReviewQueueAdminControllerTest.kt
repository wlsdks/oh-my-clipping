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
import org.junit.jupiter.api.Nested
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
class ReviewQueueAdminControllerTest {

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var sourceStore: RssSourceStore
    @Autowired lateinit var itemStore: RssItemStore
    @Autowired lateinit var summaryStore: BatchSummaryStore

    private lateinit var categoryId: String
    private lateinit var summaryId: String

    @BeforeEach
    fun setup() {
        val category = categoryStore.save(Category(id = "", name = "ReviewQueueCat-${System.nanoTime()}"))
        categoryId = category.id

        val source = sourceStore.save(
            RssSource(
                id = "",
                name = "ReviewSource",
                url = "https://93.184.216.41/rss",
                categoryId = categoryId
            )
        )

        val item = itemStore.save(
            RssItem(
                id = "",
                title = "리더십 학습 전략 업데이트",
                content = "실무 중심 학습 프레임워크 정리",
                link = "https://example.com/review-item-${System.nanoTime()}",
                language = Language.KOREAN,
                categoryId = categoryId,
                rssSourceId = source.id
            )
        )

        val summary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = item.title,
                summary = "리더십 학습 전략을 정리한 문서",
                keywords = listOf("학습", "리더십"),
                importanceScore = 0.62f,
                sourceLink = item.link,
                categoryId = categoryId,
                rssItemId = item.id
            )
        )
        summaryId = summary.id
    }

    @Test
    fun `GET review items should return list`() {
        adminClient().get().uri("/api/admin/review-items?categoryId=$categoryId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].summaryId").isEqualTo(summaryId)
            .jsonPath("$[0].categoryId").isEqualTo(categoryId)
            .jsonPath("$[0].currentStatus").isEqualTo("INCLUDE")
            .jsonPath("$[0].priorityScore").isNumber
            .jsonPath("$[0].priorityLabel").isNotEmpty
    }

    @Test
    fun `POST approve should set INCLUDE status`() {
        adminClient().post().uri("/api/admin/review-items/$summaryId/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reason":"업무 관련성이 높음","reviewedBy":"admin"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("INCLUDE")

        adminClient().get().uri("/api/admin/review-items?categoryId=$categoryId&status=INCLUDE")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].summaryId").isEqualTo(summaryId)
            .jsonPath("$[0].currentStatus").isEqualTo("INCLUDE")
            .jsonPath("$[0].reviewedBy").isEqualTo("admin")
    }

    @Test
    fun `POST exclude should set EXCLUDE status`() {
        adminClient().post().uri("/api/admin/review-items/$summaryId/exclude")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reason":"마케팅성 글","reviewedBy":"admin"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("EXCLUDE")

        adminClient().get().uri("/api/admin/review-items?categoryId=$categoryId&status=EXCLUDE")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].summaryId").isEqualTo(summaryId)
            .jsonPath("$[0].currentStatus").isEqualTo("EXCLUDE")
    }

    @Test
    fun `POST approve should append audit history`() {
        adminClient().post().uri("/api/admin/review-items/$summaryId/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reason":"검토 승인","reviewedBy":"audit-admin"}""")
            .exchange()
            .expectStatus().isOk

        adminClient().get().uri("/api/admin/review-items/$summaryId/audits?limit=10")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].summaryId").isEqualTo(summaryId)
            .jsonPath("$[0].toStatus").isEqualTo("INCLUDE")
            .jsonPath("$[0].reason").isEqualTo("검토 승인")
            .jsonPath("$[0].reviewedBy").isEqualTo("audit-admin")
    }

    @Test
    fun `POST approve should return 404 when summary does not exist`() {
        adminClient().post().uri("/api/admin/review-items/not-found/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isNotFound
    }

    @Nested
    inner class `GET review-items summary` {
        @Test
        fun `카테고리별 요약 카운트를 반환한다`() {
            adminClient().get()
                .uri("/api/admin/review-items/summary")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.totalCount").isNumber
                .jsonPath("$.reviewCount").isNumber
                .jsonPath("$.includeCount").isNumber
                .jsonPath("$.excludeCount").isNumber
                .jsonPath("$.categories").isArray
        }

        @Test
        fun `검토 항목이 있으면 카테고리 집계에 포함된다`() {
            // approve 후 summary에 포함되는지 확인한다
            adminClient().post().uri("/api/admin/review-items/$summaryId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason":"요약 테스트","reviewedBy":"test-admin"}""")
                .exchange()
                .expectStatus().isOk

            adminClient().get()
                .uri("/api/admin/review-items/summary")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.totalCount").isNumber
                .jsonPath("$.categories").isArray
        }
    }

    @Nested
    inner class `POST bulk-approve` {

        @Test
        fun `유효한 요청으로 벌크 승인 시 200과 succeeded 목록을 반환한다`() {
            adminClient().post().uri("/api/admin/review-items/bulk-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"ids":["$summaryId"],"reviewNote":"일괄 승인"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.succeeded[0]").isEqualTo(summaryId)
                .jsonPath("$.failed").isArray
        }

        @Test
        fun `이미 INCLUDE인 항목을 재승인하면 ALREADY_PROCESSED로 실패 처리된다`() {
            // 먼저 승인해서 INCLUDE 상태로 만든다
            adminClient().post().uri("/api/admin/review-items/$summaryId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk

            // 벌크 승인 재시도
            adminClient().post().uri("/api/admin/review-items/bulk-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"ids":["$summaryId"]}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.succeeded").isArray
                .jsonPath("$.failed[0].code").isEqualTo("ALREADY_PROCESSED")
        }
    }

    @Nested
    inner class `POST bulk-exclude` {

        @Test
        fun `유효한 요청으로 벌크 제외 시 200과 succeeded 목록을 반환한다`() {
            adminClient().post().uri("/api/admin/review-items/bulk-exclude")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"ids":["$summaryId"],"reviewNote":"일괄 제외"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.succeeded[0]").isEqualTo(summaryId)
                .jsonPath("$.failed").isArray
        }
    }

    @Nested
    inner class `POST bulk-revert` {

        @Test
        fun `벌크 승인 후 bulk-revert로 REVIEW 복원 시 200과 succeeded 목록을 반환한다`() {
            // 먼저 승인해서 INCLUDE 상태로 만든다
            adminClient().post().uri("/api/admin/review-items/$summaryId/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk

            // bulk-revert로 이전 상태(REVIEW)로 되돌린다
            adminClient().post().uri("/api/admin/review-items/bulk-revert")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"reverts":[{"id":"$summaryId","previousStatus":"REVIEW"}]}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.succeeded[0]").isEqualTo(summaryId)
                .jsonPath("$.failed").isArray
        }

        @Test
        fun `bulk-exclude 후 bulk-revert로 REVIEW 복원 시 상태가 REVIEW로 변경된다`() {
            // 먼저 제외 처리한다
            adminClient().post().uri("/api/admin/review-items/bulk-exclude")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"ids":["$summaryId"]}""")
                .exchange()
                .expectStatus().isOk

            // bulk-revert로 이전 상태(REVIEW)로 되돌린다
            adminClient().post().uri("/api/admin/review-items/bulk-revert")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"reverts":[{"id":"$summaryId","previousStatus":"REVIEW"}]}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.succeeded[0]").isEqualTo(summaryId)
                .jsonPath("$.failed").isArray

            // 상태가 REVIEW로 복원되었는지 확인한다
            adminClient().get().uri("/api/admin/review-items?categoryId=$categoryId&status=REVIEW")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$[0].summaryId").isEqualTo(summaryId)
                .jsonPath("$[0].currentStatus").isEqualTo("REVIEW")
        }
    }

    @Nested
    inner class `GET review-items with perCategory` {

        @Test
        fun `perCategory 파라미터 없이 호출하면 기존 동작대로 200을 반환한다`() {
            // 기본 동작(perCategory 미지정)도 여전히 작동해야 한다 — backward compatibility
            adminClient().get().uri("/api/admin/review-items?limit=100")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$").isArray
        }

        @Test
        fun `perCategory=5로 호출하면 200 OK로 응답한다`() {
            // 전체 조회 + 카테고리별 top-5 샘플링
            adminClient().get().uri("/api/admin/review-items?limit=100&perCategory=5")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$").isArray
        }

        @Test
        fun `perCategory가 limit을 초과하면 400을 반환한다`() {
            adminClient().get().uri("/api/admin/review-items?limit=5&perCategory=10")
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    inner class `GET review-stats` {

        @Test
        fun `기간별 정확도 통계를 반환한다`() {
            adminClient().get()
                .uri("/api/admin/review-items/stats?period=7d")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.period").isEqualTo("7d")
                .jsonPath("$.totalReviewed").isNumber
                .jsonPath("$.overallAccuracy").isNumber
                .jsonPath("$.includeAccuracy").isNumber
                .jsonPath("$.excludeAccuracy").isNumber
                .jsonPath("$.overriddenCount").isNumber
                .jsonPath("$.categoryBreakdown").isArray
        }

        @Test
        fun `period 파라미터 없이 호출하면 7d 기본값으로 응답한다`() {
            adminClient().get()
                .uri("/api/admin/review-items/stats")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.period").isEqualTo("7d")
        }

        @Test
        fun `30d period로 호출하면 period가 30d인 응답을 반환한다`() {
            adminClient().get()
                .uri("/api/admin/review-items/stats?period=30d")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.period").isEqualTo("30d")
                .jsonPath("$.totalReviewed").isNumber
                .jsonPath("$.categoryBreakdown").isArray
        }
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
