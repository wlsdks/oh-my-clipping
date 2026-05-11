package com.ohmyclipping.service

import com.ohmyclipping.observability.SchedulerRunTracker
import com.ohmyclipping.observability.SlackHealthStatus
import com.ohmyclipping.resilience.InMemoryCircuitBreaker
import com.ohmyclipping.service.dto.AiStatus
import com.ohmyclipping.service.dto.DatabaseStatus
import com.ohmyclipping.service.dto.JobQueueStatus
import com.ohmyclipping.service.dto.SlackStatus
import com.ohmyclipping.store.AsyncJobStore
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariPoolMXBean
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment
import java.time.Instant
import javax.sql.DataSource

class SystemStatusServiceTest {

    private val environment = mockk<Environment>()
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val asyncJobStore = mockk<AsyncJobStore>()
    private val itemSummarizationService = mockk<ItemSummarizationService>()
    private val slackHealthStatus = SlackHealthStatus()
    private val schedulerRunTracker = mockk<SchedulerRunTracker>(relaxed = true)

    @Nested
    inner class `formatUptime 메서드` {

        @Test
        fun `0밀리초이면 0d 0h 0m을 반환한다`() {
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeFormatUptime(service, 0L)
            result shouldBe "0d 0h 0m"
        }

        @Test
        fun `59초(59999ms)이면 분 미만이므로 0d 0h 0m을 반환한다`() {
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeFormatUptime(service, 59_999L)
            result shouldBe "0d 0h 0m"
        }

        @Test
        fun `정확히 1분(60000ms)이면 0d 0h 1m을 반환한다`() {
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeFormatUptime(service, 60_000L)
            result shouldBe "0d 0h 1m"
        }

        @Test
        fun `59분(3540000ms)이면 0d 0h 59m을 반환한다`() {
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeFormatUptime(service, 59 * 60_000L)
            result shouldBe "0d 0h 59m"
        }

        @Test
        fun `정확히 1시간(3600000ms)이면 0d 1h 0m을 반환한다`() {
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeFormatUptime(service, 3_600_000L)
            result shouldBe "0d 1h 0m"
        }

        @Test
        fun `정확히 24시간이면 1d 0h 0m을 반환한다`() {
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeFormatUptime(service, 24 * 3_600_000L)
            result shouldBe "1d 0h 0m"
        }

        @Test
        fun `2일 3시간 45분이면 2d 3h 45m을 반환한다`() {
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val ms = (2 * 24 * 60 + 3 * 60 + 45) * 60_000L
            val result = invokeFormatUptime(service, ms)
            result shouldBe "2d 3h 45m"
        }

        /** 리플렉션으로 private formatUptime 메서드를 호출한다. */
        private fun invokeFormatUptime(service: SystemStatusService, uptimeMs: Long): String {
            val method = SystemStatusService::class.java
                .getDeclaredMethod("formatUptime", Long::class.java)
            method.isAccessible = true
            return method.invoke(service, uptimeMs) as String
        }
    }

