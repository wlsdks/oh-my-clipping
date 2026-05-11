package com.ohmyclipping.service.competitor

import com.ohmyclipping.store.BatchSummaryCompetitorStore
import com.ohmyclipping.store.CompetitorWatchlistStore
import com.ohmyclipping.store.SummaryKeywordLookupStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger {}

/**
 * 기존 batch_summaries에서 경쟁사 키워드 매칭을 수행하여
 * batch_summary_competitors junction 테이블을 초기화한다.
 * 배포 직후 한 번 실행하여 기존 데이터를 backfill한다.
 */
@Service
class CompetitorBackfillService(
    private val watchlistStore: CompetitorWatchlistStore,
    private val summaryKeywordLookupStore: SummaryKeywordLookupStore,
    private val junctionStore: BatchSummaryCompetitorStore
) {
    /**
     * 최근 N일간의 기사를 스캔하여 경쟁사 키워드 매칭 결과를 junction에 저장한다.
     *
     * @param days 스캔 대상 기간 (기본 90일)
     * @return 생성된 junction 레코드 수
     */
    fun backfill(days: Int = 90): Int {
        val competitors = watchlistStore.findActive()
        if (competitors.isEmpty()) {
            log.info { "No active competitors — backfill skipped" }
            return 0
        }

        val allKeywords = competitors.flatMap { listOf(it.name) + it.aliases }
        val from = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val to = Instant.now()

        // 키워드 매칭으로 관련 기사 조회
        val summaries = summaryKeywordLookupStore.findByKeywordsInRange(
            from = from, to = to, keywords = allKeywords,
            orderByImportance = false, limit = 500
        )

        var linked = 0
        for (summary in summaries) {
            // 각 기사에 대해 매칭되는 경쟁사를 모두 찾아 junction에 연결
            val searchText = listOfNotNull(
                summary.originalTitle, summary.translatedTitle, summary.summary
            ).joinToString(" ").lowercase()

            for (competitor in competitors) {
                val searchTerms = listOf(competitor.name) + competitor.aliases
                val matches = searchTerms.any { keyword ->
                    searchText.contains(keyword.lowercase())
                }
                if (matches) {
                    junctionStore.link(summary.id, competitor.id)
                    linked++
                }
            }
        }

        log.info { "Backfill completed: scanned ${summaries.size} summaries, created $linked junction links" }
        return linked
    }
}
