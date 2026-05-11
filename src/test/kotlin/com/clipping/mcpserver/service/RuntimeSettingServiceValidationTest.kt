package com.clipping.mcpserver.service

import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.config.EncryptionService
import com.clipping.mcpserver.config.SlackProperties
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.RuntimeSetting
import com.clipping.mcpserver.model.RuntimeSettingAudit
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.RuntimeSettingAuditStore
import com.clipping.mcpserver.store.RuntimeSettingStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * ops 설정 범위 검증 및 opsAdminBaseUrl 정규화 테스트.
 */
class RuntimeSettingServiceValidationTest {

    private val runtimeSettingStore = mockk<RuntimeSettingStore>()
    private val runtimeSettingAuditStore = mockk<RuntimeSettingAuditStore>(relaxed = true)
    private val properties = ClippingMcpServerProperties()
    private val slackProperties = SlackProperties(botToken = "xoxb-default", digestCron = "0 0 9 * * MON-FRI")
    private val slackBlockKitTemplateService = mockk<SlackBlockKitTemplateService>(relaxed = true)
    private val encryptionService = EncryptionService("")
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)

    private val service = RuntimeSettingService(
        runtimeSettingStore = runtimeSettingStore,
        runtimeSettingAuditStore = runtimeSettingAuditStore,
        properties = properties,
        slackProperties = slackProperties,
        slackBlockKitTemplateService = slackBlockKitTemplateService,
        encryptionService = encryptionService,
        auditLogStore = auditLogStore,
        auditActorResolver = mockk(relaxed = true)
    )

    init {
        // update()는 내부적으로 list()를 호출하므로 기본 stub을 설정한다.
        every { runtimeSettingStore.list() } returns emptyList()
    }

    private fun stubSave() {
        val savedSettings = slot<List<RuntimeSetting>>()
        every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
        every { runtimeSettingAuditStore.saveAll(any<List<RuntimeSettingAudit>>()) } answers { firstArg() }
    }

    @Nested
    inner class `opsDailyForecastHour 범위 검증` {

        @Test
        fun `opsDailyForecastHour 범위 벗어나면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(opsDailyForecastHour = 25))
            }
        }

        @Test
        fun `opsDailyForecastHour 음수이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(opsDailyForecastHour = -1))
            }
        }

        @Test
        fun `opsDailyForecastHour 23은 유효하다`() {
            stubSave()
            // 예외 없이 저장된다.
            service.update(RuntimeSettingService.RuntimeSettingsUpdate(opsDailyForecastHour = 23))
        }
    }

    @Nested
    inner class `opsBudgetWarnPct vs opsBudgetCriticalPct 교차 검증` {

        @Test
        fun `opsBudgetWarnPct 가 opsBudgetCriticalPct 이상이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.update(
                    RuntimeSettingService.RuntimeSettingsUpdate(
                        opsBudgetWarnPct = 95,
                        opsBudgetCriticalPct = 90
                    )
                )
            }
        }

        @Test
        fun `opsBudgetWarnPct 와 opsBudgetCriticalPct 가 같으면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.update(
                    RuntimeSettingService.RuntimeSettingsUpdate(
                        opsBudgetWarnPct = 80,
                        opsBudgetCriticalPct = 80
                    )
                )
            }
        }

        @Test
        fun `opsBudgetWarnPct 가 opsBudgetCriticalPct 미만이면 저장된다`() {
            stubSave()
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(
                    opsBudgetWarnPct = 70,
                    opsBudgetCriticalPct = 90
                )
            )
        }
    }

    @Nested
    inner class `opsAdminBaseUrl 검증 및 정규화` {

        @Test
        fun `opsAdminBaseUrl 이 https로 시작하지 않으면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.update(
                    RuntimeSettingService.RuntimeSettingsUpdate(opsAdminBaseUrl = "http://example.com")
                )
            }
        }

        @Test
        fun `opsAdminBaseUrl 의 후행 슬래시는 저장 시 제거된다`() {
            val savedSettings = slot<List<RuntimeSetting>>()
            every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }

            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(opsAdminBaseUrl = "https://admin.example.com/")
            )

            val saved = savedSettings.captured.associateBy { it.key }
            saved["ops_admin_base_url"]?.value shouldBe "https://admin.example.com"
        }

        @Test
        fun `빈 opsAdminBaseUrl 은 URL 제거로 허용된다`() {
            stubSave()
            // 빈 문자열은 예외 없이 저장된다.
            service.update(RuntimeSettingService.RuntimeSettingsUpdate(opsAdminBaseUrl = ""))
        }
    }

    @Nested
    inner class `기타 범위 검증` {

        @Test
        fun `opsWeeklyReportHour 범위 벗어나면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(opsWeeklyReportHour = 24))
            }
        }

        @Test
        fun `opsPipelineCooldownMinutes 0이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(opsPipelineCooldownMinutes = 0))
            }
        }

        @Test
        fun `opsIncidentWindowMinutes 61이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(opsIncidentWindowMinutes = 61))
            }
        }

        @Test
        fun `opsIncidentThresholdCategories 1이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(opsIncidentThresholdCategories = 1))
            }
        }

        @Test
        fun `opsRecoveryStreakThreshold 1이면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(opsRecoveryStreakThreshold = 1))
            }
        }
    }
}
