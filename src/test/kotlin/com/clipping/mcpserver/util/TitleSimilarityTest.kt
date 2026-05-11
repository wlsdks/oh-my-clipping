package com.clipping.mcpserver.util

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class TitleSimilarityTest {

    @Test
    fun `identical titles should have similarity 1_0`() {
        TitleSimilarity.jaccardSimilarity("Hello World", "Hello World") shouldBe 1.0
    }

    @Test
    fun `completely different titles should have low similarity`() {
        TitleSimilarity.jaccardSimilarity("Apple iPhone Release", "Bitcoin Market Crash") shouldBeLessThan 0.2
    }

    @Test
    fun `similar titles should have high similarity`() {
        TitleSimilarity.jaccardSimilarity(
            "OpenAI releases GPT-5 model",
            "OpenAI releases new GPT-5 AI model"
        ) shouldBeGreaterThan 0.6
    }

    @Test
    fun `isDuplicate should return true for very similar titles`() {
        TitleSimilarity.isDuplicate(
            "Breaking: Google launches new AI",
            "Breaking: Google launches new AI service"
        ) shouldBe true
    }

    @Test
    fun `isDuplicate should return false for different titles`() {
        TitleSimilarity.isDuplicate(
            "Apple launches iPhone 17",
            "TestCorp releases Galaxy S26"
        ) shouldBe false
    }

    @Test
    fun `should handle empty strings`() {
        TitleSimilarity.jaccardSimilarity("", "") shouldBe 1.0
        TitleSimilarity.jaccardSimilarity("Hello", "") shouldBe 0.0
    }

    @Test
    fun `should be case insensitive`() {
        TitleSimilarity.jaccardSimilarity("Hello World", "hello world") shouldBe 1.0
    }

    @Test
    fun `should handle Korean titles`() {
        TitleSimilarity.jaccardSimilarity(
            "MegaCorp 반도체 투자 확대",
            "MegaCorp 반도체 대규모 투자 확대 발표"
        ) shouldBeGreaterThan 0.5
    }

    @TestFactory
    fun `duplicate judgement bad case matrix`(): List<DynamicTest> {
        data class Case(
            val name: String,
            val left: String,
            val right: String,
            val duplicate: Boolean
        )

        val cases = listOf(
            Case(
                "punctuation noise still duplicate",
                "MegaCorp, 2026년 1분기 영업이익 30퍼센트 증가",
                "MegaCorp 2026년 1분기 영업이익 30퍼센트 증가",
                true
            ),
            Case(
                "case and punctuation noise still duplicate",
                "OpenAI releases GPT-5 model!",
                "openai releases gpt 5 model",
                true
            ),
            Case(
                "extra publisher suffix still duplicate",
                "MegaCorp 반도체 투자 확대 발표",
                "MegaCorp 반도체 투자 확대 발표 Example Daily",
                true
            ),
            Case(
                "extra short urgency word still duplicate",
                "TestCorp Energy 북미 배터리 공장 증설 발표",
                "속보 TestCorp Energy 북미 배터리 공장 증설 발표",
                true
            ),
            Case(
                "same company different metric is not duplicate",
                "MegaCorp 1분기 영업이익 30퍼센트 증가",
                "MegaCorp 1분기 매출 10퍼센트 감소",
                false
            ),
            Case(
                "same topic different company is not duplicate",
                "MegaCorp 반도체 투자 확대",
                "SemiCorp 반도체 투자 확대",
                false
            ),
            Case(
                "same company different quarter is not duplicate",
                "MegaCorp 1분기 영업이익 증가",
                "MegaCorp 2분기 영업이익 증가",
                false
            ),
            Case(
                "same theme but different event is not duplicate",
                "OpenAI launches new model for coding",
                "OpenAI hires new CFO for finance team",
                false
            ),
            Case(
                "url-like punctuation does not break duplicate",
                "AI/ML 시장 전망 2026 보고서 공개",
                "AI ML 시장 전망 2026 보고서 공개",
                true
            ),
            Case(
                "emoji is ignored as punctuation noise",
                "AI 스타트업 투자 유치 성공 🚀",
                "AI 스타트업 투자 유치 성공",
                true
            ),
            Case(
                "single different token among many remains duplicate",
                "MegaCorp 2026년 1분기 영업이익 전년 대비 30퍼센트 증가 실적 발표",
                "MegaCorp 2026년 1분기 영업이익 전년 대비 30퍼센트 증가 실적 속보",
                true
            ),
            Case(
                "two mostly different Korean titles are not duplicate",
                "SearchCo 클라우드 신규 데이터센터 착공",
                "MessengerCo 모빌리티 택시 호출 정책 개편",
                false
            )
        )

        return cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                TitleSimilarity.isDuplicate(case.left, case.right) shouldBe case.duplicate
            }
        }
    }
}
