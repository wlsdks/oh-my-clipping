package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.model.RuntimeSetting
import com.clipping.mcpserver.service.dto.RuleEvaluationResult
import com.clipping.mcpserver.store.RuntimeSettingStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [ReviewPolicyRuleEvaluator] 단위 테스트.
 *
 * 검증 대상:
 *  - `exclude_event_types` blacklist 룰 (카테고리별 event_type 차단)
 *  - zero-signal 룰 (OTHER + NEUTRAL + include_keywords 있음 + 매칭 0)
 *  - over-trigger 방지 invariant — include_keywords 비어있으면 절대 발동 안 됨
 *  - 우선순위 — event_type_blacklist 가 zero-signal 보다 먼저 적용
 */
class ReviewPolicyRuleEvaluatorTest {

    private val settingStore = mockk<RuntimeSettingStore>()
    private val evaluator = ReviewPolicyRuleEvaluator(settingStore)

    // ── 테스트 픽스처 ──

    private fun summary(
        id: String = "sum-1",
        categoryId: String = "cat-1",
        originalTitle: String = "중립 제목",
        summary: String = "중립 본문",
        eventType: String? = "OTHER",
        sentiment: String? = "NEUTRAL",
    ) = BatchSummary(
        id = id,
        originalTitle = originalTitle,
        summary = summary,
        sourceLink = "https://example.com/$id",
        categoryId = categoryId,
        rssItemId = "item-$id",
        eventType = eventType,
        sentiment = sentiment,
        createdAt = Instant.now(),
    )

    private fun rule(
        categoryId: String = "cat-1",
        excludeEventTypes: List<String> = emptyList(),
        includeKeywords: List<String> = emptyList(),
    ) = CategoryRule(
        categoryId = categoryId,
        includeKeywords = includeKeywords,
        excludeEventTypes = excludeEventTypes,
    )

    private fun category(id: String = "cat-1") = Category(id = id, name = "Tech")

    private fun zeroSignalEnabled(enabled: Boolean) {
        every { settingStore.findByKey(ReviewPolicyRuleEvaluator.ZERO_SIGNAL_KEY) } returns
            if (enabled) {
                RuntimeSetting(
                    key = ReviewPolicyRuleEvaluator.ZERO_SIGNAL_KEY,
                    value = "true",
                )
            } else {
                RuntimeSetting(
                    key = ReviewPolicyRuleEvaluator.ZERO_SIGNAL_KEY,
                    value = "false",
                )
            }
    }

    // ════════════════════════════════════════════
    // event_type blacklist
    // ════════════════════════════════════════════

    @Nested
    inner class `event_type_blacklist 룰` {

        @Test
        fun `blacklist 에 포함된 event_type 은 Exclude event_type_blacklist 반환`() {
            zeroSignalEnabled(false)
            val s = summary(eventType = "OPINION")
            val r = rule(excludeEventTypes = listOf("OPINION", "RUMOR"))

            val result = evaluator.evaluate(s, category(), r)

            result.shouldBeInstanceOf<RuleEvaluationResult.Exclude>()
            result.reason shouldBe "event_type_blacklist"
        }

        @Test
        fun `blacklist 미포함 event_type 은 PassThrough`() {
            zeroSignalEnabled(false)
            val s = summary(eventType = "FUNDING", sentiment = "POSITIVE")
            val r = rule(excludeEventTypes = listOf("OPINION", "RUMOR"))

            val result = evaluator.evaluate(s, category(), r)

            result shouldBe RuleEvaluationResult.PassThrough
        }

        @Test
        fun `빈 blacklist 면 PassThrough`() {
            zeroSignalEnabled(false)
            val s = summary(eventType = "OPINION", sentiment = "POSITIVE")
            val r = rule(excludeEventTypes = emptyList())

            val result = evaluator.evaluate(s, category(), r)

            result shouldBe RuleEvaluationResult.PassThrough
        }

        @Test
        fun `event_type 이 null 이면 blacklist 비교를 건너뛴다`() {
            zeroSignalEnabled(false)
            val s = summary(eventType = null, sentiment = "POSITIVE")
            val r = rule(excludeEventTypes = listOf("OPINION"))

            val result = evaluator.evaluate(s, category(), r)

            result shouldBe RuleEvaluationResult.PassThrough
        }
    }

    // ════════════════════════════════════════════
    // zero-signal 룰
    // ════════════════════════════════════════════

