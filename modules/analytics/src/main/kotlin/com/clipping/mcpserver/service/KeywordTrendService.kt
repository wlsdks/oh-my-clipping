package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.dto.KeywordDailyCount
import com.clipping.mcpserver.service.dto.KeywordTrendItem
import com.clipping.mcpserver.service.dto.KeywordTrendPeriod
import com.clipping.mcpserver.service.dto.KeywordTrendResponse
import com.clipping.mcpserver.store.BatchSummaryStore
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

/**
 * 키워드 트렌드 데이터를 집계하는 서비스.
 * BatchSummary의 키워드를 날짜별로 집계하고 변화율을 계산한다.
 */
@Service
class KeywordTrendService(
    private val batchSummaryStore: BatchSummaryStore
) {

    /**
     * 지정 기간의 키워드 트렌드를 조회한다.
     *
     * @param days 조회 기간 일수 (1~90 범위로 보정)
     * @param top 상위 키워드 개수 (1~50 범위로 보정)
     * @param categoryId 카테고리 ID (null이면 전체)
     * @return 키워드 트렌드 응답
     */
    fun getKeywordTrend(days: Int, top: Int, categoryId: String?): KeywordTrendResponse {
        // 외부 요청값이 음수/과대 입력이어도 날짜축과 take()가 깨지지 않도록 보정한다.
        val safeDays = days.coerceIn(1, 90)
        val safeTop = top.coerceIn(1, 50)
        val zone = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(zone)
        val fromDate = today.minusDays(safeDays.toLong() - 1)

        // 날짜 범위를 Instant로 변환하여 조회한다.
        val fromInstant = fromDate.atStartOfDay(zone).toInstant()
        val toInstant = today.plusDays(1).atStartOfDay(zone).toInstant()

        val summaries = batchSummaryStore.findByDateRange(fromInstant, toInstant, categoryId)

        // 키워드를 날짜별로 집계한다.
        val keywordDateCounts = mutableMapOf<String, MutableMap<LocalDate, Int>>()
        for (summary in summaries) {
            val date = summary.createdAt.atZone(zone).toLocalDate()
            for (keyword in summary.keywords) {
                val normalized = keyword.trim().lowercase()
                if (normalized.isBlank()) continue
                keywordDateCounts
                    .getOrPut(normalized) { mutableMapOf() }
                    .merge(date, 1, Int::plus)
            }
        }

        // 전체 기간의 날짜 목록을 생성한다.
        val allDates = (0L until safeDays).map { fromDate.plusDays(it) }

        // 총 건수 기준 상위 키워드를 선정한다.
        val topKeywords = keywordDateCounts.entries
            .map { (keyword, dateCounts) -> keyword to dateCounts.values.sum() }
            .sortedByDescending { it.second }
            .take(safeTop)

        val keywords = topKeywords.map { (keyword, totalCount) ->
            val dateCounts = keywordDateCounts[keyword] ?: emptyMap()

            val dailyCounts = allDates.map { date ->
                KeywordDailyCount(
                    date = date.toString(),
                    count = dateCounts[date] ?: 0
                )
            }

            // 변화율 계산: 후반부 평균 vs 전반부 평균
            val changeRate = calculateChangeRate(dailyCounts)

            KeywordTrendItem(
                keyword = keyword,
                dailyCounts = dailyCounts,
                totalCount = totalCount,
                changeRate = changeRate
            )
        }

        return KeywordTrendResponse(
            period = KeywordTrendPeriod(
                from = fromDate.toString(),
                to = today.toString()
            ),
            keywords = keywords
        )
    }

    /**
     * 지정된 날짜 범위의 키워드 트렌드를 조회한다.
     * 월 단위 리포트에서 정확한 시작/종료일을 지정할 때 사용한다.
     *
     * @param fromDate 시작 날짜 (포함)
     * @param toDate 종료 날짜 (포함)
     * @param top 상위 키워드 개수 (1~50 범위로 보정)
     * @param categoryId 카테고리 ID (null이면 전체)
     */
    fun getKeywordTrendByRange(
        fromDate: LocalDate,
        toDate: LocalDate,
        top: Int,
        categoryId: String?
    ): KeywordTrendResponse {
        // 내부 호출에서도 잘못된 top 값으로 take()가 실패하지 않도록 보정한다.
        val safeTop = top.coerceIn(1, 50)
        val zone = ZoneId.of("Asia/Seoul")
        val fromInstant = fromDate.atStartOfDay(zone).toInstant()
        val toInstant = toDate.plusDays(1).atStartOfDay(zone).toInstant()

        val summaries = batchSummaryStore.findByDateRange(fromInstant, toInstant, categoryId)

        // 키워드를 날짜별로 집계한다.
        val keywordDateCounts = mutableMapOf<String, MutableMap<LocalDate, Int>>()
        for (summary in summaries) {
            val date = summary.createdAt.atZone(zone).toLocalDate()
            for (keyword in summary.keywords) {
                val normalized = keyword.trim().lowercase()
                if (normalized.isBlank()) continue
                keywordDateCounts
                    .getOrPut(normalized) { mutableMapOf() }
                    .merge(date, 1, Int::plus)
            }
        }

        // 전체 기간의 날짜 목록을 생성한다.
        val dayCount = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1
        val allDates = (0L until dayCount).map { fromDate.plusDays(it) }

        // 총 건수 기준 상위 키워드를 선정한다.
        val topKeywords = keywordDateCounts.entries
            .map { (keyword, dateCounts) -> keyword to dateCounts.values.sum() }
            .sortedByDescending { it.second }
            .take(safeTop)

        val keywords = topKeywords.map { (keyword, totalCount) ->
            val dateCounts = keywordDateCounts[keyword] ?: emptyMap()
            val dailyCounts = allDates.map { date ->
                KeywordDailyCount(
                    date = date.toString(),
                    count = dateCounts[date] ?: 0
                )
            }
            val changeRate = calculateChangeRate(dailyCounts)
            KeywordTrendItem(
                keyword = keyword,
                dailyCounts = dailyCounts,
                totalCount = totalCount,
                changeRate = changeRate
            )
        }

        return KeywordTrendResponse(
            period = KeywordTrendPeriod(
                from = fromDate.toString(),
                to = toDate.toString()
            ),
            keywords = keywords
        )
    }

    /**
     * 기간의 전반부와 후반부를 비교하여 변화율을 계산한다.
     * 전반부가 0이면 후반부가 있을 경우 1.0, 없으면 0.0을 반환한다.
     */
    private fun calculateChangeRate(dailyCounts: List<KeywordDailyCount>): Double {
        val mid = dailyCounts.size / 2
        if (mid == 0) return 0.0

        val firstHalf = dailyCounts.subList(0, mid)
        val secondHalf = dailyCounts.subList(mid, dailyCounts.size)

        val firstAvg = firstHalf.map { it.count }.average()
        val secondAvg = secondHalf.map { it.count }.average()

        return if (firstAvg == 0.0) {
            if (secondAvg > 0.0) 1.0 else 0.0
        } else {
            ((secondAvg - firstAvg) / firstAvg).let {
                Math.round(it * 100.0) / 100.0
            }
        }
    }
}
