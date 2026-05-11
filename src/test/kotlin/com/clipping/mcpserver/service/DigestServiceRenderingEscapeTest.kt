package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.digest.*

import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.config.AppProperties
import com.clipping.mcpserver.service.dto.clipping.DigestItemResult
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.DigestCandidateStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.SlackChannelDailySendCountStore
import com.clipping.mcpserver.store.SummaryFeedbackStore
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment

/**
 * DigestService 의 Slack 렌더링 이중 방어 로직을 회귀 테스트한다.
 * - 줄바꿈 보존 (`buildDigestParagraphs` 의 `\s+` → `[ \t]+` 변경)
 * - mrkdwn escape (category/title/summary/keywords)
 * - URL scheme 화이트리스트 (javascript: 등 차단)
 */
class DigestServiceRenderingEscapeTest {

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
            slackMessageSender = mockk<com.clipping.mcpserver.service.port.SlackDeliveryPort>(relaxed = true),
            slackChannelDailySendCountStore = mockk<SlackChannelDailySendCountStore>(relaxed = true),
            adminReviewQueueService = mockk<AdminReviewQueueService>(relaxed = true),
            summaryFeedbackStore = mockk<SummaryFeedbackStore>(relaxed = true),
            slackBlockKitTemplateService = mockk<SlackBlockKitTemplateService>(relaxed = true),
            digestDeliveryFinalizationService = mockk<DigestDeliveryFinalizationService>(relaxed = true),
            statsService = mockk<StatsService>(relaxed = true),
            summarizer = mockk<LlmSummarizationPort>(relaxed = true),
            environment = env,
            featureFlagsService = mockk<FeatureFlagsService>(relaxed = true),
            digestPreviewService = mockk<DigestPreviewService>(relaxed = true),
            categoryDigestStateService = mockk<CategoryDigestStateService>(relaxed = true),
            digestDiffLogStore = mockk<com.clipping.mcpserver.store.DigestDiffLogStore>(relaxed = true),
        )
    }

    private fun makeItem(
        title: String = "Safe title",
        summary: String = "Safe summary.",
        sourceLink: String = "https://example.com/article",
        keywords: List<String> = listOf("AI", "trend"),
    ) = DigestItemResult(
        summaryId = "sid-1",
        title = title,
        summary = summary,
        keywords = keywords,
        importanceScore = 0.85f,
        whyImportant = "중요",
        sourceLink = sourceLink,
        createdAt = "2026-04-15T00:00:00Z",
        isFallback = false,
    )

    @Nested
    inner class `줄바꿈 보존 회귀` {

        @Test
        fun `단일 paragraph 의 내부 줄바꿈은 공백으로 축약되지 않는다`() {
            // 과거 버그: `\s+` 정규식이 `\n` 까지 flatten 했다. 이제 공백/탭만 축약한다.
            val svc = makeService()
            val summary = "line1\nline2"
            val text = svc.buildDigestText(
                categoryName = "테스트",
                totalCandidates = 1,
                items = listOf(makeItem(summary = summary)),
                maxMessageChars = 3000,
                itemSummaryMaxChars = 500,
                keywordMaxCount = 5,
                userRequestedMaxItems = 1,
            )
            // line1 과 line2 는 서로 다른 줄에 남아 있어야 한다 — 공백으로 flatten 되지 않음
            text shouldContain "line1"
            text shouldContain "line2"
            text shouldNotContain "line1 line2"
        }

        @Test
        fun `공백과 줄바꿈 혼합 입력은 정규화된 뒤 paragraph 가 두 개로 분리된다`() {
            val svc = makeService()
            // 2 paragraph: "a  " 와 "  b" 가 `\n\n` 경계로 나뉜다
            val summary = "a  \n\n  b"
            val text = svc.buildDigestText(
                categoryName = "테스트",
                totalCandidates = 1,
                items = listOf(makeItem(summary = summary)),
                maxMessageChars = 3000,
                itemSummaryMaxChars = 500,
                keywordMaxCount = 5,
                userRequestedMaxItems = 1,
            )
            // 각 paragraph 가 별도 섹션으로 렌더되고 두 내용이 모두 유지되어야 한다
            text shouldContain "📌"
            text shouldContain "🔍"
            // `a` 와 `b` 가 각각 다른 줄에 존재해야 한다 (paragraph 경계 유지)
            val aIndex = text.indexOf(" a\n")
            val bIndex = text.indexOf(" b")
            assert(aIndex >= 0 && bIndex > aIndex) {
                "paragraph 분리가 유지되지 않음: text=\n$text"
            }
        }
    }

    @Nested
    inner class `mrkdwn escape` {

        @Test
        fun `카테고리명의 별표는 escape 되어 raw 굵게 표시가 되지 않는다`() {
            val svc = makeService()
            val text = svc.buildDigestText(
                categoryName = "*Promoted*",
                totalCandidates = 0,
                items = emptyList(),
                maxMessageChars = 3000,
                itemSummaryMaxChars = 500,
                keywordMaxCount = 5,
                userRequestedMaxItems = 1,
            )
            // `*Promoted*` 그대로 나오면 Slack이 굵게 렌더 — escape 되어 `\*Promoted\*` 가 되어야 한다
            text shouldContain "\\*Promoted\\*"
        }

        @Test
        fun `제목의 HTML entity 대상 문자는 escape 된다`() {
            val svc = makeService()
            val text = svc.buildDigestText(
                categoryName = "테스트",
                totalCandidates = 1,
                items = listOf(makeItem(title = "<script>alert(1)</script>")),
                maxMessageChars = 3000,
                itemSummaryMaxChars = 500,
                keywordMaxCount = 5,
                userRequestedMaxItems = 1,
            )
            text shouldContain "&lt;script&gt;"
            text shouldNotContain "<script>"
        }
    }

    @Nested
    inner class `URL scheme 화이트리스트` {

        @Test
        fun `javascript scheme 은 링크로 노출되지 않는다`() {
            val svc = makeService()
            val text = svc.buildDigestText(
                categoryName = "테스트",
                totalCandidates = 1,
                items = listOf(makeItem(sourceLink = "javascript:alert(1)")),
                maxMessageChars = 3000,
                itemSummaryMaxChars = 500,
                keywordMaxCount = 5,
                userRequestedMaxItems = 1,
            )
            text shouldNotContain "javascript:"
            text shouldContain "원문 링크 사용 불가"
        }

        @Test
        fun `https scheme 은 정상 링크로 렌더된다`() {
            val svc = makeService()
            val text = svc.buildDigestText(
                categoryName = "테스트",
                totalCandidates = 1,
                items = listOf(makeItem(sourceLink = "https://example.com/ok")),
                maxMessageChars = 3000,
                itemSummaryMaxChars = 500,
                keywordMaxCount = 5,
                userRequestedMaxItems = 1,
            )
            text shouldContain "|원문 보기>"
        }
    }
}
