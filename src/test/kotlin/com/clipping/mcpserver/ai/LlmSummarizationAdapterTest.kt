package com.clipping.mcpserver.ai

import com.clipping.mcpserver.service.dto.clipping.AiDailySummaryResponse
import com.clipping.mcpserver.service.dto.clipping.AiSummaryResponse
import com.clipping.mcpserver.model.Language
import com.clipping.mcpserver.model.Persona
import com.clipping.mcpserver.service.port.CompetitorHighlight
import com.clipping.mcpserver.service.port.CompetitorWeeklyInsight
import com.clipping.mcpserver.service.port.ImportanceScreeningResult
import com.clipping.mcpserver.service.port.LlmArticleLanguage
import com.clipping.mcpserver.service.port.LlmCompetitorTimelineItem
import com.clipping.mcpserver.service.port.LlmPersona
import com.clipping.mcpserver.service.port.LlmTokenUsage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test

class LlmSummarizationAdapterTest {

    private val clippingSummarizer = mockk<ClippingSummarizer>()
    private val adapter = LlmSummarizationAdapter(clippingSummarizer)

    @Test
    fun `summarizeArticle maps port language and persona to existing summarizer`() {
        val persona = LlmPersona(
            id = "persona-1",
            name = "Analyst",
            systemPrompt = "Be concise",
            summaryStyle = "bullet",
            targetAudience = "exec",
            isPreset = true,
            currentVersion = 3
        )
        val personaSlot = slot<Persona>()
        every {
            clippingSummarizer.summarizeArticle("title", "content", Language.KOREAN, capture(personaSlot))
        } returns AiSummaryResponse(
            translatedTitle = "번역 제목",
            summary = "요약",
            keywords = listOf("AI"),
            importanceScore = 0.8f,
            sentiment = "POSITIVE",
            eventType = "LAUNCH"
        )

        val result = adapter.summarizeArticle("title", "content", LlmArticleLanguage.KOREAN, persona)

        personaSlot.captured.id shouldBe "persona-1"
        personaSlot.captured.summaryStyle shouldBe "bullet"
        result?.translatedTitle shouldBe "번역 제목"
        result?.summary shouldBe "요약"
        result?.keywords shouldContainExactly listOf("AI")
        result?.importanceScore shouldBe 0.8f
        result?.sentiment shouldBe "POSITIVE"
        result?.eventType shouldBe "LAUNCH"
    }

    @Test
    fun `generateDailySummary maps daily summary result dto`() {
        every {
            clippingSummarizer.generateDailySummary("Tech", "summaries", 2)
        } returns AiDailySummaryResponse(title = "오늘의 Tech", topicKeywords = listOf("cloud", "ai"))

        val result = adapter.generateDailySummary("Tech", "summaries", 2)

        result?.title shouldBe "오늘의 Tech"
        result?.topicKeywords shouldContainExactly listOf("cloud", "ai")
    }

    @Test
    fun `summarizeCompetitorWeekly maps timeline items to existing scheduler dto`() {
        val captured = slot<Map<String, List<com.clipping.mcpserver.service.dto.CompetitorTimelineItem>>>()
        every {
            clippingSummarizer.summarizeCompetitorWeekly(capture(captured), "2026-W18")
        } returns CompetitorWeeklyInsight(
            competitorHighlights = listOf(CompetitorHighlight("A", "A highlight")),
            weeklyInsight = "weekly"
        )

        val result = adapter.summarizeCompetitorWeekly(
            mapOf(
                "A" to listOf(
                    LlmCompetitorTimelineItem(
                        summaryId = "s1",
                        competitorId = "c1",
                        competitorName = "A",
                        title = "title",
                        summary = "summary",
                        keywords = listOf("kw"),
                        sourceLink = "https://example.com",
                        importanceScore = 0.7f,
                        eventType = "PARTNERSHIP",
                        sentiment = "NEUTRAL",
                        createdAt = "2026-05-01T00:00:00Z"
                    )
                )
            ),
            "2026-W18"
        )

        captured.captured["A"]?.single()?.summaryId shouldBe "s1"
        captured.captured["A"]?.single()?.competitorName shouldBe "A"
        result?.weeklyInsight shouldBe "weekly"
    }

    @Test
    fun `token and screening calls delegate without remapping behavior`() {
        val tokenUsage = LlmTokenUsage(promptTokens = 10, completionTokens = 4)
        val screening = ImportanceScreeningResult(
            score = 0.6f,
            status = "SUCCEEDED",
            inputChars = 20,
            outputChars = 5,
            durationMs = 30
        )
        every { clippingSummarizer.getLastTokenUsage() } returns tokenUsage
        every { clippingSummarizer.getLastRejectReason() } returns "QUALITY_LOW"
        every { clippingSummarizer.screenImportance("title", "preview") } returns screening
        every { clippingSummarizer.translateToKorean("hello", "제목") } returns "안녕"

        adapter.getLastTokenUsage() shouldBe tokenUsage
        adapter.getLastRejectReason() shouldBe "QUALITY_LOW"
        adapter.screenImportance("title", "preview") shouldBe screening
        adapter.translateToKorean("hello", "제목") shouldBe "안녕"
    }
}
