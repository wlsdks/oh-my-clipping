package com.clipping.mcpserver.service.digest

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DigestSummaryFormattingPolicyTest {

    @Nested
    inner class `섹션 이모지 정규화` {
        @Test
        fun `같은 이모지가 연달아 오면 하나만 남기고 라벨을 제거한다`() {
            val result = DigestSummaryFormattingPolicy.sanitizeSummaryForDisplay(
                "🎯🎯 배경: 이것은 테스트입니다."
            )

            result shouldContain "🎯"
            Regex("🎯\\s*🎯").containsMatchIn(result) shouldBe false
            result shouldNotContain "배경:"
        }

        @Test
        fun `한 줄에 여러 섹션이 이어지면 줄바꿈으로 분리한다`() {
            val result = DigestSummaryFormattingPolicy.sanitizeSummaryForDisplay(
                "🎯 배경: 소개입니다. 📰 핵심 내용: 본론입니다. 🔮 전망: 향후 전망입니다."
            )

            val lines = result.split("\n").filter { it.isNotBlank() }
            lines.count { it.startsWith("🎯") } shouldBe 1
            lines.count { it.startsWith("📰") } shouldBe 1
            lines.count { it.startsWith("🔮") } shouldBe 1
        }

        @Test
        fun `알려진 섹션 이모지가 인접하면 첫 이모지만 남긴다`() {
            val result = DigestSummaryFormattingPolicy.sanitizeSummaryForDisplay(
                "📌 💡 이번 개편으로 직무별 커뮤니티가 강화되었어요"
            )

            result shouldContain "📌"
            result shouldNotContain "💡"
            result shouldContain "이번 개편으로"
        }
    }

    @Nested
    inner class `요약 문단 구성` {
        @Test
        fun `paragraph 경계와 내부 줄바꿈을 보존한다`() {
            val paragraphs = DigestSummaryFormattingPolicy.buildDigestParagraphs("a  \n\n  b", maxChars = 500)

            paragraphs shouldBe listOf("a", "b")
        }

        @Test
        fun `긴 단일 문장은 두 부분으로 나눈다`() {
            val long = List(80) { "word$it" }.joinToString(" ")

            val parts = DigestSummaryFormattingPolicy.buildDigestParagraphs(long, maxChars = 1000)

            parts shouldHaveSize 2
        }

        @Test
        fun `summary part 는 장식 이모지를 제거하고 라벨을 붙인다`() {
            val parts = DigestSummaryFormattingPolicy.buildSummaryParts(
                "📌 취업 플랫폼 잡코리아가 개편됐습니다\n\n💼 직무별 커뮤니티 강화",
                maxChars = 500
            )

            parts.map { it.title } shouldBe listOf("📌", "🔍")
            parts[0].content shouldBe "취업 플랫폼 잡코리아가 개편됐습니다"
            parts[1].content shouldBe "직무별 커뮤니티 강화"
        }
    }
}
