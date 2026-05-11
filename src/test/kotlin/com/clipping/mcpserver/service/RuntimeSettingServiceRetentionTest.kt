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
import java.time.Instant

/**
 * RuntimeSettingService 의 데이터 보관 기간(retention) 설정 키 테스트.
 * retention_rss_items_days (7..365, 기본값 30)
 * retention_batch_summaries_days (7..730, 기본값 90) 의 조회/갱신/검증을 검증한다.
 */
class RuntimeSettingServiceRetentionTest {

    private val runtimeSettingStore = mockk<RuntimeSettingStore>()
    private val runtimeSettingAuditStore = mockk<RuntimeSettingAuditStore>(relaxed = true)
    private val properties = ClippingMcpServerProperties()
    private val slackProperties = SlackProperties(botToken = "xoxb-default", digestCron = "0 0 9 * * MON-FRI")
    private val slackBlockKitTemplateService = mockk<SlackBlockKitTemplateService>(relaxed = true)
    private val encryptionService = EncryptionService("")
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
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

    private fun stubSave() {
        val savedSettings = slot<List<RuntimeSetting>>()
        val savedAudits = slot<List<RuntimeSettingAudit>>()
        every { runtimeSettingStore.saveAll(capture(savedSettings)) } answers { savedSettings.captured }
        every { runtimeSettingAuditStore.saveAll(capture(savedAudits)) } answers { savedAudits.captured }
    }

    @Nested
    inner class `current 기본값 조회` {

        @Test
        fun `설정이 없으면 retentionRssItemsDays=30 retentionBatchSummariesDays=90 default 반환`() {
            // given: 빈 DB
            every { runtimeSettingStore.list() } returns emptyList()

            // when
            val result = service.current()

            // then: 스펙 §2.4 기본값 확인
            result.retentionRssItemsDays shouldBe RuntimeSettingService.RETENTION_RSS_ITEMS_DAYS_DEFAULT
            result.retentionBatchSummariesDays shouldBe RuntimeSettingService.RETENTION_BATCH_SUMMARIES_DAYS_DEFAULT
        }

        @Test
        fun `DB에 저장된 값을 그대로 반환`() {
            // given: 두 보관 기간을 커스텀 값으로 저장해둔 상태
            val now = Instant.now()
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting(RuntimeSettingService.KEY_RETENTION_RSS_ITEMS_DAYS, "60", now),
                RuntimeSetting(RuntimeSettingService.KEY_RETENTION_BATCH_SUMMARIES_DAYS, "180", now)
            )

            // when
            val result = service.current()

            // then
            result.retentionRssItemsDays shouldBe 60
            result.retentionBatchSummariesDays shouldBe 180
        }

        @Test
        fun `DB 값이 min 미만이면 coerceIn 으로 min 으로 clamp`() {
            // given: 범위 아래 값이 DB 에 저장된 비정상 상태
            val now = Instant.now()
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting(RuntimeSettingService.KEY_RETENTION_RSS_ITEMS_DAYS, "1", now),
                RuntimeSetting(RuntimeSettingService.KEY_RETENTION_BATCH_SUMMARIES_DAYS, "3", now)
            )

            // when
            val result = service.current()

            // then: intSetting 이 coerceIn 으로 min 으로 보정
            result.retentionRssItemsDays shouldBe RuntimeSettingService.RETENTION_RSS_ITEMS_DAYS_MIN
            result.retentionBatchSummariesDays shouldBe RuntimeSettingService.RETENTION_BATCH_SUMMARIES_DAYS_MIN
        }
    }

    @Nested
    inner class `update 범위 검증` {

        @Test
        fun `retentionRssItemsDays 6 은 InvalidInputException — min=7 이하`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()

            // when & then
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(retentionRssItemsDays = 6))
            }
        }

        @Test
        fun `retentionRssItemsDays 366 은 InvalidInputException — max=365 초과`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()

            // when & then
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(retentionRssItemsDays = 366))
            }
        }

        @Test
        fun `retentionBatchSummariesDays 731 은 InvalidInputException — max=730 초과`() {
            // given
            every { runtimeSettingStore.list() } returns emptyList()

            // when & then
            shouldThrow<InvalidInputException> {
                service.update(RuntimeSettingService.RuntimeSettingsUpdate(retentionBatchSummariesDays = 731))
            }
        }
    }

    @Nested
    inner class `update 유효값 저장` {

        @Test
        fun `유효값 저장 시 runtime_setting_audits 에 각 key 당 1건씩 총 2건 기록 — delta-based`() {
            // given: 초기 상태가 비어 있으므로 두 값 모두 신규 저장(changed)
            every { runtimeSettingStore.list() } returns emptyList()
            val savedAudits = slot<List<RuntimeSettingAudit>>()
            every { runtimeSettingStore.saveAll(any()) } answers { firstArg() }
            every { runtimeSettingAuditStore.saveAll(capture(savedAudits)) } answers { savedAudits.captured }

            // when: 두 보관 기간을 동시에 갱신
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(
                    retentionRssItemsDays = 45,
                    retentionBatchSummariesDays = 120
                ),
                changedBy = "admin"
            )

            // then: 감사 이력 정확히 2건 — 각 key 당 1건
            savedAudits.captured.size shouldBe 2
            val auditKeys = savedAudits.captured.map { it.settingKey }.toSet()
            auditKeys shouldBe setOf(
                RuntimeSettingService.KEY_RETENTION_RSS_ITEMS_DAYS,
                RuntimeSettingService.KEY_RETENTION_BATCH_SUMMARIES_DAYS
            )
        }

        @Test
        fun `null 필드는 변경 없음 — partial update`() {
            // given: retention_rss_items_days 만 기존 값이 있고 batch_summaries 는 없음
            val now = Instant.now()
            every { runtimeSettingStore.list() } returns listOf(
                RuntimeSetting(RuntimeSettingService.KEY_RETENTION_RSS_ITEMS_DAYS, "30", now)
            )
            val savedAudits = slot<List<RuntimeSettingAudit>>()
            every { runtimeSettingStore.saveAll(any()) } answers { firstArg() }
            every { runtimeSettingAuditStore.saveAll(capture(savedAudits)) } answers { savedAudits.captured }

            // when: retentionBatchSummariesDays 만 갱신하고 retentionRssItemsDays 는 null
            service.update(
                RuntimeSettingService.RuntimeSettingsUpdate(retentionBatchSummariesDays = 200),
                changedBy = "admin"
            )

            // then: rss_items_days 는 건드리지 않았으므로 감사 이력 1건만
            savedAudits.captured.size shouldBe 1
            savedAudits.captured.first().settingKey shouldBe RuntimeSettingService.KEY_RETENTION_BATCH_SUMMARIES_DAYS
        }
    }
}
