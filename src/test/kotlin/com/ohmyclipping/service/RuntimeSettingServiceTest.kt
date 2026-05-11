package com.ohmyclipping.service

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.config.EncryptionService
import com.ohmyclipping.config.SlackProperties
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.RuntimeSetting
import com.ohmyclipping.service.OpsNotificationProfile
import com.ohmyclipping.model.RuntimeSettingAudit
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.RuntimeSettingAuditStore
import com.ohmyclipping.store.RuntimeSettingStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as stringShouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class RuntimeSettingServiceTest {

    private val runtimeSettingStore = mockk<RuntimeSettingStore>()
    private val runtimeSettingAuditStore = mockk<RuntimeSettingAuditStore>()
    private val properties = ClippingMcpServerProperties()
    private val slackProperties = SlackProperties(
        botToken = "xoxb-default",
        digestCron = "0 0 9 * * MON-FRI"
    )
    private val slackBlockKitTemplateService = mockk<SlackBlockKitTemplateService>(relaxed = true)
    /** 테스트에서는 암호화를 비활성화하여 평문으로 동작하게 한다. */
    private val encryptionService = EncryptionService("")
    /** 통합 감사 로그 — 기본값을 호출해도 되도록 relaxed mock을 사용한다. */
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    /** Principal → actorId passthrough: 테스트에서 `verify { auditLogStore.log(actorId = "admin", ...) }` 가 동작하도록 한다. */
    private val auditActorResolver = mockk<AuditActorResolver>().apply {
        every { resolve(any()) } answers {
            val arg = firstArg<String?>()
            ResolvedActor(id = arg, name = arg ?: "system")
        }
    }

    private val service = RuntimeSettingService(
        runtimeSettingStore = runtimeSettingStore,
        runtimeSettingAuditStore = runtimeSettingAuditStore,
        properties = properties,
        slackProperties = slackProperties,
        slackBlockKitTemplateService = slackBlockKitTemplateService,
        encryptionService = encryptionService,
        auditLogStore = auditLogStore,
        auditActorResolver = auditActorResolver
    )

    @Nested
    inner class `current 현재 설정 조회` {

        @Test
        fun `DB에 저장된 값이 없으면 기본 프로퍼티 값을 반환한다`() {
            // given: 빈 DB
            every { runtimeSettingStore.list() } returns emptyList()

            // when
            val result = service.current()

            // then: 기본값 반환
            result.defaultHoursBack shouldBe properties.defaultHoursBack
            result.summaryInputMaxChars shouldBe properties.maxContentLength
            result.digestMinImportanceScore shouldBe properties.digestMinImportanceScore
            result.slackBotToken shouldBe slackProperties.botToken
            result.opsLogChannelId shouldBe ""
            result.maintenanceMode shouldBe false
            result.updatedAt shouldBe null
        }

        @Test
        fun `DB에 값이 있으면 해당 값을 반환한다`() {
            // given
            val now = Instant.parse("2026-03-15T10:00:00Z")
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("default_hours_back", "48", now),
                RuntimeSetting("maintenance_mode", "true", now),
                RuntimeSetting("maintenance_message", "System upgrade in progress", now)
            )

            // when
            val result = service.current()

            // then
            result.defaultHoursBack shouldBe 48
            result.maintenanceMode shouldBe true
            result.maintenanceMessage shouldBe "System upgrade in progress"
        }

        @Test
        fun `범위를 초과하는 DB 값은 범위 내로 클램핑한다`() {
            // given: defaultHoursBack의 범위는 1~168인데 999가 저장된 경우
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("default_hours_back", "999", Instant.now()),
                RuntimeSetting("digest_min_importance_score", "5.0", Instant.now())
            )

            // when
            val result = service.current()

            // then: 최대값으로 클램핑
            result.defaultHoursBack shouldBe 168
            result.digestMinImportanceScore shouldBe 1.0f
        }

        @Test
        fun `파싱 불가능한 문자열은 기본값으로 폴백한다`() {
            // given: 숫자가 아닌 문자열이 저장된 경우
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("default_hours_back", "not_a_number", Instant.now())
            )

            // when
            val result = service.current()

            // then: 기본값으로 폴백
            result.defaultHoursBack shouldBe properties.defaultHoursBack
        }
    }

    @Nested
    inner class `update 설정 업데이트` {

        @Test
        fun `유효한 값으로 업데이트하면 감사 이력을 남기고 설정을 반환한다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()
            val savedSettings = slot<List<RuntimeSetting>>()
            val savedAudits = slot<List<RuntimeSettingAudit>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers {
                savedSettings.captured
            }
            every { runtimeSettingAuditStore.saveAll(capture(savedAudits)) } answers {
                savedAudits.captured
            }

            val update = RuntimeSettingService.RuntimeSettingsUpdate(
                defaultHoursBack = 48,
                maintenanceMode = true
            )

            // when
            service.update(update, changedBy = "admin-user")

            // then: 2개 설정이 저장되고 감사 이력도 2개
            verify(exactly = 1) { runtimeSettingStore.saveAll(any()) }
            savedSettings.captured shouldHaveSize 2
            verify(exactly = 1) { runtimeSettingAuditStore.saveAll(any()) }
            savedAudits.captured shouldHaveSize 2
            // 감사 이력에 actor가 기록됨
            savedAudits.captured.forEach {
                it.changedBy shouldBe "admin-user"
                it.action shouldBe "UPDATE"
            }
        }

        @Test
        fun `범위를 벗어나는 defaultHoursBack는 InvalidInputException을 던진다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()

            // when & then: 0은 범위 밖 (1~168)
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(defaultHoursBack = 0))
            }

            // 169도 범위 밖
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(defaultHoursBack = 169))
            }
        }

        @Test
        fun `범위를 벗어나는 digestMinImportanceScore는 InvalidInputException을 던진다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()

            // when & then: 1보다 큰 값
            shouldThrow<InvalidInputException> {
                service.update(
                    RuntimeSettingService.RuntimeSettingsUpdate(digestMinImportanceScore = 1.5f)
                )
            }
        }

        @Test
        fun `유효하지 않은 cron 표현식은 InvalidInputException을 던진다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()

            // when & then
            shouldThrow<InvalidInputException> {
                service.update(
                    RuntimeSettingService.RuntimeSettingsUpdate(slackDigestCron = "invalid cron")
                )
            }
        }

        @Test
        fun `잘못된 opsLogChannelId 입력은 InvalidInputException을 던진다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()

            // when & then
            shouldThrow<InvalidInputException> {
                service.update(
                    RuntimeSettingService.RuntimeSettingsUpdate(opsLogChannelId = "not-a-channel")
                )
            }
        }

        @Test
        fun `Slack archive URL 형태의 opsLogChannelId도 채널 ID로 정규화된다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()
            val savedSettings = slot<List<RuntimeSetting>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }

            // when
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(
                    opsLogChannelId = "https://test.slack.com/archives/C0123456789"
                ),
                changedBy = "admin"
            )

            // then
            val saved = savedSettings.captured.first { it.key == "ops_log_channel_id" }
            saved.value shouldBe "C0123456789"
        }

        @Test
        fun `빈 문자열 opsLogChannelId는 채널 비우기로 허용된다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()
            val savedSettings = slot<List<RuntimeSetting>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }

            // when
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(opsLogChannelId = "   "),
                changedBy = "admin"
            )

            // then
            val saved = savedSettings.captured.first { it.key == "ops_log_channel_id" }
            saved.value shouldBe ""
        }

        @Test
        fun `securityAlertChannelId를 저장하면 정규화된 채널 ID가 저장되고 감사 이력 1건이 남는다`() {
            // given: 저장 전 상태는 비어 있고, 새 채널 ID를 저장한다 (F8).
            every { runtimeSettingStore.list() } returns emptyList()
            val savedSettings = slot<List<RuntimeSetting>>()
            val savedAudits = slot<List<RuntimeSettingAudit>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
            every { runtimeSettingAuditStore.saveAll(capture(savedAudits)) } answers { savedAudits.captured }

            // when
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(
                    securityAlertChannelId = "https://test.slack.com/archives/C9999999999"
                ),
                changedBy = "admin"
            )

            // then: 정규화된 채널 ID 저장 + 감사 이력 1건
            val saved = savedSettings.captured.first { it.key == "security_alert_channel_id" }
            saved.value shouldBe "C9999999999"
            savedAudits.captured shouldHaveSize 1
            savedAudits.captured.first().settingKey shouldBe "security_alert_channel_id"
            savedAudits.captured.first().changedBy shouldBe "admin"
        }

        @Test
        fun `잘못된 securityAlertChannelId 입력은 InvalidInputException을 던진다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()

            // when & then: 채널 ID 규칙에 맞지 않는 입력
            shouldThrow<InvalidInputException> {
                service.update(
                    RuntimeSettingService.RuntimeSettingsUpdate(securityAlertChannelId = "bad-format")
                )
            }
        }

        @Test
        fun `빈 문자열 securityAlertChannelId는 채널 비우기로 허용된다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()
            val savedSettings = slot<List<RuntimeSetting>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }

            // when: 빈 문자열을 보내면 opsLog 폴백을 사용하게 된다.
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(securityAlertChannelId = "   "),
                changedBy = "admin"
            )

            // then
            val saved = savedSettings.captured.first { it.key == "security_alert_channel_id" }
            saved.value shouldBe ""
        }

        @Test
        fun `current 는 저장된 securityAlertChannelId를 그대로 반환한다`() {
            // given
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("security_alert_channel_id", "C7777777777", Instant.now())
            )

            // when
            val result = service.current()

            // then
            result.securityAlertChannelId shouldBe "C7777777777"
        }

        @Test
        fun `competitorWeeklyHour 변경 시 configChangedAt 자동 갱신`() {
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("competitor_weekly_hour", "9", Instant.now())
            )
            val savedSettings = slot<List<RuntimeSetting>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }

            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(competitorWeeklyHour = 18),
                changedBy = "admin"
            )

            // hour 변경에 더해 configChangedAt 키도 함께 저장된다
            val keys = savedSettings.captured.map { it.key }
            keys shouldContain "competitor_weekly_hour"
            keys shouldContain "competitor_weekly_config_changed_at"
        }

        @Test
        fun `competitorWeeklyDay 변경 시 configChangedAt 자동 갱신`() {
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("competitor_weekly_day", "MONDAY", Instant.now())
            )
            val savedSettings = slot<List<RuntimeSetting>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }

            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(competitorWeeklyDay = "THURSDAY"),
                changedBy = "admin"
            )

            val keys = savedSettings.captured.map { it.key }
            keys shouldContain "competitor_weekly_day"
            keys shouldContain "competitor_weekly_config_changed_at"
        }

        @Test
        fun `competitorWeekly 설정에 변경이 없으면 configChangedAt도 갱신되지 않는다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            val savedSettings = slot<List<RuntimeSetting>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }

            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(defaultHoursBack = 48),
                changedBy = "admin"
            )

            // 경쟁사 주간 설정과 무관한 변경이면 configChangedAt 미갱신
            val keys = savedSettings.captured.map { it.key }
            keys.contains("competitor_weekly_config_changed_at") shouldBe false
        }

        @Test
        fun `competitorWeekly 같은 값 update면 configChangedAt 갱신 안 함`() {
            // hour=9가 이미 저장되어 있음
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("competitor_weekly_hour", "9", Instant.now())
            )
            val savedSettings = slot<List<RuntimeSetting>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }

            // 같은 값(9)로 update — 실제 변경 없음
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(competitorWeeklyHour = 9),
                changedBy = "admin"
            )

            // 변경 없으면 saveAll 자체가 호출되지 않음
            verify(exactly = 0) { runtimeSettingStore.saveAll(any()) }
        }

        @Test
        fun `같은 값으로 업데이트하면 감사 이력을 남기지 않는다`() {
            // given: DB에 이미 48이 저장되어 있음
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("default_hours_back", "48", Instant.now())
            )

            val update = RuntimeSettingService.RuntimeSettingsUpdate(defaultHoursBack = 48)

            // when
            service.update(update, changedBy = "admin-user")

            // then: 변경 없으므로 saveAll/auditSaveAll 미호출
            verify(exactly = 0) { runtimeSettingStore.saveAll(any()) }
            verify(exactly = 0) { runtimeSettingAuditStore.saveAll(any()) }
        }

        @Test
        fun `slackBotToken 감사 이력은 마스킹된다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()
            val savedAudits = slot<List<RuntimeSettingAudit>>()
            every { runtimeSettingStore.saveAll(any()) } answers { firstArg() }
            every { runtimeSettingAuditStore.saveAll(capture(savedAudits)) } answers {
                savedAudits.captured
            }

            val update = RuntimeSettingService.RuntimeSettingsUpdate(
                slackBotToken = "xoxb-secret-token-12345"
            )

            // when
            service.update(update, changedBy = "admin")

            // then: newValue가 마스킹됨
            val tokenAudit = savedAudits.captured.find { it.settingKey == "slack_bot_token" }!!
            tokenAudit.newValue shouldBe "***"
        }

        @Test
        fun `update는 통합 감사 로그에도 변경된 키마다 한 건씩 기록한다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()
            every { runtimeSettingStore.saveAll(any()) } answers { firstArg() }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }

            // when: 2개 키 변경
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(
                    defaultHoursBack = 48,
                    maintenanceMode = true
                ),
                changedBy = "admin-user"
            )

            // then: 통합 감사 로그가 변경 건마다 호출된다 (총 2회).
            verify(exactly = 2) {
                auditLogStore.log(
                    actorId = "admin-user",
                    actorName = "admin-user",
                    action = "RUNTIME_SETTING_UPDATED",
                    targetType = "RUNTIME_SETTING",
                    targetId = any(),
                    targetName = null,
                    detail = any()
                )
            }
        }

        @Test
        fun `slackBotToken 변경 시 통합 감사 로그의 detail도 마스킹된다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()
            every { runtimeSettingStore.saveAll(any()) } answers { firstArg() }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }
            val detailSlot = slot<String>()
            every {
                auditLogStore.log(
                    actorId = any(),
                    actorName = any(),
                    action = "RUNTIME_SETTING_UPDATED",
                    targetType = "RUNTIME_SETTING",
                    targetId = "slack_bot_token",
                    targetName = null,
                    detail = capture(detailSlot)
                )
            } answers { }

            // when
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(slackBotToken = "xoxb-secret-12345"),
                changedBy = "admin"
            )

            // then: detail에는 평문 토큰이 노출되지 않고 마스킹 문자열이 들어간다.
            detailSlot.captured stringShouldContain "***"
            detailSlot.captured.contains("xoxb-secret-12345") shouldBe false
        }

        @Test
        fun `changedBy가 빈 문자열이면 system으로 정규화한다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()
            val savedAudits = slot<List<RuntimeSettingAudit>>()
            every { runtimeSettingStore.saveAll(any()) } answers { firstArg() }
            every { runtimeSettingAuditStore.saveAll(capture(savedAudits)) } answers {
                savedAudits.captured
            }

            // when
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(maintenanceMode = true),
                changedBy = "  "
            )

            // then
            savedAudits.captured.forEach {
                it.changedBy shouldBe "system"
            }
        }

        @Test
        fun `digestDefaultMaxItems 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(digestDefaultMaxItems = 0))
            }.message stringShouldContain "digestDefaultMaxItems"
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(digestDefaultMaxItems = 6))
            }
        }

        @Test
        fun `digestMaxMessageChars 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(digestMaxMessageChars = 499))
            }
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(digestMaxMessageChars = 3901))
            }
        }

        @Test
        fun `digestItemSummaryMaxChars 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(digestItemSummaryMaxChars = 239))
            }
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(digestItemSummaryMaxChars = 2201))
            }
        }

        @Test
        fun `digestKeywordMaxCount 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(digestKeywordMaxCount = 0))
            }
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(digestKeywordMaxCount = 11))
            }
        }

        @Test
        fun `jobWorkerBatchSize 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(jobWorkerBatchSize = 0))
            }
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(jobWorkerBatchSize = 51))
            }
        }

        @Test
        fun `jobMaxAttempts 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(jobMaxAttempts = 0))
            }
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(jobMaxAttempts = 11))
            }
        }

        @Test
        fun `jobInitialBackoffSeconds 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(jobInitialBackoffSeconds = 0))
            }
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(jobInitialBackoffSeconds = 901))
            }
        }

        @Test
        fun `slackAutoDigestMaxItems 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(slackAutoDigestMaxItems = 0))
            }
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(slackAutoDigestMaxItems = 6))
            }
        }

        @Test
        fun `slackDailyChannelMessageLimit 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(slackDailyChannelMessageLimit = 0))
            }
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(slackDailyChannelMessageLimit = 1001))
            }
        }

        @Test
        fun `ralphLoopMaxIterations 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(ralphLoopMaxIterations = 0))
            }
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(ralphLoopMaxIterations = 31))
            }
        }

        @Test
        fun `maintenanceMessage가 500자를 초과하면 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            // generic IllegalArgumentException 대신 도메인 예외(InvalidInputException)로 거부한다.
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(maintenanceMessage = "a".repeat(501)))
            }
        }

        @Test
        fun `summaryInputMaxChars 범위 위반 시 예외를 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(summaryInputMaxChars = 499))
            }
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(summaryInputMaxChars = 20001))
            }
        }
    }

    @Nested
    inner class `resetAll 설정 초기화` {

        @Test
        fun `설정이 있으면 전부 삭제하고 감사 이력을 남긴다`() {
            // given
            val existing = listOf(
                RuntimeSetting("default_hours_back", "48", Instant.now()),
                RuntimeSetting("maintenance_mode", "true", Instant.now())
            )
            every { runtimeSettingStore.list() } returns existing andThen emptyList()
            every { runtimeSettingStore.deleteAll() } returns 2
            val savedAudits = slot<List<RuntimeSettingAudit>>()
            every { runtimeSettingAuditStore.saveAll(capture(savedAudits)) } answers {
                savedAudits.captured
            }

            // when
            service.resetAll(changedBy = "admin-user")

            // then
            verify(exactly = 1) { runtimeSettingStore.deleteAll() }
            verify(exactly = 1) { runtimeSettingAuditStore.saveAll(any()) }
            savedAudits.captured shouldHaveSize 2
            savedAudits.captured.forEach {
                it.action shouldBe "RESET"
                it.changedBy shouldBe "admin-user"
                it.newValue shouldBe null
            }
        }

        @Test
        fun `설정이 비어 있으면 삭제와 감사 이력 저장을 하지 않는다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()

            // when
            service.resetAll()

            // then
            verify(exactly = 0) { runtimeSettingStore.deleteAll() }
            verify(exactly = 0) { runtimeSettingAuditStore.saveAll(any()) }
        }
    }

    @Nested
    inner class `maintenanceStatus 점검 모드 조회` {

        @Test
        fun `점검 모드가 활성화되면 active=true와 메시지를 반환한다`() {
            // given
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("maintenance_mode", "true", Instant.now()),
                RuntimeSetting("maintenance_message", "서버 점검 중입니다", Instant.now())
            )

            // when
            val result = service.maintenanceStatus()

            // then
            result.active shouldBe true
            result.message shouldBe "서버 점검 중입니다"
        }

        @Test
        fun `설정이 없으면 기본값 active=false를 반환한다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()

            // when
            val result = service.maintenanceStatus()

            // then
            result.active shouldBe false
            result.message shouldBe ""
        }
    }

    @Nested
    inner class `audits 감사 이력 조회` {

        @Test
        fun `limit 범위가 1~200으로 클램핑된다`() {
            // given
            every { runtimeSettingAuditStore.list(any()) } returns emptyList()

            // when
            service.audits(limit = 0)   // 0 -> 1로 클램핑
            service.audits(limit = 500) // 500 -> 200으로 클램핑

            // then
            verify(exactly = 1) { runtimeSettingAuditStore.list(1) }
            verify(exactly = 1) { runtimeSettingAuditStore.list(200) }
        }

        @Test
        fun `감사 이력을 RuntimeSettingAuditInfo 목록으로 변환한다`() {
            // given
            val auditRecord = RuntimeSettingAudit(
                id = "audit-1",
                settingKey = "default_hours_back",
                oldValue = "24",
                newValue = "48",
                action = "UPDATE",
                changedBy = "admin",
                changedAt = Instant.parse("2026-03-15T10:00:00Z")
            )
            every { runtimeSettingAuditStore.list(30) } returns listOf(auditRecord)

            // when
            val result = service.audits()

            // then
            result shouldHaveSize 1
            result[0].settingKey shouldBe "default_hours_back"
            result[0].oldValue shouldBe "24"
            result[0].newValue shouldBe "48"
            result[0].action shouldBe "UPDATE"
            result[0].changedBy shouldBe "admin"
            result[0].changedAt stringShouldContain "2026-03-15"
        }
    }

    @Nested
    inner class `reviewBatchUxEnabled 플래그` {

        @Test
        fun `DB에 값이 없으면 기본값 false를 반환한다`() {
            // given: 플래그 키가 저장되어 있지 않은 경우
            every { runtimeSettingStore.list() } returns emptyList()

            // when
            val result = service.current()

            // then: 기본값 false (PR D 점진 롤아웃 — 명시적으로 켜야 함)
            result.reviewBatchUxEnabled shouldBe false
        }

        @Test
        fun `DB에 true가 저장되어 있으면 true를 반환한다`() {
            // given
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("review_batch_ux_enabled", "true", Instant.now())
            )

            // when
            val result = service.current()

            // then
            result.reviewBatchUxEnabled shouldBe true
        }

        @Test
        fun `update로 true를 전달하면 저장되고 감사 이력이 남는다`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()
            val savedSettings = slot<List<RuntimeSetting>>()
            val savedAudits = slot<List<RuntimeSettingAudit>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers {
                savedSettings.captured
            }
            every { runtimeSettingAuditStore.saveAll(capture(savedAudits)) } answers {
                savedAudits.captured
            }

            // when
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(reviewBatchUxEnabled = true),
                changedBy = "admin-user"
            )

            // then: 플래그 키가 true로 저장되고 감사 이력에도 기록된다
            savedSettings.captured shouldHaveSize 1
            savedSettings.captured[0].key shouldBe "review_batch_ux_enabled"
            savedSettings.captured[0].value shouldBe "true"
            savedAudits.captured shouldHaveSize 1
            savedAudits.captured[0].settingKey shouldBe "review_batch_ux_enabled"
            savedAudits.captured[0].newValue shouldBe "true"
        }
    }

    @Nested
    inner class `defaultReviewPerCategory 설정` {

        @Test
        fun `DB에 값이 없으면 기본값 20을 반환한다`() {
            // 기본값 20: 300유저 스케일 기준 공평한 카테고리 노출을 위한 초기치
            every { runtimeSettingStore.list() } returns emptyList()

            val result = service.current()

            result.defaultReviewPerCategory shouldBe 20
        }

        @Test
        fun `DB에 저장된 값이 있으면 해당 값을 반환한다`() {
            // 관리자가 운영 중 10으로 낮춘 상태를 시뮬레이션
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting("default_review_per_category", "10", Instant.now())
            )

            val result = service.current()

            result.defaultReviewPerCategory shouldBe 10
        }

        @Test
        fun `update로 0을 전달하면 샘플링 비활성 의미로 저장된다`() {
            // 0 = 샘플링 OFF (기존 최근 N건 동작)
            every { runtimeSettingStore.list() } returns emptyList()
            val savedSettings = slot<List<RuntimeSetting>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers {
                savedSettings.captured
            }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }

            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(defaultReviewPerCategory = 0),
                changedBy = "admin"
            )

            savedSettings.captured shouldHaveSize 1
            savedSettings.captured[0].key shouldBe "default_review_per_category"
            savedSettings.captured[0].value shouldBe "0"
        }

        @Test
        fun `update로 범위를 벗어난 값(-1)을 전달하면 InvalidInputException을 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()

            val ex = shouldThrow<InvalidInputException> {
                service.update(
                    RuntimeSettingService.RuntimeSettingsUpdate(defaultReviewPerCategory = -1)
                )
            }
            ex.message stringShouldContain "defaultReviewPerCategory"
        }

        @Test
        fun `update로 101을 전달하면 InvalidInputException을 던진다`() {
            every { runtimeSettingStore.list() } returns emptyList()

            shouldThrow<InvalidInputException> {
                service.update(
                    RuntimeSettingService.RuntimeSettingsUpdate(defaultReviewPerCategory = 101)
                )
            }
        }
    }

    @Nested
    inner class `신규 ops 설정` {

        @Test
        fun `신규 ops 설정은 기본값으로 초기화된다`() {
            // given: DB에 ops 설정 없음
            every { runtimeSettingStore.list() } returns emptyList()

            // when
            val s = service.current()

            // then: 기본값 확인
            s.opsNotificationProfile shouldBe OpsNotificationProfile.FULL
            s.opsDailyForecastHour shouldBe 8
            s.opsLogsEnabled shouldBe true
            s.opsSilentHoursEnabled shouldBe true
            s.opsBudgetWarnPct shouldBe 80
            s.opsBudgetCriticalPct shouldBe 90
        }

        @Test
        fun `ops 설정 업데이트 후 재조회 시 값이 유지된다`() {
            // given
            val savedSettings = slot<List<RuntimeSetting>>()
            every { runtimeSettingStore.list() } returns emptyList()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
            every { runtimeSettingAuditStore.saveAll(any()) } answers { firstArg() }

            // when: opsNotificationProfile과 opsDailyForecastHour를 업데이트한다.
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(
                    opsNotificationProfile = OpsNotificationProfile.BATCHED,
                    opsDailyForecastHour = 10,
                    opsAdminBaseUrl = "https://admin.example.com",
                )
            )

            // then: 저장된 키/값을 확인한다.
            val saved = savedSettings.captured.associateBy { it.key }
            saved["ops_notification_profile"]?.value shouldBe "BATCHED"
            saved["ops_daily_forecast_hour"]?.value shouldBe "10"
            saved["ops_admin_base_url"]?.value shouldBe "https://admin.example.com"
        }
    }
}
