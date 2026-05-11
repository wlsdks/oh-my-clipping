package com.ohmyclipping.service

import com.ohmyclipping.service.competitor.CompetitorWatchlistService
import com.ohmyclipping.service.competitor.CompetitorWeeklyDigestScheduler
import com.ohmyclipping.service.port.LlmSummarizationPort
import com.ohmyclipping.service.port.CompetitorWeeklyInsight
import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.port.SlackDeliveryResult
import com.ohmyclipping.service.dto.CompetitorTimelineItem
import com.ohmyclipping.service.dto.SovResponse
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.ReportDeliveryLogStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CompetitorWeeklyDigestSchedulerTest {

    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val competitorWatchlistService = mockk<CompetitorWatchlistService>(relaxed = true)
    private val clippingSummarizer = mockk<LlmSummarizationPort>(relaxed = true)
    private val slackMessageSender = mockk<com.ohmyclipping.service.port.SlackDeliveryPort>(relaxed = true)
    private val adminUserStore = mockk<AdminUserStore>(relaxed = true)
    private val reportDeliveryLogStore = mockk<ReportDeliveryLogStore>(relaxed = true)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }

    private fun buildScheduler(): CompetitorWeeklyDigestScheduler {
        return CompetitorWeeklyDigestScheduler(
            runtimeSettingService,
            competitorWatchlistService,
            clippingSummarizer,
            slackMessageSender,
            adminUserStore,
            reportDeliveryLogStore,
            metrics,
            objectMapper
        )
    }

    private fun defaultSettings(
        enabled: Boolean = true,
        channelId: String = "C-test-channel",
        dmMode: String = "off",
        dmUserIds: String = "[]",
        day: String = "MONDAY",
        hour: Int = 9,
        configChangedAt: String = ""
    ) = mockk<RuntimeSettingService.RuntimeSettings>(relaxed = true) {
        every { competitorWeeklyEnabled } returns enabled
        every { competitorWeeklyChannelId } returns channelId
        every { competitorWeeklyDmMode } returns dmMode
        every { competitorWeeklyDmUserIds } returns dmUserIds
        every { competitorWeeklyDay } returns day
        every { competitorWeeklyHour } returns hour
        every { competitorWeeklyConfigChangedAt } returns configChangedAt
    }

    /** 월요일 오전 9시 고정 시각 */
    private val mondayAt9 = LocalDateTime.of(2026, 4, 6, 9, 0)

    /** 화요일 오전 9시 */
    private val tuesdayAt9 = LocalDateTime.of(2026, 4, 7, 9, 0)

    /** 월요일 오전 10시 */
    private val mondayAt10 = LocalDateTime.of(2026, 4, 6, 10, 0)

    private fun sampleTopArticles(): Map<String, List<CompetitorTimelineItem>> {
        return mapOf(
            "CompanyA" to listOf(
                mockk<CompetitorTimelineItem>(relaxed = true) {
                    every { title } returns "Article 1"
                }
            )
        )
    }

    @Nested
    inner class `스케줄 매칭` {

        @Test
        fun `enabled=false → 스킵`() {
            every { runtimeSettingService.current() } returns defaultSettings(enabled = false)
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 0) { reportDeliveryLogStore.tryReserve(any(), any(), any()) }
        }

        @Test
        fun `채널 미설정 → 스킵`() {
            every { runtimeSettingService.current() } returns defaultSettings(channelId = "")
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 0) { reportDeliveryLogStore.tryReserve(any(), any(), any()) }
        }

        @Test
        fun `채널 공백만 → 스킵`() {
            every { runtimeSettingService.current() } returns defaultSettings(channelId = "   ")
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 0) { reportDeliveryLogStore.tryReserve(any(), any(), any()) }
        }

        @Test
        fun `요일 불일치 → 스킵`() {
            every { runtimeSettingService.current() } returns defaultSettings(day = "MONDAY")
            val scheduler = buildScheduler()

            scheduler.tick(tuesdayAt9)

            verify(exactly = 0) { reportDeliveryLogStore.tryReserve(any(), any(), any()) }
        }

        @Test
        fun `시간 불일치 → 스킵`() {
            every { runtimeSettingService.current() } returns defaultSettings(hour = 9)
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt10)

            verify(exactly = 0) { reportDeliveryLogStore.tryReserve(any(), any(), any()) }
        }
    }

    @Nested
    inner class `발송 로직` {

        @Test
        fun `요일+시간 일치 → 채널 발송`() {
            every { runtimeSettingService.current() } returns defaultSettings()
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns "log-1"
            every { competitorWatchlistService.getTopArticlesForWeeklyDigest(any(), any()) } returns sampleTopArticles()
            every { competitorWatchlistService.getShareOfVoiceWithDelta(any()) } returns mockk(relaxed = true)
            every { clippingSummarizer.summarizeCompetitorWeekly(any(), any()) } returns mockk(relaxed = true)
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-123", channelId = "C-any")
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 1) { slackMessageSender.sendMessage("C-test-channel", any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { reportDeliveryLogStore.markSent("log-1", any(), "ts-123") }
        }

        @Test
        fun `같은 주 중복 → tryReserve null → 스킵`() {
            every { runtimeSettingService.current() } returns defaultSettings()
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns null
            every { reportDeliveryLogStore.findByKey(any(), any(), any()) } returns null
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `기사 0건 → 발송 안 함`() {
            every { runtimeSettingService.current() } returns defaultSettings()
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns "log-1"
            every { competitorWatchlistService.getTopArticlesForWeeklyDigest(any(), any()) } returns emptyMap()
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { reportDeliveryLogStore.markFailed("log-1", null, any()) }
        }
    }

    @Nested
    inner class `Graceful Degradation` {

        @Test
        fun `Gemini 실패 → AI 인사이트 없이 발송`() {
            every { runtimeSettingService.current() } returns defaultSettings()
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns "log-1"
            every { competitorWatchlistService.getTopArticlesForWeeklyDigest(any(), any()) } returns sampleTopArticles()
            every { competitorWatchlistService.getShareOfVoiceWithDelta(any()) } returns mockk(relaxed = true)
            every { clippingSummarizer.summarizeCompetitorWeekly(any(), any()) } throws RuntimeException("Gemini timeout")
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-456", channelId = "C-any")
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            // AI 실패해도 발송은 진행된다
            verify(exactly = 1) { slackMessageSender.sendMessage("C-test-channel", any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { reportDeliveryLogStore.markSent("log-1", any(), "ts-456") }
        }

        @Test
        fun `SOV 실패 → SOV 없이 발송`() {
            every { runtimeSettingService.current() } returns defaultSettings()
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns "log-1"
            every { competitorWatchlistService.getTopArticlesForWeeklyDigest(any(), any()) } returns sampleTopArticles()
            every { competitorWatchlistService.getShareOfVoiceWithDelta(any()) } throws RuntimeException("DB error")
            every { clippingSummarizer.summarizeCompetitorWeekly(any(), any()) } returns mockk(relaxed = true)
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-789", channelId = "C-any")
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 1) { slackMessageSender.sendMessage("C-test-channel", any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { reportDeliveryLogStore.markSent("log-1", any(), "ts-789") }
        }

        @Test
        fun `타임라인 조회 실패 → 전체 스킵, markFailed 호출`() {
            every { runtimeSettingService.current() } returns defaultSettings()
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns "log-1"
            every {
                competitorWatchlistService.getTopArticlesForWeeklyDigest(any(), any())
            } throws RuntimeException("Timeline DB error")
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { reportDeliveryLogStore.markFailed("log-1", null, any()) }
        }
    }

    @Nested
    inner class `설정 변경 후 재발송` {

        private fun sentSlot(updatedAt: java.time.Instant) =
            ReportDeliveryLogStore.SlotInfo(
                id = "old-slot",
                status = "SENT",
                createdAt = updatedAt,
                updatedAt = updatedAt
            )

        private fun setupSuccessfulSendMocks() {
            every { competitorWatchlistService.getTopArticlesForWeeklyDigest(any(), any()) } returns sampleTopArticles()
            every { competitorWatchlistService.getShareOfVoiceWithDelta(any()) } returns mockk(relaxed = true)
            every { clippingSummarizer.summarizeCompetitorWeekly(any(), any()) } returns mockk(relaxed = true)
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-resend", channelId = "C-any")
        }

        @Test
        fun `같은 주 SENT 슬롯 + configChangedAt이 더 최신 → 기존 슬롯 삭제 후 재발송`() {
            // 마지막 발송: 2026-04-13 03:00 UTC
            // 설정 변경:    2026-04-13 04:00 UTC (이후)
            val sentAt = java.time.Instant.parse("2026-04-13T03:00:00Z")
            val configChangedAt = "2026-04-13T04:00:00Z"

            every { runtimeSettingService.current() } returns
                defaultSettings(configChangedAt = configChangedAt)
            // 첫 tryReserve는 이미 슬롯이 있어서 null, 삭제 후 두 번째 tryReserve는 성공
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returnsMany
                listOf(null, "new-slot")
            every { reportDeliveryLogStore.findByKey(any(), any(), any()) } returns sentSlot(sentAt)
            every { reportDeliveryLogStore.deleteById("old-slot") } returns Unit
            setupSuccessfulSendMocks()
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 1) { reportDeliveryLogStore.deleteById("old-slot") }
            verify(exactly = 2) { reportDeliveryLogStore.tryReserve(any(), any(), any()) }
            verify(exactly = 1) { slackMessageSender.sendMessage("C-test-channel", any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { reportDeliveryLogStore.markSent("new-slot", any(), "ts-resend") }
        }

        @Test
        fun `같은 주 SENT 슬롯 + configChangedAt이 더 이전 → 재발송하지 않음`() {
            // 마지막 발송: 2026-04-13 04:00 UTC (더 최신)
            // 설정 변경:    2026-04-13 03:00 UTC (이전)
            val sentAt = java.time.Instant.parse("2026-04-13T04:00:00Z")
            val configChangedAt = "2026-04-13T03:00:00Z"

            every { runtimeSettingService.current() } returns
                defaultSettings(configChangedAt = configChangedAt)
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns null
            every { reportDeliveryLogStore.findByKey(any(), any(), any()) } returns sentSlot(sentAt)
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 0) { reportDeliveryLogStore.deleteById(any()) }
            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `configChangedAt이 빈 문자열(레거시) → 기존 동작 유지(스킵)`() {
            val sentAt = java.time.Instant.parse("2026-04-13T03:00:00Z")

            every { runtimeSettingService.current() } returns
                defaultSettings(configChangedAt = "")
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns null
            every { reportDeliveryLogStore.findByKey(any(), any(), any()) } returns sentSlot(sentAt)
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 0) { reportDeliveryLogStore.deleteById(any()) }
            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `RESERVED 상태 슬롯은 다른 인스턴스 발송 중 → 건드리지 않음`() {
            // RESERVED 슬롯이 있고 configChangedAt이 매우 미래여도 절대 삭제하지 않는다 (race 방지)
            val reservedAt = java.time.Instant.parse("2026-04-13T03:00:00Z")

            every { runtimeSettingService.current() } returns
                defaultSettings(configChangedAt = "2099-01-01T00:00:00Z")
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns null
            every { reportDeliveryLogStore.findByKey(any(), any(), any()) } returns
                ReportDeliveryLogStore.SlotInfo(
                    id = "reserved-slot",
                    status = "RESERVED",
                    createdAt = reservedAt,
                    updatedAt = reservedAt
                )
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 0) { reportDeliveryLogStore.deleteById(any()) }
            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `잘못된 형식의 configChangedAt → 안전하게 스킵 처리`() {
            val sentAt = java.time.Instant.parse("2026-04-13T03:00:00Z")

            every { runtimeSettingService.current() } returns
                defaultSettings(configChangedAt = "not-a-timestamp")
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns null
            every { reportDeliveryLogStore.findByKey(any(), any(), any()) } returns sentSlot(sentAt)
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            verify(exactly = 0) { reportDeliveryLogStore.deleteById(any()) }
            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `DM 발송` {

        private fun userWithDm(id: String, dmChannelId: String?) = AdminUser(
            id = id,
            username = "user-$id",
            passwordHash = "hash",
            role = AccountRole.USER,
            approvalStatus = AccountApprovalStatus.APPROVED,
            slackDmChannelId = dmChannelId
        )

        private fun setupChannelSend() {
            every { reportDeliveryLogStore.tryReserve(any(), any(), any()) } returns "log-1"
            every { competitorWatchlistService.getTopArticlesForWeeklyDigest(any(), any()) } returns sampleTopArticles()
            every { competitorWatchlistService.getShareOfVoiceWithDelta(any()) } returns mockk(relaxed = true)
            every { clippingSummarizer.summarizeCompetitorWeekly(any(), any()) } returns mockk(relaxed = true)
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SlackDeliveryResult(ts = "ts-dm", channelId = "C-any")
        }

        @Test
        fun `DM mode=off → DM 안 보냄`() {
            every { runtimeSettingService.current() } returns defaultSettings(dmMode = "off")
            setupChannelSend()
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            // 채널 발송 1회만 — DM 없음
            verify(exactly = 1) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { adminUserStore.listByRole(any(), any()) }
        }

        @Test
        fun `DM mode=all → 승인 사용자 전원 DM`() {
            val users = listOf(
                userWithDm("u1", "D-dm1"),
                userWithDm("u2", "D-dm2"),
                userWithDm("u3", null) // DM 채널 없는 사용자 — 스킵
            )
            every { runtimeSettingService.current() } returns defaultSettings(dmMode = "all")
            setupChannelSend()
            every {
                adminUserStore.listByRole(AccountRole.USER, AccountApprovalStatus.APPROVED)
            } returns users
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            // 채널 1회 + DM 2회 (u3은 slackDmChannelId 없어서 제외)
            verify(exactly = 1) { slackMessageSender.sendMessage("C-test-channel", any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { slackMessageSender.sendMessage("D-dm1", any(), any(), any()) }
            verify(exactly = 1) { slackMessageSender.sendMessage("D-dm2", any(), any(), any()) }
        }

        @Test
        fun `DM mode=selected → 지정 사용자만 DM`() {
            val selectedJson = """["u1","u3"]"""
            val users = listOf(
                userWithDm("u1", "D-dm1"),
                userWithDm("u3", "D-dm3")
            )
            every { runtimeSettingService.current() } returns defaultSettings(
                dmMode = "selected",
                dmUserIds = selectedJson
            )
            setupChannelSend()
            every { adminUserStore.findByIds(listOf("u1", "u3")) } returns users
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            // 채널 1회 + DM 2회
            verify(exactly = 1) { slackMessageSender.sendMessage("C-test-channel", any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { slackMessageSender.sendMessage("D-dm1", any(), any(), any()) }
            verify(exactly = 1) { slackMessageSender.sendMessage("D-dm3", any(), any(), any()) }
            verify(exactly = 0) { adminUserStore.listByRole(any(), any()) }
        }

        @Test
        fun `DM mode=selected + 잘못된 JSON → DM 생략`() {
            every { runtimeSettingService.current() } returns defaultSettings(
                dmMode = "selected",
                dmUserIds = "invalid-json"
            )
            setupChannelSend()
            val scheduler = buildScheduler()

            scheduler.tick(mondayAt9)

            // 채널 발송만 — DM 파싱 실패로 생략
            verify(exactly = 1) { slackMessageSender.sendMessage("C-test-channel", any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { adminUserStore.findByIds(any()) }
        }
    }

    @Nested
    inner class `weeklyPeriodKey` {

        @Test
        fun `ISO 주차 기반 키를 반환한다`() {
            val scheduler = buildScheduler()
            val key = scheduler.weeklyPeriodKey(java.time.LocalDate.of(2026, 4, 6))
            key shouldBe "2026-W15"
        }
    }

    @Nested
    inner class `parseSelectedUserIds` {

        @Test
        fun `정상 JSON 배열을 파싱한다`() {
            val scheduler = buildScheduler()
            val result = scheduler.parseSelectedUserIds("""["id1","id2","id3"]""")
            result shouldBe listOf("id1", "id2", "id3")
        }

        @Test
        fun `빈 배열을 파싱한다`() {
            val scheduler = buildScheduler()
            val result = scheduler.parseSelectedUserIds("[]")
            result shouldBe emptyList()
        }

        @Test
        fun `잘못된 JSON → 빈 리스트 반환`() {
            val scheduler = buildScheduler()
            val result = scheduler.parseSelectedUserIds("not-json")
            result shouldBe emptyList()
        }
    }
}
