package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.digest.*

import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.config.AppProperties
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.DigestCandidateStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.SlackChannelDailySendCountStore
import com.clipping.mcpserver.store.SummaryFeedbackStore
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment

/**
 * buildTrackingUrl 의 path-based Slack 경로 생성 검증.
 * source 태그가 URL 복사/북마크에도 유지되는지 확인한다.
 */
class DigestServiceTrackingUrlTest {

    private fun makeService(baseUrl: String): DigestService {
        val env = mockk<Environment>()
        every { env.getProperty("clipping.digest.fair_share.lambda", "0.15") } returns "0.15"
        every { env.getProperty("clipping.digest.fair_share.min_raw_score", "0.3") } returns "0.3"
        return DigestService(
            categoryStore = mockk<CategoryStore>(relaxed = true),
            summaryStore = mockk<BatchSummaryStore>(relaxed = true),
            digestCandidateStore = mockk<DigestCandidateStore>(relaxed = true),
            runtimeSettingService = mockk<RuntimeSettingService>(relaxed = true),
            appProperties = AppProperties(baseUrl = baseUrl),
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

    @Nested
    @DisplayName("buildTrackingUrl — path-based Slack URL")
    inner class PathBasedUrl {

        @Test
        fun `slack path 경로를 반환`() {
            val svc = makeService("https://app.example.com")
            val url = svc.buildTrackingUrl("summary-abc", "https://news.example.com/article")
            url shouldContain "/api/track/click/slack/summary-abc"
        }

        @Test
        fun `원본 URL 은 query parameter 로 인코딩`() {
            val svc = makeService("https://app.example.com")
            val url = svc.buildTrackingUrl("s1", "https://news.example.com/article?p=1&q=2")
            url shouldContain "url=https%3A%2F%2Fnews.example.com%2Farticle%3Fp%3D1%26q%3D2"
        }

        @Test
        fun `baseUrl trailing slash 는 제거`() {
            val svc = makeService("https://app.example.com/")
            val url = svc.buildTrackingUrl("s1", "https://news.example.com")
            url shouldContain "https://app.example.com/api/track/click/slack/s1"
            url shouldNotContain "//api/track"
        }

        @Test
        fun `summaryId 에 URL 특수문자 있으면 path segment 에 인코딩`() {
            val svc = makeService("https://app.example.com")
            // summaryId에 "/" 또는 "?" 포함 시 path segment 오염 방지
            val url = svc.buildTrackingUrl("sid/with?slash", "https://x.com")
            url shouldContain "/api/track/click/slack/sid%2Fwith%3Fslash"
        }

        @Test
        fun `기존 query-param 방식 URL 은 더 이상 생성하지 않음`() {
            val svc = makeService("https://app.example.com")
            val url = svc.buildTrackingUrl("any-sid", "https://news.example.com")
            url shouldNotContain "/api/track/click?sid="
        }
    }
}
