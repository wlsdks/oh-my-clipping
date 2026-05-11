package com.ohmyclipping.admin

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssItemStore
import org.junit.jupiter.api.AfterEach
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30s")
@ActiveProfiles("test")
class ClippingAdminControllerTest {

    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var itemStore: RssItemStore

    @Autowired
    lateinit var summaryStore: BatchSummaryStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private lateinit var categoryId: String
    private lateinit var highSummaryId: String

    /**
     * runtime_settings 는 DB 레벨 글로벌 상태다. 다른 테스트 클래스(ClipDigestToolTest 등)로
     * 오염이 넘어가지 않도록 테스트 종료 후 본 테스트가 변경한 설정 키를 초기화한다.
     * maintenance_* 처럼 MaintenanceModeIntegrationTest 가 관리하는 키는 유지한다.
     */
    @AfterEach
    fun cleanupRuntimeSettings() {
        jdbc.update(
            """
            DELETE FROM clipping_runtime_settings
             WHERE setting_key NOT IN ('maintenance_mode', 'maintenance_message')
            """.trimIndent()
        )
    }

    @BeforeEach
    fun setup() {
        val category = categoryStore.save(
            Category(
                id = "",
                name = "AdminClipCat-${System.nanoTime()}",
                maxItems = 3
            )
        )
        categoryId = category.id

        val item = itemStore.save(
            RssItem(
                id = "",
                title = "Admin summary title",
                content = "Admin test content",
                link = "https://93.184.216.34/admin-clip-${System.nanoTime()}",
                categoryId = categoryId
            )
        )
        val highSummary = summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = item.title,
                translatedTitle = "Admin translated title",
                summary = "Admin summary body",
                keywords = listOf("admin", "digest"),
                importanceScore = 0.9f,
                sourceLink = item.link,
                categoryId = categoryId,
                rssItemId = item.id
            )
        )
        highSummaryId = highSummary.id

        val lowItem = itemStore.save(
            RssItem(
                id = "",
                title = "Admin low summary",
                content = "Admin low content",
                link = "https://93.184.216.34/admin-clip-low-${System.nanoTime()}",
                categoryId = categoryId
            )
        )
        summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = lowItem.title,
                translatedTitle = "Admin low translated",
                summary = "Admin low summary body",
                keywords = listOf("admin", "low"),
                importanceScore = 0.2f,
                sourceLink = lowItem.link,
                categoryId = categoryId,
                rssItemId = lowItem.id
            )
        )
    }

    @Test
    fun `GET clipping settings list should return array`() {
        adminClient().get().uri("/api/admin/clipping/settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    fun `PUT clipping settings should update category and retention`() {
        adminClient().put().uri("/api/admin/clipping/$categoryId/settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"slackChannelId":"CSETTINGS01","maxItems":5,"retentionKeepDays":14,"retentionEnabled":true}
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.categoryId").isEqualTo(categoryId)
            .jsonPath("$.slackChannelId").isEqualTo("CSETTINGS01")
            .jsonPath("$.maxItems").isEqualTo(5)
            .jsonPath("$.retentionKeepDays").isEqualTo(14)
            .jsonPath("$.retentionEnabled").isEqualTo(true)
    }

    @Test
    fun `POST clipping digest should return digest result without slack send`() {
        adminClient().post().uri("/api/admin/clipping/$categoryId/digest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"sendToSlack":false,"maxItems":1}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.categoryId").isEqualTo(categoryId)
            .jsonPath("$.postedToSlack").isEqualTo(false)
            .jsonPath("$.selectedCount").isEqualTo(1)
    }

    @Test
    fun `POST clipping pipeline should apply Ralph loop overrides and return loop metadata`() {
        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"ralphOrchestrationEnabled":true,"ralphLoopEnabled":true,"ralphLoopMaxIterations":4,"ralphLoopStopPhrase":"RALPH_STOP"}""")
            .exchange()
            .expectStatus().isOk

        adminClient().post().uri("/api/admin/clipping/$categoryId/pipeline")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"sendToSlack":false,"ralphLoopEnabled":true,"ralphLoopMaxIterations":5,"ralphLoopStopPhrase":"다이제스트"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.orchestrationMode").isEqualTo("RALPH")
            .jsonPath("$.loopEnabled").isEqualTo(true)
            .jsonPath("$.loopIterationCount").isEqualTo(1)
            .jsonPath("$.loopStopReason").isEqualTo("STOP_PHRASE_DETECTED")
            .jsonPath("$.loopStopPhrase").isEqualTo("다이제스트")
            .jsonPath("$.stepTraces").isArray
    }

    @Test
    fun `GET runtime settings should return defaults`() {
        adminClient().get().uri("/api/admin/runtime-settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.defaultHoursBack").isNumber
            .jsonPath("$.digestMinImportanceScore").isNumber
            .jsonPath("$.digestItemSummaryMaxChars").isNumber
            .jsonPath("$.digestKeywordMaxCount").isNumber
            .jsonPath("$.jobWorkerBatchSize").isNumber
            .jsonPath("$.ralphOrchestrationEnabled").isBoolean
            .jsonPath("$.ralphLoopEnabled").isBoolean
            .jsonPath("$.ralphLoopMaxIterations").isNumber
            .jsonPath("$.ralphLoopStopPhrase").isNotEmpty
            .jsonPath("$.slackBotTokenConfigured").isBoolean
    }

    @Test
    fun `PUT runtime settings should update Ralph orchestration toggle`() {
        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"ralphOrchestrationEnabled":true,"ralphLoopEnabled":true,"ralphLoopMaxIterations":6,"ralphLoopStopPhrase":"DONE_SIGNAL"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.ralphOrchestrationEnabled").isEqualTo(true)
            .jsonPath("$.ralphLoopEnabled").isEqualTo(true)
            .jsonPath("$.ralphLoopMaxIterations").isEqualTo(6)
            .jsonPath("$.ralphLoopStopPhrase").isEqualTo("DONE_SIGNAL")

        adminClient().get().uri("/api/admin/runtime-settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.ralphOrchestrationEnabled").isEqualTo(true)
            .jsonPath("$.ralphLoopEnabled").isEqualTo(true)
            .jsonPath("$.ralphLoopMaxIterations").isEqualTo(6)
            .jsonPath("$.ralphLoopStopPhrase").isEqualTo("DONE_SIGNAL")
    }

    @Test
    fun `runtime should mask slack token in response and keep existing token when blank value is sent`() {
        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"slackBotToken":"xoxb-secret-token"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.slackBotToken").isEqualTo("********")
            .jsonPath("$.slackBotTokenConfigured").isEqualTo(true)

        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"defaultHoursBack":33,"slackBotToken":"   "}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.defaultHoursBack").isEqualTo(33)
            .jsonPath("$.slackBotToken").isEqualTo("********")
            .jsonPath("$.slackBotTokenConfigured").isEqualTo(true)
    }

    @Test
    fun `PUT runtime settings should update values`() {
        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "defaultHoursBack":48,
                  "digestMinImportanceScore":0.65,
                  "digestItemSummaryMaxChars":1200,
                  "digestKeywordMaxCount":4,
                  "jobWorkerBatchSize":9
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.defaultHoursBack").isEqualTo(48)
            .jsonPath("$.digestMinImportanceScore").isEqualTo(0.65)
            .jsonPath("$.digestItemSummaryMaxChars").isEqualTo(1200)
            .jsonPath("$.digestKeywordMaxCount").isEqualTo(4)
            .jsonPath("$.jobWorkerBatchSize").isEqualTo(9)

        adminClient().get().uri("/api/admin/runtime-settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.defaultHoursBack").isEqualTo(48)
            .jsonPath("$.digestMinImportanceScore").isEqualTo(0.65)
            .jsonPath("$.digestItemSummaryMaxChars").isEqualTo(1200)
            .jsonPath("$.digestKeywordMaxCount").isEqualTo(4)
            .jsonPath("$.jobWorkerBatchSize").isEqualTo(9)
    }

    @Test
    fun `runtime digest importance should affect selection`() {
        disableAutoReviewGate()

        adminClient().post().uri("/api/admin/clipping/$categoryId/digest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"sendToSlack":false,"maxItems":2}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.selectedCount").isEqualTo(1)

        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"digestMinImportanceScore":0.1}""")
            .exchange()
            .expectStatus().isOk

        adminClient().post().uri("/api/admin/clipping/$categoryId/digest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"sendToSlack":false,"maxItems":2}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.selectedCount").isEqualTo(2)
    }

    @Test
    fun `digest should exclude review-pending summaries by manual decision`() {
        disableAutoReviewGate()

        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"digestMinImportanceScore":0.1}""")
            .exchange()
            .expectStatus().isOk

        adminClient().post().uri("/api/admin/review-items/$highSummaryId/review")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"reason":"수동 검토 대기","reviewedBy":"admin"}""")
            .exchange()
            .expectStatus().isOk

        adminClient().post().uri("/api/admin/clipping/$categoryId/digest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"sendToSlack":false,"maxItems":2}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.selectedCount").isEqualTo(1)
    }

    @Test
    fun `POST runtime settings reset should restore property defaults`() {
        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"defaultHoursBack":72,"digestItemSummaryMaxChars":1500,"digestKeywordMaxCount":3,"jobWorkerBatchSize":11}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.defaultHoursBack").isEqualTo(72)
            .jsonPath("$.digestItemSummaryMaxChars").isEqualTo(1500)
            .jsonPath("$.digestKeywordMaxCount").isEqualTo(3)
            .jsonPath("$.jobWorkerBatchSize").isEqualTo(11)

        adminClient().post().uri("/api/admin/runtime-settings/reset")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.defaultHoursBack").isEqualTo(24)
            .jsonPath("$.digestItemSummaryMaxChars").isEqualTo(960)
            .jsonPath("$.digestKeywordMaxCount").isEqualTo(6)
            .jsonPath("$.jobWorkerBatchSize").isEqualTo(5)
    }

    @Test
    fun `GET runtime settings audits should include actor and action`() {
        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"defaultHoursBack":36}""")
            .exchange()
            .expectStatus().isOk

        adminClient().get().uri("/api/admin/runtime-settings/audits?limit=1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
            .jsonPath("$[0].settingKey").isEqualTo("default_hours_back")
            .jsonPath("$[0].action").isEqualTo("UPDATE")
            .jsonPath("$[0].changedBy").isEqualTo("admin-api")
    }

    @Test
    fun `runtime should save and expose block kit template`() {
        val template = """
            [
              {"type":"header","text":{"type":"plain_text","text":"{{categoryName}}","emoji":true}}
            ]
        """.trimIndent()

        adminClient().put().uri("/api/admin/runtime-settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"slackDigestBlockKitTemplate":${quoteJson(template)}}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.slackDigestBlockKitTemplate").isEqualTo(template)

        adminClient().get().uri("/api/admin/runtime-settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.slackDigestBlockKitTemplate").isEqualTo(template)
    }

    @Test
    fun `runtime block kit preview should render sample blocks`() {
        val template = """
            [
              {"type":"header","text":{"type":"plain_text","text":"{{categoryName}} 다이제스트","emoji":true}},
              {"type":"section","text":{"type":"mrkdwn","text":"*{{itemTitle}}*\\n{{itemSummary}}"}}
            ]
        """.trimIndent()

        adminClient().post().uri("/api/admin/runtime-settings/slack/block-kit/preview")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"template":${quoteJson(template)}}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.valid").isEqualTo(true)
            .jsonPath("$.blocks").isArray
            .jsonPath("$.blocks[0].type").isEqualTo("header")
            .jsonPath("$.placeholders").isArray
            .jsonPath("$.renderedText").isNotEmpty
    }

    @Test
    fun `admin clipping endpoint should require bearer token`() {
        webClient.get().uri("/api/admin/clipping/settings")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `GET internal clipping admin ui should redirect to login when unauthenticated`() {
        webClient.get().uri("/admin")
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().valueMatches(HttpHeaders.LOCATION, ".*/login.*")
    }

    @Test
    fun `GET internal clipping sub page should redirect to login when unauthenticated`() {
        webClient.get().uri("/admin/operations")
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().valueMatches(HttpHeaders.LOCATION, ".*/login.*")
    }

    private fun adminClient(): WebTestClient =
        webClient.mutate()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-admin-token")
            .build()

    private fun disableAutoReviewGate() {
        adminClient().put().uri("/api/admin/category-rules/$categoryId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"uncertainToReview":false,"autoExcludeEnabled":false,"updatedBy":"test-admin"}""")
            .exchange()
            .expectStatus().isOk
    }

    private fun quoteJson(raw: String): String =
        "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
