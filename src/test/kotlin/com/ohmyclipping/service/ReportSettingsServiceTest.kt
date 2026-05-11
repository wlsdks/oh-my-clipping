package com.ohmyclipping.service

import com.ohmyclipping.service.dto.user.ReportSettingsUpdateRequest
import com.ohmyclipping.store.ReportSettingsStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReportSettingsServiceTest {

    private val store = mockk<ReportSettingsStore>(relaxed = true)
    private val auditLogStore = mockk<com.ohmyclipping.store.AuditLogStore>(relaxed = true)
    /** Principal → actorId passthrough: 테스트에서 `verify { auditLogStore.log(actorId = "admin", ...) }` 가 동작하도록 한다. */
    private val auditActorResolver = mockk<AuditActorResolver>().apply {
        every { resolve(any()) } answers {
            val arg = firstArg<String?>()
            ResolvedActor(id = arg, name = arg ?: "system")
        }
    }
    private val service = ReportSettingsService(store, auditLogStore, auditActorResolver)

    private val defaultSettings = mapOf(
        "weekly_enabled" to "false",
        "weekly_day" to "MONDAY",
        "weekly_hour" to "9",
        "weekly_slack_channel_id" to "",
        "weekly_include_keyword_trend" to "true",
        "weekly_include_competitor" to "true",
        "weekly_include_top_articles" to "true",
        "weekly_include_sentiment" to "false",
        "monthly_enabled" to "false",
        "monthly_hour" to "9",
        "monthly_slack_channel_id" to ""
    )

    @Nested
    inner class `설정 조회` {

        @Test
        fun `기본 설정을 올바르게 반환한다`() {
            every { store.findAll() } returns defaultSettings

            val result = service.getSettings()

            result.weeklyEnabled shouldBe false
            result.weeklyDay shouldBe "MONDAY"
            result.weeklyHour shouldBe 9
            result.weeklySlackChannelId shouldBe null
            result.weeklyIncludeKeywordTrend shouldBe true
            result.monthlyEnabled shouldBe false
        }

        @Test
        fun `Slack 채널 값은 trim 후 blank면 null로 반환한다`() {
            every { store.findAll() } returns defaultSettings + mapOf(
                "weekly_slack_channel_id" to "  C1234567890  ",
                "monthly_slack_channel_id" to "   "
            )

            val result = service.getSettings()

            result.weeklySlackChannelId shouldBe "C1234567890"
            result.monthlySlackChannelId shouldBe null
        }

        @Test
        fun `빈 맵이면 기본값을 사용한다`() {
            every { store.findAll() } returns emptyMap()

            val result = service.getSettings()

            result.weeklyEnabled shouldBe false
            result.weeklyDay shouldBe "MONDAY"
            result.weeklyHour shouldBe 9
            result.weeklyIncludeKeywordTrend shouldBe true
        }
    }

    @Nested
    inner class `설정 업데이트` {

        @Test
        fun `주간 활성화를 변경한다`() {
            every { store.findAll() } returns defaultSettings + ("weekly_enabled" to "true")

            val result = service.updateSettings(
                ReportSettingsUpdateRequest(weeklyEnabled = true)
            )

            verify(exactly = 1) { store.upsert("weekly_enabled", "true") }
            result.weeklyEnabled shouldBe true
        }

        @Test
        fun `null 필드는 기존 값을 유지한다`() {
            every { store.findAll() } returns defaultSettings

            service.updateSettings(ReportSettingsUpdateRequest(weeklyEnabled = true))

            verify(exactly = 1) { store.upsert("weekly_enabled", "true") }
            verify(exactly = 0) { store.upsert("weekly_day", any()) }
            verify(exactly = 0) { store.upsert("monthly_enabled", any()) }
        }

        @Test
        fun `유효하지 않은 요일이면 예외를 던진다`() {
            assertThrows<com.ohmyclipping.error.InvalidInputException> {
                service.updateSettings(ReportSettingsUpdateRequest(weeklyDay = "FUNDAY"))
            }
        }

        @Test
        fun `시간이 범위를 벗어나면 예외를 던진다`() {
            assertThrows<com.ohmyclipping.error.InvalidInputException> {
                service.updateSettings(ReportSettingsUpdateRequest(weeklyHour = 25))
            }
        }

        @Test
        fun `weeklyHour가 -1이면 예외를 던진다`() {
            assertThrows<com.ohmyclipping.error.InvalidInputException> {
                service.updateSettings(ReportSettingsUpdateRequest(weeklyHour = -1))
            }
        }

        @Test
        fun `monthlyHour가 -1이면 예외를 던진다`() {
            assertThrows<com.ohmyclipping.error.InvalidInputException> {
                service.updateSettings(ReportSettingsUpdateRequest(monthlyHour = -1))
            }
        }

        @Test
        fun `monthlyHour가 24이면 예외를 던진다`() {
            assertThrows<com.ohmyclipping.error.InvalidInputException> {
                service.updateSettings(ReportSettingsUpdateRequest(monthlyHour = 24))
            }
        }

        @Test
        fun `weeklyHour가 0이면 정상 처리된다`() {
            every { store.findAll() } returns defaultSettings + ("weekly_hour" to "0")

            val result = service.updateSettings(ReportSettingsUpdateRequest(weeklyHour = 0))

            verify(exactly = 1) { store.upsert("weekly_hour", "0") }
            result.weeklyHour shouldBe 0
        }

        @Test
        fun `weeklyHour가 23이면 정상 처리된다`() {
            every { store.findAll() } returns defaultSettings + ("weekly_hour" to "23")

            val result = service.updateSettings(ReportSettingsUpdateRequest(weeklyHour = 23))

            verify(exactly = 1) { store.upsert("weekly_hour", "23") }
            result.weeklyHour shouldBe 23
        }

        @Test
        fun `주간 Slack 채널은 trim 후 저장한다`() {
            every { store.findAll() } returns defaultSettings + ("weekly_slack_channel_id" to "C1234567890")

            val result = service.updateSettings(
                ReportSettingsUpdateRequest(weeklySlackChannelId = "  C1234567890  ")
            )

            verify(exactly = 1) { store.upsert("weekly_slack_channel_id", "C1234567890") }
            result.weeklySlackChannelId shouldBe "C1234567890"
        }

        @Test
        fun `월간 Slack 채널 blank 입력은 빈 문자열로 저장해 해제한다`() {
            every { store.findAll() } returns defaultSettings

            val result = service.updateSettings(
                ReportSettingsUpdateRequest(monthlySlackChannelId = "   ")
            )

            verify(exactly = 1) { store.upsert("monthly_slack_channel_id", "") }
            result.monthlySlackChannelId shouldBe null
        }

        @Test
        fun `monthlyHour가 0이면 정상 처리된다`() {
            every { store.findAll() } returns defaultSettings + ("monthly_hour" to "0")

            val result = service.updateSettings(ReportSettingsUpdateRequest(monthlyHour = 0))

            verify(exactly = 1) { store.upsert("monthly_hour", "0") }
            result.monthlyHour shouldBe 0
        }

        @Test
        fun `monthlyHour가 23이면 정상 처리된다`() {
            every { store.findAll() } returns defaultSettings + ("monthly_hour" to "23")

            val result = service.updateSettings(ReportSettingsUpdateRequest(monthlyHour = 23))

            verify(exactly = 1) { store.upsert("monthly_hour", "23") }
            result.monthlyHour shouldBe 23
        }
    }
}