    @Nested
    inner class `buildDatabaseStatus 메서드` {

        @Test
        fun `HikariDataSource 캐스트 성공 시 풀 정보를 반환한다`() {
            val poolMXBean = mockk<HikariPoolMXBean> {
                every { activeConnections } returns 3
                every { idleConnections } returns 7
                every { totalConnections } returns 10
            }
            val hikariDataSource = mockk<HikariDataSource>()
            every { hikariDataSource.hikariPoolMXBean } returns poolMXBean

            val service = createService(hikariDataSource)
            val result = invokeBuildDatabaseStatus(service)

            result.connected shouldBe true
            result.poolActive shouldBe 3
            result.poolIdle shouldBe 7
            result.poolTotal shouldBe 10
        }

        @Test
        fun `HikariDataSource가 아닌 DataSource이면 기본값을 반환한다`() {
            val plainDataSource = mockk<DataSource>()

            val service = createService(plainDataSource)
            val result = invokeBuildDatabaseStatus(service)

            result.connected shouldBe true
            result.poolActive shouldBe 0
            result.poolIdle shouldBe 0
            result.poolTotal shouldBe 0
        }

        @Test
        fun `HikariDataSource의 hikariPoolMXBean이 null이면 0을 반환한다`() {
            val hikariDataSource = mockk<HikariDataSource> {
                every { hikariPoolMXBean } returns null
            }

            val service = createService(hikariDataSource)
            val result = invokeBuildDatabaseStatus(service)

            result.connected shouldBe true
            result.poolActive shouldBe 0
            result.poolIdle shouldBe 0
            result.poolTotal shouldBe 0
        }

        /** 리플렉션으로 private buildDatabaseStatus 메서드를 호출한다. */
        private fun invokeBuildDatabaseStatus(
            service: SystemStatusService
        ): DatabaseStatus {
            val method = SystemStatusService::class.java
                .getDeclaredMethod("buildDatabaseStatus")
            method.isAccessible = true
            return method.invoke(service) as DatabaseStatus
        }
    }

    @Nested
    inner class `buildSlackStatus 메서드` {

        @Test
        fun `Slack 토큰이 비어 있으면 botTokenConfigured=false를 반환한다`() {
            every { runtimeSettingService.current() } returns runtimeSettings(
                slackBotToken = ""
            )
            val service = createService(mockk<HikariDataSource>(relaxed = true))

            val result = invokeBuildSlackStatus(service)

            result.botTokenConfigured shouldBe false
        }

        @Test
        fun `Slack 토큰이 공백 문자만 있으면 botTokenConfigured=false를 반환한다`() {
            every { runtimeSettingService.current() } returns runtimeSettings(
                slackBotToken = "   "
            )
            val service = createService(mockk<HikariDataSource>(relaxed = true))

            val result = invokeBuildSlackStatus(service)

            result.botTokenConfigured shouldBe false
        }

        @Test
        fun `Slack 토큰이 정상이면 botTokenConfigured=true와 채널 ID를 반환한다`() {
            every { runtimeSettingService.current() } returns runtimeSettings(
                slackBotToken = "xoxb-valid-token"
            )
            val service = createService(mockk<HikariDataSource>(relaxed = true))

            val result = invokeBuildSlackStatus(service)

            result.botTokenConfigured shouldBe true
        }

        @Test
        fun `SlackHealthStatus가 healthy이면 healthy=true를 반환한다`() {
            every { runtimeSettingService.current() } returns runtimeSettings()
            slackHealthStatus.isHealthy.set(true)
            slackHealthStatus.lastCheckTime.set(Instant.parse("2026-04-10T09:00:00Z"))

            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeBuildSlackStatus(service)

            result.healthy shouldBe true
            result.lastCheckTime shouldBe "2026-04-10T09:00:00Z"
        }

        @Test
        fun `SlackHealthStatus가 unhealthy이면 healthy=false를 반환한다`() {
            every { runtimeSettingService.current() } returns runtimeSettings()
            slackHealthStatus.isHealthy.set(false)
            slackHealthStatus.lastCheckTime.set(null)

            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeBuildSlackStatus(service)

            result.healthy shouldBe false
            result.lastCheckTime shouldBe null
        }

        /** 리플렉션으로 private buildSlackStatus 메서드를 호출한다. */
        private fun invokeBuildSlackStatus(
            service: SystemStatusService
        ): SlackStatus {
            val method = SystemStatusService::class.java
                .getDeclaredMethod("buildSlackStatus")
            method.isAccessible = true
            return method.invoke(service) as SlackStatus
        }
    }

