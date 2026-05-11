package com.ohmyclipping.service.dto

/** 시스템 상태 조회 응답. */
data class SystemStatusResponse(
    val server: ServerStatus,
    val database: DatabaseStatus,
    val slack: SlackStatus,
    val ai: AiStatus,
    val jobQueue: JobQueueStatus,
    val schedulers: List<SchedulerInfo>
)

/** JVM 서버 상태 정보. */
data class ServerStatus(
    val uptime: String,
    val javaVersion: String,
    val activeProfiles: List<String>,
    val memoryUsedMb: Long,
    val memoryMaxMb: Long
)

/** 데이터베이스 커넥션 풀 상태 정보. */
data class DatabaseStatus(
    val connected: Boolean,
    val poolActive: Int,
    val poolIdle: Int,
    val poolTotal: Int
)

/** Slack 연동 설정 및 헬스 상태 정보. */
data class SlackStatus(
    val botTokenConfigured: Boolean,
    val healthy: Boolean,
    val lastCheckTime: String?
)

/** AI(Gemini) 서킷 브레이커 상태 정보. */
data class AiStatus(
    val circuitBreakerState: String,
    val canCall: Boolean,
    val consecutiveOpenCount: Int,
    val totalOpenCount: Int,
    val lastOpenedAt: String?
)

/** 비동기 작업 큐 상태 정보. */
data class JobQueueStatus(
    val pendingJobs: Long,
    val threshold: Int
)

/** 등록된 스케줄러 정보. */
data class SchedulerInfo(
    val name: String,
    val schedule: String,
    val description: String,
    val lastRunAt: String? = null,
    val lastResult: String? = null
)
