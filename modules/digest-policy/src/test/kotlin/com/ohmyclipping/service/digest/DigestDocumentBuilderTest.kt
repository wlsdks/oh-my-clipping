package com.ohmyclipping.service.digest

import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.time.Instant

class DigestDocumentBuilderTest {

    private fun item(
        id: String,
        keywords: List<String>,
        fallback: Boolean = false,
        importanceScore: Double = 0.8,
    ) = DigestDocumentItem(
        id = id,
        title = "Title $id",
        summary = "Summary $id",
        keywords = keywords,
        importanceScore = importanceScore,
        whyImportant = "reason",
        sourceLink = "https://example.com/$id",
        createdAt = Instant.parse("2026-04-15T00:00:00Z"),
        isFallback = fallback,
    )

    @Test
    fun `키워드는 대소문자를 무시하고 최초 표기를 유지해 빈도순으로 정렬한다`() {
        val document = DigestDocumentBuilder.build(
            categoryName = "AI",
            totalCandidates = 3,
            requestedMaxItems = 3,
            keywordLimit = 3,
            items = listOf(
                item("a", listOf("AI", "Cloud")),
                item("b", listOf("ai", "Semiconductor")),
                item("c", listOf("cloud", "AI")),
            )
        )

        document.topKeywords shouldBe listOf("AI", "Cloud", "Semiconductor")
    }

    @Test
    fun `요청 수보다 선정 수가 적으면 thin-day 로 표시한다`() {
        val document = DigestDocumentBuilder.build(
            categoryName = "AI",
            totalCandidates = 5,
            requestedMaxItems = 3,
            keywordLimit = 10,
            items = listOf(item("a", listOf("AI")))
        )

        document.selectedCount shouldBe 1
        document.isThinDay shouldBe true
    }

    @Test
    fun `fallback 항목 포함 여부를 문서에서 계산한다`() {
        val document = DigestDocumentBuilder.build(
            categoryName = "AI",
            totalCandidates = 2,
            requestedMaxItems = 2,
            keywordLimit = 10,
            items = listOf(
                item("a", listOf("AI")),
                item("b", listOf("Cloud"), fallback = true),
            )
        )

        document.hasFallbackItems shouldBe true
    }

    @Test
    fun `음수 totalCandidates 는 엔진 입력 오류로 거부한다`() {
        shouldThrow<EngineInvalidInputException> {
            DigestDocumentBuilder.build(
                categoryName = "AI",
                totalCandidates = -1,
                requestedMaxItems = 3,
                keywordLimit = 10,
                items = listOf(item("a", listOf("AI"))),
            )
        }.message shouldBe "totalCandidates must be non-negative"
    }

    @Test
    fun `음수 requestedMaxItems 는 엔진 입력 오류로 거부한다`() {
        shouldThrow<EngineInvalidInputException> {
            DigestDocumentBuilder.build(
                categoryName = "AI",
                totalCandidates = 1,
                requestedMaxItems = -1,
                keywordLimit = 10,
                items = listOf(item("a", listOf("AI"))),
            )
        }.message shouldBe "requestedMaxItems must be non-negative"
    }

    @Test
    fun `비정상 item importanceScore 는 엔진 입력 오류로 거부한다`() {
        shouldThrow<EngineInvalidInputException> {
            DigestDocumentBuilder.build(
                categoryName = "AI",
                totalCandidates = 1,
                requestedMaxItems = 1,
                keywordLimit = 10,
                items = listOf(item("bad", listOf("AI"), importanceScore = Double.NaN)),
            )
        }.message shouldBe "document item importanceScore must be finite: bad"
    }
}