    @Nested
    inner class `buildAiStatus 메서드` {

        @Test
        fun `서킷 브레이커가 CLOSED이면 canCall=true와 카운트 0을 반환한다`() {
            val cb = InMemoryCircuitBreaker(name = "gemini", failureThreshold = 5)
            every { itemSummarizationService.geminiCircuitBreaker } returns cb
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeBuildAiStatus(service)
            result.circuitBreakerState shouldBe "CLOSED"
            result.canCall shouldBe true
            result.consecutiveOpenCount shouldBe 0
            result.totalOpenCount shouldBe 0
            result.lastOpenedAt.shouldBeNull()
        }

        @Test
        fun `서킷 브레이커가 OPEN이면 카운트와 시각을 반환한다`() {
            val cb = InMemoryCircuitBreaker(name = "gemini", failureThreshold = 3)
            repeat(3) { cb.recordFailure() }
            every { itemSummarizationService.geminiCircuitBreaker } returns cb
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeBuildAiStatus(service)
            result.circuitBreakerState shouldBe "OPEN"
            result.canCall shouldBe false
            result.consecutiveOpenCount shouldBe 0
            result.totalOpenCount shouldBe 1
            result.lastOpenedAt.shouldNotBeNull()
        }

        private fun invokeBuildAiStatus(
            service: SystemStatusService
        ): AiStatus {
            val method = SystemStatusService::class.java
                .getDeclaredMethod("buildAiStatus")
            method.isAccessible = true
            return method.invoke(service) as AiStatus
        }
    }

    @Nested
    inner class `buildJobQueueStatus 메서드` {

        @Test
        fun `대기 작업 수와 임계값을 반환한다`() {
            every { asyncJobStore.countPending() } returns 42L

            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeBuildJobQueueStatus(service)

            result.pendingJobs shouldBe 42L
            result.threshold shouldBe 100
        }

        @Test
        fun `대기 작업이 0이면 0을 반환한다`() {
            every { asyncJobStore.countPending() } returns 0L

            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = invokeBuildJobQueueStatus(service)

            result.pendingJobs shouldBe 0L
        }

        private fun invokeBuildJobQueueStatus(
            service: SystemStatusService
        ): JobQueueStatus {
            val method = SystemStatusService::class.java
                .getDeclaredMethod("buildJobQueueStatus")
            method.isAccessible = true
            return method.invoke(service) as JobQueueStatus
        }
    }

    @Nested
    inner class `스케줄러 실행 이력 병합` {

        @Test
        fun `tracker에 기록이 있으면 lastRunAt과 lastResult를 포함한다`() {
            setupDefaultMocks()
            every { schedulerRunTracker.getLastRun("async_clip_job") } returns
                SchedulerRunTracker.SchedulerRunRecord(
                    lastRunAt = Instant.parse("2026-04-10T09:00:00Z"), success = true)
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = service.getStatus()
            val asyncWorker = result.schedulers.first { it.name == "AsyncClipJobWorker" }
            asyncWorker.lastRunAt shouldBe "2026-04-10T09:00:00Z"
            asyncWorker.lastResult shouldBe "success"
        }

        @Test
        fun `tracker에 기록이 없으면 lastRunAt과 lastResult는 null이다`() {
            setupDefaultMocks()
            every { schedulerRunTracker.getLastRun(any()) } returns null
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = service.getStatus()
            val asyncWorker = result.schedulers.first { it.name == "AsyncClipJobWorker" }
            asyncWorker.lastRunAt.shouldBeNull()
            asyncWorker.lastResult.shouldBeNull()
        }

        @Test
        fun `tracker 실패 기록이면 lastResult가 failure이다`() {
            setupDefaultMocks()
            every { schedulerRunTracker.getLastRun("data_cleanup") } returns
                SchedulerRunTracker.SchedulerRunRecord(
                    lastRunAt = Instant.parse("2026-04-10T03:00:00Z"), success = false)
            val service = createService(mockk<HikariDataSource>(relaxed = true))
            val result = service.getStatus()
            val cleanupScheduler = result.schedulers.first { it.name == "DataCleanupScheduler" }
            cleanupScheduler.lastRunAt shouldBe "2026-04-10T03:00:00Z"
            cleanupScheduler.lastResult shouldBe "failure"
        }

        private fun setupDefaultMocks() {
            every { environment.activeProfiles } returns arrayOf("prod")
            every { runtimeSettingService.current() } returns runtimeSettings()
            val cb = InMemoryCircuitBreaker(name = "gemini", failureThreshold = 5)
            every { itemSummarizationService.geminiCircuitBreaker } returns cb
            every { asyncJobStore.countPending() } returns 0L
            slackHealthStatus.isHealthy.set(true)
        }
    }

