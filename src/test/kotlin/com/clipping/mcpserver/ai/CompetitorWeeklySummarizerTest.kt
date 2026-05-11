package com.clipping.mcpserver.ai

import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.observability.SchedulerRunTracker
import com.clipping.mcpserver.service.dto.CompetitorTimelineItem
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation

/**
 * ClippingSummarizer.summarizeCompetitorWeekly() 단위 테스트.
 * Gemini 주간 경쟁사 요약 프롬프트 파싱 및 엣지케이스 검증.
 */
class CompetitorWeeklySummarizerTest {

    private val chatClient = mockk<ChatClient>()
    private val promptSpec = mockk<ChatClient.ChatClientRequestSpec>()
    private val callSpec = mockk<ChatClient.CallResponseSpec>()
    private val registry = SimpleMeterRegistry()
    private val metrics = ClippingMetrics(null, registry, SchedulerRunTracker())
    private val summaryQualityValidator = mockk<SummaryQualityValidator>(relaxed = true)

    private val summarizer = ClippingSummarizer(
        chatClient = chatClient,
        metrics = metrics,
        summaryQualityValidator = summaryQualityValidator
    )

    private val periodLabel = "2026-04-05 ~ 2026-04-11"

    /** 유효한 CompetitorTimelineItem 픽스처를 생성한다. */
    private fun makeItem(
        competitorId: String = "c1",
        competitorName: String = "경쟁사A",
        title: String = "테스트 기사 제목",
        summary: String = "테스트 요약 내용"
    ) = CompetitorTimelineItem(
        summaryId = "s1",
        competitorId = competitorId,
        competitorName = competitorName,
        title = title,
        summary = summary,
        keywords = emptyList(),
        sourceLink = "https://example.com",
        importanceScore = 0.8f,
        eventType = null,
        sentiment = null,
        createdAt = "2026-04-10T10:00:00Z"
    )

    private fun stubChatClient(content: String?) {
        every { chatClient.prompt() } returns promptSpec
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callSpec
        val chatResponse = if (content != null) {
            ChatResponse(listOf(Generation(AssistantMessage(content))))
        } else {
            null
        }
        every { callSpec.chatResponse() } returns chatResponse
    }

    @Nested
    inner class `정상 응답 파싱` {

        @Test
        fun `유효한 JSON 응답을 CompetitorWeeklyInsight로 파싱한다`() {
            val validJson = """
                {
                  "competitorHighlights": [
                    {"name": "경쟁사A", "highlight": "신제품 출시로 시장 점유율 확대 중"},
                    {"name": "경쟁사B", "highlight": "글로벌 파트너십 체결로 해외 진출 가속화"}
                  ],
                  "weeklyInsight": "이번 주 경쟁사들은 공격적인 시장 확대 전략을 보였다. 경쟁사A의 신제품과 경쟁사B의 파트너십이 특히 주목된다."
                }
            """.trimIndent()
            stubChatClient(validJson)

            val articles = mapOf(
                "경쟁사A" to listOf(makeItem(competitorName = "경쟁사A")),
                "경쟁사B" to listOf(makeItem(competitorId = "c2", competitorName = "경쟁사B"))
            )

            val result = summarizer.summarizeCompetitorWeekly(articles, periodLabel)

            result.shouldNotBeNull()
            result.competitorHighlights shouldHaveSize 2
            result.competitorHighlights[0].name shouldBe "경쟁사A"
            result.competitorHighlights[0].highlight shouldBe "신제품 출시로 시장 점유율 확대 중"
            result.weeklyInsight shouldBe "이번 주 경쟁사들은 공격적인 시장 확대 전략을 보였다. 경쟁사A의 신제품과 경쟁사B의 파트너십이 특히 주목된다."
        }

        @Test
        fun `성공 시 competitor_weekly 성공 메트릭을 기록한다`() {
            val validJson = """{"competitorHighlights":[{"name":"A","highlight":"동향"}],"weeklyInsight":"인사이트"}"""
            stubChatClient(validJson)

            summarizer.summarizeCompetitorWeekly(
                mapOf("A" to listOf(makeItem(competitorName = "A"))),
                periodLabel
            )

            registry.get("clipping.summarize.calls")
                .tag("mode", "competitor_weekly")
                .tag("result", "success")
                .counter()
                .count() shouldBe 1.0
        }
    }

    @Nested
    inner class `빈 입력 처리` {

        @Test
        fun `경쟁사 기사 맵이 비어 있으면 null을 반환하고 API를 호출하지 않는다`() {
            val result = summarizer.summarizeCompetitorWeekly(emptyMap(), periodLabel)

            result.shouldBeNull()
            verify(exactly = 0) { chatClient.prompt() }
        }
    }

    @Nested
    inner class `잘못된 JSON 응답 처리` {

        @Test
        fun `Gemini가 잘못된 JSON을 반환하면 null을 반환한다`() {
            stubChatClient("이것은 JSON이 아닙니다.")

            val result = summarizer.summarizeCompetitorWeekly(
                mapOf("A" to listOf(makeItem())),
                periodLabel
            )

            result.shouldBeNull()
        }

        @Test
        fun `파싱 실패 시 competitor_weekly 실패 메트릭을 기록한다`() {
            stubChatClient("invalid-json")

            summarizer.summarizeCompetitorWeekly(
                mapOf("A" to listOf(makeItem())),
                periodLabel
            )

            registry.get("clipping.summarize.calls")
                .tag("mode", "competitor_weekly")
                .tag("result", "failure")
                .counter()
                .count() shouldBe 1.0
        }
    }

    @Nested
    inner class `마크다운 코드 블록 처리` {

        @Test
        fun `응답이 backtick json 블록으로 감싸져 있어도 올바르게 파싱한다`() {
            val wrappedJson = """
                ```json
                {"competitorHighlights":[{"name":"경쟁사X","highlight":"신규 서비스 론칭"}],"weeklyInsight":"경쟁사X가 주목된다."}
                ```
            """.trimIndent()
            stubChatClient(wrappedJson)

            val result = summarizer.summarizeCompetitorWeekly(
                mapOf("경쟁사X" to listOf(makeItem(competitorName = "경쟁사X"))),
                periodLabel
            )

            result.shouldNotBeNull()
            result.competitorHighlights shouldHaveSize 1
            result.competitorHighlights[0].name shouldBe "경쟁사X"
            result.weeklyInsight shouldBe "경쟁사X가 주목된다."
        }
    }
}
