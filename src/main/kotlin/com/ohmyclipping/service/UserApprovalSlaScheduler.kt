package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsNotificationEvent

import com.ohmyclipping.config.SlaEscalationProperties
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.store.AdminUserStore
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
 * 가입 승인 SLA 에스컬레이션 스케줄러.
 *
 * 동작:
 * - 평일(월~금) 오전 9시 KST 에 `admin_users` 테이블의
 *   `role='USER'` + `approvalStatus='PENDING'` + `createdAt < now - N일` 인 계정을 조회한다.
 * - N 건이 있으면 운영 채널로 **한 건의 요약 알림**(`USER_APPROVAL_SLA_EXCEEDED`)을 발송한다.
 * - N 건이 0 이면 조용히 종료한다.
 *
 * Dedup 전략:
 * - 같은 유저에 대해 같은 날 중복 알림이 가지 않도록 in-memory `ConcurrentHashMap<String, LocalDate>` 사용.
 * - 키: `"${userId}"`, 값: 마지막 알림 날짜.
 * - 매 실행 시작 시점에 오늘 이전 날짜 엔트리는 prune 한다(메모리 누수 방지).
 * - 재시작 시 dedup 상태는 초기화되며 그 경우 같은 날 한 번 더 알림이 갈 수 있으나,
 *   파일럿 규모(수십 건)에서는 수용 가능한 비용이다.
 */
@Component
class UserApprovalSlaScheduler(
    private val adminUserStore: AdminUserStore,
    private val properties: SlaEscalationProperties,
    private val opsLogNotifier: OpsLogNotifier,
    private val metrics: ClippingMetrics,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        internal const val SCHEDULER_NAME = "user_approval_sla"
    }

    /** (userId → 마지막 알림 날짜) in-memory dedup. */
    private val notifiedToday = ConcurrentHashMap<String, LocalDate>()

    /**
     * 평일 오전 9시(KST)에 실행되어 가입 승인 PENDING 장기 지연을 에스컬레이션한다.
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Seoul")
    fun run() = metrics.recordSchedulerRun(SCHEDULER_NAME) {
        if (!properties.enabled) {
            log.debug { "$SCHEDULER_NAME disabled via properties" }
            return@recordSchedulerRun
        }

        val today = LocalDate.now(clock.withZone(KST))
        // 오래된 dedup 엔트리 정리
        pruneStaleDedupEntries(today)

        val cutoff = clock.instant().minus(Duration.ofDays(properties.userApprovalStaleDays.toLong()))
        // 기준일 이전에 생성된 PENDING 유저 계정 조회
        val stalePending = adminUserStore.findPendingUsersCreatedBefore(cutoff)

        // 오늘 이미 알림 발송된 userId 는 제외
        val freshTargets = stalePending.filter { entity ->
            notifiedToday[entity.id] != today
        }

        if (freshTargets.isEmpty()) {
            log.debug { "$SCHEDULER_NAME — no stale pending accounts to escalate" }
            return@recordSchedulerRun
        }

        // 요약 알림 1건 발송 — 개별 알림을 N건 쏘지 않는다.
        val items = freshTargets.map { entity ->
            mapOf(
                "userId" to entity.id,
                "username" to entity.username,
                "displayName" to (entity.displayName ?: ""),
                "createdAt" to entity.createdAt.toString(),
            )
        }

        log.warn {
            "$SCHEDULER_NAME escalating ${freshTargets.size} stale pending user accounts " +
                "(stale threshold: ${properties.userApprovalStaleDays}d)"
        }

        opsLogNotifier.postOpsEvent(
            OpsNotificationEvent.USER_APPROVAL_SLA_EXCEEDED,
            mapOf(
                "count" to freshTargets.size,
                "stale_days" to properties.userApprovalStaleDays,
                "items" to items,
            )
        )

        // 전송 성공/실패 여부와 무관하게 오늘자 dedup 기록 — 연속 실패 시 재시도는 내일 수행
        freshTargets.forEach { entity -> notifiedToday[entity.id] = today }
    }

    /** 오늘이 아닌 모든 dedup 엔트리를 제거한다. */
    private fun pruneStaleDedupEntries(today: LocalDate) {
        notifiedToday.entries.removeIf { it.value != today }
    }
}