    @Nested
    inner class `zero_signal 룰` {

        @Test
        fun `OTHER + NEUTRAL + include_keywords 있음 + 매칭 0 이면 Exclude zero_signal`() {
            zeroSignalEnabled(true)
            val s = summary(
                originalTitle = "관련 없는 제목",
                summary = "관련 없는 본문",
            )
            val r = rule(includeKeywords = listOf("AI", "반도체"))

            val result = evaluator.evaluate(s, category(), r)

            result.shouldBeInstanceOf<RuleEvaluationResult.Exclude>()
            result.reason shouldBe "zero_signal"
        }

        @Test
        fun `include_keywords 가 비어있으면 PassThrough — over-trigger 방지`() {
            zeroSignalEnabled(true)
            val s = summary(
                originalTitle = "아무 제목",
                summary = "아무 본문",
            )
            val r = rule(includeKeywords = emptyList())

            val result = evaluator.evaluate(s, category(), r)

            // 핵심 invariant: include_keywords 가 비어있으면 절대 zero_signal 룰 발동 안 됨
            result shouldBe RuleEvaluationResult.PassThrough
        }

        @Test
        fun `runtime setting zero_signal_exclude가 false 이면 PassThrough`() {
            zeroSignalEnabled(false)
            val s = summary()
            val r = rule(includeKeywords = listOf("AI"))

            val result = evaluator.evaluate(s, category(), r)

            result shouldBe RuleEvaluationResult.PassThrough
        }

        @Test
        fun `runtime setting 이 존재하지 않으면 비활성으로 해석 — PassThrough`() {
            every { settingStore.findByKey(ReviewPolicyRuleEvaluator.ZERO_SIGNAL_KEY) } returns null
            val s = summary()
            val r = rule(includeKeywords = listOf("AI"))

            val result = evaluator.evaluate(s, category(), r)

            result shouldBe RuleEvaluationResult.PassThrough
        }

        @Test
        fun `OTHER 지만 sentiment 가 POSITIVE 이면 PassThrough`() {
            zeroSignalEnabled(true)
            val s = summary(sentiment = "POSITIVE")
            val r = rule(includeKeywords = listOf("AI"))

            val result = evaluator.evaluate(s, category(), r)

            result shouldBe RuleEvaluationResult.PassThrough
        }

        @Test
        fun `event_type 이 OTHER 가 아니면 PassThrough`() {
            zeroSignalEnabled(true)
            val s = summary(eventType = "FUNDING")
            val r = rule(includeKeywords = listOf("AI"))

            val result = evaluator.evaluate(s, category(), r)

            result shouldBe RuleEvaluationResult.PassThrough
        }

        @Test
        fun `제목에 include_keyword 가 매칭되면 PassThrough`() {
            zeroSignalEnabled(true)
            val s = summary(
                originalTitle = "AI 업계 동향 정리",
                summary = "중립 본문",
            )
            val r = rule(includeKeywords = listOf("AI", "반도체"))

            val result = evaluator.evaluate(s, category(), r)

            result shouldBe RuleEvaluationResult.PassThrough
        }

        @Test
        fun `요약 본문에 include_keyword 가 매칭되면 PassThrough`() {
            zeroSignalEnabled(true)
            val s = summary(
                originalTitle = "중립 제목",
                summary = "AI 관련 뉴스가 쏟아지고 있다",
            )
            val r = rule(includeKeywords = listOf("AI"))

            val result = evaluator.evaluate(s, category(), r)

            result shouldBe RuleEvaluationResult.PassThrough
        }

        @Test
        fun `키워드 매칭은 대소문자를 구분하지 않는다`() {
            zeroSignalEnabled(true)
            val s = summary(
                originalTitle = "AI NEWS",
                summary = "중립 본문",
            )
            val r = rule(includeKeywords = listOf("ai"))

            val result = evaluator.evaluate(s, category(), r)

            // 제목에 대소문자 달라도 매칭돼야 함 → PassThrough
            result shouldBe RuleEvaluationResult.PassThrough
        }
    }

    // ════════════════════════════════════════════
    // 우선순위 (event_type_blacklist > zero_signal)
    // ════════════════════════════════════════════

    @Nested
    inner class `룰 우선순위` {

        @Test
        fun `event_type_blacklist 가 zero_signal 보다 먼저 적용된다`() {
            zeroSignalEnabled(true)
            // 둘 다 발동 조건 충족
            val s = summary(
                eventType = "OPINION", // blacklist 에 포함
                sentiment = "NEUTRAL",
                originalTitle = "관련 없는 제목",
                summary = "관련 없는 본문",
            )
            val r = rule(
                excludeEventTypes = listOf("OPINION"),
                includeKeywords = listOf("AI"), // zero_signal 조건도 충족
            )

            val result = evaluator.evaluate(s, category(), r)

            result.shouldBeInstanceOf<RuleEvaluationResult.Exclude>()
            // zero_signal 이 아닌 event_type_blacklist 가 나와야 함
            result.reason shouldBe "event_type_blacklist"
        }
    }
}
