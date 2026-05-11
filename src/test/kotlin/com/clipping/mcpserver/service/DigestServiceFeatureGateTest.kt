package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.digest.*

import com.clipping.mcpserver.service.competitor.CompetitorCollectionService
import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.config.AppProperties
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.service.port.SlackDeliveryResult
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.DigestCandidateStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.DigestDiffLogStore
import com.clipping.mcpserver.store.SlackChannelDailySendCountStore
import com.clipping.mcpserver.store.SlackChannelSendReservation
import com.clipping.mcpserver.store.SummaryFeedbackStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment
import java.time.Instant

/**
 * DigestService 가 account-based feature flag gate 를 올바르게 참조하고
 * flag=ON 시 shadow/real-send 경로를 올바르게 분기하는지 검증한다.
 */
class DigestServiceFeatureGateTest {

    private val flags = mockk<FeatureFlagsService>()
    private val categoryStore = mockk<CategoryStore>()
    private val summaryStore = mockk<BatchSummaryStore>()
    private val digestCandidateStore = mockk<DigestCandidateStore>()
    private val slackMessageSender = mockk<com.clipping.mcpserver.service.port.SlackDeliveryPort>(relaxed = true)
    private val digestPreviewService = mockk<DigestPreviewService>(relaxed = true)
    private val digestDiffLogStore = mockk<DigestDiffLogStore>(relaxed = true)
    private val categoryDigestStateService = mockk<CategoryDigestStateService>(relaxed = true)
    private val slackChannelDailySendCountStore = mockk<SlackChannelDailySendCountStore>(relaxed = true)

    /** account-based 실제 전송 테스트에서 사용 — 쿼터 허용 stub */
    private fun stubReservationAllowed() {
        every {
            slackChannelDailySendCountStore.reserveSlot(any(), any(), any())
        } returns SlackChannelSendReservation(messageCount = 1, allowed = true)
        every { slackChannelDailySendCountStore.releaseSlot(any(), any()) } just Runs
    }

    /** account-based 실제 전송 테스트에서 사용 — 쿼터 초과 stub */
    private fun stubReservationDenied() {
        every {
            slackChannelDailySendCountStore.reserveSlot(any(), any(), any())
        } returns SlackChannelSendReservation(messageCount = 5, allowed = false)
    }

    /** blocks JSON 이 포함된 non-EMPTY dry-run 결과 */
    private val nonEmptyPreview = DigestPreviewService.DigestDryRunResult(
        mode = "TOPIC_ONLY",
        blocks = """[{"type":"section","text":{"type":"mrkdwn","text":"hello"}}]""",
        sectionState = listOf(
            DigestPreviewService.SectionStateSnapshot(
                kind = "topic", articlesCount = 3, badgedCount = 0, isEmpty = false
            )
        ),
    )

    /** DUAL_SECTION dry-run 결과 (legend counter 검증용) */
    private val dualSectionPreview = DigestPreviewService.DigestDryRunResult(
        mode = "DUAL_SECTION",
        blocks = """[{"type":"section","text":{"type":"mrkdwn","text":"dual"}}]""",
        sectionState = listOf(
            DigestPreviewService.SectionStateSnapshot(kind = "topic", articlesCount = 2, badgedCount = 1, isEmpty = false),
            DigestPreviewService.SectionStateSnapshot(kind = "account", articlesCount = 1, badgedCount = 0, isEmpty = false),
        ),
    )

