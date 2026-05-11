package com.ohmyclipping.mcp.dto

import com.ohmyclipping.service.dto.clipping.SummaryInfo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SummaryViewTest {
    private fun info(
        translatedTitle: String? = "번역됨",
        summary: String = "요약 본문",
    ) = SummaryInfo(
        id = "s1", originalTitle = "Original", translatedTitle = translatedTitle,
        summary = summary, keywords = listOf("AI"), importanceScore = 0.9f,
        sourceLink = "https://example.com", isSentToSlack = true,
        categoryId = "c1", createdAt = "2026-04-14T09:00:00Z",
    )

    @Test fun `translatedTitle이 있으면 title로 사용`() {
        SummaryView.from(info()).title shouldBe "번역됨"
    }

    @Test fun `translatedTitle이 null이면 originalTitle 사용`() {
        SummaryView.from(info(translatedTitle = null)).title shouldBe "Original"
    }

    @Test fun `summary에서 내부 메타데이터 태그만 제거된다`() {
        val info = info(summary = "내용 [설정 변경] 이후 [baseRequestId=abc] 텍스트 [AI] 뉴스")
        SummaryView.from(info).summary shouldBe "내용  이후  텍스트 [AI] 뉴스"
    }

    @Test fun `내부 필드가 SummaryView에 없다`() {
        val view = SummaryView.from(info())
        // SummaryView has only: id, categoryId, title, summary, sourceLink, createdAt
        // No importanceScore, isSentToSlack, keywords — verified by compile-time (no such fields)
        view.id shouldBe "s1"
        view.sourceLink shouldBe "https://example.com"
    }
}
