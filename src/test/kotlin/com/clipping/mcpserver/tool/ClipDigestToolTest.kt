package com.clipping.mcpserver.tool

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.model.SummaryFeedback
import com.clipping.mcpserver.service.SlackMessageSender
import com.clipping.mcpserver.service.SlackMessageSender.SendResult
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.ReviewItemDecisionStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.SummaryFeedbackStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
class ClipDigestToolTest {

    @Autowired
    lateinit var toolCallbackProvider: ToolCallbackProvider

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var itemStore: RssItemStore

    @Autowired
    lateinit var summaryStore: BatchSummaryStore

    @Autowired
    lateinit var summaryFeedbackStore: SummaryFeedbackStore

    @Autowired
    lateinit var reviewItemDecisionStore: ReviewItemDecisionStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @MockitoBean
    lateinit var slackMessageSender: SlackMessageSender

    private lateinit var categoryWithChannel: Category
    private lateinit var categoryWithoutChannel: Category
    private lateinit var highImportanceSummaryId: String
    private lateinit var lowImportanceSummaryId: String
    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun setup() {
        // PR #445 이후 admin_send_digest MCP 도구는 sendToSlack 파라미터를 제거하고
        // 항상 Slack 에 게시한다. 개별 테스트가 별도로 stub 하지 않아도 digest 합성
        // 경로가 NPE 없이 끝나도록, 기본 SendResult 를 여기서 한 번 세팅한다.
        // 전송 실패 시나리오(`bundled slack delivery fails`) 와 override 시나리오는
        // 각 테스트 내부에서 재-stub 하여 덮어쓴다.
        doReturn(SendResult(ts = "1731234567.000000", channelId = "C123DIGEST"))
            .`when`(slackMessageSender)
            .sendMessage(anyString(), anyString(), anyList(), any(), anyBoolean(), any(), any(), any())

        categoryWithChannel = categoryStore.save(
            Category(
                id = "",
                name = "DigestCat-${System.nanoTime()}",
                slackChannelId = "C123DIGEST"
            )
        )
        categoryWithoutChannel = categoryStore.save(
            Category(
                id = "",
                name = "DigestNoChannel-${System.nanoTime()}",
                slackChannelId = null
            )
        )

        highImportanceSummaryId = seedSummary(
            categoryId = categoryWithChannel.id,
            slug = "digest-slack-1",
            importance = 0.95f,
            originalTitle = "Semiconductor supply chain signal",
            translatedTitle = "반도체 공급망 핵심 신호",
            summary = "Semiconductor suppliers announced capacity changes that may impact procurement planning.",
            keywords = listOf("semiconductor", "supply", "capacity")
        )
        lowImportanceSummaryId = seedSummary(
            categoryId = categoryWithChannel.id,
            slug = "digest-slack-2",
            importance = 0.75f,
            originalTitle = "Labor policy update for remote work",
            translatedTitle = "원격근무 관련 노동정책 업데이트",
            summary = "Labor policy guidance for remote work compliance was updated with practical checkpoints.",
            keywords = listOf("labor", "policy", "remote")
        )
        seedSummary(categoryWithoutChannel.id, "digest-no-channel-1", 0.9f)
    }

    @Test
    fun `admin_send_digest should post to category slack channel and mark selected summaries as sent`() {
        // main 의 7-arg sendMessage 시그니처를 그대로 사용한다. F3 의 fallbackUsed 는 기본값(false).
        doReturn(SendResult(ts = "1731234567.123456", channelId = categoryWithChannel.slackChannelId!!))
            .`when`(slackMessageSender)
            .sendMessage(anyString(), anyString(), anyList(), any(), anyBoolean(), any(), any(), any())

        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        val result = tool.call(
            """
            {"categoryId":"${categoryWithChannel.id}","maxItems":2,"sendToSlack":true}
            """.trimIndent()
        )

        result.shouldContain("\"postedToSlack\":true")
        result.shouldContain("\"slackChannelId\":\"${categoryWithChannel.slackChannelId}\"")
        result.shouldContain("\"selectedCount\":2")
        verify(slackMessageSender, times(1)).sendMessage(anyString(), anyString(), anyList(), any(), anyBoolean(), any(), any(), any())

        summaryStore.findUnsent(categoryWithChannel.id).size shouldBe 0
    }

