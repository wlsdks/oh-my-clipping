package com.ohmyclipping.service

import com.ohmyclipping.model.DeliveryPreset
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.collection.NaverNewsCollectionService
import com.ohmyclipping.service.collection.NaverNewsSearchPort
import com.ohmyclipping.service.pipeline.PipelineLogService
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.CategoryRuleStore
import com.ohmyclipping.store.RssSourceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

@Component
class AutoCollectionScheduler(
    private val categoryStore: CategoryStore,
    private val sourceStore: RssSourceStore,
    private val categoryRuleStore: CategoryRuleStore,
    private val asyncClipJobService: AsyncClipJobService,
    private val runtimeSettingService: RuntimeSettingService,
    private val naverNewsSearchPort: NaverNewsSearchPort,
    private val naverNewsCollectionService: NaverNewsCollectionService,
    private val metrics: ClippingMetrics,
    private val pipelineLogService: PipelineLogService,
    private val itemSummarizationService: ItemSummarizationService,
) {
    companion object {
        private val WEEKEND_DAYS = setOf("SAT", "SUN")
        internal val PRIMARY_COLLECTION_HOURS = setOf(3, 7, 11, 15, 19, 23)
    }

    internal val activeCategoryTimestamps = ConcurrentHashMap<String, Instant>()

    /** 가장 최근 collectActiveCategories 실행 시각. ScheduleMissDetector가 참조한다. */
    @Volatile
    internal var lastFiredAt: Instant? = null

    /**
     * 이 스케줄러가 사용하는 cron 표현식을 반환한다.
     * Phase 8에서 런타임 설정 기반의 동적 cron으로 교체될 예정이다.
     */
    fun cronExpression(): String = "0 5 3,7,11,15,19,23 * * *"

    @Scheduled(cron = "0 5 3,7,11,15,19,23 * * *", zone = "Asia/Seoul")
    fun collectActiveCategories() = metrics.recordSchedulerRun("auto_collection") {
        lastFiredAt = Instant.now()
        log.info { "AutoCollectionScheduler.collectActiveCategories started" }
        val start = System.nanoTime()
        val runtime = runtimeSettingService.current()
        if (runtime.maintenanceMode) return@recordSchedulerRun
        val dayOfWeek = currentDayOfWeek()
        val categories = categoryStore.findOperational()
        var enqueued = 0
        for (category in categories) {
            if (!shouldCollectToday(category.id, dayOfWeek)) continue
            val approvedSources = sourceStore.listApproved(category.id)
            if (approvedSources.isEmpty()) continue
            runCatching {
                asyncClipJobService.enqueueCollect(category.id, null)
                enqueued++
            }.onFailure { e ->
                log.warn { "Auto collect enqueue failed: category=${category.name} error=${e.message}" }
            }
        }
        if (enqueued > 0) {
            log.info { "Auto collection scheduled: $enqueued/${categories.size} categories (day=$dayOfWeek)" }
        }

        // RSS 수집 후 SearchCo 뉴스 검색 API로 보완 수집을 실행한다
        collectNaverNewsForCategories(categories.filter { shouldCollectToday(it.id, dayOfWeek) })
        val elapsed = (System.nanoTime() - start) / 1_000_000
        log.info { "AutoCollectionScheduler.collectActiveCategories completed in ${elapsed}ms, enqueued=$enqueued" }
        // 스케줄러는 파이프라인 실행 트리거만 담당한다 — Slack 알림은 PipelineLogService/OpsLogNotifier가 처리한다
    }

    @Scheduled(cron = "0 35 3,7,11,15,19,23 * * *")
    fun summarizeActiveCategories() = metrics.recordSchedulerRun("auto_summarization") {
        log.info { "AutoCollectionScheduler.summarizeActiveCategories started" }
        val start = System.nanoTime()
        val runtime = runtimeSettingService.current()
        if (runtime.maintenanceMode) return@recordSchedulerRun
        val dayOfWeek = currentDayOfWeek()
        val categories = categoryStore.findOperational()
        var enqueued = 0
        for (category in categories) {
            if (!shouldCollectToday(category.id, dayOfWeek)) continue
            runCatching {
                asyncClipJobService.enqueueSummarize(category.id)
                enqueued++
            }.onFailure { e ->
                log.warn { "Auto summarize enqueue failed: category=${category.name} error=${e.message}" }
            }
        }
        if (enqueued > 0) {
            log.info { "Auto summarization scheduled: $enqueued/${categories.size} categories (day=$dayOfWeek)" }
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        if (enqueued > 0) {
            log.info { "AutoCollectionScheduler.summarizeActiveCategories completed in ${elapsed}ms, enqueued=$enqueued" }
            // 스케줄러는 파이프라인 실행 트리거만 담당한다 — Slack 알림은 PipelineLogService/OpsLogNotifier가 처리한다
        } else {
            log.debug { "AutoCollectionScheduler.summarizeActiveCategories completed in ${elapsed}ms, enqueued=0" }
        }

        // 기존 요약 작업 완료 후, fallback 재요약 시도
        runCatching { itemSummarizationService.resummarizeFallbacks() }
            .onFailure { e -> log.warn(e) { "Fallback re-summarization failed: ${e.message}" } }
    }

    @Scheduled(cron = "0 5 * * * *")
    fun collectHighFrequencyCategories() = metrics.recordSchedulerRun("auto_collection_high_frequency") {
        log.info { "AutoCollectionScheduler.collectHighFrequencyCategories started" }
        val start = System.nanoTime()
        val runtime = runtimeSettingService.current()
        if (runtime.maintenanceMode) return@recordSchedulerRun
        val dayOfWeek = currentDayOfWeek()
        if (dayOfWeek in WEEKEND_DAYS) return@recordSchedulerRun
        val currentHour = currentHour()
        if (currentHour in PRIMARY_COLLECTION_HOURS) return@recordSchedulerRun
        evictExpiredActiveCategories()
        if (activeCategoryTimestamps.isEmpty()) return@recordSchedulerRun
        val activeIds = activeCategoryTimestamps.keys.toSet()
        var enqueued = 0
        for (categoryId in activeIds) {
            if (!shouldCollectToday(categoryId, dayOfWeek)) continue
            runCatching {
                asyncClipJobService.enqueueCollect(categoryId, null)
                enqueued++
            }.onFailure { e ->
                log.warn { "Adaptive collect enqueue failed: categoryId=$categoryId error=${e.message}" }
            }
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        if (enqueued > 0) {
            log.info { "Adaptive collection scheduled: $enqueued/${activeIds.size} active categories (hour=$currentHour), completed in ${elapsed}ms" }
        } else {
            log.debug { "AutoCollectionScheduler.collectHighFrequencyCategories completed in ${elapsed}ms, enqueued=0" }
        }
    }

    fun markCategoryActive(categoryId: String) {
        activeCategoryTimestamps[categoryId] = Instant.now()
    }

    fun markCategoryInactive(categoryId: String) {
        activeCategoryTimestamps.remove(categoryId)
    }

    internal fun evictExpiredActiveCategories() {
        val cutoff = Instant.now().minusSeconds(24 * 3600)
        activeCategoryTimestamps.entries.removeIf { it.value.isBefore(cutoff) }
    }

    internal fun shouldCollectToday(categoryId: String, dayOfWeek: String): Boolean {
        val rule = categoryRuleStore.findByCategoryId(categoryId)
        val deliveryPreset = rule?.deliveryPreset
        if (deliveryPreset != null) {
            return when (deliveryPreset) {
                DeliveryPreset.EVERYDAY -> true
                DeliveryPreset.WEEKDAYS -> dayOfWeek !in WEEKEND_DAYS
                DeliveryPreset.CUSTOM -> rule.deliveryDays?.contains(dayOfWeek) == true
            }
        }
        return dayOfWeek !in WEEKEND_DAYS
    }

    internal open fun currentHour(): Int =
        ZonedDateTime.now(ZoneId.of("Asia/Seoul")).hour

    internal open fun currentDayOfWeek(): String =
        ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            .dayOfWeek
            .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            .uppercase()
            .take(3)

    /**
     * SearchCo 뉴스 검색 API로 카테고리별 키워드 기반 보완 수집을 실행한다.
     * API 키가 미설정이면 건너뛴다.
     */
    private fun collectNaverNewsForCategories(categories: List<com.ohmyclipping.model.Category>) {
        if (!naverNewsSearchPort.isConfigured()) return

        var totalNew = 0
        for (category in categories) {
            runCatching {
                val newCount = naverNewsCollectionService.collectForCategory(category.id)
                totalNew += newCount
            }.onFailure { e ->
                log.warn { "Naver collection failed: category=${category.name} error=${e.message}" }
            }
        }
        if (totalNew > 0) {
            log.info { "Naver supplementary collection: $totalNew new items across ${categories.size} categories" }
        }
    }
}
