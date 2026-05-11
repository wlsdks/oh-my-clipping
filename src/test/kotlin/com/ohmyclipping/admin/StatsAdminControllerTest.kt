package com.ohmyclipping.admin

import com.ohmyclipping.model.Category
import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.ClippingStat
import com.ohmyclipping.model.LlmRun
import com.ohmyclipping.model.ReviewDecisionStatus
import com.ohmyclipping.model.ReviewItemDecision
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.service.StatsService
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.LlmRunStore
import com.ohmyclipping.store.ReviewItemDecisionStore
import com.ohmyclipping.store.RssItemStore
import com.ohmyclipping.store.StatsStore
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class StatsAdminControllerTest {

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var statsService: StatsService
    @Autowired lateinit var statsStore: StatsStore
    @Autowired lateinit var itemStore: RssItemStore
    @Autowired lateinit var summaryStore: BatchSummaryStore
    @Autowired lateinit var reviewItemDecisionStore: ReviewItemDecisionStore
    @Autowired lateinit var llmRunStore: LlmRunStore

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        val cat = categoryStore.save(Category(id = "", name = "StatsTestCat-${System.nanoTime()}"))
        categoryId = cat.id
    }

    @Test
    fun `GET monthly stats should return list`() {
        statsService.recordCollection(categoryId, 10)
        val yearMonth = YearMonth.now().toString()

        adminClient().get().uri("/api/admin/stats/monthly?yearMonth=$yearMonth&categoryId=$categoryId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
            .jsonPath("$[0].itemsCollected").isEqualTo(10)
    }

    @Test
    fun `GET monthly stats without categoryId`() {
        val yearMonth = YearMonth.now().toString()
        adminClient().get().uri("/api/admin/stats/monthly?yearMonth=$yearMonth")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    fun `GET daily kpi should return operational metrics by day`() {
        val today = LocalDate.now()
        val seoulZone = ZoneId.of("Asia/Seoul")
        val summaryCreatedAt = today.atTime(9, 0).atZone(seoulZone).toInstant()
        val reviewedAt = today.atTime(11, 0).atZone(seoulZone).toInstant()
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "KPI summary",
                content = "KPI content",
                link = "https://93.184.216.34/kpi-${System.nanoTime()}",
                categoryId = categoryId
            )
        )
        val summary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = item.title,
                translatedTitle = "KPI summary ko",
                summary = "Operational KPI summary",
                keywords = listOf("kpi"),
                importanceScore = 0.7f,
                sourceLink = item.link,
                categoryId = categoryId,
                rssItemId = item.id,
                createdAt = summaryCreatedAt
            )
        )
        jdbc.update(
            "UPDATE batch_summaries SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.from(summaryCreatedAt),
            summary.id
        )
        reviewItemDecisionStore.upsert(
            ReviewItemDecision(
                summaryId = summary.id,
                categoryId = categoryId,
                status = ReviewDecisionStatus.EXCLUDE,
                reason = "noise",
                reviewedBy = "test-admin",
                reviewedAt = reviewedAt
            )
        )
        statsStore.upsert(
            ClippingStat(
                id = "",
                categoryId = categoryId,
                statDate = today,
                itemsCollected = 10,
                itemsDuplicates = 2,
                slackSendAttempts = 5,
                slackSendSuccesses = 4
            )
        )
        llmRunStore.save(
            LlmRun(
                id = "",
                categoryId = categoryId,
                rssItemId = item.id,
                model = "gemini-2.5-flash",
                promptVersion = "article.v3",
                inputHash = "hash-${System.nanoTime()}",
                inputChars = 10000,
                outputChars = 5000,
                status = "SUCCEEDED",
                durationMs = 400
            )
        )

        adminClient().get().uri("/api/admin/stats/daily-kpi?categoryId=$categoryId&from=$today&to=$today")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
            .jsonPath("$[0].itemsCollected").isEqualTo(10)
            .jsonPath("$[0].itemsDuplicates").isEqualTo(2)
            .jsonPath("$[0].excludedCount").isEqualTo(1)
            .jsonPath("$[0].duplicateRate").isEqualTo(0.2)
            .jsonPath("$[0].noiseRate").isEqualTo(0.1)
            .jsonPath("$[0].sendAttempts").isEqualTo(5)
            .jsonPath("$[0].sendSuccesses").isEqualTo(4)
            .jsonPath("$[0].sendSuccessRate").isEqualTo(0.8)
            .jsonPath("$[0].reviewLeadTimeHours").isEqualTo(2.0)
            .jsonPath("$[0].llmEstimatedCostUsd").value<Number> { assertTrue(it.toDouble() > 0.0) }
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
