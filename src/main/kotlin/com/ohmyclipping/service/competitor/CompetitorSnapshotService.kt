package com.ohmyclipping.service.competitor

import com.ohmyclipping.service.dto.CompetitorSnapshotItem
import com.ohmyclipping.service.dto.CompetitorSnapshotResponse
import com.ohmyclipping.store.CompetitorWatchlistStore
import com.ohmyclipping.store.SummaryKeywordLookupStore
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

/**
 * 경쟁사 관련 기사 스냅샷을 제공하는 서비스.
 * CompetitorWatchlistStore에서 활성 경쟁사 키워드를 읽고,
 * BatchSummary에서 SQL 수준 키워드 매칭으로 기사를 검색한다.
 */
@Service
class CompetitorSnapshotService(
    private val summaryKeywordLookupStore: SummaryKeywordLookupStore,
    private val competitorWatchlistStore: CompetitorWatchlistStore
) {

    /**
     * 경쟁사 기사 스냅샷을 조회한다.
     * 각 경쟁사의 키워드로 SQL 수준 검색을 수행하고,
     * 중복 기사는 가장 높은 importanceScore를 기준으로 유지한다.
     *
     * @param days 조회 기간 일수 (1~365 범위로 보정)
     * @param limit 최대 결과 수 (1~100 범위로 보정)
     * @return 경쟁사 스냅샷 응답
     */
    fun getSnapshot(days: Int, limit: Int): CompetitorSnapshotResponse {
        // 외부 요청값이 음수/과대 입력이어도 SQL LIMIT 과 take()가 깨지지 않도록 보정한다.
        val safeDays = days.coerceIn(1, 365)
        val safeLimit = limit.coerceIn(1, 100)
        // 활성 경쟁사에서 키워드 맵을 구성한다.
        val competitors = competitorWatchlistStore.findActive()
        if (competitors.isEmpty()) {
            return CompetitorSnapshotResponse(items = emptyList())
        }

        val zone = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(zone)
        val fromDate = today.minusDays(safeDays.toLong() - 1)
        val fromInstant = fromDate.atStartOfDay(zone).toInstant()
        val toInstant = today.plusDays(1).atStartOfDay(zone).toInstant()

        // 모든 경쟁사 키워드를 하나의 쿼리로 통합해 N+1을 방지한다.
        val allKeywords = competitors.flatMap { listOf(it.name) + it.aliases }.distinct()
        if (allKeywords.isEmpty()) {
            return CompetitorSnapshotResponse(items = emptyList())
        }

        // 키워드→경쟁사명 매핑을 구성한다 (중복 키워드는 첫 번째 경쟁사 우선).
        val keywordToCompetitor = mutableMapOf<String, String>()
        for (competitor in competitors) {
            for (kw in listOf(competitor.name) + competitor.aliases) {
                keywordToCompetitor.putIfAbsent(kw.lowercase(), competitor.name)
            }
        }

        // 전체 키워드로 단일 쿼리 검색을 수행한다.
        val matched = summaryKeywordLookupStore.findByKeywordsInRange(
            from = fromInstant,
            to = toInstant,
            keywords = allKeywords,
            orderByImportance = true,
            limit = safeLimit * competitors.size
        )

        // 결과를 경쟁사별로 매핑한다.
        val itemMap = mutableMapOf<String, CompetitorSnapshotItem>()
        for (summary in matched) {
            val searchText = listOfNotNull(
                summary.originalTitle, summary.translatedTitle, summary.summary
            ).joinToString(" ").lowercase()

            // 매칭된 키워드에서 경쟁사명을 결정한다.
            val competitorName = keywordToCompetitor.entries
                .firstOrNull { (kw, _) -> searchText.contains(kw) }
                ?.value ?: continue

            val existing = itemMap[summary.id]
            // 중복 기사는 더 높은 importanceScore를 가진 것을 유지한다.
            if (existing == null || summary.importanceScore > existing.importanceScore) {
                itemMap[summary.id] = CompetitorSnapshotItem(
                    summaryId = summary.id,
                    competitorName = competitorName,
                    title = summary.translatedTitle ?: summary.originalTitle,
                    sourceLink = summary.sourceLink,
                    importanceScore = summary.importanceScore,
                    createdAt = summary.createdAt.toString()
                )
            }
        }

        // 중요도 순으로 정렬하고 제한 수만큼 반환한다.
        val topItems = itemMap.values
            .sortedByDescending { it.importanceScore }
            .take(safeLimit)

        return CompetitorSnapshotResponse(items = topItems)
    }
}
