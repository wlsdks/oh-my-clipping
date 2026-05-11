package com.ohmyclipping.service

import com.ohmyclipping.service.digest.*

import com.ohmyclipping.service.port.LlmSummarizationPort
import com.ohmyclipping.config.AppProperties
import com.ohmyclipping.service.dto.clipping.DigestItemResult
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.DigestCandidateStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.SlackChannelDailySendCountStore
import com.ohmyclipping.store.SummaryFeedbackStore
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment

/**
 * DigestService 의 thin-day 푸터 로직을 행위 기반으로 검증하는 회귀 가드.
 *
 * 선정된 기사 수가 사용자 요청 수보다 적을 때 품질 안내 푸터가 삽입되는지,
 * 그리고 수가 같을 때는 삽입되지 않는지를 **실제 렌더링 결과 문자열** 로 검증한다.
 * (이전 버전은 소스 파일을 파싱했으나 구현 리팩토링에 취약해 행위 기반으로 교체했다.)
 */
class DigestServiceThinDayFooterTest {

    private fun makeService(): DigestService {
        val env = mockk<Environment>()
        every { env.getProperty("clipping.digest.fair_share.lambda", "0.15") } returns "0.15"
        every { env.getProperty("clipping.digest.fair_share.min_raw_score", "0.3") } returns "0.3"
        every { env.getProperty("clipping.digest.lookback_hours", "720") } returns "720"
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
            digestPreviewService = mockk<DigestPreviewService>(relaxed = true),
            categoryDigestStateService = mockk<CategoryDigestStateService>(relaxed = true),
            digestDiffLogStore = mockk<com.ohmyclipping.store.DigestDiffLogStore>(relaxed = true),
        )
    }

    private fun makeItem(id: String) = DigestItemResult(
        summaryId = id,
        title = "Title-$id",
        summary = "Summary of $id",
        keywords = listOf("keyword"),
        importanceScore = 0.8f,
        whyImportant = "reason",
        sourceLink = "https://example.com/$id",
        createdAt = "2026-04-15"
    )

    @Nested
    inner class `buildDigestText 행위 검증` {

        @Test
        fun `선정 수가 요청 수보다 적으면 thin-day 푸터가 출력에 포함된다`() {
            val service = makeService()
            val items = listOf(makeItem("1"), makeItem("2"))
            val text = service.buildDigestText(
                categoryName = "테스트 카테고리",
                totalCandidates = 10,
                items = items,
                maxMessageChars = 10000,
                itemSummaryMaxChars = 500,
                keywordMaxCount = 5,
                userRequestedMaxItems = 5
            )
            assertThat(text)
                .withFailMessage("선정 2건 / 요청 5건인 경우 thin-day 푸터가 포함돼야 합니다.")
                .contains("품질 기준을 넘은 기사가 2건")
            assertThat(text).contains("설정: 5건")
        }

        @Test
        fun `선정 수와 요청 수가 같으면 thin-day 푸터가 출력에 포함되지 않는다`() {
            val service = makeService()
            val items = listOf(makeItem("1"), makeItem("2"), makeItem("3"))
            val text = service.buildDigestText(
                categoryName = "테스트 카테고리",
                totalCandidates = 10,
                items = items,
                maxMessageChars = 10000,
                itemSummaryMaxChars = 500,
                keywordMaxCount = 5,
                userRequestedMaxItems = 3
            )
            assertThat(text)
                .withFailMessage("선정 수 == 요청 수인 경우 thin-day 푸터가 포함되면 안 됩니다.")
                .doesNotContain("품질 기준을 넘은 기사")
        }

        @Test
        fun `선정 수가 0건이면 빈 요약 안내 문구만 출력한다`() {
            val service = makeService()
            val text = service.buildDigestText(
                categoryName = "테스트 카테고리",
                totalCandidates = 0,
                items = emptyList(),
                maxMessageChars = 10000,
                itemSummaryMaxChars = 500,
                keywordMaxCount = 5,
                userRequestedMaxItems = 5
            )
            assertThat(text)
                .withFailMessage("항목이 비어 있을 때는 thin-day 푸터가 출력되면 안 됩니다.")
                .doesNotContain("품질 기준을 넘은 기사")
            assertThat(text).contains("전송할 요약이 없습니다")
        }

        @Test
        fun `선정 수가 요청 수보다 적은 특수 케이스에서도 푸터가 정확한 숫자로 출력된다`() {
            val service = makeService()
            val items = listOf(makeItem("1"))
            val text = service.buildDigestText(
                categoryName = "테스트 카테고리",
                totalCandidates = 10,
                items = items,
                maxMessageChars = 10000,
                itemSummaryMaxChars = 500,
                keywordMaxCount = 5,
                userRequestedMaxItems = 7
            )
            // 선정 수(items.size)가 두 번 노출돼야 한다 ("기사가 1건이라 1건만 보내드려요").
            assertThat(text).contains("품질 기준을 넘은 기사가 1건")
            assertThat(text).contains("1건만 보내드려요")
            assertThat(text).contains("설정: 7건")
        }
    }
}
