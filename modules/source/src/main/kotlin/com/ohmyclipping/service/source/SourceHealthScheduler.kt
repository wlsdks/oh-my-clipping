package com.ohmyclipping.service.source

import com.ohmyclipping.model.RssSource
import com.ohmyclipping.service.port.OpsNotificationEvent
import com.ohmyclipping.service.port.RssCollectionPort
import com.ohmyclipping.service.port.RssCollectionSource
import com.ohmyclipping.service.port.SourceOpsNotificationPort
import com.ohmyclipping.service.port.SourceSchedulerMetricsPort
import com.ohmyclipping.store.RssSourceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * RSS 소스 상태를 주기적으로 점검하여 연속 실패 소스를 자동 비활성화하고,
 * 비활성화된 소스를 매일 새벽에 재시도하여 복구 가능한 소스를 재활성화한다.
 */
@Component
class SourceHealthScheduler(
    private val rssSourceStore: RssSourceStore,
    private val rssFeedCollector: RssCollectionPort,
    private val notificationService: SourceOpsNotificationPort,
    private val metrics: SourceSchedulerMetricsPort,
    private val reliabilityCalculator: SourceReliabilityCalculator,
    private val crawlLogStore: com.ohmyclipping.store.SourceCrawlLogStore
) {

    companion object {
        /** 자동 비활성화 기준 연속 실패 횟수 */
        const val FAIL_COUNT_THRESHOLD = 10

        /** 재시도 시 수집 대상 시간 범위(시간) */
        const val RETRY_HOURS_BACK = 1

        /** 크롤 로그 보관 기간(일) */
        const val CRAWL_LOG_RETENTION_DAYS = 90L
    }

    /**
     * 매시 30분에 실행: 연속 실패 횟수가 임계값 이상인 소스를 비활성화하고 관리자에게 알린다.
     */
    @Scheduled(cron = "0 30 * * * *")
    fun deactivateFailedSources() = metrics.recordSourceSchedulerRun("source_health_deactivate") {
        log.info { "SourceHealthScheduler.deactivateFailedSources started" }
        val start = System.nanoTime()
        val failedSources = runCatching {
            rssSourceStore.findFailedSources(FAIL_COUNT_THRESHOLD)
        }.onFailure { e ->
            log.error(e) { "Failed to query failed sources" }
        }.getOrDefault(emptyList())

        if (failedSources.isNotEmpty()) {
            log.warn { "Found ${failedSources.size} sources with >= $FAIL_COUNT_THRESHOLD consecutive failures" }

            for (source in failedSources) {
                runCatching {
                    rssSourceStore.deactivate(source.id)
                    log.info { "Deactivated source: ${source.name} (${source.url}), failCount=${source.crawlFailCount}" }
                }.onFailure { e ->
                    log.error(e) { "Failed to deactivate source: ${source.id}" }
                }

                notificationService.sendOps(
                    OpsNotificationEvent.SOURCE_AUTO_DISABLED,
                    "소스 '${source.name}' (${source.url})이 연속 ${source.crawlFailCount}회 실패하여 자동 비활성화되었습니다.",
                    mapOf("sourceId" to source.id)
                )
            }
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        if (failedSources.isNotEmpty()) {
            log.info { "SourceHealthScheduler.deactivateFailedSources completed in ${elapsed}ms, deactivated=${failedSources.size}" }
        } else {
            log.debug { "SourceHealthScheduler.deactivateFailedSources completed in ${elapsed}ms, no failed sources" }
        }
    }

    /**
     * 매일 03:30 실행: 비활성화된 소스를 재시도하여 복구 가능한 소스를 재활성화한다.
     * 경량 수집(1시간 범위)을 시도하고 성공하면 실패 카운트를 초기화한 뒤 재활성화한다.
     */
    @Scheduled(cron = "0 30 3 * * *")
    fun retryDeactivatedSources() = metrics.recordSourceSchedulerRun("source_health_retry") {
        log.info { "SourceHealthScheduler.retryDeactivatedSources started" }
        val start = System.nanoTime()
        val deactivated = runCatching {
            rssSourceStore.findDeactivated()
        }.onFailure { e ->
            log.error(e) { "Failed to query deactivated sources" }
        }.getOrDefault(emptyList())

        if (deactivated.isNotEmpty()) {
            log.info { "Retrying ${deactivated.size} deactivated sources" }
            var recovered = 0

            for (source in deactivated) {
                val success = runCatching {
                    rssFeedCollector.collect(source.toSourceHealthCollectionSource(), RETRY_HOURS_BACK)
                    true
                }.getOrDefault(false)

                if (success) {
                    runCatching {
                        rssSourceStore.resetFailCount(source.id)
                        rssSourceStore.reactivate(source.id)
                        recovered++
                        log.info { "Reactivated source: ${source.name} (${source.url})" }
                    }.onFailure { e ->
                        log.error(e) { "Failed to reactivate source: ${source.id}" }
                    }
                }
            }

            if (recovered > 0) {
                notificationService.sendOps(
                    OpsNotificationEvent.SOURCE_RETRY_RESULT,
                    "비활성화 소스 재시도 결과: ${deactivated.size}개 중 ${recovered}개 복구되었습니다.",
                    mapOf("date" to java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString())
                )
            }
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        log.info { "SourceHealthScheduler.retryDeactivatedSources completed in ${elapsed}ms" }
    }

    /**
     * 매시간 실행: 모든 소스의 신뢰도 점수를 재계산하여 DB에 반영한다.
     */
    @Scheduled(fixedDelay = 3600000)
    fun updateReliabilityScores() = metrics.recordSourceSchedulerRun("source_reliability_update") {
        log.info { "SourceHealthScheduler.updateReliabilityScores started" }
        val start = System.nanoTime()
        val scores = runCatching {
            reliabilityCalculator.calculateAll()
        }.onFailure { e ->
            log.error(e) { "Failed to calculate reliability scores" }
        }.getOrDefault(emptyMap())

        if (scores.isNotEmpty()) {
            runCatching {
                rssSourceStore.updateReliabilityScores(scores)
            }.onFailure { e ->
                log.error(e) { "Failed to persist reliability scores" }
            }
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        log.info { "SourceHealthScheduler.updateReliabilityScores completed in ${elapsed}ms, sources=${scores.size}" }
    }

    /**
     * 매일 04:00 실행: 보관 기간(90일)을 초과한 크롤 로그를 정리한다.
     */
    @Scheduled(cron = "0 0 4 * * *")
    fun cleanupOldCrawlLogs() = metrics.recordSourceSchedulerRun("crawl_log_cleanup") {
        log.info { "SourceHealthScheduler.cleanupOldCrawlLogs started" }
        val start = System.nanoTime()
        // 보관 기간 cutoff를 Java에서 계산한다.
        val cutoff = java.time.LocalDateTime.now()
            .minusDays(CRAWL_LOG_RETENTION_DAYS)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
        val deleted = runCatching {
            crawlLogStore.deleteOlderThan(cutoff)
        }.onFailure { e ->
            log.error(e) { "Failed to cleanup old crawl logs" }
        }.getOrDefault(0)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        log.info { "SourceHealthScheduler.cleanupOldCrawlLogs completed in ${elapsed}ms, deleted=$deleted" }
    }
}

private fun RssSource.toSourceHealthCollectionSource(): RssCollectionSource =
    RssCollectionSource(
        id = id,
        name = name,
        url = url,
        emoji = emoji,
        isActive = isActive,
        crawlApproved = crawlApproved,
        approvedBy = approvedBy,
        approvedAt = approvedAt,
        legalBasis = legalBasis.name,
        summaryAllowed = summaryAllowed,
        fulltextAllowed = fulltextAllowed,
        termsReviewedAt = termsReviewedAt,
        expectedReviewAt = expectedReviewAt,
        reviewNotes = reviewNotes,
        verificationStatus = verificationStatus,
        reliabilityScore = reliabilityScore,
        lastCrawlError = lastCrawlError,
        crawlFailCount = crawlFailCount,
        lastSuccessAt = lastSuccessAt,
        sourceRegion = sourceRegion.name,
        categoryId = categoryId,
        curated = curated,
        responsibilityAcknowledgedAt = responsibilityAcknowledgedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        systemUpdatedAt = systemUpdatedAt,
        origin = origin,
    )
