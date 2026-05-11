package com.clipping.mcpserver.manual

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.service.ClippingService
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RssItemStore
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
class ManualSlackDigestLayoutSmokeTest {

    @Value("\${manual.slack.smoke.enabled:false}")
    private var manualSlackSmokeEnabled: Boolean = false

    @Autowired
    private lateinit var categoryStore: CategoryStore

    @Autowired
    private lateinit var itemStore: RssItemStore

    @Autowired
    private lateinit var summaryStore: BatchSummaryStore

    @Autowired
    private lateinit var clippingService: ClippingService

    @Value("\${SLACK_TEST_CHANNEL_ID:}")
    private lateinit var defaultChannelId: String

    @Value("\${clipping-mcp-server.slack.bot-token:}")
    private lateinit var slackBotToken: String

    @Test
    fun `should post digest with date and section dividers to Slack`() {
        Assumptions.assumeTrue(manualSlackSmokeEnabled, "manual Slack smoke tests are opt-in")
        Assumptions.assumeTrue(defaultChannelId.isNotBlank(), "SLACK channel is required")
        Assumptions.assumeTrue(slackBotToken.isNotBlank(), "Real Slack bot token is required")

        val category = categoryStore.save(
            Category(
                id = "",
                name = "manual-layout-smoke-${System.currentTimeMillis()}",
                description = "manual layout smoke test category",
                slackChannelId = defaultChannelId,
                isActive = true,
                maxItems = 1
            )
        )

        val link = "https://www.nasa.gov/news-release/nasa-names-first-chief-artificial-intelligence-officer/"
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "NASA, 최초의 최고 인공지능 책임자 임명",
                content = "manual-seeded",
                link = link,
                categoryId = category.id
            )
        )
        summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = "NASA, 최초의 최고 인공지능 책임자 임명",
                translatedTitle = "NASA, 최초의 최고 인공지능 책임자 임명",
                summary = """
                    NASA는 데이비드 살바니니를 새로운 최고 인공지능 책임자로 임명했다. 이번 임명은 기존 최고 데이터 책임자 역할의 확장으로 즉시 효력이 발생한다.
                    NASA는 연구, 데이터 분석, 자율 시스템 등 핵심 임무 전반에 AI 활용을 확대하고 있으며 이를 위한 전략·거버넌스 체계를 강화하고 있다.
                    살바니니는 정부·학계·산업 파트너 협력을 통해 AI 도입의 속도와 신뢰성을 동시에 높이는 역할을 맡는다.
                """.trimIndent(),
                keywords = listOf("NASA", "인공지능", "최고책임자", "AI 전략"),
                importanceScore = 0.92f,
                sourceLink = link,
                categoryId = category.id,
                rssItemId = item.id
            )
        )

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
