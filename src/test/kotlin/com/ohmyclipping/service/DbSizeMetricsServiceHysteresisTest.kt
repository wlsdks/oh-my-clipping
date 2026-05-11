package com.ohmyclipping.service

import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.dto.DailyGrowthSummary
import com.ohmyclipping.service.dto.DbSizeSnapshot
import com.ohmyclipping.service.dto.RetentionEligibleSummary
import com.ohmyclipping.service.port.OpsLogNotifier
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * DbSizeMetricsService 히스테리시스 + Slack 알림 단위 테스트.
 *
 * 테스트 대상: evaluateAndFireHysteresisAlert / computeHysteresisLevel
 * - critical 첫 진입 시 Slack 알림 발송 (엣지 트리거)
 * - critical 유지 중 재발송 억제
 * - 90-95% 억제 구간(히스테리시스 밴드) 유지
 * - 90% 미만 복귀 시 ok 초기화
 * - warning(80-95%) 은 Slack 미발송
 * - setStringSetting 으로 상태가 올바르게 퍼시스팅되는지 확인
 */
class DbSizeMetricsServiceHysteresisTest {

    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val opsLogNotifier = mockk<OpsLogNotifier>()
    private val clippingMetrics = mockk<ClippingMetrics>()

    /** service.computeHysteresisLevel 을 직접 호출하기 위해 내부 메서드를 노출한 래퍼. */
    private lateinit var service: DbSizeMetricsService

    @BeforeEach
    fun setUp() {
        // meterRegistry 없이 computeHysteresisLevel 만 테스트하는 목적이므로
        // DbSizeMetricsService 인스턴스는 테스트마다 직접 구성한다.
        // ClippingMetrics 와 OpsLogNotifier 는 mock 으로 대체한다.
        justRun { clippingMetrics.recordDbSizeAlertFired(any()) }
    }

    // ── 히스테리시스 수준 계산 ─────────────────────────────────────────────

    @Nested
    inner class `computeHysteresisLevel — 상태 전이 규칙` {

        /**
         * computeHysteresisLevel 은 인스턴스 메서드이므로 minimal mock 으로 service 를 구성한다.
         * DbSizeMetricsService 의 init 블록에서 MeterRegistry 가 필요하므로
         * SimpleMeterRegistry 를 사용한다.
         */
        private val meterRegistry = io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        private val namedJdbc = mockk<org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate>()
        private val dataSource = mockk<javax.sql.DataSource>()

        @BeforeEach
        fun buildService() {
            service = DbSizeMetricsService(
                namedJdbc = namedJdbc,
                dataSource = dataSource,
                runtimeSettingService = runtimeSettingService,
                meterRegistry = meterRegistry,
                opsLogNotifier = opsLogNotifier,
                clippingMetrics = clippingMetrics
            )
        }

        @Test
        fun `ok 상태에서 95퍼센트 이상이면 critical 로 전환`() {
            val newLevel = service.computeHysteresisLevel("ok", 96.0)
            newLevel shouldBe "critical"
        }

        @Test
        fun `warning 상태에서 95퍼센트 이상이면 critical 로 전환`() {
            val newLevel = service.computeHysteresisLevel("warning", 95.0)
            newLevel shouldBe "critical"
        }

        @Test
        fun `critical 상태에서 96퍼센트 유지 시 critical 그대로`() {
            val newLevel = service.computeHysteresisLevel("critical", 96.0)
            newLevel shouldBe "critical"
        }

        @Test
        fun `critical 상태에서 94퍼센트 억제 구간은 critical 유지`() {
            val newLevel = service.computeHysteresisLevel("critical", 94.0)
            newLevel shouldBe "critical"
        }

        @Test
        fun `critical 상태에서 89퍼센트 90퍼센트 미만이면 ok 로 초기화`() {
            val newLevel = service.computeHysteresisLevel("critical", 89.0)
            newLevel shouldBe "ok"
        }

        @Test
        fun `ok 상태에서 85퍼센트 warning 구간은 warning 으로 전환`() {
            val newLevel = service.computeHysteresisLevel("ok", 85.0)
            newLevel shouldBe "warning"
        }

        @Test
        fun `ok 상태에서 79퍼센트 ok 구간은 ok 유지`() {
            val newLevel = service.computeHysteresisLevel("ok", 79.0)
            newLevel shouldBe "ok"
        }
    }