    @Test
    fun `admin_send_digest should keep all items unsent when bundled slack delivery fails`() {
        doAnswer {
            throw IllegalStateException("slack failure")
        }
            .`when`(slackMessageSender)
            .sendMessage(anyString(), anyString(), anyList(), any(), anyBoolean(), any(), any(), any())

        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        val result = tool.call(
            """
            {"categoryId":"${categoryWithChannel.id}","maxItems":2,"sendToSlack":true}
            """.trimIndent()
        )

        result.shouldContain("error")
        val unsentIds = summaryStore.findUnsent(categoryWithChannel.id).map { it.id }
        unsentIds.size shouldBe 2
        unsentIds.contains(lowImportanceSummaryId) shouldBe true
        unsentIds.contains(highImportanceSummaryId) shouldBe true
    }

    @Test
    fun `admin_send_digest should fail when category has no slack channel`() {
        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        val result = tool.call(
            """
            {"categoryId":"${categoryWithoutChannel.id}","sendToSlack":true}
            """.trimIndent()
        )

        result.shouldContain("error")
        result.shouldContain("Slack channel is not configured")
    }

    @Test
    fun `admin_send_digest ignores legacy sendToSlack false payload and always posts`() {
        // PR #445 (consolidate send_digest API) 에서 MCP 도구 파라미터 sendToSlack 을 제거했다.
        // LLM 이 과거 스펙 기억으로 sendToSlack:false 를 보내더라도 도구는 무시하고 항상 게시한다
        // (미리보기는 admin_pipeline 으로 분리). 회귀 방지용으로 이 분기를 직접 잠근다.
        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        val result = tool.call(
            """
            {"categoryId":"${categoryWithChannel.id}","maxItems":1,"sendToSlack":false}
            """.trimIndent()
        )

        result.shouldContain("\"postedToSlack\":true")
        result.shouldContain("\"selectedCount\":1")
        result.shouldContain("DigestCat")
    }

    @Test
    fun `admin_send_digest should use request slack channel override when provided`() {
        doReturn(SendResult(ts = "1731234567.999999", channelId = "COVERRIDE01"))
            .`when`(slackMessageSender)
            .sendMessage(anyString(), anyString(), anyList(), any(), anyBoolean(), any(), any(), any())

        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        val result = tool.call(
            """
            {"categoryId":"${categoryWithoutChannel.id}","sendToSlack":true,"slackChannelId":"COVERRIDE01"}
            """.trimIndent()
        )

        result.shouldContain("\"postedToSlack\":true")
        result.shouldContain("\"slackChannelId\":\"COVERRIDE01\"")
    }

    @Test
    fun `admin_send_digest should send concise fallback text for Slack notifications`() {
        var capturedText: String? = null
        var capturedBlocks: List<Map<String, Any?>>? = null
        // main 의 7-arg sendMessage 시그니처로 text/blocks 를 캡처한다. F3 의 fallbackUsed 는 기본값(false).
        doAnswer { invocation ->
            capturedText = invocation.getArgument(1)
            capturedBlocks = invocation.getArgument(2)
            SendResult(ts = "1731234567.222222", channelId = categoryWithChannel.slackChannelId!!)
        }
            .`when`(slackMessageSender)
            .sendMessage(anyString(), anyString(), anyList(), any(), anyBoolean(), any(), any(), any())

        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        tool.call(
            """
            {"categoryId":"${categoryWithChannel.id}","maxItems":1,"sendToSlack":true}
            """.trimIndent()
        )

        verify(slackMessageSender, times(1)).sendMessage(anyString(), anyString(), anyList(), any(), anyBoolean(), any(), any(), any())
        val fallbackText = requireNotNull(capturedText) { "fallback text should be captured" }
        fallbackText.shouldContain("반도체 공급망 핵심 신호")
        fallbackText.shouldContain(categoryWithChannel.name)
        fallbackText.shouldNotContain("importance=")
        fallbackText.shouldContain("원문")

        val blocks = requireNotNull(capturedBlocks) { "blocks should be captured" }
        val headerText = (((blocks.first()["text"] as Map<*, *>)["text"]) as String)
        headerText.shouldContain(categoryWithChannel.name)
        headerText.shouldContain("다이제스트")
    }

