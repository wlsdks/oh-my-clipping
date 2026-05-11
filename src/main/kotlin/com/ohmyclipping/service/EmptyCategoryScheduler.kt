package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsNotificationEvent

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.model.CategoryStatus
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.UserClippingRequestStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger {}

/**
 * 구독자가 0명인 카테고리를 주기적으로 점검하여 30일 이상 비활동 상태이면 자동 비활성화한다.
 * 매일 04:00에 실행된다.
 */
@Component
class EmptyCategoryScheduler(
    private val categoryStore: CategoryStore,
    private val requestStore: UserClippingRequestStore,
    private val jdbc: JdbcTemplate,
    private val notificationService: OperationsNotificationService,
    private val metrics: ClippingMetrics
) {

    companion object {
        /** 구독자 0명 상태 유지 기간 기준(일) */
        const val INACTIVE_DAYS_THRESHOLD = 30
    }

    /**
     * 매일 04:00 실행: 구독자 0인 활성 카테고리 중 최근 발송 이력이 30일 이상 없는 카테고리를 비활성화한다.
     */
    @Scheduled(cron = "0 0 4 * * *")
    fun deactivateEmptyCategories() = metrics.recordSchedulerRun("empty_category") {
        log.info { "EmptyCategoryScheduler started" }
        val start = System.nanoTime()
        // 구독자 수 집계: 승인된 요청 + 소유권 테이블 양쪽 모두 확인
        val subscribersByApproval = runCatching {
            requestStore.countApprovedGroupByCategoryId().keys
        }.onFailure { e ->
            log.error(e) { "Failed to query approved subscription categories" }
        }.getOrDefault(emptySet())

        // 소유권 테이블에서도 구독자 매핑 확인 (위자드에서 직접 생성된 카테고리 포함)
        val subscribersByOwnership = runCatching {
            jdbc.queryForList(
                "SELECT DISTINCT category_id FROM clipping_user_owned_categories",
                String::class.java
            ).toSet()
        }.onFailure { e ->
            log.error(e) { "Failed to query owned categories" }
        }.getOrDefault(emptySet())

        // 둘 중 하나라도 구독자가 있으면 구독자 있는 것으로 판단
        val categoriesWithSubscribers = subscribersByApproval + subscribersByOwnership

        // 활성 카테고리 중 구독자가 0인 것을 필터링한다
        val activeCategories = runCatching {
            categoryStore.findOperational()
        }.onFailure { e ->
            log.error(e) { "Failed to query categories" }
        }.getOrDefault(emptyList())

        val cutoff = Instant.now().minus(INACTIVE_DAYS_THRESHOLD.toLong(), ChronoUnit.DAYS)
        var deactivatedCount = 0

        for (category in activeCategories) {
            // 시스템 카테고리(__competitor__ 등)는 비활성화 대상에서 제외한다
            if (category.id.startsWith("__")) continue
            if (category.id in categoriesWithSubscribers) continue

            // 최근 발송 이력이 있는지 확인한다
            val hasRecentDelivery = runCatching {
                hasDeliverySince(category.id, cutoff)
            }.getOrDefault(true)

            if (hasRecentDelivery) continue

            // 비활성화한다
            runCatching {
                categoryStore.update(category.copy(isActive = false, status = CategoryStatus.PAUSED))
                deactivatedCount++
                log.info {
                    "Auto-deactivated empty category: ${category.name} (${category.id}), " +
                        "no subscribers and no delivery in $INACTIVE_DAYS_THRESHOLD days"
                }
            }.onFailure { e ->
                log.error(e) { "Failed to deactivate category: ${category.id}" }
            }
        }

        if (deactivatedCount > 0) {
            notificationService.sendOps(
                OpsNotificationEvent.EMPTY_CATEGORY_CLEANUP,
                "구독자 없는 카테고리 ${deactivatedCount}개가 자동 비활성화되었습니다.",
                mapOf("date" to java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString())
            )
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        if (deactivatedCount > 0) {
            log.info { "EmptyCategoryScheduler completed in ${elapsed}ms, deactivated=$deactivatedCount" }
        } else {
            log.debug { "EmptyCategoryScheduler completed in ${elapsed}ms, deactivated=0" }
        }
    }

    /**
     * 지정 카테고리의 cutoff 이후 발송 이력 존재 여부를 확인한다.
     */
    private fun hasDeliverySince(categoryId: String, cutoff: Instant): Boolean {
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM delivery_log WHERE category_id = ? AND created_at >= ?",
            Int::class.java,
            categoryId,
            java.sql.Timestamp.from(cutoff)
        ) ?: 0
        return count > 0
    }
}