    @Nested
    inner class `getStatus 메서드` {

        @Test
        fun `전체 조합 시 server, database, slack, ai, jobQueue, schedulers를 정상 반환한다`() {
            val poolMXBean = mockk<HikariPoolMXBean> {
                every { activeConnections } returns 2
                every { idleConnections } returns 8
                every { totalConnections } returns 10
            }
            val hikariDataSource = mockk<HikariDataSource>()
            every { hikariDataSource.hikariPoolMXBean } returns poolMXBean

            every { environment.activeProfiles } returns arrayOf("prod")
            every { runtimeSettingService.current() } returns runtimeSettings(
                slackBotToken = "xoxb-test"
            )

            val cb = InMemoryCircuitBreaker(name = "gemini", failureThreshold = 5)
            every { itemSummarizationService.geminiCircuitBreaker } returns cb
            every { asyncJobStore.countPending() } returns 5L
            slackHealthStatus.isHealthy.set(true)
            slackHealthStatus.lastCheckTime.set(Instant.parse("2026-04-10T09:00:00Z"))

            val service = createService(hikariDataSource)
            val result = service.getStatus()

            // server
            result.server.shouldNotBeNull()
            result.server.activeProfiles shouldBe listOf("prod")
            result.server.javaVersion shouldBe System.getProperty("java.version")

            // database
            result.database.connected shouldBe true
            result.database.poolActive shouldBe 2
            result.database.poolIdle shouldBe 8
            result.database.poolTotal shouldBe 10

            // slack (including new fields)
            result.slack.botTokenConfigured shouldBe true
            result.slack.healthy shouldBe true
            result.slack.lastCheckTime shouldBe "2026-04-10T09:00:00Z"

            // ai — 실제 InMemoryCircuitBreaker 사용
            result.ai.circuitBreakerState shouldBe "CLOSED"
            result.ai.canCall shouldBe true
            result.ai.consecutiveOpenCount shouldBe 0
            result.ai.totalOpenCount shouldBe 0
            result.ai.lastOpenedAt.shouldBeNull()

            // jobQueue
            result.jobQueue.pendingJobs shouldBe 5L
            result.jobQueue.threshold shouldBe 100

            // schedulers
            result.schedulers.size shouldBe 13
        }
    }

    private fun createService(dataSource: DataSource): SystemStatusService =
        SystemStatusService(
            environment = environment,
            dataSource = dataSource,
            runtimeSettingService = runtimeSettingService,
            itemSummarizationService = itemSummarizationService,
            asyncJobStore = asyncJobStore,
            slackHealthStatus = slackHealthStatus,
            schedulerRunTracker = schedulerRunTracker
        )

    private fun runtimeSettings(
        slackBotToken: String = "xoxb-test"
    ): RuntimeSettingService.RuntimeSettings =
        RuntimeSettingService.RuntimeSettings(
            defaultHoursBack = 24,
            summaryInputMaxChars = 5000,
            digestMinImportanceScore = 0.3f,
            digestDefaultMaxItems = 10,
            digestMaxMessageChars = 3000,
            digestItemSummaryMaxChars = 500,
            digestKeywordMaxCount = 5,
            jobWorkerBatchSize = 5,
            jobMaxAttempts = 3,
            jobInitialBackoffSeconds = 5,
            slackBotToken = slackBotToken,
            slackDigestBlockKitTemplate = "default",
            slackAutoDigestEnabled = false,
            slackDigestCron = "0 0 9 * * MON-FRI",
            slackAutoDigestMaxItems = 10,
            slackAutoDigestUnsentOnly = true,
            slackDailyChannelMessageLimit = 50,
            updatedAt = null
        )
}
