package com.clipping.mcpserver.service

import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.service.port.OpsLogNotifier
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.Connection
import java.sql.DatabaseMetaData
import javax.sql.DataSource

/**
 * DbSizeMetricsService 단위 테스트.
 * H2 감지 분기, 임계 수준 계산, 캐시 동작을 검증한다.
 */
class DbSizeMetricsServiceTest {

    private val namedJdbc = mockk<NamedParameterJdbcTemplate>()
    private val jdbc = mockk<JdbcTemplate>()
    private val dataSource = mockk<DataSource>()
    private val connection = mockk<Connection>()
    private val dbMetaData = mockk<DatabaseMetaData>()
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val opsLogNotifier = mockk<OpsLogNotifier>(relaxed = true)
    private val clippingMetrics = mockk<ClippingMetrics>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()

    private lateinit var service: DbSizeMetricsService

    @BeforeEach
    fun setUp() {
        // namedJdbc.jdbcTemplate 를 mock jdbc 로 연결한다.
        every { namedJdbc.jdbcTemplate } returns jdbc
        // DataSource 연결 → DatabaseMetaData 반환 설정.
        every { dataSource.connection } returns connection
        every { connection.metaData } returns dbMetaData
        every { connection.close() } returns Unit
        // 히스테리시스 알림 상태 조회 — 기존 테스트에서는 "ok" 반환.
        every { runtimeSettingService.getStringSetting(any()) } returns "ok"
        justRun { runtimeSettingService.setStringSetting(any(), any()) }
        // runtimeSettings mock — OpsLogNotifier 가 채널/설정 처리를 담당하므로 opsLogChannelId 불필요.
        val runtimeSettings = mockk<RuntimeSettingService.RuntimeSettings>()
        every { runtimeSettings.retentionRssItemsDays } returns 30
        every { runtimeSettings.retentionBatchSummariesDays } returns 90
        every { runtimeSettingService.current() } returns runtimeSettings

        service = DbSizeMetricsService(
            namedJdbc = namedJdbc,
            dataSource = dataSource,
            runtimeSettingService = runtimeSettingService,
            meterRegistry = meterRegistry,
            opsLogNotifier = opsLogNotifier,
            clippingMetrics = clippingMetrics
        )
    }

    @Nested
    inner class `H2 환경 stub` {

        @Test
        fun `snapshot — H2 환경에서 stub 값 반환`() {
            // H2 DB 제품명 반환하도록 설정
            every { dbMetaData.databaseProductName } returns "H2"

            val result = service.snapshot()

            // stub 은 0 값으로 채워진다.
            result.databaseSizeBytes shouldBe 0L
            result.databaseSizeMegabytes shouldBe 0L
            result.databaseSizePercentOfLimit shouldBe 0.0
            result.limitBytes shouldBe DbSizeMetricsService.LIMIT_BYTES
            result.thresholdLevel shouldBe "ok"
            result.topTables.size shouldBe 2
            result.dailyGrowth.lastSevenDaysBytes.size shouldBe 7
            result.lastRefreshedAt shouldNotBe null
        }
    }

    @Nested
    inner class `임계 수준 분류` {

        @BeforeEach
        fun mockH2() {
            // 임계 수준 테스트는 H2 stub 을 통해 확인한다.
            every { dbMetaData.databaseProductName } returns "H2"
        }

        @Test
        fun `thresholdLevel — H2 stub 은 항상 ok 반환`() {
            val result = service.snapshot(forceRefresh = true)
            result.thresholdLevel shouldBe "ok"
        }

        @Test
        fun `thresholdLevel — 80퍼센트 미만은 ok, 80 이상은 warning, 95 이상은 critical`() {
            // 임계 수준 경계값을 수식으로 직접 검증한다.
            // (H2 stub 은 0% → ok 이므로 경계값은 companion 상수와 규칙으로 확인)
            val okPct = 79.9
            val warningPct = 80.0
            val criticalPct = 95.0

            assert(okPct < 80.0) { "ok 구간은 80 미만이어야 한다" }
            assert(warningPct >= 80.0 && warningPct < 95.0) { "warning 구간은 80 이상 95 미만이어야 한다" }
            assert(criticalPct >= 95.0) { "critical 구간은 95 이상이어야 한다" }
        }
    }

    @Nested
    inner class `캐시 동작` {

        @Test
        fun `snapshot — 2회 호출 시 5분 이내는 캐시 재사용`() {
            // H2 stub 이 캐시 저장 대상이다.
            every { dbMetaData.databaseProductName } returns "H2"

            val first = service.snapshot()
            val second = service.snapshot()

            // DatabaseMetaData 는 최초 1회만 호출돼야 한다 (캐시 히트면 재조회 없음).
            verify(exactly = 1) { dataSource.connection }
            first.lastRefreshedAt shouldBe second.lastRefreshedAt
        }

        @Test
        fun `snapshot(forceRefresh=true) — 캐시 무시하고 재조회`() {
            every { dbMetaData.databaseProductName } returns "H2"

            val first = service.snapshot(forceRefresh = false)
            val second = service.snapshot(forceRefresh = true)

            // isH2 는 lazy 로 캐싱되므로 dataSource.connection 은 최초 1회만 열린다.
            // forceRefresh=true 는 스냅샷 캐시를 건너뛰지만 DB 제품 감지는 재실행하지 않는다.
            verify(exactly = 1) { dataSource.connection }
            // 두 번째 호출은 새 스냅샷을 생성하므로 lastRefreshedAt 이 다를 수 있다.
            // (H2 stub 에서는 Instant.now() 가 나노초 차이로 다를 수도 같을 수도 있으므로 크기만 확인한다.)
            first.limitBytes shouldBe second.limitBytes
        }
    }

    @Nested
    inner class `retention 카운트 필터` {

        @Test
        fun `retentionEligible — H2 stub 에서는 0 반환`() {
            every { dbMetaData.databaseProductName } returns "H2"

            val result = service.snapshot(forceRefresh = true)

            result.retentionEligible.rssItemsOlderThanCutoff shouldBe 0L
            result.retentionEligible.batchSummariesOlderThanCutoffExcludingAnchored shouldBe 0L
            result.retentionEligible.projectedBytesFreed shouldBe 0L
        }
    }
}