    @Test
    fun `admin_send_digest should include selection reason in items`() {
        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        val result = tool.call(
            """
            {"categoryId":"${categoryWithChannel.id}","maxItems":1,"sendToSlack":false}
            """.trimIndent()
        )

        val root = mapper.readTree(result)
        val item = root.path("items").first()
        item.path("whyImportant").asText().isNotBlank() shouldBe true
        item.path("whyImportant").asText().shouldContain("중요도")
    }

    @Test
    fun `admin_send_digest should reflect feedback signal in next selection`() {
        val feedbackCategory = categoryStore.save(
            Category(
                id = "",
                name = "FeedbackDigest-${System.nanoTime()}",
                slackChannelId = "CFEEDBACK01"
            )
        )
        val baselineId = seedSummary(feedbackCategory.id, "feedback-baseline", 0.81f)
        val feedbackBoostedId = seedSummary(feedbackCategory.id, "feedback-boosted", 0.74f)
        // V117 이후 summary_feedback.user_id 는 admin_users(id) 로 FK 가 걸려 있다.
        // 피드백을 주는 가상의 10명을 실제 admin_users 에 먼저 시드한다.
        repeat(10) { idx -> seedAdminUser("u-like-$idx") }
        repeat(10) { idx ->
            summaryFeedbackStore.upsert(
                SummaryFeedback(
                    id = "",
                    summaryId = feedbackBoostedId,
                    feedbackType = "LIKE",
                    userId = "u-like-$idx"
                )
            )
        }

        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        val result = tool.call(
            """
            {"categoryId":"${feedbackCategory.id}","maxItems":1,"sendToSlack":false}
            """.trimIndent()
        )

        val root = mapper.readTree(result)
        val selectedId = root.path("items").first().path("summaryId").asText()
        selectedId shouldBe feedbackBoostedId
        (selectedId == baselineId) shouldBe false
        root.path("items").first().path("whyImportant").asText().shouldContain("피드백")
    }

    @Test
    fun `admin_send_digest should auto-route low confidence item to review queue`() {
        val reviewCategory = categoryStore.save(
            Category(
                id = "",
                name = "ReviewGate-${System.nanoTime()}",
                slackChannelId = "CREVIEW01"
            )
        )
        val lowSummaryId = seedSummary(reviewCategory.id, "review-low-confidence", 0.12f)

        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        val result = tool.call(
            """
            {"categoryId":"${reviewCategory.id}","maxItems":1,"sendToSlack":false}
            """.trimIndent()
        )

        val root = mapper.readTree(result)
        root.path("selectedCount").asInt() shouldBe 0
        root.path("items").size() shouldBe 0

        val decision = reviewItemDecisionStore.findBySummaryId(lowSummaryId).shouldNotBeNull()
        decision.status shouldBe ReviewDecisionStatus.REVIEW
        decision.reason.isNullOrBlank() shouldBe false
    }

