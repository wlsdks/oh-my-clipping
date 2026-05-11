package com.ohmyclipping.ai

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.service.dto.clipping.AiSummaryResponse
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class SummaryQualityValidatorTest {

    private val validator = SummaryQualityValidator(
        properties = ClippingMcpServerProperties(
            summaryMinChars = 40,
            summaryMaxChars = 160,
            summaryMinParagraphCount = 2,
            summaryMinSentenceCount = 3,
            summaryKeywordMinCount = 3,
            summaryKeywordMaxCount = 5
        )
    )

    @Test
    fun `should reject too short summary`() {
        val result = validator.validate(
            title = "짧은 요약 테스트",
            response = AiSummaryResponse(
                translatedTitle = null,
                summary = "너무 짧음",
                keywords = listOf("ai", "test"),
                importanceScore = 0.7f
            )
        )

        result.accepted.shouldBeFalse()
        (result.normalized == null).shouldBeTrue()
    }

    @Test
    fun `should clamp importance and normalize keywords`() {
        val result = validator.validate(
            title = "품질 검증 제목",
            response = AiSummaryResponse(
                translatedTitle = "  번역 제목  ",
                summary = """
                    첫째 문장은 이슈의 핵심 변화를 설명합니다. 둘째 문장은 적용 범위와 영향을 구체화합니다.

                    셋째 문장은 조직이 당장 검토해야 할 실행 포인트를 제시합니다.
                """.trimIndent(),
                keywords = listOf(" AI ", "AI", "agent", "", "clipping", "quality", "signal", "signal"),
                importanceScore = 1.7f
            )
        )

        result.accepted.shouldBeTrue()
        val normalized = result.normalized!!
        normalized.importanceScore shouldBe 1.0f
        normalized.keywords.size shouldBe 5
        normalized.keywords shouldHaveSize 5
        normalized.translatedTitle shouldBe "번역 제목"
    }

    @Test
    fun `should generate fallback keywords when keywords are missing`() {
        val result = validator.validate(
            title = "OpenAI 에이전트 클리핑 시스템",
            response = AiSummaryResponse(
                translatedTitle = null,
                summary = """
                    OpenAI 에이전트가 클리핑 시스템에서 요약 품질을 높이기 위한 기본 구조를 설명합니다.
                    이 구조는 중요도 평가와 근거 중심 요약을 함께 적용하도록 설계되었습니다.
                    결과적으로 팀은 더 빠르게 우선순위를 판단하고 후속 행동을 결정할 수 있습니다.
                """.trimIndent(),
                keywords = emptyList(),
                importanceScore = 0.4f
            )
        )

        result.accepted.shouldBeTrue()
        result.normalized!!.keywords.size shouldBe 3
    }

    @Test
    fun `should split long single paragraph into summary and implication paragraphs`() {
        val result = validator.validate(
            title = "단일 문단 보정",
            response = AiSummaryResponse(
                translatedTitle = null,
                summary = "첫째 문장은 핵심 사실을 설명합니다. 둘째 문장은 배경 맥락을 보완합니다. 셋째 문장은 현업에 주는 시사점을 전달합니다.",
                keywords = listOf("핵심", "배경", "시사점"),
                importanceScore = 0.7f
            )
        )

        result.accepted.shouldBeTrue()
        val normalized = result.normalized!!.summary
        normalized shouldContain "\n\n"
    }

    @Test
    fun `should reject summary with too few sentences`() {
        val result = validator.validate(
            title = "문장 수 부족",
            response = AiSummaryResponse(
                translatedTitle = null,
                summary = "충분히 긴 문장이라 길이 요건은 넘지만 문장 수 요건은 넘지 못하도록 작성합니다.".repeat(2),
                keywords = listOf("요약", "품질", "검증"),
                importanceScore = 0.6f
            )
        )

        result.accepted.shouldBeFalse()
        (result.normalized == null).shouldBeTrue()
    }
}
