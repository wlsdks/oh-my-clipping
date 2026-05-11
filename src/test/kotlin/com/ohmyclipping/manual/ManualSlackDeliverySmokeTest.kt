package com.ohmyclipping.manual

import com.ohmyclipping.model.Category
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.service.ClippingService
import com.ohmyclipping.service.source.VerificationResult
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssSourceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ManualSlackDeliverySmokeTest {

    @Value("\${manual.slack.smoke.enabled:false}")
    private var manualSlackSmokeEnabled: Boolean = false

    @Autowired
    private lateinit var categoryStore: CategoryStore

    @Autowired
    private lateinit var sourceStore: RssSourceStore

    @Autowired
    private lateinit var clippingService: ClippingService

    @Value("\${SLACK_TEST_CHANNEL_ID:}")
    private lateinit var defaultChannelId: String

    @Value("\${spring.ai.google.genai.api-key:}")
    private lateinit var genAiApiKey: String

    @Value("\${clipping-mcp-server.slack.bot-token:}")
    private lateinit var slackBotToken: String

    @Test
    fun `should clip one trusted URL and post digest to Slack`() {
        Assumptions.assumeTrue(manualSlackSmokeEnabled, "manual Slack smoke tests are opt-in")
        Assumptions.assumeTrue(defaultChannelId.isNotBlank(), "SLACK channel is required")
        Assumptions.assumeTrue(genAiApiKey.isNotBlank() && genAiApiKey != "test-key", "Real Gemini API key is required")
        Assumptions.assumeTrue(slackBotToken.isNotBlank(), "Real Slack bot token is required")

        val category = categoryStore.save(
            Category(
                id = "",
                name = "manual-slack-smoke-${System.currentTimeMillis()}",
                description = "manual smoke test category",
                slackChannelId = defaultChannelId,
                isActive = true,
                maxItems = 1
            )
        )
        sourceStore.save(
            RssSource(
                id = "",
                name = "nasa-approved-${System.currentTimeMillis()}",
                url = "https://www.nasa.gov/rss/dyn/breaking_news.rss",
                categoryId = category.id,
                crawlApproved = true,
                verificationStatus = VerificationResult.VERIFIED.name
            )
        )

        val addResult = clippingService.addUrl(
            categoryId = category.id,
            rawUrl = "https://www.nasa.gov/news-release/nasa-names-first-chief-artificial-intelligence-officer/"
        )
        assertTrue(addResult.added, "URL should be added")

        val summarizeResult = clippingService.summarize(category.id)
        assertEquals(1, summarizeResult.totalSummarized, "Expected exactly one summarized item")

        val digestResult = clippingService.digest(
            categoryId = category.id,
            maxItems = 1,
            unsentOnly = true,
            sendToSlack = true,
            slackChannelId = defaultChannelId
        )

        assertTrue(digestResult.postedToSlack, "Digest should be posted to Slack")
        assertEquals(1, digestResult.selectedCount, "Expected exactly one digest item")
        assertTrue(!digestResult.slackMessageTs.isNullOrBlank(), "Slack message ts should be present")
    }
}
