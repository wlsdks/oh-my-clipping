package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.digest.*

import com.clipping.mcpserver.service.competitor.CompetitorCollectionService
import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.config.AppProperties
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.DigestCandidateStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.SlackChannelDailySendCountStore
import com.clipping.mcpserver.store.SummaryFeedbackStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment

/**
 * DigestService가 경쟁사 카테고리(__competitor__)를 거부하는지 검증한다.
 */
class DigestServiceCompetitorGuardTest {

    private val service = DigestService(
        categoryStore = mockk<CategoryStore>(),
        summaryStore = mockk<BatchSummaryStore>(),
        digestCandidateStore = mockk<DigestCandidateStore>(),
        runtimeSettingService = mockk<RuntimeSettingService>(),
        appProperties = AppProperties(),
        applicationEventPublisher = mockk<ApplicationEventPublisher>(),
        slackMessageSender = mockk<com.clipping.mcpserver.service.port.SlackDeliveryPort>(),
        slackChannelDailySendCountStore = mockk<SlackChannelDailySendCountStore>(),
        adminReviewQueueService = mockk<AdminReviewQueueService>(),
        summaryFeedbackStore = mockk<SummaryFeedbackStore>(),
        slackBlockKitTemplateService = mockk<SlackBlockKitTemplateService>(),
        digestDeliveryFinalizationService = mockk<DigestDeliveryFinalizationService>(),
        statsService = mockk<StatsService>(),
        summarizer = mockk<LlmSummarizationPort>(),
        environment = mockk<Environment>(),
        featureFlagsService = mockk<FeatureFlagsService>(relaxed = true),
        digestPreviewService = mockk<DigestPreviewService>(relaxed = true),
        categoryDigestStateService = mockk<CategoryDigestStateService>(relaxed = true),
        digestDiffLogStore = mockk<com.clipping.mcpserver.store.DigestDiffLogStore>(relaxed = true),
    )

    @Test
    fun `경쟁사 카테고리로 다이제스트 요청 시 InvalidInputException을 던진다`() {
        val ex = shouldThrow<InvalidInputException> {
            service.digest(
                categoryId = CompetitorCollectionService.COMPETITOR_CATEGORY_ID,
                maxItems = null,
                unsentOnly = null,
                sendToSlack = null,
                slackChannelId = null
            )
        }
        ex.message shouldContain "경쟁사 카테고리"
    }
}
