package com.ohmyclipping.service

import com.ohmyclipping.service.digest.*

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class LlmSummarizationPortBoundaryTest {

    @Test
    fun `services should depend on llm summarization port instead of concrete summarizer`() {
        val serviceSourceRoot = Paths.get("src/main/kotlin/com/ohmyclipping/service")
        val serviceSources = listOf(
            "ClippingService.kt",
            "ItemSummarizationService.kt",
            "digest/DigestService.kt",
            "digest/DigestSelectionService.kt",
            "competitor/CompetitorArticleSummarizationService.kt",
            "competitor/CompetitorWeeklyDigestScheduler.kt"
        )

        serviceSources.forEach { fileName ->
            val source = Files.readString(serviceSourceRoot.resolve(fileName))

            source shouldContain "LlmSummarizationPort"
            source shouldNotContain "import com.ohmyclipping.ai.ClippingSummarizer"
        }
    }

    @Test
    fun `llm summarization port should not expose app models`() {
        val source = Files.readString(
            Paths.get("clipping-engine/src/main/kotlin/com/ohmyclipping/service/port/LlmSummarizationPort.kt")
        )

        source shouldContain "LlmArticleSummaryResult"
        source shouldContain "LlmDailySummaryResult"
        source shouldContain "LlmCompetitorTimelineItem"
        source shouldNotContain "com.ohmyclipping.model"
        source shouldNotContain "com.ohmyclipping.service.dto"
        source shouldNotContain "AiSummaryResponse"
        source shouldNotContain "AiDailySummaryResponse"
        source shouldNotContain "import com.ohmyclipping.service.dto.CompetitorTimelineItem"
    }

    @Test
    fun `clipping summarizer should be adapted instead of implementing the port directly`() {
        val summarizerSource = Files.readString(
            Paths.get("src/main/kotlin/com/ohmyclipping/ai/ClippingSummarizer.kt")
        )
        val adapterSource = Files.readString(
            Paths.get("src/main/kotlin/com/ohmyclipping/ai/LlmSummarizationAdapter.kt")
        )

        summarizerSource shouldNotContain ": LlmSummarizationPort"
        adapterSource shouldContain ": LlmSummarizationPort"
    }
}
