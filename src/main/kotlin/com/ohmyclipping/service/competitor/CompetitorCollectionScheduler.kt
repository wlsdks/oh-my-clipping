package com.ohmyclipping.service.competitor

import com.ohmyclipping.observability.ClippingMetrics
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/**
 * 경쟁사 뉴스 수집 전용 스케줄러.
 * 기존 AutoCollectionScheduler와 독립적으로 동작하여 실패를 격리한다.
 * 카테고리 수집 5분 후에 실행된다.
 */
@Component
class CompetitorCollectionScheduler(
    private val competitorCollectionService: CompetitorCollectionService,
    private val competitorArticleSummarizationService: CompetitorArticleSummarizationService,
    private val metrics: ClippingMetrics
) {

    /**
     * 경쟁사 뉴스를 수집한다. 매일 오전 7:10, 오후 7:10 (KST 기준) 2회 실행한다.
     * 수집 간격이 12시간이므로 hoursBack=14로 버퍼를 넉넉히 잡는다.
     */
    @Scheduled(cron = "0 10 7,19 * * *")
    fun collectCompetitorNews() = metrics.recordSchedulerRun("competitor_collection") {
        log.info { "CompetitorCollectionScheduler started" }
        val start = System.nanoTime()
        // 모든 활성 경쟁사의 뉴스를 수집한다 (일 2회 수집, 12시간 간격 + 2시간 버퍼 = 14시간)
        val results = competitorCollectionService.collectAll(hoursBack = 14)
        val totalNew = results.sumOf { it.newItemCount }
        val totalErrors = results.count { !it.isSuccess }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        log.info { "Competitor collection completed in ${elapsed}ms: ${results.size} competitors, $totalNew new, $totalErrors errors" }

        // 수집 후 미요약 경쟁사 기사를 AI로 요약한다
        if (totalNew > 0) {
            runCatching {
                val summarized = competitorArticleSummarizationService.summarizeUnsummarized()
                log.info { "Competitor summarization completed: $summarized articles" }
            }.onFailure { e ->
                log.warn(e) { "Competitor summarization failed" }
            }
        }
    }
}
