package com.ohmyclipping.admin

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.ClippingStat
import com.ohmyclipping.model.Language
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssItemStore
import com.ohmyclipping.store.RssSourceStore
import com.ohmyclipping.store.StatsStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class TrendSnapshotAdminControllerTest {

    private val mapper = jacksonObjectMapper()

    @Autowired lateinit var webClient: WebTestClient
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var sourceStore: RssSourceStore
    @Autowired lateinit var itemStore: RssItemStore
    @Autowired lateinit var summaryStore: BatchSummaryStore
    @Autowired lateinit var statsStore: StatsStore

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        val category = categoryStore.save(
            Category(
                id = "",
                name = "TrendCat-${System.nanoTime()}",
                maxItems = 5
            )
        )
        categoryId = category.id

        val source = sourceStore.save(
            RssSource(
                id = "",
                name = "TrendSource",
                url = "https://93.184.216.56/rss",
                categoryId = categoryId
            )
        )

        val item = itemStore.save(
            RssItem(
                id = "",
                title = "Global HR report released 2026",
                content = "국내 적용 시사점 포함",
                link = "https://example.com/report-${System.nanoTime()}.pdf",
                language = Language.FOREIGN,
                categoryId = categoryId,
                rssSourceId = source.id
            )
        )

        summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = item.title,
                translatedTitle = "글로벌 HR 리포트 발간",
                summary = "글로벌과 국내 적용 포인트를 함께 다룬 리포트입니다.",
                keywords = listOf("global", "리포트", "HR"),
                importanceScore = 0.78f,
                sourceLink = item.link,
                categoryId = categoryId,
                rssItemId = item.id
            )
        )
    }

    @Test
    fun `trend snapshot lifecycle endpoints should work`() {
        val snapshotId = adminClient().post().uri("/api/admin/trend-snapshots/run")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "periodType":"WEEKLY",
                  "categoryId":"$categoryId",
                  "regionType":"ALL",
                  "generatedBy":"admin"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.periodType").isEqualTo("WEEKLY")
            .jsonPath("$.categoryId").isEqualTo(categoryId)
            .jsonPath("$.status").isEqualTo("DRAFT")
            .jsonPath("$.id").value<String> { value -> value.isNotBlank() }
            .returnResult()
            .responseBody
            ?.let { mapper.readTree(it).path("id").asText() }
            ?: ""

        adminClient().get().uri("/api/admin/trend-snapshots?limit=20")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isNotEmpty

        adminClient().post().uri("/api/admin/trend-snapshots/$snapshotId/publish")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"publishedBy":"admin"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("PUBLISHED")
    }

    @Test
    fun `visual generation and review endpoints should work`() {
        val snapshotId = createSnapshot()

        val cardId = adminClient().post().uri("/api/admin/trend-snapshots/$snapshotId/generate-visual")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"cardType":"COMIC_4","generatedBy":"admin"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.cardType").isEqualTo("COMIC_4")
            .jsonPath("$.reviewStatus").isEqualTo("PENDING")
            .jsonPath("$.id").value<String> { value -> value.isNotBlank() }
            .returnResult()
            .responseBody
            ?.let { mapper.readTree(it).path("id").asText() }
            ?: ""

        adminClient().get().uri("/api/admin/trend-snapshots/$snapshotId/visuals?limit=20")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].snapshotId").isEqualTo(snapshotId)

        adminClient().post().uri("/api/admin/visual-cards/$cardId/review")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"approved":true,"reviewNote":"발행 가능","reviewedBy":"admin","publish":true}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.reviewStatus").isEqualTo("APPROVED")
            .jsonPath("$.published").isEqualTo(true)
    }

    @Test
    fun `report release and quality summary endpoints should work`() {
        createSnapshot()

        adminClient().get().uri("/api/admin/ops-reports/releases?days=30&limit=20")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].releaseType").isNotEmpty

        adminClient().get().uri("/api/admin/ops-reports/summary?days=30")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.days").isEqualTo(30)
            .jsonPath("$.recommendations").isArray
    }

    @Test
    fun `quality summary should aggregate stats across full day window`() {
        val baseline = fetchQualitySummary(days = 60).path("itemsCollected").asInt()
        val statCategory = categoryStore.save(Category(id = "", name = "QualityWindow-${System.nanoTime()}"))
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        statsStore.upsert(
            ClippingStat(
                id = "",
                categoryId = statCategory.id,
                statDate = today.minusDays(45),
                itemsCollected = 11,
                itemsSummarized = 6,
                itemsSent = 4
            )
        )
        statsStore.upsert(
            ClippingStat(
                id = "",
                categoryId = statCategory.id,
                statDate = today.minusDays(3),
                itemsCollected = 7,
                itemsSummarized = 3,
                itemsSent = 2
            )
        )

        val updated = fetchQualitySummary(days = 60).path("itemsCollected").asInt()
        (updated - baseline) shouldBe 18
    }

    private fun createSnapshot(): String =
        adminClient().post().uri("/api/admin/trend-snapshots/run")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"periodType":"WEEKLY","categoryId":"$categoryId","regionType":"ALL"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").value<String> { value -> value.isNotBlank() }
            .returnResult()
            .responseBody
            ?.let { mapper.readTree(it).path("id").asText() }
            ?: ""

    private fun fetchQualitySummary(days: Int) =
        adminClient().get().uri("/api/admin/ops-reports/summary?days=$days")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .returnResult()
            .responseBody
            ?.let { mapper.readTree(it) }
            ?: error("quality summary response is empty")

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()
}
