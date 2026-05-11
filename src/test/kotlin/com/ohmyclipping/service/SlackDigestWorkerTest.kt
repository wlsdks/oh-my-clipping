package com.ohmyclipping.service

import com.ohmyclipping.service.digest.*

import com.ohmyclipping.model.*
import com.ohmyclipping.service.dto.clipping.*
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.event.DigestDeliveryFinalizationRequestedEvent
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.CategoryRuleStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.DeliveryLogStore
import com.ohmyclipping.store.UserClippingRequestStore
import com.ohmyclipping.store.UserDeliveryScheduleStore
import com.ohmyclipping.service.DeliveryRetryOrchestrator
import com.ohmyclipping.service.digest.DigestOpsNotifier
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

class SlackDigestWorkerTest {

    private val categoryStore = mockk<CategoryStore>()
    private val categoryRuleStore = mockk<CategoryRuleStore>()
    private val clippingService = mockk<com.ohmyclipping.service.port.DigestDeliveryWorkflowPort>()
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val scheduleStore = mockk<UserDeliveryScheduleStore>()
    private val requestStore = mockk<UserClippingRequestStore>()
    private val deliveryLogStore = mockk<DeliveryLogStore>()
    private val adminUserStore = mockk<AdminUserStore>()
    private val slackMessageSender = mockk<com.ohmyclipping.service.port.SlackDeliveryPort>(relaxed = true)
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }
    private val digestTaskScheduler = mockk<ThreadPoolTaskScheduler>(relaxed = true)
    private val retryOrchestrator = mockk<DeliveryRetryOrchestrator>(relaxed = true)

    private lateinit var worker: SlackDigestWorker

    /** 현재 KST 시각을 기준으로 테스트 환경을 구성한다. */
    private val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
    private val today = now.toLocalDate()
    private val currentHour = now.hour
    private val dayOfWeek = now.dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        .uppercase()
        .take(3)

    private val baseRuntime = RuntimeSettingService.RuntimeSettings(
        defaultHoursBack = 24,
        summaryInputMaxChars = 5000,
        digestMinImportanceScore = 0.5f,
        digestDefaultMaxItems = 10,
        digestMaxMessageChars = 3000,
        digestItemSummaryMaxChars = 500,
        digestKeywordMaxCount = 5,
        jobWorkerBatchSize = 10,
        jobMaxAttempts = 3,
        jobInitialBackoffSeconds = 30,
        slackBotToken = "xoxb-test",
        slackDigestBlockKitTemplate = "",
        slackAutoDigestEnabled = true,
        slackDigestCron = "* * * * * *",
        slackAutoDigestMaxItems = 5,
        slackAutoDigestUnsentOnly = true,
        slackDailyChannelMessageLimit = 10,
        updatedAt = null
    )

    // createdAt 타임스탬프: 현재 시각을 기준으로 상대 계산 — 하드코딩된 날짜는 시간이 흐르면서
    // "방금 수집됐다"는 문맥을 왜곡할 수 있어 flaky 보강 차원에서 now-relative 로 전환한다.
    private val itemCreatedAtBase = now.toInstant()
    private val digestResult = DigestResult(
        categoryId = "cat-1",
        categoryName = "test",
        unsentOnly = true,
        totalCandidates = 5,
        selectedCount = 3,
        postedToSlack = false,
        slackChannelId = null,
        slackMessageTs = null,
        markedSentCount = 0,
        digestText = "digest",
        items = listOf(
            DigestItemResult(
                summaryId = "sum-1",
                title = "제목 1",
                summary = "요약 1",
                keywords = listOf("AI"),
                importanceScore = 0.9f,
                whyImportant = "중요",
                sourceLink = "https://example.com/1",
                createdAt = itemCreatedAtBase.toString()
            ),
            DigestItemResult(
                summaryId = "sum-2",
                title = "제목 2",
                summary = "요약 2",
                keywords = listOf("정책"),
                importanceScore = 0.8f,
                whyImportant = "중요",
                sourceLink = "https://example.com/2",
                createdAt = itemCreatedAtBase.plus(java.time.Duration.ofMinutes(5)).toString()
            ),
            DigestItemResult(
                summaryId = "sum-3",
                title = "제목 3",
                summary = "요약 3",
                keywords = listOf("시장"),
                importanceScore = 0.7f,
                whyImportant = "중요",
                sourceLink = "https://example.com/3",
                createdAt = itemCreatedAtBase.plus(java.time.Duration.ofMinutes(10)).toString()
            )
        )
    )

    @BeforeEach
    fun setUp() {
        worker = spyk(SlackDigestWorker(
            categoryStore, categoryRuleStore, clippingService,
            runtimeSettingService, scheduleStore, requestStore,
            deliveryLogStore, adminUserStore, slackMessageSender,
            applicationEventPublisher, metrics, mockk<DigestOpsNotifier>(relaxed = true),
            mockk<org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler>(relaxed = true),
            retryOrchestrator,
            null
        ))
        // DM 분산 로직: 테스트 userId "user-1"의 해시 오프셋에 맞춰 현재 분을 고정한다.
        val testUserOffset = ("user-1".hashCode().and(Int.MAX_VALUE)) % SlackDigestWorker.DM_SPREAD_MINUTES
        every { worker.currentMinuteKst() } returns testUserOffset
        // 기본: 개인 스케줄에 매칭되는 카테고리는 없고, 카테고리별 승인 구독은 필요한 경우만 조회한다.
        every { requestStore.findApprovedCategoryIdsByRequesterIds(any()) } returns emptySet()
        every { requestStore.listApprovedByCategoryId(any()) } answers {
            when (firstArg<String>()) {
                "cat-1" -> listOf(makeApprovedRequest("req-default", "cat-1"))
                "cat-2" -> listOf(makeApprovedRequest("req-default-2", "cat-2"))
                else -> emptyList()
            }
        }
        // 기본: resolveRequestNameForTarget에서 사용자 DM 채널 조회 시 필요
        every { adminUserStore.findById(any()) } returns null
        // 기본: 현재 시각에 매칭되는 개인 스케줄 없음
        every { scheduleStore.findSchedulesDueNow(any(), any()) } returns emptyList()
        // 기본: 실패 재시도 대상 없음
        every { retryOrchestrator.recoverStuckClaims() } just runs
        every { retryOrchestrator.findPendingRetries() } returns emptyList()
        every { retryOrchestrator.transitionStale() } just runs
        // 기본: 연속 무발송 안내 관련 — SENT 기록 없음 (신규 구독)
        every { deliveryLogStore.findLastSentDate(any(), any()) } returns null
        every { deliveryLogStore.hasNotifiedSinceLastSent(any(), any()) } returns false
        every { deliveryLogStore.savePreparedDigest(any(), any()) } just runs
        every { clippingService.prepareDigest(any(), any(), any(), false, null) } returns digestResult.toPreparedDigestResult()
        every { clippingService.sendPreparedDigest(any(), any(), any(), any()) } answers {
            val categoryId = firstArg<String>()
            val targetId = thirdArg<String>()
            digestResult.copy(
                categoryId = categoryId,
                postedToSlack = true,
                slackChannelId = targetId,
                slackMessageTs = "123.456",
                markedSentCount = digestResult.items.size
            ).toPreparedDigestResult()
        }
    }

    @Nested
    inner class `카테고리 기반 발송` {

        @Test
        fun `개별 스케줄이 매칭되는 카테고리는 다이제스트를 발송한다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            // 현재 요일·시간에 매칭되는 개별 스케줄
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-1"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            worker.publishDigests()

            verify(exactly = 1) { clippingService.prepareDigest("cat-1", any(), any(), false, null) }
            verify(exactly = 1) { deliveryLogStore.savePreparedDigest("log-1", digestResult) }
            verify(exactly = 1) { clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C0123", any()) }
            verify(exactly = 1) { deliveryLogStore.updateStatus("log-1", "SENT", 3, "123.456") }
        }

        @Test
        fun `개인 스케줄에 구독된 카테고리도 발송한다`() {
            every { runtimeSettingService.current() } returns baseRuntime.copy(
                slackDigestCron = "-"
            )
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            // 개별 스케줄 없음
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null
            // 현재 시간에 매칭되는 개인 스케줄 존재
            every { scheduleStore.findSchedulesDueNow(dayOfWeek, currentHour) } returns listOf(
                UserDeliverySchedule(
                    userId = "user-1",
                    deliveryDays = listOf(dayOfWeek),
                    deliveryHour = currentHour
                )
            )
            every { requestStore.findApprovedCategoryIdsByRequesterIds(setOf("user-1")) } returns setOf("cat-1")
            every { requestStore.listApprovedByCategoryId("cat-1") } returns listOf(
                makeApprovedRequest("req-1", "cat-1").copy(requesterUserId = "user-1")
            )
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-1"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            worker.publishDigests()

            verify(exactly = 1) { clippingService.prepareDigest("cat-1", any(), any(), false, null) }
            verify(exactly = 1) { clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C0123", any()) }
            verify(exactly = 1) { scheduleStore.findSchedulesDueNow(dayOfWeek, currentHour) }
            verify(exactly = 0) { requestStore.listByRequesterUserId(any()) }
            verify(exactly = 0) { requestStore.listAll(UserClippingRequestStatus.APPROVED) }
        }

        @Test
        fun `카테고리 자체 채널이 있으면 해당 채널로 발송한다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C9999")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            // 구독자도 같은 카테고리 채널 사용 + 유저 개인 채널(C0123)도 있음 → 둘 다 발송
            every { requestStore.listApprovedByCategoryId("cat-1") } returns listOf(
                makeApprovedRequest("req-1", "cat-1")  // C0123
            )
            every { deliveryLogStore.tryReserve("cat-1", any(), today, currentHour) } returns "log-1"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            worker.publishDigests()

            // 카테고리 채널(C9999)과 유저 채널(C0123) 모두 발송
            verify(exactly = 1) { clippingService.prepareDigest("cat-1", any(), any(), false, null) }
            verify(exactly = 1) { clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C9999", any()) }
        }

        @Test
        fun `legacy DM 문자열 구독도 사용자 DM 채널로 발송한다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            every { requestStore.listApprovedByCategoryId("cat-1") } returns listOf(
                makeApprovedRequest("req-dm", "cat-1").copy(slackChannelId = "DM")
            )
            val dmUser = AdminUser(
                id = "user-1",
                username = "testuser",
                passwordHash = "hashed",
                role = AccountRole.USER,
                approvalStatus = AccountApprovalStatus.APPROVED,
                slackDmChannelId = "D_DM_TEST"
            )
            every { adminUserStore.findByIds(listOf("user-1")) } returns listOf(dmUser)
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-channel"
            every { deliveryLogStore.tryReserve("cat-1", "D_DM_TEST", today, currentHour) } returns "log-dm"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            worker.publishDigests()

            verify(exactly = 1) { clippingService.prepareDigest("cat-1", any(), any(), false, null) }
            verify(exactly = 1) {
                clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "D_DM_TEST", any())
            }
            verify(exactly = 0) { adminUserStore.findById(any()) }
        }

        @Test
        fun `채널과 DM이 함께 있으면 동일 스냅샷을 재사용해 fan-out한다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            every { requestStore.listApprovedByCategoryId("cat-1") } returns listOf(
                makeApprovedRequest("req-dm", "cat-1").copy(slackChannelId = "DM")
            )
            val dmUser = AdminUser(
                id = "user-1",
                username = "testuser",
                passwordHash = "hashed",
                role = AccountRole.USER,
                approvalStatus = AccountApprovalStatus.APPROVED,
                slackDmChannelId = "D_DM_TEST"
            )
            every { adminUserStore.findByIds(listOf("user-1")) } returns listOf(dmUser)
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-channel"
            every { deliveryLogStore.tryReserve("cat-1", "D_DM_TEST", today, currentHour) } returns "log-dm"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            worker.publishDigests()

            verify(exactly = 1) { clippingService.prepareDigest("cat-1", any(), any(), false, null) }
            verify(exactly = 1) { clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C0123", any()) }
            verify(exactly = 1) {
                clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "D_DM_TEST", any())
            }
        }

    }

    @Nested
    inner class `스케줄 비매칭` {

        @Test
        fun `개별 스케줄 시간이 맞지 않으면 발송하지 않는다`() {
            every { runtimeSettingService.current() } returns baseRuntime.copy(slackDigestCron = "-")
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            // 다른 시간대의 개별 스케줄
            val otherHour = (currentHour + 5) % 24
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = otherHour
            )

            worker.publishDigests()

            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { clippingService.sendPreparedDigest(any(), any(), any(), any()) }
        }

        @Test
        fun `deliveryDays가 null인 카테고리는 건너뛴다`() {
            every { runtimeSettingService.current() } returns baseRuntime.copy(slackDigestCron = "-")
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = null,
                deliveryHour = currentHour
            )

            worker.publishDigests()

            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `deliveryHour가 null인 카테고리는 건너뛴다`() {
            every { runtimeSettingService.current() } returns baseRuntime.copy(slackDigestCron = "-")
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = null
            )

            worker.publishDigests()

            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `비활성 카테고리는 발송하지 않는다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = false, status = CategoryStatus.PAUSED)
            )

            worker.publishDigests()

            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `자동 발송이 비활성화이면 아무것도 실행하지 않는다`() {
            every { runtimeSettingService.current() } returns baseRuntime.copy(
                slackAutoDigestEnabled = false
            )

            worker.publishDigests()

            verify(exactly = 0) { categoryStore.findOperational() }
            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `중복 발송 방지` {

        @Test
        fun `tryReserve가 null을 반환하면 중복으로 간주해 건너뛴다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            // 이미 발송됨 → null 반환
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns null

            worker.publishDigests()

            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `두 번 호출해도 tryReserve가 한 번만 성공하면 한 번만 발송한다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            // 첫 호출만 성공, 이후 null
            every {
                deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour)
            } returns "log-1" andThen null
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            worker.publishDigests()
            worker.publishDigests()

            verify(exactly = 1) { clippingService.prepareDigest("cat-1", any(), any(), false, null) }
            verify(exactly = 1) { clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C0123", any()) }
        }
    }

    @Nested
    inner class `에러 처리` {

        @Test
        fun `다이제스트 발송 실패 시 FAILED 상태를 기록하고 다음 카테고리를 처리한다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123"),
                Category(id = "cat-2", name = "정책", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            every { categoryRuleStore.findByCategoryId("cat-2") } returns CategoryRule(
                categoryId = "cat-2",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-1"
            every { deliveryLogStore.tryReserve("cat-2", "C0123", today, currentHour) } returns "log-2"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs
            every { deliveryLogStore.recordFailure(any(), any(), any(), any(), any()) } just runs

            // cat-1은 prepared digest 전송 실패, cat-2는 성공
            every {
                clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C0123", any())
            } throws RuntimeException("Slack error")
            every {
                clippingService.prepareDigest("cat-2", any(), any(), false, null)
            } returns digestResult.copy(categoryId = "cat-2").toPreparedDigestResult()
            every {
                clippingService.sendPreparedDigest(
                    "cat-2",
                    digestResult.copy(categoryId = "cat-2").toPreparedDigestResult(),
                    "C0123",
                    any()
                )
            } returns digestResult.copy(categoryId = "cat-2", postedToSlack = true, slackChannelId = "C0123", slackMessageTs = "123.456", markedSentCount = 3).toPreparedDigestResult()

            worker.publishDigests()

            // cat-1은 발송 실패로 recordFailure 호출, cat-2는 SENT
            verify(exactly = 1) { deliveryLogStore.recordFailure("log-1", 1, any(), "FAILED", any()) }
            verify(exactly = 1) { deliveryLogStore.updateStatus("log-2", "SENT", 3, "123.456") }
            // 두 카테고리 모두 snapshot 생성 후 전송 시도
            verify(exactly = 1) { clippingService.prepareDigest("cat-1", any(), any(), false, null) }
            verify(exactly = 1) { clippingService.prepareDigest("cat-2", any(), any(), false, null) }
            verify(exactly = 1) { clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C0123", any()) }
            verify(exactly = 1) {
                clippingService.sendPreparedDigest(
                    "cat-2",
                    digestResult.copy(categoryId = "cat-2").toPreparedDigestResult(),
                    "C0123",
                    any()
                )
            }
        }

        @Test
        fun `Slack 성공 후 후처리만 실패하면 FINALIZATION_FAILED로 남겨 재전송 대신 복구 대상으로 분리한다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-finalize"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs
            every {
                clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C0123", any())
            } throws DigestDeliveryFinalizationException(
                categoryId = "cat-1",
                channelId = "C0123",
                slackMessageTs = "ts-after-send",
                summaryIds = listOf("sum-1", "sum-2", "sum-3"),
                itemCount = 3,
                cause = RuntimeException("markSent failed")
            )

            worker.publishDigests()

            verify(exactly = 1) { deliveryLogStore.savePreparedDigest("log-finalize", digestResult) }
            verify(exactly = 1) {
                deliveryLogStore.updateStatus("log-finalize", "FINALIZATION_FAILED", 3, "ts-after-send")
            }
        }

        @Test
        fun `Slack 성공 후 후처리만 실패하면 FINALIZATION_FAILED로 남기고 즉시 복구 이벤트를 발행한다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-finalize"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs
            every {
                clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C0123", any())
            } throws DigestDeliveryFinalizationException(
                categoryId = "cat-1",
                channelId = "C0123",
                slackMessageTs = "1712.777",
                summaryIds = listOf("sum-1", "sum-2", "sum-3"),
                itemCount = 3,
                cause = RuntimeException("db down")
            )

            worker.publishDigests()

            verify(exactly = 1) {
                deliveryLogStore.updateStatus("log-finalize", "FINALIZATION_FAILED", 3, "1712.777")
            }
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(
                    match<DigestDeliveryFinalizationRequestedEvent> {
                        it.categoryId == "cat-1" &&
                            it.summaryIds == listOf("sum-1", "sum-2", "sum-3") &&
                            it.deliveryLogId == "log-finalize" &&
                            it.slackMessageTs == "1712.777"
                    }
                )
            }
        }

        @Test
        fun `cron이 빈 문자열이면 글로벌 cron 경로에서 매칭되지 않는다`() {
            every { runtimeSettingService.current() } returns baseRuntime.copy(slackDigestCron = "")
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            // 개별 스케줄 없음, 개인 스케줄 없음 → 글로벌 cron만 남는데 빈 문자열
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null
            every { scheduleStore.findSchedulesDueNow(any(), any()) } returns emptyList()

            worker.publishDigests()

            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `유효하지 않은 cron 표현식은 안전하게 무시한다`() {
            every { runtimeSettingService.current() } returns baseRuntime.copy(
                slackDigestCron = "invalid cron"
            )
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null
            every { scheduleStore.findSchedulesDueNow(any(), any()) } returns emptyList()

            worker.publishDigests()

            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `FAILED 재시도는 저장된 prepared digest를 우선 사용해 같은 payload를 다시 보낸다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns emptyList()
            every { retryOrchestrator.findPendingRetries() } returns listOf(
                DeliveryLogStore.DeliveryRetryCandidate(
                    id = "retry-1",
                    categoryId = "cat-1",
                    channelId = "C0123",
                    status = "FAILED",
                    slackMessageTs = null,
                    preparedDigest = digestResult,
                    retryCount = 1,
                    createdAt = now.toInstant()
                )
            )
            every { retryOrchestrator.claim("retry-1") } returns true
            every { categoryStore.findById("cat-1") } returns Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            worker.publishDigests()

            verify(exactly = 1) { retryOrchestrator.claim("retry-1") }
            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
            verify(exactly = 1) { clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C0123", any()) }
            verify(exactly = 1) { deliveryLogStore.updateStatus("retry-1", "SENT", 3, "123.456") }
        }

        @Test
        fun `FINALIZATION_FAILED 재시도는 저장된 prepared digest로 후처리만 복구하고 재전송하지 않는다`() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns emptyList()
            every { retryOrchestrator.findPendingRetries() } returns listOf(
                DeliveryLogStore.DeliveryRetryCandidate(
                    id = "retry-finalize",
                    categoryId = "cat-1",
                    channelId = "C0123",
                    status = "FINALIZATION_FAILED",
                    slackMessageTs = "ts-after-send",
                    preparedDigest = digestResult,
                    retryCount = 2,
                    createdAt = now.toInstant()
                )
            )
            every { retryOrchestrator.claim("retry-finalize") } returns true
            every { categoryStore.findById("cat-1") } returns Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            every { clippingService.finalizePreparedDigest("cat-1", digestResult.toPreparedDigestResult()) } returns 3
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            worker.publishDigests()

            verify(exactly = 1) { retryOrchestrator.claim("retry-finalize") }
            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { clippingService.sendPreparedDigest(any(), any(), any(), any()) }
            verify(exactly = 1) { clippingService.finalizePreparedDigest("cat-1", digestResult.toPreparedDigestResult()) }
            verify(exactly = 1) {
                deliveryLogStore.updateStatus("retry-finalize", "SENT", 3, "ts-after-send")
            }
        }
    }

    @Nested
    inner class `글로벌 cron 폴백` {

        @Test
        fun `개별 스케줄도 개인 스케줄도 없을 때 글로벌 cron이 매칭되면 발송한다`() {
            // 매 초마다 실행되는 cron → 항상 매칭
            every { runtimeSettingService.current() } returns baseRuntime.copy(
                slackDigestCron = "* * * * * *"
            )
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null
            every { scheduleStore.findSchedulesDueNow(any(), any()) } returns emptyList()
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-1"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            worker.publishDigests()

            verify(exactly = 1) { clippingService.prepareDigest("cat-1", any(), any(), false, null) }
            verify(exactly = 1) { clippingService.sendPreparedDigest("cat-1", digestResult.toPreparedDigestResult(), "C0123", any()) }
        }

        @Test
        fun `cron이 대시이면 비활성 처리한다`() {
            every { runtimeSettingService.current() } returns baseRuntime.copy(slackDigestCron = "-")
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null
            every { scheduleStore.findSchedulesDueNow(any(), any()) } returns emptyList()

            worker.publishDigests()

            verify(exactly = 0) { clippingService.prepareDigest(any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `연속 무발송 안내 DM` {

        /** SKIPPED 상태를 발생시키기 위한 공통 설정 */
        private fun setupSkippedDelivery() {
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI 뉴스", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-1"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs
            // sendPreparedDigest가 postedToSlack=false를 반환하면 SKIPPED
            every { clippingService.sendPreparedDigest("cat-1", any(), "C0123", any()) } returns
                digestResult.copy(postedToSlack = false, slackChannelId = "C0123", markedSentCount = 0).toPreparedDigestResult()
        }

        @Test
        fun `마지막 발송이 3일 이상 경과하고 안내 미발송이면 DM을 보낸다`() {
            setupSkippedDelivery()
            // 4일 전에 마지막 SENT 발송
            val fourDaysAgo = Instant.now().minus(java.time.Duration.ofDays(4))
            every { deliveryLogStore.findLastSentDate("C0123", "cat-1") } returns fourDaysAgo
            every { deliveryLogStore.hasNotifiedSinceLastSent("C0123", "cat-1") } returns false
            // NOTIFIED_NO_CONTENT 로그 저장용
            every { deliveryLogStore.tryReserve("cat-1", "C0123", any(), any()) } returns "log-1" andThen "log-notify"

            worker.publishDigests()

            // 안내 DM이 발송되었는지 확인
            val messageSlot = slot<String>()
            verify(exactly = 1) {
                slackMessageSender.sendMessage(
                    channelId = "C0123",
                    text = capture(messageSlot)
                )
            }
            messageSlot.captured shouldContain "AI 뉴스"
            messageSlot.captured shouldContain "새 뉴스가 없어요"
            // NOTIFIED_NO_CONTENT 상태 기록
            verify { deliveryLogStore.updateStatus("log-notify", "NOTIFIED_NO_CONTENT", 0, null) }
        }

        @Test
        fun `마지막 발송이 3일 미만이면 DM을 보내지 않는다`() {
            setupSkippedDelivery()
            // 1일 전에 마지막 SENT 발송
            val oneDayAgo = Instant.now().minus(java.time.Duration.ofDays(1))
            every { deliveryLogStore.findLastSentDate("C0123", "cat-1") } returns oneDayAgo

            worker.publishDigests()

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `이미 안내를 보냈으면 중복 발송하지 않는다`() {
            setupSkippedDelivery()
            val fiveDaysAgo = Instant.now().minus(java.time.Duration.ofDays(5))
            every { deliveryLogStore.findLastSentDate("C0123", "cat-1") } returns fiveDaysAgo
            // 이미 NOTIFIED_NO_CONTENT 기록이 있음
            every { deliveryLogStore.hasNotifiedSinceLastSent("C0123", "cat-1") } returns true

            worker.publishDigests()

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `발송 기록이 없으면 안내하지 않는다`() {
            setupSkippedDelivery()
            // SENT 기록 없음 (신규 구독)
            every { deliveryLogStore.findLastSentDate("C0123", "cat-1") } returns null

            worker.publishDigests()

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `Slack rate limiter throttling (regression for dead-code bug)` {

        @Test
        fun `여러 대상이 있을 때 발송 사이마다 rate limiter가 호출된다`() {
            // GIVEN: rate limiter 를 명시적으로 주입한 worker 를 별도로 구성한다.
            val rateLimiter = mockk<com.ohmyclipping.resilience.TokenBucketRateLimiter>(relaxed = true)
            val workerWithLimiter = spyk(SlackDigestWorker(
                categoryStore, categoryRuleStore, clippingService,
                runtimeSettingService, scheduleStore, requestStore,
                deliveryLogStore, adminUserStore, slackMessageSender,
                applicationEventPublisher, metrics, mockk<DigestOpsNotifier>(relaxed = true),
                mockk<ThreadPoolTaskScheduler>(relaxed = true),
                retryOrchestrator,
                null,
                rateLimiter
            ))
            val testUserOffset = ("user-1".hashCode().and(Int.MAX_VALUE)) % SlackDigestWorker.DM_SPREAD_MINUTES
            every { workerWithLimiter.currentMinuteKst() } returns testUserOffset

            // 채널 C0123 + user-1 DM = 2 개 대상
            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            // user-1 의 DM channel 이 등록되어 있어 추가 대상 생성
            val adminUser = mockk<com.ohmyclipping.model.AdminUser>()
            every { adminUser.id } returns "user-1"
            every { adminUser.isActive } returns true
            every { adminUser.slackDmChannelId } returns "D_USER_1"
            every { adminUserStore.findByIds(listOf("user-1")) } returns listOf(adminUser)
            every { requestStore.listApprovedByCategoryId("cat-1") } returns listOf(
                makeApprovedRequest("req-dm", "cat-1").copy(
                    requesterUserId = "user-1",
                    slackChannelId = "D_USER_1"
                )
            )
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-ch"
            every { deliveryLogStore.tryReserve("cat-1", "D_USER_1", today, currentHour) } returns "log-dm"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            // WHEN
            workerWithLimiter.publishDigests()

            // THEN: 2 개 대상 발송 → 마지막 대상 이후엔 throttle 생략, 따라서 acquire 는 정확히 1 회.
            // 버그 재발 시: acquire 호출 0 회 (dead code) 또는 2 회 (마지막에도 대기 — 과도 throttle).
            verify(exactly = 2) { clippingService.sendPreparedDigest("cat-1", any(), any(), any()) }
            verify(exactly = 1) { rateLimiter.acquire() }
        }

        @Test
        fun `단일 대상일 때 rate limiter는 호출되지 않는다`() {
            val rateLimiter = mockk<com.ohmyclipping.resilience.TokenBucketRateLimiter>(relaxed = true)
            val workerWithLimiter = spyk(SlackDigestWorker(
                categoryStore, categoryRuleStore, clippingService,
                runtimeSettingService, scheduleStore, requestStore,
                deliveryLogStore, adminUserStore, slackMessageSender,
                applicationEventPublisher, metrics, mockk<DigestOpsNotifier>(relaxed = true),
                mockk<ThreadPoolTaskScheduler>(relaxed = true),
                retryOrchestrator,
                null,
                rateLimiter
            ))
            val testUserOffset = ("user-1".hashCode().and(Int.MAX_VALUE)) % SlackDigestWorker.DM_SPREAD_MINUTES
            every { workerWithLimiter.currentMinuteKst() } returns testUserOffset

            every { runtimeSettingService.current() } returns baseRuntime
            every { categoryStore.findOperational() } returns listOf(
                Category(id = "cat-1", name = "AI", isActive = true, slackChannelId = "C0123")
            )
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf(dayOfWeek),
                deliveryHour = currentHour
            )
            every { deliveryLogStore.tryReserve("cat-1", "C0123", today, currentHour) } returns "log-1"
            every { deliveryLogStore.updateStatus(any(), any(), any(), any()) } just runs

            workerWithLimiter.publishDigests()

            // 단일 대상: send 는 1회, acquire 는 대상 간 대기가 없으므로 0회.
            verify(exactly = 1) { clippingService.sendPreparedDigest("cat-1", any(), any(), any()) }
            verify(exactly = 0) { rateLimiter.acquire() }
        }
    }

    private fun makeApprovedRequest(id: String, categoryId: String) = UserClippingRequest(
        id = id,
        requesterUserId = "user-1",
        requestName = "요청",
        sourceName = "소스",
        sourceUrl = "https://example.com/rss",
        slackChannelId = "C0123",
        personaName = "페르소나",
        personaPrompt = "prompt",
        status = UserClippingRequestStatus.APPROVED,
        approvedCategoryId = categoryId
    )
}