    // ── 엔드-투-엔드 히스테리시스 알림 시나리오 ──────────────────────────────

    @Nested
    inner class `Slack 알림 발송 시나리오` {

        private val meterRegistry = io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        private val namedJdbc = mockk<org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate>()
        private val dataSource = mockk<javax.sql.DataSource>()
        private val connection = mockk<java.sql.Connection>()
        private val dbMetaData = mockk<java.sql.DatabaseMetaData>()
        private val jdbc = mockk<org.springframework.jdbc.core.JdbcTemplate>()

        @BeforeEach
        fun buildService() {
            // H2 stub 경로를 타도록 설정한다.
            every { namedJdbc.jdbcTemplate } returns jdbc
            every { dataSource.connection } returns connection
            every { connection.metaData } returns dbMetaData
            every { connection.close() } returns Unit
            every { dbMetaData.databaseProductName } returns "H2"

            service = DbSizeMetricsService(
                namedJdbc = namedJdbc,
                dataSource = dataSource,
                runtimeSettingService = runtimeSettingService,
                meterRegistry = meterRegistry,
                opsLogNotifier = opsLogNotifier,
                clippingMetrics = clippingMetrics
            )
        }

        /**
         * 헬퍼: snapshot() 가 실제 DB 조회 없이 동작하도록 runtimeSettingService 를 세팅하고
         * 원하는 pct 의 스냅샷을 반환할 수 있게 service 내부 캐시를 강제 주입한다.
         */
        private fun stubSnapshotWithPct(
            previousLevel: String,
            currentPct: Double
        ): DbSizeSnapshot {
            // runtimeSettingService.getStringSetting 은 이전 수준 반환
            every {
                runtimeSettingService.getStringSetting(RuntimeSettingService.KEY_DB_SIZE_ALERT_LAST_LEVEL)
            } returns previousLevel

            // setStringSetting 은 호출만 허용
            justRun {
                runtimeSettingService.setStringSetting(any(), any())
            }

            // current() — OpsLogNotifier 가 채널/설정 처리를 담당하므로 runtimeSettings 를 간소화한다.
            val runtimeSettings = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtimeSettings.retentionRssItemsDays } returns 30
            every { runtimeSettings.retentionBatchSummariesDays } returns 90
            every { runtimeSettingService.current() } returns runtimeSettings

            // OpsLogNotifier.postDbSizeCritical 은 호출 허용 (justRun)
            justRun {
                opsLogNotifier.postDbSizeCritical(any(), any(), any())
            }

            val snapshot = DbSizeSnapshot(
                databaseSizeBytes = (DbSizeMetricsService.LIMIT_BYTES * currentPct / 100.0).toLong(),
                databaseSizeMegabytes = (DbSizeMetricsService.LIMIT_BYTES * currentPct / 100.0 / (1024 * 1024)).toLong(),
                databaseSizePercentOfLimit = currentPct,
                limitBytes = DbSizeMetricsService.LIMIT_BYTES,
                thresholdLevel = when {
                    currentPct >= 95.0 -> "critical"
                    currentPct >= 80.0 -> "warning"
                    else -> "ok"
                },
                topTables = emptyList(),
                retentionEligible = RetentionEligibleSummary(0L, 0L, 0L),
                dailyGrowth = DailyGrowthSummary(List(7) { 0L }, 0L),
                lastRefreshedAt = Instant.now()
            )
            return snapshot
        }

