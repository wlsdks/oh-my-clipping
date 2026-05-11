package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsNotificationEvent

import com.ohmyclipping.config.SlaEscalationProperties
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.store.UserClippingRequestStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

/**
 * 사용자 구독 신청 SLA 에스컬레이션 스케줄러.
 *
 * 동작:
 * - 평일(월~금) 오전 9시 KST 에 `clipping_user_requests` 테이블의
 *   `status='PENDING'` + `createdAt < now - N일` 인 요청을 조회한다.
 * - N 건이 있으면 운영 채널로 **한 건의 요약 알림**(`CLIPPING_REQUEST_SLA_EXCEEDED`)을 발송한다.
 * - 0 건이면 조용히 종료한다.
 *
 * Dedup 전략:
 * - 같은 requestId 에 대해 같은 날 중복 알림이 가지 않도록 in-memory `ConcurrentHashMap<String, LocalDate>` 사용.
 * - 매 실행 시작 시 오늘 이전 날짜 엔트리는 prune 한다.
 * - 재시작 시 dedup 상태는 초기화된다(파일럿 규모 기준 수용 가능).
 */
@Component
class ClippingRequestSlaScheduler(
    private val userClippingRequestStore: UserClippingRequestStore,
    private val properties: SlaEscalationProperties,
    private val opsLogNotifier: OpsLogNotifier,
    private val metrics: ClippingMetrics,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        internal const val SCHEDULER_NAME = "clipping_request_sla"
    }

    /** (requestId → 마지막 알림 날짜) in-memory dedup. */
    private val notifiedToday = ConcurrentHashMap<String, LocalDate>()

    /**
     * 평일 오전 9시(KST)에 실행되어 구독 신청 PENDING 장기 지연을 에스컬레이션한다.
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Seoul")
    fun run() = metrics.recordSchedulerRun(SCHEDULER_NAME) {
        if (!properties.enabled) {
            log.debug { "$SCHEDULER_NAME disabled via properties" }
            return@recordSchedulerRun
        }

        val today = LocalDate.now(clock.withZone(KST))
        pruneStaleDedupEntries(today)

        val cutoff = clock.instant().minus(Duration.ofDays(properties.clippingRequestStaleDays.toLong()))
        // 기준일 이전에 생성된 PENDING 구독 신청 조회
        val stalePending = userClippingRequestStore.findPendingCreatedBefore(cutoff)

        val freshTargets = stalePending.filter { entity -> notifiedToday[entity.id] != today }

        if (freshTargets.isEmpty()) {
            log.debug { "$SCHEDULER_NAME — no stale pending clipping requests to escalate" }
            return@recordSchedulerRun
        }

        // 요약 알림 1건 — 개별 알림을 N 건 쏘지 않는다.
        val items = freshTargets.map { entity ->
            mapOf(
                "requestId" to entity.id,
                "requesterUserId" to entity.requesterUserId,
                "requestName" to entity.requestName,
                "createdAt" to entity.createdAt.toString(),
            )
        }

        log.warn {
            "$SCHEDULER_NAME escalating ${freshTargets.size} stale pending clipping requests " +
                "(stale threshold: ${properties.clippingRequestStaleDays}d)"
        }

        opsLogNotifier.postOpsEvent(
            OpsNotificationEvent.CLIPPING_REQUEST_SLA_EXCEEDED,
            mapOf(
                "count" to freshTargets.size,
                "stale_days" to properties.clippingRequestStaleDays,
                "items" to items,
            )
        )

        freshTargets.forEach { entity -> notifiedToday[entity.id] = today }
    }

    /** 오늘이 아닌 모든 dedup 엔트리를 제거한다. */
    private fun pruneStaleDedupEntries(today: LocalDate) {
        notifiedToday.entries.removeIf { it.value != today }
    }
}
