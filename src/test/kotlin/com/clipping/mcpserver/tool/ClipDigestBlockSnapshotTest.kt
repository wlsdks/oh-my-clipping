package com.clipping.mcpserver.tool

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.service.ClippingService
import com.clipping.mcpserver.service.SlackMessageSender
import com.clipping.mcpserver.service.SlackMessageSender.SendResult
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.RuntimeSettingStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.doAnswer
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ClipDigestBlockSnapshotTest {

    @Autowired
    lateinit var clippingService: ClippingService

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var itemStore: RssItemStore

    @Autowired
    lateinit var summaryStore: BatchSummaryStore

    @Autowired
    lateinit var runtimeSettingStore: RuntimeSettingStore

    @MockitoBean
    lateinit var slackMessageSender: SlackMessageSender

    @Test
    fun `digest block kit payload should keep required blocks and actions`() {
        runtimeSettingStore.deleteAll()
        val category = categoryStore.save(
            Category(
                id = "",
                name = "SnapshotCat",
                slackChannelId = "CSNAPSHOT"
            )
        )
        seedSummary(
            categoryId = category.id,
            slug = "snapshot-1",
            originalTitle = "AI 리더십과 학습 전략",
            summary = "AI 도구 도입 과정에서 리더십 운영 모델을 재설계하고 학습 체계를 병행해야 한다는 사례를 정리한다.",
            keywords = listOf("AI", "L&D", "리더십"),
            importance = 0.95f,
            sourceLink = "https://example.com/ai-ld-1"
        )
        seedSummary(
            categoryId = category.id,
            slug = "snapshot-2",
            originalTitle = "L&D 자동화 사례",
            summary = "사내 러닝 운영에서 자동 분류와 추천을 적용해 큐레이션 시간을 줄이고 학습 반응률을 높인 결과를 설명한다.",
            keywords = listOf("학습", "자동화", "코칭"),
            importance = 0.72f,
            sourceLink = "https://example.com/ai-ld-2"
        )

        var capturedChannel: String? = null
        var capturedBlocks: List<Map<String, Any?>>? = null
        // main 의 7-arg sendMessage 시그니처에 맞춰 stub 한다. F3 의 fallbackUsed 는 기본값(false)으로 내려간다.
        doAnswer { invocation ->
            capturedChannel = invocation.getArgument(0)
            capturedBlocks = invocation.getArgument(2)
            SendResult(ts = "1700000000.000001", channelId = invocation.getArgument(0))
        }
            .`when`(slackMessageSender)
            .sendMessage(anyString(), anyString(), anyList(), any(), anyBoolean(), any(), any(), any())

        val result = clippingService.digest(
            categoryId = category.id,
            maxItems = 5,
            unsentOnly = true,
            sendToSlack = true,
            slackChannelId = null
        )
        result.selectedCount shouldBe 2
        result.slackChannelId shouldBe "CSNAPSHOT"
        capturedChannel shouldBe "CSNAPSHOT"

        val blocks = requireNotNull(capturedBlocks) { "blocks should be captured from slack sender" }
        val headerBlock = blocks.firstOrNull { it["type"] == "header" } ?: error("header block is missing")
        val headerText = (((headerBlock["text"] as Map<*, *>)["text"]) as String)
        headerText.shouldContain("SnapshotCat")
        headerText.shouldContain("다이제스트")

        // 원문 보기 버튼은 클릭 추적 URL을 거쳐 원본 기사로 리다이렉트된다.
        val hasSourceButton = blocks
            .filter { it["type"] == "actions" }
            .flatMap { (it["elements"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList() }
            .any { element ->
                (element["action_id"] as? String)?.startsWith("open_source_") == true &&
                    (element["url"] as? String)?.contains("/api/track/click/slack/") == true &&
                    (element["url"] as? String)?.contains("example.com") == true
            }
        hasSourceButton shouldBe true

        val feedbackButtons = blocks
            .filter { (it["block_id"] as? String)?.startsWith("feedback_") == true }
            .flatMap { (it["elements"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList() }
            .mapNotNull { it["action_id"] as? String }
        feedbackButtons.any { it.startsWith("feedback_like:") } shouldBe true
        feedbackButtons.any { it.startsWith("feedback_dislike:") } shouldBe true
    }

    private fun seedSummary(
        categoryId: String,
        slug: String,
        originalTitle: String,
        summary: String,
        keywords: List<String>,
        importance: Float,
        sourceLink: String
    ) {
        val item = itemStore.save(
            RssItem(
                id = "",
                title = originalTitle,
                content = "Body-$slug",
                link = sourceLink,
                categoryId = categoryId
            )
        )
        summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = originalTitle,
                translatedTitle = null,
                summary = summary,
                keywords = keywords,
                importanceScore = importance,
                sourceLink = item.link,
                categoryId = categoryId,
                rssItemId = item.id
            )
        )
    }
}