    private fun makeService(): DigestService {
        val env = mockk<Environment>()
        every { env.getProperty("clipping.digest.fair_share.lambda", "0.15") } returns "0.15"
        every { env.getProperty("clipping.digest.fair_share.min_raw_score", "0.3") } returns "0.3"
        every { env.getProperty(eq("clipping.digest.lookback_hours"), any<String>()) } returns "168"

        // 후보 조회가 빈 결과를 반환하도록 스텁 (legacy 경로 조기 반환 유도)
        every {
            digestCandidateStore.findDigestCandidatesWithSource(any(), any<Instant>(), any())
        } returns (emptyList<BatchSummary>() to emptyMap<String, String?>())

        return DigestService(
            categoryStore = categoryStore,
            summaryStore = summaryStore,
            digestCandidateStore = digestCandidateStore,
            runtimeSettingService = mockk<RuntimeSettingService>(relaxed = true),
            appProperties = AppProperties(),
            applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true),
            slackMessageSender = slackMessageSender,
            slackChannelDailySendCountStore = slackChannelDailySendCountStore,
            adminReviewQueueService = mockk<AdminReviewQueueService>(relaxed = true),
            summaryFeedbackStore = mockk<SummaryFeedbackStore>(relaxed = true),
            slackBlockKitTemplateService = mockk<SlackBlockKitTemplateService>(relaxed = true),
            digestDeliveryFinalizationService = mockk<DigestDeliveryFinalizationService>(relaxed = true),
            statsService = mockk<StatsService>(relaxed = true),
            summarizer = mockk<LlmSummarizationPort>(relaxed = true),
            environment = env,
            featureFlagsService = flags,
            digestPreviewService = digestPreviewService,
            categoryDigestStateService = categoryDigestStateService,
            digestDiffLogStore = digestDiffLogStore,
        )
    }

    private fun stubCategory(categoryId: String, slackChannelId: String = "C-TEST"): Category {
        val cat = mockk<Category>(relaxed = true)
        every { cat.id } returns categoryId
        every { cat.name } returns "Test Category"
        every { cat.slackChannelId } returns slackChannelId
        every { categoryStore.findById(categoryId) } returns cat
        return cat
    }

    @Nested
    inner class `feature flag gate` {

        @Test
        fun `flag off — featureFlagsService 가 호출된다`() {
            every { flags.isAccountBasedDigestEnabled("cat-1") } returns false
            every { categoryStore.findById("cat-1") } returns mockk<Category>(relaxed = true)

            val service = makeService()
            service.digest(categoryId = "cat-1", maxItems = null, unsentOnly = null,
                sendToSlack = false, slackChannelId = null)

            verify(exactly = 1) { flags.isAccountBasedDigestEnabled("cat-1") }
        }

        @Test
        fun `flag on — featureFlagsService 가 호출된다`() {
            every { flags.isAccountBasedDigestEnabled("cat-2") } returns true
            every { flags.isShadowModeEnabled("cat-2") } returns false
            stubCategory("cat-2")
            every { digestPreviewService.dryRunForCategory("cat-2") } returns DigestPreviewService.DigestDryRunResult(
                mode = "EMPTY", blocks = "[]"
            )
            // EMPTY → falls through to legacy
            every { categoryStore.findById("cat-2") } returns mockk<Category>(relaxed = true)

            val service = makeService()
            service.digest(categoryId = "cat-2", maxItems = null, unsentOnly = null,
                sendToSlack = false, slackChannelId = null)

            verify(exactly = 1) { flags.isAccountBasedDigestEnabled("cat-2") }
        }

        @Test
        fun `경쟁사 카테고리도 flag 조회 후 거부된다`() {
            every { flags.isAccountBasedDigestEnabled(CompetitorCollectionService.COMPETITOR_CATEGORY_ID) } returns false

            val service = makeService()
            shouldThrow<com.clipping.mcpserver.error.InvalidInputException> {
                service.digest(categoryId = CompetitorCollectionService.COMPETITOR_CATEGORY_ID,
                    maxItems = null, unsentOnly = null, sendToSlack = false, slackChannelId = null)
            }

            verify(exactly = 1) { flags.isAccountBasedDigestEnabled(CompetitorCollectionService.COMPETITOR_CATEGORY_ID) }
        }
    }

    @Nested
    inner class `flag OFF — legacy path` {

        @Test
        fun `dryRunForCategory 가 호출되지 않는다`() {
            every { flags.isAccountBasedDigestEnabled("cat-legacy") } returns false
            every { categoryStore.findById("cat-legacy") } returns mockk<Category>(relaxed = true)

            makeService().digest(categoryId = "cat-legacy", maxItems = null, unsentOnly = null,
                sendToSlack = false, slackChannelId = null)

            // account-based 경로가 실행되지 않아야 한다
            verify(exactly = 0) { digestPreviewService.dryRunForCategory(any()) }
        }

        @Test
        fun `후보 조회(legacy 경로 마커)가 실행된다`() {
            every { flags.isAccountBasedDigestEnabled("cat-legacy2") } returns false
            every { categoryStore.findById("cat-legacy2") } returns mockk<Category>(relaxed = true)

            makeService().digest(categoryId = "cat-legacy2", maxItems = null, unsentOnly = null,
                sendToSlack = false, slackChannelId = null)

            verify(atLeast = 1) { digestCandidateStore.findDigestCandidatesWithSource(any(), any(), any()) }
        }
    }

    @Nested
    inner class `flag ON + shadow OFF + non-EMPTY — real send` {

        @Test
        fun `slackMessageSender sendMessage 가 non-empty blocks 로 호출된다`() {
            stubReservationAllowed()
            every { flags.isAccountBasedDigestEnabled("cat-send") } returns true
            every { flags.isShadowModeEnabled("cat-send") } returns false
            stubCategory("cat-send")
            every { digestPreviewService.dryRunForCategory("cat-send") } returns nonEmptyPreview

            val blocksSlot = slot<List<Map<String, Any?>>>()
            every {
                slackMessageSender.sendMessage(any(), any(), capture(blocksSlot), any(), any(), any(), any(), any())
            } returns SlackDeliveryResult(ts = "12345.67890", channelId = "C-TEST")

            makeService().digest(categoryId = "cat-send", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            blocksSlot.captured.shouldNotBeEmpty()
        }

        @Test
        fun `dryRunForCategory 가 호출된다`() {
            stubReservationAllowed()
            every { flags.isAccountBasedDigestEnabled("cat-send2") } returns true
            every { flags.isShadowModeEnabled("cat-send2") } returns false
            stubCategory("cat-send2")
            every { digestPreviewService.dryRunForCategory("cat-send2") } returns nonEmptyPreview

            makeService().digest(categoryId = "cat-send2", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            verify(exactly = 1) { digestPreviewService.dryRunForCategory("cat-send2") }
        }

        @Test
        fun `digestDiffLogStore insertIfAbsent 는 호출되지 않는다`() {
            stubReservationAllowed()
            every { flags.isAccountBasedDigestEnabled("cat-send3") } returns true
            every { flags.isShadowModeEnabled("cat-send3") } returns false
            stubCategory("cat-send3")
            every { digestPreviewService.dryRunForCategory("cat-send3") } returns nonEmptyPreview

            makeService().digest(categoryId = "cat-send3", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            verify(exactly = 0) { digestDiffLogStore.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `DUAL_SECTION 모드면 sendResult ok=true 일 때 legend counter 가 증가한다`() {
            stubReservationAllowed()
            every { flags.isAccountBasedDigestEnabled("cat-dual") } returns true
            every { flags.isShadowModeEnabled("cat-dual") } returns false
            stubCategory("cat-dual")
            every { digestPreviewService.dryRunForCategory("cat-dual") } returns dualSectionPreview
            // sendMessage 가 ok=true 로 명시적으로 반환 (relaxed Boolean default=false 이므로 반드시 stub 필요)
            every {
                slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            } returns SlackDeliveryResult(ts = "ts-dual", channelId = "C-TEST", ok = true)

            makeService().digest(categoryId = "cat-dual", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            verify(exactly = 1) { categoryDigestStateService.incrementLegendDisplayCount("cat-dual") }
        }

        @Test
        fun `TOPIC_ONLY 모드면 legend counter 가 호출되지 않는다`() {
            stubReservationAllowed()
            every { flags.isAccountBasedDigestEnabled("cat-topic") } returns true
            every { flags.isShadowModeEnabled("cat-topic") } returns false
            stubCategory("cat-topic")
            every { digestPreviewService.dryRunForCategory("cat-topic") } returns nonEmptyPreview // TOPIC_ONLY

            makeService().digest(categoryId = "cat-topic", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            verify(exactly = 0) { categoryDigestStateService.incrementLegendDisplayCount(any()) }
        }

        @Test
        fun `DUAL_SECTION + sendMessage ok=false 이면 legend counter 가 호출되지 않는다`() {
            // Blocking #3 guard: legend counter must NOT fire when Slack send fails
            stubReservationAllowed()
            every { flags.isAccountBasedDigestEnabled("cat-dual-fail") } returns true
            every { flags.isShadowModeEnabled("cat-dual-fail") } returns false
            stubCategory("cat-dual-fail")
            every { digestPreviewService.dryRunForCategory("cat-dual-fail") } returns dualSectionPreview
            // sendMessage 반환 ok=false (전송 실패 시뮬레이션)
            every {
                slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            } returns SlackDeliveryResult(ts = "", channelId = "C-TEST", ok = false)

            makeService().digest(categoryId = "cat-dual-fail", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            // 전송 실패 시 legend counter 는 절대 증가하면 안 된다
            verify(exactly = 0) { categoryDigestStateService.incrementLegendDisplayCount(any()) }
        }

        @Test
        fun `일일 쿼터 초과 시 sendMessage 가 호출되지 않고 postedToSlack=false 로 반환된다`() {
            // Blocking #2: daily quota reservation prevents send when exhausted
            stubReservationDenied()
            every { flags.isAccountBasedDigestEnabled("cat-quota") } returns true
            every { flags.isShadowModeEnabled("cat-quota") } returns false
            stubCategory("cat-quota")
            every { digestPreviewService.dryRunForCategory("cat-quota") } returns nonEmptyPreview

            val result = makeService().digest(categoryId = "cat-quota", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
            result.postedToSlack shouldBe false
        }

        @Test
        fun `reserveSlot 이 sendMessage 보다 먼저 호출된다`() {
            // Blocking #2: ordering — reserveSlot must precede sendMessage
            var reserveCalledFirst = false
            var sendCalledAfterReserve = false

            every {
                slackChannelDailySendCountStore.reserveSlot(any(), any(), any())
            } answers {
                reserveCalledFirst = true
                SlackChannelSendReservation(messageCount = 1, allowed = true)
            }
            every { slackChannelDailySendCountStore.releaseSlot(any(), any()) } just Runs

            every { flags.isAccountBasedDigestEnabled("cat-order") } returns true
            every { flags.isShadowModeEnabled("cat-order") } returns false
            stubCategory("cat-order")
            every { digestPreviewService.dryRunForCategory("cat-order") } returns nonEmptyPreview
            every {
                slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            } answers {
                sendCalledAfterReserve = reserveCalledFirst
                SlackDeliveryResult(ts = "ts", channelId = "C-TEST")
            }

            makeService().digest(categoryId = "cat-order", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            reserveCalledFirst shouldBe true
            sendCalledAfterReserve shouldBe true
        }
    }

    @Nested
    inner class `flag ON + shadow ON — shadow mode` {

        @Test
        fun `sendMessage 가 호출되지 않는다`() {
            every { flags.isAccountBasedDigestEnabled("cat-shadow") } returns true
            every { flags.isShadowModeEnabled("cat-shadow") } returns true
            stubCategory("cat-shadow")
            every { digestPreviewService.dryRunForCategory("cat-shadow") } returns nonEmptyPreview

            makeService().digest(categoryId = "cat-shadow", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `digestDiffLogStore insertIfAbsent 가 정확히 1번 호출된다`() {
            every { flags.isAccountBasedDigestEnabled("cat-shadow2") } returns true
            every { flags.isShadowModeEnabled("cat-shadow2") } returns true
            stubCategory("cat-shadow2")
            every { digestPreviewService.dryRunForCategory("cat-shadow2") } returns nonEmptyPreview

            makeService().digest(categoryId = "cat-shadow2", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            verify(exactly = 1) {
                digestDiffLogStore.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `결과는 postedToSlack=false 로 반환된다`() {
            every { flags.isAccountBasedDigestEnabled("cat-shadow3") } returns true
            every { flags.isShadowModeEnabled("cat-shadow3") } returns true
            stubCategory("cat-shadow3")
            every { digestPreviewService.dryRunForCategory("cat-shadow3") } returns nonEmptyPreview

            val result = makeService().digest(categoryId = "cat-shadow3", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null)

            result.postedToSlack shouldBe false
        }
    }

    @Nested
    inner class `flag ON + dry-run EMPTY — legacy fallback` {

        @Test
        fun `dryRunForCategory 가 EMPTY 를 반환하면 legacy 후보 조회가 실행된다`() {
            every { flags.isAccountBasedDigestEnabled("cat-empty") } returns true
            every { flags.isShadowModeEnabled("cat-empty") } returns false
            every { categoryStore.findById("cat-empty") } returns mockk<Category>(relaxed = true)
            every { digestPreviewService.dryRunForCategory("cat-empty") } returns DigestPreviewService.DigestDryRunResult(
                mode = "EMPTY", blocks = "[]"
            )

            makeService().digest(categoryId = "cat-empty", maxItems = null, unsentOnly = null,
                sendToSlack = false, slackChannelId = null)

            // legacy 경로 마커: summaryStore 후보 조회가 실행돼야 한다
            verify(atLeast = 1) { digestCandidateStore.findDigestCandidatesWithSource(any(), any(), any()) }
        }

        @Test
        fun `sendMessage 는 호출되지 않는다 (sendToSlack=false 임)`() {
            every { flags.isAccountBasedDigestEnabled("cat-empty2") } returns true
            every { flags.isShadowModeEnabled("cat-empty2") } returns false
            every { categoryStore.findById("cat-empty2") } returns mockk<Category>(relaxed = true)
            every { digestPreviewService.dryRunForCategory("cat-empty2") } returns DigestPreviewService.DigestDryRunResult(
                mode = "EMPTY", blocks = "[]"
            )

            makeService().digest(categoryId = "cat-empty2", maxItems = null, unsentOnly = null,
                sendToSlack = false, slackChannelId = null)

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `flag ON + blocks JSON parse fails — legacy fallback` {

        @Test
        fun `JSON 파싱 실패 시 legacy 후보 조회가 실행된다`() {
            every { flags.isAccountBasedDigestEnabled("cat-badjson") } returns true
            every { flags.isShadowModeEnabled("cat-badjson") } returns false
            stubCategory("cat-badjson")
            every { digestPreviewService.dryRunForCategory("cat-badjson") } returns DigestPreviewService.DigestDryRunResult(
                mode = "TOPIC_ONLY",
                blocks = "{not valid json",
            )

            makeService().digest(categoryId = "cat-badjson", maxItems = null, unsentOnly = null,
                sendToSlack = false, slackChannelId = null)

            // JSON 파싱 실패로 account-based 경로가 null 반환 → legacy 마커 실행
            verify(atLeast = 1) { digestCandidateStore.findDigestCandidatesWithSource(any(), any(), any()) }
        }

        @Test
        fun `JSON 파싱 실패 시 sendMessage 는 호출되지 않는다`() {
            every { flags.isAccountBasedDigestEnabled("cat-badjson2") } returns true
            every { flags.isShadowModeEnabled("cat-badjson2") } returns false
            stubCategory("cat-badjson2")
            every { digestPreviewService.dryRunForCategory("cat-badjson2") } returns DigestPreviewService.DigestDryRunResult(
                mode = "TOPIC_ONLY",
                blocks = "{not valid json",
            )

            makeService().digest(categoryId = "cat-badjson2", maxItems = null, unsentOnly = null,
                sendToSlack = false, slackChannelId = null)

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    /**
     * 세 가지 핵심 시나리오를 하나의 클래스에서 명시적 verify 로 통합 검증한다.
     * D11e 스펙: flag OFF / flag ON+shadow OFF / flag ON+shadow ON 경로 분기.
     */
    @Nested
    inner class `계정 기반 다이제스트 기능 플래그` {

        @Test
        fun `플래그 OFF 이면 account-based 경로를 타지 않는다`() {
            // flag OFF → dryRunForCategory 는 호출되지 않아야 한다
            every { flags.isAccountBasedDigestEnabled("cd-gate-off") } returns false
            every { categoryStore.findById("cd-gate-off") } returns mockk<Category>(relaxed = true)

            makeService().digest(
                categoryId = "cd-gate-off", maxItems = null, unsentOnly = null,
                sendToSlack = false, slackChannelId = null
            )

            verify(exactly = 0) { digestPreviewService.dryRunForCategory(any()) }
        }

        @Test
        fun `플래그 ON + Shadow OFF 이면 실제 Slack sendMessage 가 blocks 로 호출된다`() {
            // flag ON + shadow OFF + non-EMPTY → slackMessageSender.sendMessage 가 blocks 와 함께 호출돼야 한다
            stubReservationAllowed()
            every { flags.isAccountBasedDigestEnabled("cd-gate-send") } returns true
            every { flags.isShadowModeEnabled("cd-gate-send") } returns false
            stubCategory("cd-gate-send")
            every { digestPreviewService.dryRunForCategory("cd-gate-send") } returns nonEmptyPreview

            makeService().digest(
                categoryId = "cd-gate-send", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null
            )

            // sendMessage 가 정확히 1번 호출됐는지 검증한다 (blocks 포함 8-param 시그니처 전체)
            verify(exactly = 1) {
                slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            }
            // shadow 모드가 아니므로 diff_log 는 기록되지 않아야 한다
            verify(exactly = 0) {
                digestDiffLogStore.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `플래그 ON + Shadow ON 이면 Slack 미발송하고 diff_log 만 기록된다`() {
            // flag ON + shadow ON + non-EMPTY → sendMessage 없음, insertIfAbsent 1번
            every { flags.isAccountBasedDigestEnabled("cd-gate-shadow") } returns true
            every { flags.isShadowModeEnabled("cd-gate-shadow") } returns true
            stubCategory("cd-gate-shadow")
            every { digestPreviewService.dryRunForCategory("cd-gate-shadow") } returns nonEmptyPreview

            makeService().digest(
                categoryId = "cd-gate-shadow", maxItems = null, unsentOnly = null,
                sendToSlack = true, slackChannelId = null
            )

            // shadow 모드이므로 Slack 전송은 절대 발생하면 안 된다
            verify(exactly = 0) {
                slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any(), any())
            }
            // diff_log 는 정확히 1번 기록돼야 한다
            verify(exactly = 1) {
                digestDiffLogStore.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }
    }
}
