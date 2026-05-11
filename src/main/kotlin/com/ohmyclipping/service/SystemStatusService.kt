package com.ohmyclipping.service

import com.ohmyclipping.observability.SchedulerRunTracker
import com.ohmyclipping.observability.SlackHealthStatus
import com.ohmyclipping.service.dto.admin.AiStatus
import com.ohmyclipping.service.dto.admin.DatabaseStatus
import com.ohmyclipping.service.dto.admin.JobQueueStatus
import com.ohmyclipping.service.dto.admin.SchedulerInfo
import com.ohmyclipping.service.dto.admin.ServerStatus
import com.ohmyclipping.service.dto.admin.SlackStatus
import com.ohmyclipping.service.dto.admin.SystemStatusResponse
import com.ohmyclipping.store.AsyncJobStore
import com.zaxxer.hikari.HikariDataSource
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.lang.management.ManagementFactory
import javax.sql.DataSource

/**
 * 시스템 상태(서버, DB, Slack, AI, 작업 큐, 스케줄러) 정보를 수집하여 반환한다.
 * 관리자 대시보드에서 시스템 헬스를 한눈에 확인할 수 있도록 지원한다.
 */
@Service
class SystemStatusService(
    private val environment: Environment,
    private val dataSource: DataSource,
    private val runtimeSettingService: RuntimeSettingService,
    private val itemSummarizationService: ItemSummarizationService,
    private val asyncJobStore: AsyncJobStore,
    private val slackHealthStatus: SlackHealthStatus,
    private val schedulerRunTracker: SchedulerRunTracker
) {

    /** 서버, DB, Slack, AI, 작업 큐, 스케줄러 상태를 한 번에 조회한다. */
    fun getStatus(): SystemStatusResponse {
        return SystemStatusResponse(
            server = buildServerStatus(),
            database = buildDatabaseStatus(),
            slack = buildSlackStatus(),
            ai = buildAiStatus(),
            jobQueue = buildJobQueueStatus(),
            // 스케줄러별로 트래커 실행 이력을 병합하여 반환한다
            schedulers = KNOWN_SCHEDULERS.map { info ->
                val trackerKey = SCHEDULER_TRACKER_KEYS[info.name]
                val lastRun = trackerKey?.let { schedulerRunTracker.getLastRun(it) }
                info.copy(
                    lastRunAt = lastRun?.lastRunAt?.toString(),
                    lastResult = lastRun?.let { if (it.success) "success" else "failure" }
                )
            }
        )
    }

    /** JVM 업타임, 자바 버전, 활성 프로필, 메모리 사용량을 수집한다. */
    private fun buildServerStatus(): ServerStatus {
        val runtime = Runtime.getRuntime()
        val uptimeMs = ManagementFactory.getRuntimeMXBean().uptime

        return ServerStatus(
            uptime = formatUptime(uptimeMs),
            javaVersion = System.getProperty("java.version"),
            activeProfiles = environment.activeProfiles.toList(),
            memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / MB,
            memoryMaxMb = runtime.maxMemory() / MB
        )
    }

    /**
     * HikariCP 커넥션 풀 상태를 조회한다.
     * HikariDataSource가 아닌 경우 기본값을 반환한다.
     */
    private fun buildDatabaseStatus(): DatabaseStatus {
        return try {
            val hikari = dataSource as HikariDataSource
            val pool = hikari.hikariPoolMXBean
            DatabaseStatus(
                connected = true,
                poolActive = pool?.activeConnections ?: 0,
                poolIdle = pool?.idleConnections ?: 0,
                poolTotal = pool?.totalConnections ?: 0
            )
        } catch (_: ClassCastException) {
            // HikariDataSource가 아닌 경우 연결 가능 여부만 확인
            DatabaseStatus(
                connected = true,
                poolActive = 0,
                poolIdle = 0,
                poolTotal = 0
            )
        }
    }

    /** Slack 봇 토큰 설정 여부, 기본 채널 ID, 헬스 상태를 확인한다. */
    private fun buildSlackStatus(): SlackStatus {
        val settings = runtimeSettingService.current()
        return SlackStatus(
            botTokenConfigured = settings.slackBotToken.isNotBlank(),
            healthy = slackHealthStatus.isHealthy.get(),
            lastCheckTime = slackHealthStatus.lastCheckTime.get()?.toString()
        )
    }

    /** Gemini API 서킷 브레이커 스냅숏을 조회하여 상태와 카운트를 반환한다. */
    private fun buildAiStatus(): AiStatus {
        val snap = itemSummarizationService.geminiCircuitBreaker.snapshot()
        return AiStatus(
            circuitBreakerState = snap.state.name,
            canCall = snap.canCall,
            consecutiveOpenCount = snap.consecutiveOpenCount,
            totalOpenCount = snap.totalOpenCount,
            lastOpenedAt = snap.openedAt?.toString()
        )
    }

    /** 비동기 작업 큐의 대기 작업 수를 조회한다. */
    private fun buildJobQueueStatus(): JobQueueStatus {
        return JobQueueStatus(
            pendingJobs = asyncJobStore.countPending(),
            threshold = PENDING_JOB_THRESHOLD
        )
    }

    /** 밀리초 단위 업타임을 "Xd Xh Xm" 형식으로 변환한다. */
    private fun formatUptime(uptimeMs: Long): String {
        val totalMinutes = uptimeMs / MS_PER_MINUTE
        val days = totalMinutes / MINUTES_PER_DAY
        val hours = (totalMinutes % MINUTES_PER_DAY) / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return "${days}d ${hours}h ${minutes}m"
    }

    companion object {
        private const val MB = 1_048_576L
        private const val MS_PER_MINUTE = 60_000L
        private const val MINUTES_PER_HOUR = 60L
        private const val MINUTES_PER_DAY = 1_440L
        private const val PENDING_JOB_THRESHOLD = 100

        /** 스케줄러 이름 → SchedulerRunTracker 키 매핑. */
        private val SCHEDULER_TRACKER_KEYS = mapOf(
            "AsyncClipJobWorker" to "async_clip_job",
            "StuckJobRecoveryScheduler" to "stuck_job_recovery",
            "SlackDigestWorker" to "slack_digest",
            "SlackTokenValidationScheduler" to "slack_token_validation",
            "SlackSocketModeService" to "slack_socket_mode",
            "SourceHealthScheduler (비활성화)" to "source_health_deactivate",
            "SourceHealthScheduler (재시도)" to "source_health_retry",
            "EmptyCategoryScheduler" to "empty_category",
            "DataCleanupScheduler" to "data_cleanup",
            "CostAlertScheduler" to "cost_alert",
            "AutoReportScheduler" to "auto_report",
            "CompanySearchService" to "company_search",
            "RateLimitFilter" to "rate_limit",
        )

        /** 코드베이스에 등록된 스케줄러 목록 (정적). */
        private val KNOWN_SCHEDULERS = listOf(
            SchedulerInfo(
                name = "AsyncClipJobWorker",
                schedule = "fixedDelay 3초",
                description = "비동기 클리핑 작업 큐 폴링"
            ),
            SchedulerInfo(
                name = "StuckJobRecoveryScheduler",
                schedule = "fixedDelay 1분",
                description = "멈춘 작업 복구"
            ),
            SchedulerInfo(
                name = "SlackDigestWorker",
                schedule = "fixedDelay 10초",
                description = "Slack 다이제스트 발송"
            ),
            SchedulerInfo(
                name = "SlackTokenValidationScheduler",
                schedule = "매시 정각",
                description = "Slack 봇 토큰 유효성 검증"
            ),
            SchedulerInfo(
                name = "SlackSocketModeService",
                schedule = "fixedDelay 15초",
                description = "Slack Socket Mode 연결 유지"
            ),
            SchedulerInfo(
                name = "SourceHealthScheduler (비활성화)",
                schedule = "매시 30분",
                description = "연속 실패 소스 자동 비활성화"
            ),
            SchedulerInfo(
                name = "SourceHealthScheduler (재시도)",
                schedule = "매일 03:00",
                description = "비활성 소스 수집 재시도 및 복구"
            ),
            SchedulerInfo(
                name = "EmptyCategoryScheduler",
                schedule = "매일 04:00",
                description = "구독자 없는 카테고리 자동 비활성화"
            ),
            SchedulerInfo(
                name = "DataCleanupScheduler",
                schedule = "매일 03:00",
                description = "오래된 데이터 일괄 정리"
            ),
            SchedulerInfo(
                name = "CostAlertScheduler",
                schedule = "매시 정각",
                description = "LLM 비용 임계값 초과 알림"
            ),
            SchedulerInfo(
                name = "AutoReportScheduler",
                schedule = "매시 정각",
                description = "주간/월간 자동 리포트 생성"
            ),
            SchedulerInfo(
                name = "CompanySearchService",
                schedule = "매주 월요일 03:00",
                description = "DART 기업 코드 캐시 갱신"
            ),
            SchedulerInfo(
                name = "RateLimitFilter",
                schedule = "fixedDelay 5분",
                description = "Rate Limit 비활성 엔트리 정리"
            )
        )
    }
}
