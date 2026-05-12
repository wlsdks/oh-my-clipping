package com.ohmyclipping.service

import com.ohmyclipping.service.digest.*

import com.ohmyclipping.service.port.LlmSummarizationPort
import com.ohmyclipping.config.AppProperties
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.DigestCandidateStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.SlackChannelDailySendCountStore
import com.ohmyclipping.store.SummaryFeedbackStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment

/**
 * `sanitizeSummaryForDisplay` 의 이모지 중복 제거 및 섹션 줄바꿈 방어 로직을 검증한다.
 *
 * LLM(페르소나 프리셋) 출력이 이상적인 포맷을 지키지 않는 현실 케이스를 보호한다:
 *   - 같은 섹션 이모지가 두 번 붙는 경우 (🎯🎯 배경 …)
 *   - 여러 섹션을 한 줄에 이어 붙이는 경우 (… 내용. 📊 핵심 내용: …)
 *   - 섹션 라벨(배경/핵심 내용/영향 분석/전망 등)이 콜론과 함께 그대로 남는 경우
 */
class DigestServiceSanitizeSummaryTest {

    private fun makeService(): DigestService {
        val env = mockk<Environment>()
        every { env.getProperty("clipping.digest.fair_share.lambda", "0.15") } returns "0.15"
        every { env.getProperty("clipping.digest.fair_share.min_raw_score", "0.3") } returns "0.3"
        return DigestService(
            categoryStore = mockk<CategoryStore>(relaxed = true),
            summaryStore = mockk<BatchSummaryStore>(relaxed = true),
            digestCandidateStore = mockk<DigestCandidateStore>(relaxed = true),
            runtimeSettingService = mockk<RuntimeSettingService>(relaxed = true),
            appProperties = AppProperties(),
            applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true),
            slackMessageSender = mockk<com.ohmyclipping.service.port.SlackDeliveryPort>(relaxed = true),
            slackChannelDailySendCountStore = mockk<SlackChannelDailySendCountStore>(relaxed = true),
            adminReviewQueueService = mockk<AdminReviewQueueService>(relaxed = true),
            summaryFeedbackStore = mockk<SummaryFeedbackStore>(relaxed = true),
            slackBlockKitTemplateService = mockk<SlackBlockKitTemplateService>(relaxed = true),
            digestDeliveryFinalizationService = mockk<DigestDeliveryFinalizationService>(relaxed = true),
            statsService = mockk<StatsService>(relaxed = true),
            summarizer = mockk<LlmSummarizationPort>(relaxed = true),
            environment = env,
            featureFlagsService = mockk<FeatureFlagsService>(relaxed = true),
            accountBasedDigestService = mockk<com.ohmyclipping.service.digest.AccountBasedDigestService>(relaxed = true),
        )
    }

    private val service = makeService()

    @Nested
    inner class `섹션 이모지 중복 제거` {

        @Test
        fun `같은 이모지가 연달아 오면 하나만 남긴다`() {
            val input = "🎯🎯 배경: 이것은 테스트입니다."
            val result = service.sanitizeSummaryForDisplay(input)
            // "배경:" 라벨도 함께 제거되므로 본문만 남는다
            result shouldContain "🎯"
            // 🎯이 연속 2번 이상 나오지 않아야 한다
            Regex("🎯\\s*🎯").containsMatchIn(result) shouldBe false
        }

        @Test
        fun `같은 이모지가 3번 연속돼도 하나로 축약된다`() {
            val input = "📰📰📰 핵심 내용: MegaCorp이 새로운 칩을 공개했습니다."
            val result = service.sanitizeSummaryForDisplay(input)
            Regex("📰\\s*📰").containsMatchIn(result) shouldBe false
            result shouldContain "MegaCorp이 새로운 칩을 공개했습니다"
        }

        @Test
        fun `서로 다른 섹션 이모지는 축약하지 않는다`() {
            val input = "🎯 배경: 소개\n\n📰 핵심 내용: 본론"
            val result = service.sanitizeSummaryForDisplay(input)
            result shouldContain "🎯"
            result shouldContain "📰"
        }
    }

    @Nested
    inner class `섹션 줄바꿈 강제` {

        @Test
        fun `한 줄에 여러 섹션이 이어져 있으면 각 섹션을 줄바꿈으로 분리한다`() {
            val input = "🎯 배경: 소개 부분입니다. 📰 핵심 내용: 본론 부분입니다. 🔮 전망: 향후 전망입니다."
            val result = service.sanitizeSummaryForDisplay(input)
            // 분리 후 각 이모지는 자기만의 라인에서 시작해야 한다
            val lines = result.split("\n").filter { it.isNotBlank() }
            lines.count { it.startsWith("🎯") } shouldBe 1
            lines.count { it.startsWith("📰") } shouldBe 1
            lines.count { it.startsWith("🔮") } shouldBe 1
        }

        @Test
        fun `줄 시작이 섹션 이모지면 추가 줄바꿈을 넣지 않는다`() {
            val input = "🎯 배경: 첫 섹션\n\n📰 핵심 내용: 둘째 섹션"
            val result = service.sanitizeSummaryForDisplay(input)
            // 결과가 과도한 빈 줄을 포함하지 않아야 한다
            result shouldNotContain "\n\n\n\n"
        }
    }

    @Nested
    inner class `섹션 라벨 제거` {

        @Test
        fun `기존 라벨뿐 아니라 새로 추가된 라벨(영향 분석, 전망 등)도 제거한다`() {
            val input = "✅ 영향 분석: 시장에 큰 파급이 있을 것이다."
            val result = service.sanitizeSummaryForDisplay(input)
            result shouldNotContain "영향 분석:"
            result shouldContain "시장에 큰 파급이 있을 것이다"
        }

        @Test
        fun `기존 배경 및 맥락 라벨 제거 로직은 그대로 유지된다 (회귀)`() {
            val input = "배경 및 맥락: 이전 회귀 테스트 보호"
            val result = service.sanitizeSummaryForDisplay(input)
            result shouldNotContain "배경 및 맥락:"
            result shouldContain "이전 회귀 테스트 보호"
        }

        @Test
        fun `알려진 섹션 이모지가 인접하면 첫 이모지만 남긴다`() {
            // LLM 이 두 개의 알려진 섹션 이모지(📌 + 💡 등) 를 연달아 출력하는 케이스.
            val input = "📌 💡 이번 개편으로 직무별 커뮤니티가 강화되었어요"
            val result = service.sanitizeSummaryForDisplay(input)

            result shouldContain "📌"
            result shouldNotContain "💡"
            result shouldContain "이번 개편으로"
        }
    }

    /**
     * `stripLeadingDecoration` — buildSummaryParts 가 label(📌/🔍/💡) 을 prepend 하기 전에
     * 본문 앞쪽 이모지·장식 문자를 제거해 중복 표시를 막는다.
     */
    @Nested
    inner class `stripLeadingDecoration 본문 앞 장식 제거` {

        private val service = makeService()

        @Test
        fun `알려진 섹션 이모지가 본문 앞에 있으면 제거한다 (실제 사용자 케이스)`() {
            // 사용자 스크린샷: "📌 📌 취업 플랫폼..." → label 추가 전 본문에서 📌 제거 → "📌 취업..." 하나만 남음
            val input = "📌 취업 플랫폼 잡코리아가 개편됐습니다"
            val result = service.stripLeadingDecoration(input)

            result shouldBe "취업 플랫폼 잡코리아가 개편됐습니다"
        }

        @Test
        fun `알려지지 않은 장식 이모지(🍃, 💼)도 본문 앞에서 제거한다`() {
            // LLM 이 임의로 추가한 장식 이모지 — section emoji 목록에 없어 sanitize 에선 안 잡힘
            val a = "🍃 이번 개편으로 직무별 커뮤니티가 강화"
            val b = "💼 직무별 커뮤니티 강화는 체류 시간을 늘립니다"

            service.stripLeadingDecoration(a) shouldBe "이번 개편으로 직무별 커뮤니티가 강화"
            service.stripLeadingDecoration(b) shouldBe "직무별 커뮤니티 강화는 체류 시간을 늘립니다"
        }

        @Test
        fun `여러 이모지가 연달아 있어도 모두 제거한다`() {
            val input = "📌 🍃 💼 본문 시작"
            service.stripLeadingDecoration(input) shouldBe "본문 시작"
        }

        @Test
        fun `bullet 마커(- 또는 •)도 함께 제거한다`() {
            service.stripLeadingDecoration("- 본문") shouldBe "본문"
            service.stripLeadingDecoration("• 본문") shouldBe "본문"
            service.stripLeadingDecoration("▸ 본문") shouldBe "본문"
        }

        @Test
        fun `본문이 한글로 시작하면 변경 없이 그대로 반환한다`() {
            val input = "이번 개편으로 직무별 커뮤니티가 강화"
            service.stripLeadingDecoration(input) shouldBe input
        }

        @Test
        fun `본문이 숫자나 영문으로 시작해도 변경 없이 그대로 반환한다`() {
            service.stripLeadingDecoration("2026년에는 이런 변화가") shouldBe "2026년에는 이런 변화가"
            service.stripLeadingDecoration("AI 기술 도입") shouldBe "AI 기술 도입"
        }
    }

    /**
     * 사용자 보고 (#453) 이후 회귀 방지용 — `summarizeForSlackText` 의 최종 출력에 대해
     * 다양한 LLM 패턴을 검증한다. 이전 PR #381 이 같은 문제를 부분적으로만 막아 #453 에서
     * 재발한 사례. 패턴이 늘어나면 여기에 fixture 추가.
     */
    @Nested
    inner class `summarizeForSlackText 다양한 LLM 패턴 회귀 방지` {

        private val service = makeService()
        private val maxChars = 1000

        @Test
        fun `깨끗한 입력 — label 만 prepend 되고 본문은 그대로 보존`() {
            val input = "이번 개편으로 직무별 커뮤니티가 강화되었습니다."
            val result = service.summarizeForSlackText(input, maxChars)

            result shouldContain "📌 이번 개편으로 직무별 커뮤니티가 강화되었습니다."
            // 본문이 한 paragraph 라 1개 SummaryPart 만 나옴
            result shouldNotContain "🔍"
            result shouldNotContain "💡"
        }

        @Test
        fun `LLM 이 같은 섹션 이모지를 두 번 출력 — label 과 합쳐 중복되지 않는다`() {
            val input = "📌 📌 본문이 시작합니다"
            val result = service.summarizeForSlackText(input, maxChars)

            // 정확히 한 번의 📌 만 등장 (label 의 📌)
            (result.split("📌").size - 1) shouldBe 1
            result shouldContain "본문이 시작합니다"
        }

        @Test
        fun `LLM 이 임의 장식 이모지(🍃 💼) 를 추가 — label 외에 어떤 이모지도 prefix 에 남지 않는다`() {
            val input = "🍃 💼 본문이 시작합니다"
            val result = service.summarizeForSlackText(input, maxChars)

            result shouldContain "📌 본문이 시작합니다"
            result shouldNotContain "🍃"
            result shouldNotContain "💼"
        }

        @Test
        fun `LLM 이 5개 bullet 모두 같은 섹션 이모지로 시작 — buildDigestParagraphs 가 3개로 합치고 label 이 깨끗`() {
            // 사용자 2026-04-20 스크린샷 케이스 재현
            val input = """
                📌 취업 플랫폼 잡코리아가 오픈채팅방 기능을 전면 개편했습니다.

                📌 이번 개편으로 직무별 커뮤니티 기능이 대폭 강화되었습니다.

                📌 이는 단순한 구인구직 정보 제공을 넘어서서, 실질적인 직무 중심의 네트워킹을 지원합니다.

                📌 직무별 커뮤니티 강화는 사용자들의 플랫폼 체류 시간을 늘리고 재방문율을 높일 것으로 예상됩니다.

                📌 이러한 기능 강화는 향후 다른 채용 플랫폼에도 유사한 커뮤니티 도입을 촉진할 수 있습니다.
            """.trimIndent()
            val result = service.summarizeForSlackText(input, maxChars)

            // label 은 정확히 [📌, 🔍, 💡] 순서
            result shouldContain "📌 취업 플랫폼"
            result shouldContain "🔍 이번 개편으로"
            result shouldContain "💡"
            // 본문에 📌 가 한 번도 더 등장하지 않아야 한다 (각 paragraph 의 leading 📌 가 strip 됨)
            (result.split("📌").size - 1) shouldBe 1
        }

        @Test
        fun `LLM 이 같은 줄에 섹션 이모지 + 장식 이모지 (📌 🍃) 콤보 — 둘 다 strip`() {
            val input = """
                📌 🍃 첫 번째 본문

                📌 📃 두 번째 본문

                📌 💼 세 번째 본문
            """.trimIndent()
            val result = service.summarizeForSlackText(input, maxChars)

            result shouldContain "첫 번째 본문"
            result shouldContain "두 번째 본문"
            result shouldContain "세 번째 본문"
            result shouldNotContain "🍃"
            result shouldNotContain "📃"
            result shouldNotContain "💼"
            // label 정확히 1개씩
            (result.split("📌").size - 1) shouldBe 1
            (result.split("🔍").size - 1) shouldBe 1
            (result.split("💡").size - 1) shouldBe 1
        }

        @Test
        fun `LLM 이 bullet 마커(• 또는 -) 사용 — 마커도 strip 되고 본문 보존`() {
            val input = """
                • 첫 번째 bullet

                - 두 번째 bullet

                ▸ 세 번째 bullet
            """.trimIndent()
            val result = service.summarizeForSlackText(input, maxChars)

            result shouldContain "첫 번째 bullet"
            result shouldContain "두 번째 bullet"
            result shouldContain "세 번째 bullet"
            result shouldNotContain "•"
            result shouldNotContain "- 두"
            result shouldNotContain "▸ 세"
        }

        @Test
        fun `LLM 이 zero-width space (BOM) 를 prefix 에 끼워넣음 — 안 보이는 문자도 strip`() {
            val input = "\uFEFF📌 본문"
            val result = service.summarizeForSlackText(input, maxChars)

            result shouldContain "📌 본문"
            (result.split("📌").size - 1) shouldBe 1
            result shouldNotContain "\uFEFF"
        }

        @Test
        fun `LLM 이 em-dash 를 본문 앞에 추가 (📌 — 본문) — 함께 strip`() {
            val input = "📌 — 핵심 본문"
            val result = service.summarizeForSlackText(input, maxChars)

            result shouldContain "📌 핵심 본문"
            // em-dash 는 strip 되지만 label 의 📌 는 살아있음
            result shouldNotContain "—"
        }

        @Test
        fun `paragraph 사이에 빈 줄 한 줄로 구분된다`() {
            val input = """
                첫 번째 paragraph

                두 번째 paragraph

                세 번째 paragraph
            """.trimIndent()
            val result = service.summarizeForSlackText(input, maxChars)

            // 각 paragraph 사이에 \n\n 으로 구분된다 (모바일 호환 — ZWS 제거)
            result shouldContain "\n\n"
            result shouldNotContain "\u200B"
        }
    }
}
