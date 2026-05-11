package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.ClippingStat
import com.clipping.mcpserver.service.port.CollectionStatsPort
import com.clipping.mcpserver.store.StatsStore
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Service
class StatsService(private val statsStore: StatsStore) : CollectionStatsPort {

    override fun recordCollection(categoryId: String, itemsCollected: Int, itemsDuplicates: Int) {
        statsStore.upsert(
            ClippingStat(
                id = "",
                categoryId = categoryId,
                statDate = LocalDate.now(ZoneId.of("Asia/Seoul")),
                itemsCollected = itemsCollected,
                itemsDuplicates = itemsDuplicates
            )
        )
    }

    fun recordSummarization(categoryId: String, itemsSummarized: Int, keywords: List<String>, avgScore: Float) {
        statsStore.upsert(
            ClippingStat(
                id = "",
                categoryId = categoryId,
                statDate = LocalDate.now(ZoneId.of("Asia/Seoul")),
                itemsSummarized = itemsSummarized,
                topKeywords = keywords,
                avgImportanceScore = avgScore
            )
        )
    }

    fun recordSent(categoryId: String, itemsSent: Int) {
        statsStore.upsert(
            ClippingStat(
                id = "",
                categoryId = categoryId,
                statDate = LocalDate.now(ZoneId.of("Asia/Seoul")),
                itemsSent = itemsSent
            )
        )
    }

    fun recordDigestDelivery(categoryId: String, sendAttempts: Int, sendSuccesses: Int) {
        statsStore.upsert(
            ClippingStat(
                id = "",
                categoryId = categoryId,
                statDate = LocalDate.now(ZoneId.of("Asia/Seoul")),
                slackSendAttempts = sendAttempts.coerceAtLeast(0),
                slackSendSuccesses = sendSuccesses.coerceAtLeast(0)
            )
        )
    }

    fun getMonthlyStats(categoryId: String?, yearMonth: YearMonth): List<ClippingStat> =
        statsStore.findMonthly(categoryId, yearMonth)

    /**
     * 날짜 범위로 일별 통계를 조회한다.
     * categoryId가 null이면 전체 카테고리 통계를 반환한다.
     */
    fun getStatsByRange(categoryId: String?, from: LocalDate, to: LocalDate): List<ClippingStat> =
        statsStore.findDailyRange(categoryId, from, to)
}
