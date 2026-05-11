package com.clipping.mcpserver.service.digest

import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.resilience.TokenBucketRateLimiter
import com.clipping.mcpserver.service.DeliveryRetryOrchestrator
import com.clipping.mcpserver.service.RuntimeSettingService
import com.clipping.mcpserver.service.event.DigestDeliveryFinalizationRequestedEvent
import com.clipping.mcpserver.service.port.DigestDeliveryWorkflowPort
import com.clipping.mcpserver.service.port.SlackDeliveryPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.model.UserClippingRequest
import com.clipping.mcpserver.support.InterruptibleSleep
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.UserClippingRequestStore
import com.clipping.mcpserver.store.UserDeliveryScheduleStore
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private val log = KotlinLogging.logger {}

/** delivery_log 예약까지 완료되어 실제 전송을 시도할 대상이다. */
private data class ReservedDeliveryTarget(
    val logId: String,
    val targetId: String,
    val requestNameOverride: String?
)

/** 예약 전 발송 후보와 DM 별칭 오버라이드를 함께 들고 있는 값 객체다. */
private data class DeliveryTarget(
    val targetId: String,
    val requestNameOverride: String?
)

private enum class DeliveryOutcome { SENT, SKIPPED, FAILED }

/**
 * Slack 다이제스트 자동 발송 워커.
 * 카테고리 단위로 발송 루프를 돌며, delivery_log를 통해 중복 발송을 방지한다.
 * 기존 유저 단위 루프의 "첫 유저가 summaries를 소비하는" 버그를 해결한다.
 */
