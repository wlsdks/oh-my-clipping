package com.ohmyclipping.service

import com.ohmyclipping.service.dto.analytics.KeywordTrendPeriod
import com.ohmyclipping.service.dto.analytics.SentimentDailyCount
import com.ohmyclipping.service.dto.analytics.SentimentSummary
import com.ohmyclipping.service.dto.analytics.SentimentTrendResponse
import com.ohmyclipping.store.BatchSummaryStore
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

/**
 * 논조(감성) 추이 데이터를 집계하는 서비스.
 * BatchSummary의 sentiment 필드를 날짜별로 그룹화하여 긍정/중립/부정 건수를 계산한다.
 */
@Service
class SentimentTrendService(
    private val batchSummaryStore: BatchSummaryStore
) {

    /**
     * 지정 기간의 논조 추이를 조회한다.
     *
     * @param days 조회 기간 일수 (1~90 범위로 보정)
     * @param categoryId 카테고리 ID (null이면 전체)
     * @return 일별 논조 건수 및 요약 통계
     */
    fun getSentimentTrend(days: Int, categoryId: String?): SentimentTrendResponse {
        // 외부 요청값이 음수/과대 입력이어도 날짜축이 깨지지 않도록 보정한다.
        val safeDays = days.coerceIn(1, 90)
        val zone = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(zone)
        val fromDate = today.minusDays(safeDays.toLong() - 1)

        // 날짜 범위를 Instant로 변환하여 조회한다.
        val fromInstant = fromDate.atStartOfDay(zone).toInstant()
        val toInstant = today.plusDays(1).atStartOfDay(zone).toInstant()

        val summaries = batchSummaryStore.findByDateRange(fromInstant, toInstant, categoryId)

        // sentiment가 null인 기사는 제외하고 날짜별로 그룹화한다.
        val grouped = summaries
            .filter { it.sentiment != null }
            .groupBy { it.createdAt.atZone(zone).toLocalDate() }

        // 전체 기간의 날짜 목록을 생성한다.
        val allDates = (0L until safeDays).map { fromDate.plusDays(it) }

        val dailyCounts = buildDailyCounts(allDates, grouped)
        val summary = buildSummary(dailyCounts)

        return SentimentTrendResponse(
            period = KeywordTrendPeriod(
                from = fromDate.toString(),
                to = today.toString()
            ),
            daily = dailyCounts,
            summary = summary,
        )
    }

    /**
     * 날짜 목록에 대해 일별 논조 건수를 생성한다.
     * 데이터가 없는 날짜는 모두 0으로 채운다.
     */
    private fun buildDailyCounts(
        allDates: List<LocalDate>,
        grouped: Map<LocalDate, List<com.ohmyclipping.model.BatchSummary>>
    ): List<SentimentDailyCount> = allDates.map { date ->
        val items = grouped[date] ?: emptyList()
        SentimentDailyCount(
            date = date.toString(),
            positive = items.count { it.sentiment == "POSITIVE" },
            neutral = items.count { it.sentiment == "NEUTRAL" },
            negative = items.count { it.sentiment == "NEGATIVE" },
            total = items.size,
        )
    }

    /**
     * 일별 건수로부터 전체 기간 요약(비율, 지배적 논조, 전반기 대비 변화)을 계산한다.
     * 데이터가 없으면 모든 비율은 0.0, dominantSentiment는 null을 반환한다.
     */
    private fun buildSummary(dailyCounts: List<SentimentDailyCount>): SentimentSummary {
        val totalPositive = dailyCounts.sumOf { it.positive }
        val totalNeutral = dailyCounts.sumOf { it.neutral }
        val totalNegative = dailyCounts.sumOf { it.negative }
        val grandTotal = totalPositive + totalNeutral + totalNegative

        if (grandTotal == 0) {
            return SentimentSummary(0.0, 0.0, 0.0, null, 0.0)
        }

        val posRate = totalPositive.toDouble() / grandTotal
        val neuRate = totalNeutral.toDouble() / grandTotal
        val negRate = totalNegative.toDouble() / grandTotal

        // 가장 높은 비율의 논조를 지배적 논조로 판정한다.
        val dominant = when {
            posRate >= neuRate && posRate >= negRate -> "POSITIVE"
            neuRate >= posRate && neuRate >= negRate -> "NEUTRAL"
            else -> "NEGATIVE"
        }

        // 전반부 대비 후반부의 긍정 비율 변화를 계산한다.
        val changeFromPrevious = calculatePositiveRateChange(dailyCounts)

        return SentimentSummary(posRate, neuRate, negRate, dominant, changeFromPrevious)
    }

    /**
     * 기간의 전반부와 후반부를 비교하여 긍정 비율 변화를 계산한다.
     * 전반부 긍정 비율이 0이면 후반부에 긍정이 있으면 1.0, 없으면 0.0을 반환한다.
     */
    private fun calculatePositiveRateChange(dailyCounts: List<SentimentDailyCount>): Double {
        val mid = dailyCounts.size / 2
        if (mid == 0) return 0.0

        val firstHalf = dailyCounts.subList(0, mid)
        val secondHalf = dailyCounts.subList(mid, dailyCounts.size)

        val firstTotal = firstHalf.sumOf { it.total }
        val secondTotal = secondHalf.sumOf { it.total }

        val firstPosRate = if (firstTotal > 0) firstHalf.sumOf { it.positive }.toDouble() / firstTotal else 0.0
        val secondPosRate = if (secondTotal > 0) secondHalf.sumOf { it.positive }.toDouble() / secondTotal else 0.0

        return if (firstPosRate == 0.0) {
            if (secondPosRate > 0.0) 1.0 else 0.0
        } else {
            Math.round((secondPosRate - firstPosRate) * 100.0) / 100.0
        }
    }
}