    @Test
    fun `admin_send_digest should remove semantic duplicates from selection`() {
        val dedupeCategory = categoryStore.save(
            Category(
                id = "",
                name = "DedupeDigest-${System.nanoTime()}",
                slackChannelId = "CDEDUPE01"
            )
        )
        seedSummary(
            categoryId = dedupeCategory.id,
            slug = "dedupe-a",
            importance = 0.91f,
            originalTitle = "OpenAI enterprise agent platform release",
            translatedTitle = "오픈AI 엔터프라이즈 에이전트 플랫폼 출시",
            summary = "OpenAI announced an enterprise AI agent platform for workflow automation across teams.",
            keywords = listOf("openai", "enterprise", "agent", "automation")
        )
        seedSummary(
            categoryId = dedupeCategory.id,
            slug = "dedupe-b",
            importance = 0.89f,
            originalTitle = "OpenAI enterprise agent platform released",
            translatedTitle = "오픈AI 엔터프라이즈 에이전트 플랫폼 공개",
            summary = "The enterprise AI agent platform from OpenAI focuses on team workflow automation.",
            keywords = listOf("openai", "enterprise", "agent", "workflow")
        )

        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        val result = tool.call(
            """
            {"categoryId":"${dedupeCategory.id}","maxItems":2,"sendToSlack":false}
            """.trimIndent()
        )

        val root = mapper.readTree(result)
        root.path("selectedCount").asInt() shouldBe 1
        root.path("items").size() shouldBe 1
    }

    @Test
    fun `admin_send_digest should cap selected items to seven even when maxItems is larger`() {
        val cappedCategory = categoryStore.save(
            Category(
                id = "",
                name = "DigestCap-${System.nanoTime()}",
                slackChannelId = "CCAPLIMIT"
            )
        )
        repeat(7) { idx ->
            val topics = listOf(
                "chip", "hiring", "privacy", "supply", "security", "finance", "education"
            )
            val topic = topics[idx]
            seedSummary(
                categoryId = cappedCategory.id,
                slug = "digest-cap-$idx",
                importance = 0.99f - idx * 0.01f,
                originalTitle = "$topic market update $idx",
                translatedTitle = "$topic 시장 업데이트 $idx",
                summary = "$topic domain update $idx with concrete operational implications.",
                keywords = listOf(topic, "update", "signal-$idx")
            )
        }

        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_send_digest" }
        val result = tool.call(
            """
            {"categoryId":"${cappedCategory.id}","maxItems":20,"sendToSlack":false}
            """.trimIndent()
        )

        result.shouldContain("\"selectedCount\":7")
    }

    private fun seedSummary(
        categoryId: String,
        slug: String,
        importance: Float,
        originalTitle: String = "Title-$slug",
        translatedTitle: String = "Translated-$slug",
        summary: String = "Summary-$slug",
        keywords: List<String> = listOf("agent", "clipping", slug)
    ): String {
        val item = itemStore.save(
            RssItem(
                id = "",
                title = originalTitle,
                content = "Body-$slug",
                link = "https://93.184.216.34/$slug-${System.nanoTime()}",
                categoryId = categoryId
            )
        )
        return summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = originalTitle,
                translatedTitle = translatedTitle,
                summary = summary,
                keywords = keywords,
                importanceScore = importance,
                sourceLink = item.link,
                categoryId = categoryId,
                rssItemId = item.id
            )
        ).id
    }

    /**
     * V117 FK 제약을 만족시키기 위한 최소 admin_users row 를 삽입한다.
     * 이미 존재하면 no-op — 동일 테스트의 @BeforeEach 반복 실행에도 안전하다.
     */
    private fun seedAdminUser(userId: String) {
        val exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_users WHERE id = ?",
            Int::class.java,
            userId
        ) ?: 0
        if (exists > 0) return
        val now = Timestamp.from(Instant.now())
        // H2/PostgreSQL 공통으로 동작하도록 표준 INSERT 만 사용한다.
        jdbc.update(
            """
            INSERT INTO admin_users
                (id, username, password_hash, display_name, is_active, last_login_at,
                 created_at, updated_at, role, department, approval_status,
                 approval_note, approved_by_user_id, approved_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            userId,
            "test-$userId-${System.nanoTime()}",
            "ANONYMIZED",
            "피드백 테스트 $userId",
            true,
            null,
            now,
            now,
            "USER",
            "테스트팀",
            "APPROVED",
            null,
            null,
            now
        )
    }
}
