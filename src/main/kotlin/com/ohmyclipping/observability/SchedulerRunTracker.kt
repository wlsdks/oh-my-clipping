package com.ohmyclipping.observability

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 스케줄러 실행 이력을 인메모리로 추적한다.
 * 각 스케줄러의 마지막 실행 시각, 성공 여부, 소요시간, 마지막 에러 메시지를 기록하여
 * 시스템 상태 대시보드와 스케줄러 상태 패널에서 실시간 헬스를 조회할 수 있도록 지원한다.
 *
 * 다중 인스턴스 배포 시 인스턴스별 기록이므로, 글로벌 뷰가 필요하면 DB 기반 추적을 사용한다.
 */
@Component
class SchedulerRunTracker(
    private val clock: Clock = Clock.systemUTC()
) {
    /**
     * 스케줄러 단일 실행 기록.
     *
     * @param lastRunAt 마지막 실행 시각
     * @param success 실행 성공 여부
     * @param durationMs 실행 소요 시간(ms). 실패 시 실패까지 걸린 시간
     * @param lastError 실패한 경우 예외 메시지 일부 (200자 제한). 성공 시 null
     */
    data class SchedulerRunRecord(
        val lastRunAt: Instant,
        val success: Boolean,
        val durationMs: Long = 0,
        val lastError: String? = null
    )

    private val records = ConcurrentHashMap<String, SchedulerRunRecord>()

    /**
     * 스케줄러 실행 결과를 기록한다 (하위 호환성 유지용 단순 버전).
     *
     * @param name 스케줄러 식별 키 (예: "async_clip_job")
     * @param success 실행 성공 여부
     */
    fun record(name: String, success: Boolean) {
        records[name] = SchedulerRunRecord(
            lastRunAt = clock.instant(),
            success = success
        )
    }

    /**
     * 스케줄러 실행 결과를 소요시간/에러 정보와 함께 기록한다.
     *
     * @param name 스케줄러 식별 키
     * @param success 실행 성공 여부
     * @param durationMs 실행 소요 시간(ms)
     * @param lastError 실패한 경우 에러 메시지 (성공 시 null)
     */
    fun record(
        name: String,
        success: Boolean,
        durationMs: Long,
        lastError: String? = null
    ) {
        // 에러 메시지가 너무 길면 저장 부담을 줄이기 위해 200자로 자른다
        val trimmedError = lastError?.take(MAX_ERROR_MESSAGE_LENGTH)
        records[name] = SchedulerRunRecord(
            lastRunAt = clock.instant(),
            success = success,
            durationMs = durationMs.coerceAtLeast(0),
            lastError = trimmedError
        )
    }

    /**
     * 특정 스케줄러의 마지막 실행 기록을 반환한다.
     *
     * @param name 스케줄러 식별 키
     * @return 기록이 없으면 null
     */
    fun getLastRun(name: String): SchedulerRunRecord? = records[name]

    /**
     * 모든 스케줄러의 마지막 실행 기록을 반환한다.
     */
    fun allRecords(): Map<String, SchedulerRunRecord> = records.toMap()

    companion object {
        private const val MAX_ERROR_MESSAGE_LENGTH = 200
    }
}