@Component
@ConditionalOnProperty(
    name = ["clipping.scheduler.enabled", "clipping.slack.delivery.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class SlackDigestWorker(
    private val categoryStore: CategoryStore,
    private val categoryRuleStore: CategoryRuleStore,
    private val digestDeliveryWorkflowPort: DigestDeliveryWorkflowPort,
    private val runtimeSettingService: RuntimeSettingService,
    private val scheduleStore: UserDeliveryScheduleStore,
    private val requestStore: UserClippingRequestStore,
    private val deliveryLogStore: DeliveryLogStore,
    private val adminUserStore: AdminUserStore,
    private val slackMessageSender: SlackDeliveryPort,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val metrics: ClippingMetrics,
    private val opsNotifier: DigestOpsNotifier,
    @param:Qualifier("digestTaskScheduler") private val digestTaskScheduler: ThreadPoolTaskScheduler,
    private val retryOrchestrator: DeliveryRetryOrchestrator,
    @param:org.springframework.context.annotation.Lazy
    private val schedulerErrorNotifier: com.clipping.mcpserver.observability.SchedulerErrorNotifier?,
    private val slackRateLimiter: TokenBucketRateLimiter? = null,
) {
    private val running = AtomicBoolean(false)

    /**
     * 한 번의 워커 실행에서 재사용할 발송 판단용 스냅샷이다.
     * 개인 스케줄 매칭 결과와 카테고리별 승인 구독 조회 결과를 재사용한다.
     */
    private data class DeliveryDispatchContext(
        val dueCategoryIds: Set<String>,
        val approvedRequestsByCategory: MutableMap<String, List<UserClippingRequest>> = mutableMapOf()
    )

    companion object {
        /**
         * Slack API 레이트 리밋 보호용 지연(ms).
         * Slack chat.postMessage Tier 1 한도(~1건/초)를 안전하게 준수하기 위해 1100ms로 설정한다.
         */
        const val SLACK_RATE_LIMIT_DELAY_MS = 1100L

        /**
         * DM 발송 자동 분산 범위(분). userId 해시로 0~(N-1)분에 분산된다.
         * Slack chat.postMessage Tier 1 = ~50건/분, 실제 처리량 ~54건/분(1100ms delay).
         * 300명 × 5구독 = 1500건 ÷ 54건/분 = 28분 + 안전 마진 20% = 34분.
         * 0으로 설정하면 분산 없이 즉시 발송한다 (테스트용).
         */
        const val DM_SPREAD_MINUTES = 34

        /** 다이제스트 스케줄러의 고정 지연 간격(ms) */
        const val DIGEST_FIXED_DELAY_MS = 10_000L

        /** 연속 무발송 안내 DM 발송 기준 일수 */
        const val SILENT_PERIOD_DAYS = 3L
    }

    /**
     * 다이제스트 전용 스레드 풀(digest-)에 고정 지연 태스크를 등록한다.
     * SchedulerConfig에서 정의된 digestTaskScheduler Bean을 재사용해 중복 풀 생성을 방지한다.
     * 기본 sched- 풀을 점유하지 않아 RSS 수집·AI 요약 등 다른 작업이 차단되지 않는다.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent::class)
    fun scheduleOnDedicatedPool() {
        digestTaskScheduler.scheduleWithFixedDelay(
            { publishDigests() },
            java.time.Duration.ofMillis(DIGEST_FIXED_DELAY_MS)
        )
        log.info { "SlackDigestWorker started on digestTaskScheduler bean (digest- pool)" }
    }

    /**
     * 활성 카테고리별로 발송 조건을 판단해 다이제스트를 전송한다.
     * running 플래그로 동시 실행을 방지한다.
     * 다이제스트 전용 풀(digest-)에서 scheduleOnDedicatedPool()을 통해 10초 간격으로 호출된다.
     */
    fun publishDigests() {
        if (!running.compareAndSet(false, true)) {
            log.debug { "SlackDigestWorker.publishDigests skipped — already running" }
            return
        }
        try {
            log.info { "SlackDigestWorker.publishDigests tick" }
            processCategories()
        } catch (e: Exception) {
            log.error(e) { "SlackDigestWorker.publishDigests error" }
            schedulerErrorNotifier?.notifyBackgroundError("다이제스트 발송", e)
        } finally {
            running.set(false)
        }
    }

    /**
     * 카테고리 기반 발송 루프.
     * 1단계: 카테고리 기본 채널에 1회 발송 (채널 멤버 전원이 봄)
     * 2단계: DM 구독자에게 개별 발송 (같은 요약 재사용, AI 비용 0)
     */
    private fun processCategories() {
        val runtime = runtimeSettingService.current()
        if (!runtime.slackAutoDigestEnabled) return
        // 점검 모드에서는 사용자 대상 발송을 중지한다
        if (runtime.maintenanceMode) return
        // 24시간 이상 경과한 ABANDONED 건을 STALE로 하우스키핑한다
        retryOrchestrator.transitionStale()

        val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
        val today = now.toLocalDate()
        val currentHour = now.hour
        val dayOfWeek = now.dayOfWeek
            .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            .uppercase()
            .take(3)
        // 개인 스케줄 매칭 결과를 DB DISTINCT 조회로 계산해 전체 승인 구독 로드를 피한다.
        val deliveryContext = buildDeliveryDispatchContext(
            dayOfWeek = dayOfWeek,
            currentHour = currentHour
        )
        // 글로벌 cron 매칭도 카테고리마다 다시 파싱하지 않도록 한 번만 계산한다.
        val globalCronDue = matchesGlobalCron(runtime.slackDigestCron, currentHour)
        val categories = categoryStore.findOperational()

        val details = mutableListOf<String>()
        val digestFailures = mutableListOf<com.clipping.mcpserver.service.port.DigestFailure>()

        for (category in categories) {
            if (!shouldDeliverNow(category, dayOfWeek, currentHour, deliveryContext.dueCategoryIds, globalCronDue)) {
                continue
            }

            // 채널과 DM 대상 목록을 먼저 확정해 같은 스냅샷을 fan-out한다.
            val deliveryTargets = resolveDeliveryTargets(category, deliveryContext)
            if (deliveryTargets.isEmpty()) {
                details.add("${category.name} → 발송 대상 없음 (DM 미설정 또는 분산 대기)")
                continue
            }

            // 실제로 이번 사이클에 전송할 대상만 먼저 예약해 불필요한 스냅샷 생성을 막는다.
            val reservedTargets = reserveDeliveryTargets(category.id, deliveryTargets, today, currentHour)
            if (reservedTargets.isEmpty()) {
                continue
            }

            // 카테고리당 다이제스트 스냅샷을 한 번만 만들고 모든 목적지에 재사용한다.
            val preparedDigest = digestDeliveryWorkflowPort.prepareDigest(
                categoryId = category.id,
                maxItems = runtime.slackAutoDigestMaxItems,
                unsentOnly = runtime.slackAutoDigestUnsentOnly,
                sendToSlack = false,
                slackChannelId = null
            ).toDigestResult()

            if (preparedDigest.selectedCount == 0) {
                details.add("${category.name} → 기사 없음 (후보 ${preparedDigest.totalCandidates}건 중 선정 0건)")
            }

            for ((index, reservedTarget) in reservedTargets.withIndex()) {
                val result = deliverPreparedDigestWithResult(category, reservedTarget, preparedDigest)
                when (result) {
                    DeliveryOutcome.SENT,
                    DeliveryOutcome.SKIPPED -> Unit
                    DeliveryOutcome.FAILED -> {
                        digestFailures.add(
                            com.clipping.mcpserver.service.port.DigestFailure(
                                categoryId = category.id,
                                categoryName = category.name,
                                targetLabel = if (reservedTarget.targetId.startsWith("D") || reservedTarget.targetId.startsWith("U")) "DM" else "#channel",
                                errorMessage = "발송 실패",
                                failedAt = java.time.Instant.now(),
                            )
                        )
                    }
                }

                // 연속 Slack 호출 간 레이트리밋 대기 (Slack tier 1 `chat.postMessage` ~1/sec 보호).
                // 마지막 대상 이후엔 대기 생략 — 다음 category 루프가 다시 throttle 걸린다.
                if (index < reservedTargets.size - 1) {
                    if (!acquireSlackRateLimitPermit(context = "SlackDigestWorker delivery rate limit")) {
                        log.info { "SlackDigestWorker delivery rate limit interrupted — skipping remaining targets for category=${category.name}" }
                        break
                    }
                }
            }
        }

        // 실패가 있는 경우에만 OpsLogNotifier를 통해 M6 알림을 발송한다
        opsNotifier.notifyTickSummary(digestFailures)

        // 실패 발송 재시도 (5분 경과 건)
        retryFailedDeliveries(runtime)
    }

    /** 단일 대상(채널 또는 DM)에 준비된 다이제스트 스냅샷을 발송하고 delivery_log에 기록한다. */
    /** 발송 결과를 반환하는 래퍼. processCategories에서 집계에 사용한다. */
    private fun deliverPreparedDigestWithResult(
        category: Category,
        reservedTarget: ReservedDeliveryTarget,
        preparedDigest: com.clipping.mcpserver.service.dto.clipping.DigestResult
    ): DeliveryOutcome {
        return try {
            deliverPreparedDigest(category, reservedTarget, preparedDigest)
        } catch (_: Exception) {
            DeliveryOutcome.FAILED
        }
    }

    private fun deliverPreparedDigest(
        category: Category,
        reservedTarget: ReservedDeliveryTarget,
        preparedDigest: com.clipping.mcpserver.service.dto.clipping.DigestResult
    ): DeliveryOutcome {
        try {
            // ABANDONED 건이 있으면 해당 아이템을 현재 다이제스트에 병합해 누락 없이 발송한다.
            val (mergedDigest, abandonedLogIds) = mergeAbandonedItems(category.id, reservedTarget.targetId, preparedDigest)

            // 재시도 시 같은 payload를 쓰도록 대상별 delivery_log에 prepared digest를 먼저 고정한다.
            deliveryLogStore.savePreparedDigest(reservedTarget.logId, mergedDigest)
            // DM 발송 시 사용자가 설정한 구독 별칭(requestName)을 카테고리 이름 대신 사용한다.
            // 준비된 스냅샷을 그대로 전송해 채널과 DM이 같은 후보 집합을 공유하게 한다.
            val result = digestDeliveryWorkflowPort.sendPreparedDigest(
                categoryId = category.id,
                preparedDigest = mergedDigest.toPreparedDigestResult(),
                slackChannelId = reservedTarget.targetId,
                categoryNameOverride = reservedTarget.requestNameOverride
            ).toDigestResult()
            val status = if (result.postedToSlack) "SENT" else "SKIPPED"
            deliveryLogStore.updateStatus(
                reservedTarget.logId, status, result.markedSentCount, result.slackMessageTs
            )
            // Slack payload 에러로 text-only fallback 이 사용된 경우 delivery_log 에 표시한다.
            if (result.fallbackUsed) {
                deliveryLogStore.markFallbackUsed(reservedTarget.logId, true)
                log.warn {
                    "Slack fallback used: category=${category.name} target=${reservedTarget.targetId}"
                }
            }

            // 병합된 ABANDONED 건은 SENT로 전이해 재발송되지 않도록 한다.
            if (result.postedToSlack && abandonedLogIds.isNotEmpty()) {
                abandonedLogIds.forEach { abandonedId ->
                    deliveryLogStore.updateStatus(abandonedId, "SENT", 0, result.slackMessageTs)
                }
                log.info { "Merged ${abandonedLogIds.size} ABANDONED digest(s) into delivery: category=${category.name} target=${reservedTarget.targetId}" }
            }

            if (result.postedToSlack) {
                log.info { "Digest delivered: category=${category.name} target=${reservedTarget.targetId}" }
                opsNotifier.notifyDelivered(category.name, reservedTarget.targetId, result.selectedCount)
                return DeliveryOutcome.SENT
            }

            // 연속 무발송 3일 경과 시 1회 안내 DM 발송
            if (status == "SKIPPED") {
                checkAndNotifySilentPeriod(
                    channelId = reservedTarget.targetId,
                    categoryId = category.id,
                    categoryName = category.name
                )
            }
            return DeliveryOutcome.SKIPPED
        } catch (e: DigestDeliveryFinalizationException) {
            // Slack은 성공했지만 sent 마킹/통계가 실패한 경우 재전송 대신 후처리 복구 대상으로 남긴다.
            deliveryLogStore.updateStatus(
                reservedTarget.logId,
                "FINALIZATION_FAILED",
                e.itemCount,
                e.slackMessageTs
            )
            // 즉시 이벤트로 한 번 더 복구를 시도하고, 실패 시에는 예약된 워커 재시도가 폴백으로 남는다.
            requestDigestFinalizationRecovery(
                logId = reservedTarget.logId,
                exception = e
            )
            log.error(e) {
                "Digest finalization failed after send: category=${category.name} target=${reservedTarget.targetId}"
            }
            return DeliveryOutcome.SENT // Slack 전송은 성공했으므로 SENT로 처리
        } catch (e: Exception) {
            val outcome = DeliveryRetryOrchestrator.computeFailureOutcome(0, e.message)
            deliveryLogStore.recordFailure(reservedTarget.logId, outcome.newRetryCount, outcome.nextRetryAt, outcome.newStatus, outcome.lastError)
            log.warn(e) { "Digest delivery failed: category=${category.name} target=${reservedTarget.targetId}" }
            schedulerErrorNotifier?.notifyBackgroundError("다이제스트 개별 발송", e, "category=${category.name}, target=${reservedTarget.targetId}")
            return DeliveryOutcome.FAILED
        }
    }

    /**
     * Slack rate limit 토큰을 획득하거나, 토큰 버킷 미주입 시 고정 간격만큼 잔다.
     * 인터럽트 발생 시 false 를 반환하고 인터럽트 플래그를 복원해 상위 루프가 중단할 수 있게 한다.
     */
    private fun acquireSlackRateLimitPermit(context: String): Boolean {
        if (slackRateLimiter != null) {
            slackRateLimiter.acquire()
            return true
        }
        return try {
            InterruptibleSleep.sleep(
                delayMs = SLACK_RATE_LIMIT_DELAY_MS,
                context = context
            )
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /** delivery_log 예약에 성공한 대상만 추려 이번 사이클의 실제 전송 목록으로 확정한다. */
    private fun reserveDeliveryTargets(
        categoryId: String,
        targets: List<DeliveryTarget>,
        today: java.time.LocalDate,
        currentHour: Int
    ): List<ReservedDeliveryTarget> {
        return targets.mapNotNull { target ->
            // 중복 방지 예약이 성공한 대상만 이후 스냅샷 fan-out에 참여시킨다.
            deliveryLogStore.tryReserve(categoryId, target.targetId, today, currentHour)
                ?.let { logId ->
                    ReservedDeliveryTarget(
                        logId = logId,
                        targetId = target.targetId,
                        requestNameOverride = target.requestNameOverride
                    )
                }
        }
    }

    /**
     * 카테고리의 발송 대상(채널 + DM)을 결정한다.
     * - 채널 발송: 정시에 즉시 (delivery_log가 중복 방지)
     * - DM 발송: userId 해시 기반으로 0~29분에 자동 분산
     *   (Slack chat.postMessage Tier 1 ≈ 50건/분 한도 준수)
     */
    /** 테스트에서 오버라이드할 수 있도록 현재 분을 별도 메서드로 분리한다. */
    internal fun currentMinuteKst(): Int = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minute

    private fun resolveDeliveryTargets(
        category: Category,
        deliveryContext: DeliveryDispatchContext
    ): List<DeliveryTarget> {
        val targets = mutableListOf<DeliveryTarget>()
        val currentMinute = currentMinuteKst()

        // 채널 발송은 정시에 즉시 포함한다 (분산 대상 아님)
        val channelId = category.slackChannelId?.takeIf { it.isNotBlank() }
        if (channelId != null && !isDirectMessage(channelId)) {
            targets += DeliveryTarget(targetId = channelId, requestNameOverride = null)
        }

        // DM 구독자는 userId 해시로 분(minute) 오프셋을 계산해 자동 분산한다.
        // 예: 9시 설정 → user-A는 9:02, user-B는 9:17, user-C는 9:25에 발송
        val dmRequests = approvedRequestsForCategory(category.id, deliveryContext)
            .filter { isDirectMessage(it.slackChannelId) }
        if (dmRequests.isNotEmpty()) {
            val userIds = dmRequests.map { it.requesterUserId }.distinct()
            // 일괄 조회로 N+1 쿼리를 제거한다
            val users = adminUserStore.findByIds(userIds)
            val dmChannelByUserId = users
                .filter { it.isActive && !it.slackDmChannelId.isNullOrBlank() }
                .mapNotNull { user ->
                    val channelId = user.slackDmChannelId ?: return@mapNotNull null
                    user.id to channelId
                }.toMap()

            for (dmRequest in dmRequests) {
                val uid = dmRequest.requesterUserId
                val dmChannel = dmChannelByUserId[uid] ?: continue
                // DM_SPREAD_MINUTES > 0이면 userId 해시로 분 오프셋을 계산해 분산한다
                if (DM_SPREAD_MINUTES > 0) {
                    val minuteOffset = (uid.hashCode().and(Int.MAX_VALUE)) % DM_SPREAD_MINUTES
                    if (currentMinute % DM_SPREAD_MINUTES != minuteOffset) continue
                }
                targets += DeliveryTarget(
                    targetId = dmChannel,
                    requestNameOverride = dmRequest.requestName.takeIf { it != category.name }
                )
            }
        }

        return targets.distinctBy { it.targetId }
    }

    /**
     * 지수 백오프 스케줄에 따라 실패 발송 건을 재시도한다.
     * stuck claim 복구 → 재시도 후보 조회 → claim → 재시도 순서로 진행한다.
     */
    private fun retryFailedDeliveries(runtime: RuntimeSettingService.RuntimeSettings) {
        // 비정상 종료로 인해 RETRYING 상태로 남은 행을 먼저 복구한다
        retryOrchestrator.recoverStuckClaims()

        val retryCandidates = retryOrchestrator.findPendingRetries()
        if (retryCandidates.isEmpty()) return

        log.info { "Retrying ${retryCandidates.size} failed deliveries" }
        for (candidate in retryCandidates) {
            // 원자적 claim 성공 시에만 재시도 진행 — 중복 재시도를 방지한다
            if (!retryOrchestrator.claim(candidate.id)) {
                log.debug { "Retry claim skipped (already claimed): id=${candidate.id}" }
                continue
            }

            val category = categoryStore.findById(candidate.categoryId)
            if (category == null) {
                log.warn { "Retry skipped: category not found (${candidate.categoryId})" }
                retryOrchestrator.recordFailure(candidate.id, candidate.retryCount, IllegalStateException("Category not found: ${candidate.categoryId}"))
                continue
            }

            try {
                retryDeliveryCandidate(category, candidate, runtime)
            } catch (e: Exception) {
                // 재시도 실패 시 지수 백오프 결과를 기록한다
                retryOrchestrator.recordFailure(candidate.id, candidate.retryCount, e)
                log.error(e) {
                    "Retry failed (count=${candidate.retryCount}): category=${category.name} channel=${candidate.channelId}"
                }
            }

            // 재시도 간 레이트리밋 대기도 인터럽트 안전하게 수행한다.
            if (slackRateLimiter != null) {
                slackRateLimiter.acquire()
            } else {
                try {
                    InterruptibleSleep.sleep(
                        delayMs = SLACK_RATE_LIMIT_DELAY_MS,
                        context = "SlackDigestWorker retry rate limit"
                    )
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    log.info { "SlackDigestWorker retry rate limit sleep interrupted — skipping remaining retries for category=${category.name}" }
                    break
                }
            }
        }
    }

    /**
     * 실패 로그의 상태에 따라 전송 재시도 또는 후처리 복구만 다시 수행한다.
     */
    private fun retryDeliveryCandidate(
        category: Category,
        candidate: DeliveryLogStore.DeliveryRetryCandidate,
        runtime: RuntimeSettingService.RuntimeSettings
    ) {
        if (candidate.status == "FINALIZATION_FAILED") {
            // Slack은 이미 보냈으므로 재전송 대신 sent 마킹/통계 후처리만 복구한다.
            val preparedDigest = candidate.preparedDigest ?: run {
                log.warn {
                    "Retry skipped: prepared digest missing for finalization retry (${candidate.id})"
                }
                return
            }
            val markedCount = digestDeliveryWorkflowPort.finalizePreparedDigest(
                candidate.categoryId,
                preparedDigest.toPreparedDigestResult()
            )
            deliveryLogStore.updateStatus(candidate.id, "SENT", markedCount, candidate.slackMessageTs)
            log.info {
                "Digest finalization retry succeeded: category=${category.name} channel=${candidate.channelId}"
            }
            return
        }

        // 일반 FAILED 재시도는 저장된 prepared digest를 우선 사용해 최초 payload를 유지한다.
        val preparedDigest = candidate.preparedDigest ?: digestDeliveryWorkflowPort.prepareDigest(
            categoryId = candidate.categoryId,
            maxItems = runtime.slackAutoDigestMaxItems,
            unsentOnly = runtime.slackAutoDigestUnsentOnly,
            sendToSlack = false,
            slackChannelId = null
        ).toDigestResult()
        val result = digestDeliveryWorkflowPort.sendPreparedDigest(
            categoryId = candidate.categoryId,
            preparedDigest = preparedDigest.toPreparedDigestResult(),
            slackChannelId = candidate.channelId
        ).toDigestResult()
        // 재시도 성공 시 기존 로그의 상태를 갱신한다.
        val status = if (result.postedToSlack) "SENT" else "SKIPPED"
        deliveryLogStore.updateStatus(
            candidate.id, status, result.markedSentCount, result.slackMessageTs
        )
        if (result.postedToSlack) {
            log.info { "Retry succeeded: category=${category.name} channel=${candidate.channelId}" }
        }
    }

    /**
     * 승인 요청과 개인 스케줄을 조합해 이번 실행에서 발송해야 할 카테고리 집합을 계산한다.
     */
    private fun buildDeliveryDispatchContext(
        dayOfWeek: String,
        currentHour: Int
    ): DeliveryDispatchContext {
        // 현재 시각에 발송 대상인 사용자 집합을 먼저 구해 카테고리 판단용 N+1 조회를 제거한다.
        val dueUserIds = scheduleStore.findSchedulesDueNow(dayOfWeek, currentHour)
            .map { it.userId }
            .toSet()

        // 개인 스케줄 판단에는 카테고리 ID만 필요하므로 DB DISTINCT 조회로 메모리 사용을 제한한다.
        val dueCategoryIds = requestStore.findApprovedCategoryIdsByRequesterIds(dueUserIds)

        return DeliveryDispatchContext(
            dueCategoryIds = dueCategoryIds
        )
    }

    /**
     * 한 워커 실행 안에서 카테고리별 승인 구독을 캐시한다.
     * 전체 승인 구독 캐시 대신 실제 발송 후보 카테고리만 조회해 10,000건 안전 상한 누락을 피한다.
     */
    private fun approvedRequestsForCategory(
        categoryId: String,
        deliveryContext: DeliveryDispatchContext
    ): List<UserClippingRequest> {
        return deliveryContext.approvedRequestsByCategory.getOrPut(categoryId) {
            requestStore.listApprovedByCategoryId(categoryId)
        }
    }

    /** DM 대상인지 판별한다. D(DM 채널)와 U(사용자 ID) 접두어, legacy blank/`DM` 값을 호환 처리한다. */
    private fun isDirectMessage(channelId: String): Boolean {
        val normalized = channelId.trim()
        return normalized.isBlank() ||
            normalized.equals("DM", ignoreCase = true) ||
            normalized.uppercase().let { it.startsWith("D") || it.startsWith("U") }
    }

    /**
     * 카테고리의 발송 시점을 판단한다.
     * 1순위: 카테고리별 개별 스케줄(delivery_preset 설정)
     * 2순위: 개인 스케줄에 구독된 카테고리인 경우
     * 3순위: 글로벌 cron 매칭
     */
    private fun shouldDeliverNow(
        category: Category,
        dayOfWeek: String,
        currentHour: Int,
        dueCategoryIds: Set<String>,
        globalCronDue: Boolean
    ): Boolean {
        // 1. 카테고리별 개별 스케줄 확인
        val rule = categoryRuleStore.findByCategoryId(category.id)
        if (rule?.deliveryPreset != null) {
            return matchesCategoryRule(rule, dayOfWeek, currentHour)
        }

        // 2. 개인 스케줄과 승인 요청을 미리 조합해 둔 카테고리 집합을 재사용한다.
        if (dueCategoryIds.contains(category.id)) {
            return true
        }

        // 3. 글로벌 cron 매칭 폴백
        return globalCronDue
    }

    /** 카테고리별 개별 스케줄(deliveryDays, deliveryHour)과 현재 시각을 비교한다. */
    private fun matchesCategoryRule(
        rule: CategoryRule,
        dayOfWeek: String,
        currentHour: Int
    ): Boolean {
        val ruleDays = rule.deliveryDays ?: return false
        val ruleHour = rule.deliveryHour ?: return false
        return dayOfWeek in ruleDays && currentHour == ruleHour
    }

    /**
     * 글로벌 cron 표현식이 현재 시각(시 단위)에 매칭되는지 확인한다.
     * Spring CronExpression.next()로 다음 실행 시각을 구해, 현재 시와 같은지 비교한다.
     */
    private fun matchesGlobalCron(cronExpression: String, currentHour: Int): Boolean {
        val trimmed = cronExpression.trim()
        if (trimmed.isBlank() || trimmed == "-") return false

        val expr = try {
            CronExpression.parse(trimmed)
        } catch (_: IllegalArgumentException) {
            log.warn { "Invalid Slack digest cron expression: $trimmed" }
            return false
        }

        // 오늘 00:00(KST)부터 다음 실행 시각을 구해 현재 시와 비교한다.
        val kst = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.now(kst)
        val startOfHour = now.withMinute(0).withSecond(0).withNano(0)
        val nextRun = expr.next(startOfHour.minusSeconds(1)) ?: return false
        return nextRun.hour == currentHour &&
            nextRun.toLocalDate() == now.toLocalDate()
    }

    /** FINALIZATION_FAILED 로그를 즉시 회복시키기 위해 후처리 재시도 이벤트를 발행한다. */
    private fun requestDigestFinalizationRecovery(
        logId: String,
        exception: DigestDeliveryFinalizationException
    ) {
        // 비동기 이벤트로 먼저 회복을 시도하고, 실패하면 기존 delivery_log 재시도로 흡수한다.
        runCatching {
            applicationEventPublisher.publishEvent(
                DigestDeliveryFinalizationRequestedEvent(
                    summaryIds = exception.summaryIds,
                    categoryId = exception.categoryId,
                    sendAttempts = 1,
                    sendSuccesses = 1,
                    deliveryLogId = logId,
                    slackMessageTs = exception.slackMessageTs
                )
            )
        }.onFailure { error ->
            log.error(error) {
                "Failed to request digest finalization recovery event: " +
                    "categoryId=${exception.categoryId}, logId=$logId"
            }
        }
    }

    /**
     * ABANDONED 상태의 기존 발송 건의 아이템을 현재 다이제스트 앞에 병합한다.
     * 병합 가능한 ABANDONED 건이 없으면 원본 digest와 빈 ID 목록을 반환한다.
     *
     * @param categoryId 발송 카테고리 ID
     * @param channelId 발송 채널 ID
     * @param current 현재 준비된 다이제스트
     * @return (병합된 다이제스트, 병합된 ABANDONED 로그 ID 목록) 쌍
     */
    private fun mergeAbandonedItems(
        categoryId: String,
        channelId: String,
        current: com.clipping.mcpserver.service.dto.clipping.DigestResult
    ): Pair<com.clipping.mcpserver.service.dto.clipping.DigestResult, List<String>> {
        // ABANDONED 건 중 preparedDigest가 있는 건의 아이템을 수집한다.
        val abandonedCandidates = retryOrchestrator.findAbandonedForMerge(categoryId, channelId)
        if (abandonedCandidates.isEmpty()) return Pair(current, emptyList())

        val abandonedItems = abandonedCandidates
            .flatMap { it.preparedDigest?.items ?: emptyList() }
        if (abandonedItems.isEmpty()) return Pair(current, emptyList())

        // ABANDONED 아이템을 앞에 prepend해 누락 기사가 먼저 노출되도록 한다.
        val mergedItems = abandonedItems + current.items
        log.info {
            "Merging ${abandonedItems.size} abandoned items before ${current.items.size} current items: category=$categoryId channel=$channelId"
        }
        val merged = current.copy(
            items = mergedItems,
            selectedCount = mergedItems.size,
            totalCandidates = current.totalCandidates + abandonedItems.size
        )
        return Pair(merged, abandonedCandidates.map { it.id })
    }

    /**
     * 마지막 SENT 발송 후 3일 이상 경과했고 아직 안내하지 않았으면,
     * 사용자에게 1회 DM을 보내고 NOTIFIED_NO_CONTENT로 기록한다.
     */
    private fun checkAndNotifySilentPeriod(
        channelId: String,
        categoryId: String,
        categoryName: String
    ) {
        // 마지막 SENT 발송 시각을 조회한다 (SENT 기록이 없으면 신규 구독 — 안내 불필요)
        val lastSent = deliveryLogStore.findLastSentDate(channelId, categoryId) ?: return

        // 마지막 발송 이후 경과 일수를 계산한다
        val daysSinceLastSent = Duration.between(lastSent, Instant.now()).toDays()
        if (daysSinceLastSent < SILENT_PERIOD_DAYS) return

        // 이미 안내 DM을 보냈는지 확인한다 (중복 발송 방지)
        if (deliveryLogStore.hasNotifiedSinceLastSent(channelId, categoryId)) return

        // 안내 DM 발송
        val message = buildSilentPeriodMessage(categoryName, lastSent, daysSinceLastSent)
        try {
            slackMessageSender.sendMessage(channelId = channelId, text = message)
            // NOTIFIED_NO_CONTENT 기록을 delivery_log에 저장한다
            saveSilentPeriodNotificationLog(channelId, categoryId)
        } catch (e: Exception) {
            log.warn(e) {
                "Silent period notification failed: channelId=$channelId categoryId=$categoryId"
            }
        }
    }

    /** 연속 무발송 안내 DM의 delivery_log 기록을 저장한다. */
    private fun saveSilentPeriodNotificationLog(channelId: String, categoryId: String) {
        val now = java.time.LocalDate.now(ZoneId.of("Asia/Seoul"))
        val currentHour = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).hour
        // tryReserve 대신 직접 저장 — NOTIFIED_NO_CONTENT는 중복 방지 UNIQUE 제약과 무관하다
        val logId = deliveryLogStore.tryReserve(categoryId, channelId, now, currentHour)
        if (logId != null) {
            deliveryLogStore.updateStatus(logId, "NOTIFIED_NO_CONTENT", 0)
        }
    }

    /** 연속 무발송 안내 메시지를 생성한다. */
    private fun buildSilentPeriodMessage(
        categoryName: String,
        lastSent: Instant,
        daysSince: Long
    ): String {
        val kst = ZoneId.of("Asia/Seoul")
        val lastSentDate = lastSent.atZone(kst).toLocalDate()
        val formatter = DateTimeFormatter.ofPattern("M월 d일")
        return "💤 *$categoryName* — 최근 ${daysSince}일간 새 뉴스가 없어요\n" +
            "마지막 발송: ${lastSentDate.format(formatter)} (${daysSince}일 전)\n" +
            "서비스는 정상 동작 중이에요. 새 뉴스가 나오면 바로 보내드릴게요."
    }
}
