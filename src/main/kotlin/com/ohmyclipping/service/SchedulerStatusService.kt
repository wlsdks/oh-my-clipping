package com.ohmyclipping.service

import com.ohmyclipping.observability.SchedulerRunTracker
import com.ohmyclipping.service.dto.SchedulerStatusResponse
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 관리자 대시보드용 스케줄러 상태 조회 서비스.
 * 코드베이스에 등록된 스케줄러 정의와 인메모리 실행 이력(`SchedulerRunTracker`)을 병합하여
 * 마지막 실행 결과 · 다음 실행 시각 · 마지막 에러 메시지를 하나의 뷰로 제공한다.
 *
 * 다중 인스턴스 배포에서는 인스턴스별 인메모리 기록이므로
 * 글로벌 관점의 정확도는 SchedulerRunTracker 한계에 준한다.
 */
@Service
class SchedulerStatusService(
    private val schedulerRunTracker: SchedulerRunTracker,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

    /**
     * 등록된 모든 스케줄러의 상태 목록을 반환한다.
     *
     * 정렬 순서는 [KNOWN_SCHEDULERS] 선언 순서와 동일하다 (대시보드 표 순서 고정 목적).
     */
    fun list(): List<SchedulerStatusResponse> {
        val now = clock.instant()
        return KNOWN_SCHEDULERS.map { def ->
            val trackerKey = def.trackerKey
            val record = trackerKey?.let { schedulerRunTracker.getLastRun(it) }
            val nextRunAt = resolveNextRunAt(def)
            val status = resolveStatus(record)

            SchedulerStatusResponse(
                name = def.displayName,
                trackerKey = trackerKey,
                schedule = def.scheduleLabel,
                description = def.description,
                lastRunAt = record?.lastRunAt?.toString(),
                lastDurationMs = record?.durationMs,
                lastResult = record?.let { if (it.success) "success" else "failure" },
                lastError = record?.lastError,
                nextRunAt = nextRunAt?.toString(),
                status = status,
                stalenessSeconds = record?.lastRunAt?.let { Duration.between(it, now).seconds }
            )
        }
    }

    /**
     * cron 표현식이 있는 스케줄러는 다음 실행 시각을 계산한다.
     * fixedDelay 기반 스케줄러는 미래 시점을 예측할 수 없으므로 null 을 반환한다.
     */
    private fun resolveNextRunAt(def: SchedulerDef): Instant? {
        val cron = def.cron ?: return null
        return try {
            val expression = CronExpression.parse(cron)
            val zonedNow = LocalDateTime.now(clock.withZone(seoulZone))
            expression.next(zonedNow.atZone(seoulZone))?.toInstant()
        } catch (_: IllegalArgumentException) {
            // 알 수 없는 cron 표현식은 미래 예측에서 제외한다.
            null
        }
    }

    /**
     * 마지막 실행 기록을 상태 라벨로 환산한다.
     *
     * - 기록 없음: IDLE (서버 시작 후 아직 실행되지 않음)
     * - 가장 최근 실행이 성공: IDLE
     * - 가장 최근 실행이 실패: FAILED
     */
    private fun resolveStatus(record: SchedulerRunTracker.SchedulerRunRecord?): String {
        if (record == null) return "IDLE"
        return if (record.success) "IDLE" else "FAILED"
    }

    /** 코드베이스에 등록된 스케줄러 정의. cron 이 있으면 다음 실행 시각 계산에 사용한다. */
    private data class SchedulerDef(
        val displayName: String,
        val trackerKey: String?,
        val scheduleLabel: String,
        val description: String,
        val cron: String? = null
    )

    companion object {
        // SystemStatusService.KNOWN_SCHEDULERS 와 동기화를 유지한다.
        private val KNOWN_SCHEDULERS = listOf(
            SchedulerDef(
                displayName = "AsyncClipJobWorker",
                trackerKey = "async_clip_job",
                scheduleLabel = "fixedDelay 3초",
                description = "비동기 클리핑 작업 큐 폴링"
            ),
            SchedulerDef(
                displayName = "StuckJobRecoveryScheduler",
                trackerKey = "stuck_job_recovery",
                scheduleLabel = "fixedDelay 1분",
                description = "멈춘 작업 복구"
            ),
            SchedulerDef(
                displayName = "SlackDigestWorker",
                trackerKey = "slack_digest",
                scheduleLabel = "fixedDelay 10초",
                description = "Slack 다이제스트 발송"
            ),
            SchedulerDef(
                displayName = "SlackTokenValidationScheduler",
                trackerKey = "slack_token_validation",
                scheduleLabel = "매시 정각",
                description = "Slack 봇 토큰 유효성 검증",
                cron = "0 0 * * * *"
            ),
            SchedulerDef(
                displayName = "SlackSocketModeService",
                trackerKey = "slack_socket_mode",
                scheduleLabel = "fixedDelay 15초",
                description = "Slack Socket Mode 연결 유지"
            ),
            SchedulerDef(
                displayName = "SourceHealthScheduler (비활성화)",
                trackerKey = "source_health_deactivate",
                scheduleLabel = "매시 30분",
                description = "연속 실패 소스 자동 비활성화",
                cron = "0 30 * * * *"
            ),
            SchedulerDef(
                displayName = "SourceHealthScheduler (재시도)",
                trackerKey = "source_health_retry",
                scheduleLabel = "매일 03:00",
                description = "비활성 소스 수집 재시도 및 복구",
                cron = "0 0 3 * * *"
            ),
            SchedulerDef(
                displayName = "EmptyCategoryScheduler",
                trackerKey = "empty_category",
                scheduleLabel = "매일 04:00",
                description = "구독자 없는 카테고리 자동 비활성화",
                cron = "0 0 4 * * *"
            ),
            SchedulerDef(
                displayName = "DataCleanupScheduler",
                trackerKey = "data_cleanup",
                scheduleLabel = "매일 03:00",
                description = "오래된 데이터 일괄 정리",
                cron = "0 0 3 * * *"
            ),
            SchedulerDef(
                displayName = "CostAlertScheduler",
                trackerKey = "cost_alert",
                scheduleLabel = "매시 정각",
                description = "LLM 비용 임계값 초과 알림",
                cron = "0 0 * * * *"
            ),
            SchedulerDef(
                displayName = "AutoReportScheduler",
                trackerKey = "auto_report",
                scheduleLabel = "매시 정각",
                description = "주간/월간 자동 리포트 생성",
                cron = "0 0 * * * *"
            ),
            SchedulerDef(
                displayName = "CompanySearchService",
                trackerKey = "company_search",
                scheduleLabel = "매주 월요일 03:00",
                description = "DART 기업 코드 캐시 갱신",
                cron = "0 0 3 * * MON"
            ),
            SchedulerDef(
                displayName = "RateLimitFilter",
                trackerKey = "rate_limit",
                scheduleLabel = "fixedDelay 5분",
                description = "Rate Limit 비활성 엔트리 정리"
            )
        )
    }
}