        @Test
        fun `첫 번째 95퍼센트 초과 시 Slack 알림 1회 발송`() {
            val snapshot = stubSnapshotWithPct(previousLevel = "ok", currentPct = 96.0)

            // evaluateAndFireHysteresisAlert 를 직접 호출한다 (internal 메서드는 같은 패키지에서 접근 가능).
            service.evaluateAndFireHysteresisAlert(snapshot)

            // OpsLogNotifier.postDbSizeCritical 이 정확히 1회 호출됐는지 확인한다.
            verify(exactly = 1) {
                opsLogNotifier.postDbSizeCritical(
                    databaseSizeMegabytes = any(),
                    limitMegabytes = any(),
                    percent = any()
                )
            }
            // 메트릭도 1회 기록됐는지 확인한다.
            verify(exactly = 1) { clippingMetrics.recordDbSizeAlertFired("critical") }
            // 새 상태가 DB 에 저장됐는지 확인한다.
            verify(exactly = 1) {
                runtimeSettingService.setStringSetting(
                    RuntimeSettingService.KEY_DB_SIZE_ALERT_LAST_LEVEL, "critical"
                )
            }
        }

        @Test
        fun `이미 critical 상태에서 96퍼센트 유지 시 Slack 재발송 없음`() {
            val snapshot = stubSnapshotWithPct(previousLevel = "critical", currentPct = 96.0)

            service.evaluateAndFireHysteresisAlert(snapshot)

            // critical → critical 은 상태 변화 없으므로 OpsLogNotifier 미호출.
            verify(exactly = 0) { opsLogNotifier.postDbSizeCritical(any(), any(), any()) }
            verify(exactly = 0) { clippingMetrics.recordDbSizeAlertFired(any()) }
        }

        @Test
        fun `critical 상태에서 94퍼센트 억제 구간으로 하락 시 알림 미발송, 상태 ok 미전환`() {
            val snapshot = stubSnapshotWithPct(previousLevel = "critical", currentPct = 94.0)

            service.evaluateAndFireHysteresisAlert(snapshot)

            // 억제 구간(90-95%)이므로 알림 미발송, 상태도 critical 유지 (setStringSetting 미호출).
            verify(exactly = 0) { opsLogNotifier.postDbSizeCritical(any(), any(), any()) }
            verify(exactly = 0) {
                runtimeSettingService.setStringSetting(any(), any())
            }
        }

        @Test
        fun `critical 상태에서 89퍼센트 90퍼센트 미만으로 하락 시 상태 ok 초기화, 알림 미발송`() {
            val snapshot = stubSnapshotWithPct(previousLevel = "critical", currentPct = 89.0)

            service.evaluateAndFireHysteresisAlert(snapshot)

            // ok 초기화 → 알림 미발송.
            verify(exactly = 0) { opsLogNotifier.postDbSizeCritical(any(), any(), any()) }
            // 상태가 ok 로 변경됐는지 확인한다.
            verify(exactly = 1) {
                runtimeSettingService.setStringSetting(
                    RuntimeSettingService.KEY_DB_SIZE_ALERT_LAST_LEVEL, "ok"
                )
            }
        }

        @Test
        fun `warning 상태 85퍼센트는 알림 발송하지 않음`() {
            val snapshot = stubSnapshotWithPct(previousLevel = "ok", currentPct = 85.0)

            service.evaluateAndFireHysteresisAlert(snapshot)

            // warning 은 알림 대상이 아니다.
            verify(exactly = 0) { opsLogNotifier.postDbSizeCritical(any(), any(), any()) }
            verify(exactly = 0) { clippingMetrics.recordDbSizeAlertFired(any()) }
        }

        @Test
        fun `setStringSetting 이 올바른 키와 값으로 호출됨 — 퍼시스팅 검증`() {
            val levelSlot = slot<String>()
            val snapshot = stubSnapshotWithPct(previousLevel = "ok", currentPct = 96.0)

            service.evaluateAndFireHysteresisAlert(snapshot)

            // KEY_DB_SIZE_ALERT_LAST_LEVEL 키로 "critical" 값이 저장됐는지 검증한다.
            verify {
                runtimeSettingService.setStringSetting(
                    RuntimeSettingService.KEY_DB_SIZE_ALERT_LAST_LEVEL,
                    capture(levelSlot)
                )
            }
            levelSlot.captured shouldBe "critical"
        }
    }
}
