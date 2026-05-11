package com.clipping.mcpserver.service.source

import com.clipping.mcpserver.service.port.OpsNotificationEvent
import com.clipping.mcpserver.service.port.OpsLogNotifier
import com.clipping.mcpserver.service.port.SourceSchedulerMetricsPort
import com.clipping.mcpserver.service.port.SourceSlaSettingsPort
import com.clipping.mcpserver.store.RssSourceStore
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
 * RSS 소스 검증 SLA 에스컬레이션 스케줄러.
 *
 * 동작:
 * - 평일(월~금) 오전 9시 KST 에 `rss_sources` 테이블의
 *   `verificationStatus='PENDING'` + `createdAt < now - N일` 인 소스를 조회한다.
 * - 1 건 이상이면 운영 채널로 **한 건의 요약 알림**(`SOURCE_REQUEST_SLA_EXCEEDED`)을 발송한다.
 * - 0 건이면 조용히 종료한다.
 *
 * Dedup 전략:
 * - 같은 sourceId 에 대해 같은 날 중복 알림이 가지 않도록 in-memory `ConcurrentHashMap<String, LocalDate>` 사용.
 * - 매 실행 시작 시 오늘 이전 날짜 엔트리는 prune 한다.
 * - 재시작 시 dedup 상태는 초기화된다(파일럿 규모 기준 수용 가능).
 */
@Component
class SourceRequestSlaScheduler(
    private val rssSourceStore: RssSourceStore,
    private val slaSettingsPort: SourceSlaSettingsPort,
    private val opsLogNotifier: OpsLogNotifier,
    private val metrics: SourceSchedulerMetricsPort,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        internal const val SCHEDULER_NAME = "source_request_sla"
    }

    /** (sourceId → 마지막 알림 날짜) in-memory dedup. */
    private val notifiedToday = ConcurrentHashMap<String, LocalDate>()

    /**
     * 평일 오전 9시(KST)에 실행되어 RSS 소스 검증 PENDING 장기 지연을 에스컬레이션한다.
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Seoul")
    fun run() = metrics.recordSourceSchedulerRun(SCHEDULER_NAME) {
        val settings = slaSettingsPort.currentSourceSlaSettings()
        if (!settings.enabled) {
            log.debug { "$SCHEDULER_NAME disabled via properties" }
            return@recordSourceSchedulerRun
        }

        val today = LocalDate.now(clock.withZone(KST))
        pruneStaleDedupEntries(today)

        val cutoff = clock.instant().minus(Duration.ofDays(settings.sourceRequestStaleDays.toLong()))
        // 기준일 이전에 등록된 PENDING 검증 소스 조회
        val stalePending = rssSourceStore.findPendingVerificationCreatedBefore(cutoff)

        val freshTargets = stalePending.filter { entity -> notifiedToday[entity.id] != today }

        if (freshTargets.isEmpty()) {
            log.debug { "$SCHEDULER_NAME — no stale pending sources to escalate" }
            return@recordSourceSchedulerRun
        }

        // 요약 알림 1건 — 개별 알림 N 건 쏘지 않는다.
        val items = freshTargets.map { entity ->
            mapOf(
                "sourceId" to entity.id,
                "name" to entity.name,
                "url" to entity.url,
                "categoryId" to entity.categoryId,
                "createdAt" to entity.createdAt.toString(),
            )
        }

        log.warn {
            "$SCHEDULER_NAME escalating ${freshTargets.size} stale pending sources " +
                "(stale threshold: ${settings.sourceRequestStaleDays}d)"
        }

        opsLogNotifier.postOpsEvent(
            OpsNotificationEvent.SOURCE_REQUEST_SLA_EXCEEDED,
            mapOf(
                "count" to freshTargets.size,
                "stale_days" to settings.sourceRequestStaleDays,
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
