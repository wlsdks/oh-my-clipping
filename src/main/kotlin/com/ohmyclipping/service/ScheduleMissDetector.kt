package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.store.PipelineRunStore
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * auto-collection 스케줄러의 미발동을 감지해 M5 알림을 발송한다.
 *
 * 매 1분마다 cron 표현식 기준의 직전 예정 실행 시각을 계산하고,
 * (1) AutoCollectionScheduler의 인메모리 lastFiredAt 마커를 우선 확인한 뒤,
 * (2) 폴백으로 pipeline_runs.started_at ±1분 창을 확인한다.
 * 두 곳 모두 실행 흔적이 없으면 스케줄 미발동으로 간주한다.
 * 부팅 이전의 예정 시각과 이미 처리된 시각은 dedup 맵으로 중복 알림을 방지한다.
 */
@Component
class ScheduleMissDetector(
    private val autoCollectionScheduler: AutoCollectionScheduler,
    private val pipelineRunStore: PipelineRunStore,
    private val runtime: RuntimeSettingService,
    private val notifier: OpsLogNotifier,
    private val clock: Clock,
) {
    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

    /** 서버 시작 시각 — 부팅 이전 예정 시각은 감지 대상에서 제외한다 */
    private val bootAt: Instant = clock.instant()

    /** 이미 처리(알림 발송 또는 실행 확인)된 예정 시각을 중복 방지하기 위해 추적한다 */
    private val dedupedExpectedTimes = ConcurrentHashMap<Instant, Boolean>()

    /**
     * 1분마다 직전 cron 예정 시각을 계산하고 실제 실행 여부를 확인한다.
     * grace 기간 이내이거나 실행이 존재하면 조용히 스킵한다.
     */
    @Scheduled(fixedDelay = 60_000)
    fun runCheck() {
        val cron = autoCollectionScheduler.cronExpression()
        val cronExpr = CronExpression.parse(cron)
        val grace = runtime.current().opsScheduleMissGraceMinutes.toLong()

        val prevExpected = findPrevExpected(cronExpr) ?: return

        // 부팅 이전 예정 시각은 dedup에 기록하고 알림은 발송하지 않는다
        if (prevExpected.isBefore(bootAt)) {
            dedupedExpectedTimes.putIfAbsent(prevExpected, true)
            return
        }

        // 이미 처리된 예정 시각은 건너뛴다
        if (dedupedExpectedTimes.containsKey(prevExpected)) return

        // grace 기간이 아직 지나지 않았으면 발동 유예 중이므로 대기한다
        val minutesSincePrev = Duration.between(prevExpected, clock.instant()).toMinutes()
        if (minutesSincePrev < grace) return

        // 스케줄러 인메모리 마커 확인 — AutoCollectionScheduler는 async_jobs 경로를 사용하므로
        // pipeline_runs에 기록하지 않는다. lastFiredAt이 예정 시각 ±2분 이내이면 실행된 것으로 판단한다.
        val firedAt = autoCollectionScheduler.lastFiredAt
        if (firedAt != null) {
            val diff = Duration.between(prevExpected, firedAt).abs()
            if (diff <= Duration.ofMinutes(2)) {
                dedupedExpectedTimes[prevExpected] = true
                return
            }
        }

        // 폴백: pipeline_runs 테이블에서 예정 시각 ±1분 창에 실행이 있는지 확인한다
        val startedAtLower = prevExpected.minus(Duration.ofMinutes(1))
        val startedAtUpper = prevExpected.plus(Duration.ofMinutes(1))
        val anyStarted = pipelineRunStore.hasRunStartedBetween(startedAtLower, startedAtUpper)
        if (anyStarted) {
            dedupedExpectedTimes[prevExpected] = true
            return
        }

        // 미발동 감지 — M5 알림을 발송하고 dedup에 기록한다
        notifier.postScheduleMiss("auto-collection", prevExpected, grace.toInt())
        dedupedExpectedTimes[prevExpected] = true
    }

    /**
     * cron 표현식 기준으로 현재 시각 이전의 가장 최근 예정 시각을 반환한다.
     * 25시간 이내의 최대 20회 발화 지점만 탐색한다.
     *
     * @param cron 파싱된 CronExpression
     * @return 현재보다 이전인 마지막 예정 Instant, 없으면 null
     */
    private fun findPrevExpected(cron: CronExpression): Instant? {
        val now = clock.instant()
        // 25시간 이전부터 탐색을 시작해 현재까지의 발화 지점을 추적한다
        var pivot = ZonedDateTime.ofInstant(now.minus(Duration.ofHours(25)), seoulZone)
        var last: ZonedDateTime? = null

        repeat(20) {
            val next = cron.next(pivot) ?: return last?.toInstant()
            if (next.toInstant().isAfter(now)) return last?.toInstant()
            last = next
            pivot = next
        }
        return last?.toInstant()
    }

    /** 테스트용 dedup 맵 스냅샷 접근자 */
    internal fun dedupSnapshot(): Map<Instant, Boolean> = dedupedExpectedTimes.toMap()
}
