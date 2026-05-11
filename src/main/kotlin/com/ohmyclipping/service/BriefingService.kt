package com.ohmyclipping.service

import com.ohmyclipping.service.dto.user.BriefingItem
import com.ohmyclipping.service.dto.user.BriefingListResponse
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.DailySummaryStore
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

/**
 * 오늘의 브리핑 데이터를 조회하는 서비스.
 * DailySummary와 Category를 조합하여 브리핑 목록을 반환한다.
 */
@Service
class BriefingService(
    private val dailySummaryStore: DailySummaryStore,
    private val categoryStore: CategoryStore
) {

    /**
     * 오늘 날짜의 브리핑 목록을 조회한다.
     * categoryId가 주어지면 해당 카테고리만 필터링한다.
     *
     * @param categoryId 카테고리 ID (null이면 전체)
     * @return 브리핑 목록 응답
     */
    fun getTodayBriefings(categoryId: String?): BriefingListResponse {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

        // 오늘 날짜의 일간 요약을 조회한다.
        val summaries = if (categoryId != null) {
            val summary = dailySummaryStore.findByCategoryAndDate(categoryId, today)
            if (summary != null) listOf(summary) else emptyList()
        } else {
            dailySummaryStore.findByDate(today)
        }

        // 카테고리 이름을 조회하여 매핑한다.
        val categoryMap = categoryStore.list().associateBy { it.id }

        val briefings = summaries.map { summary ->
            val category = categoryMap[summary.categoryId]
            BriefingItem(
                categoryId = summary.categoryId,
                categoryName = category?.name ?: "알 수 없음",
                summaryDate = summary.summaryDate.toString(),
                title = summary.title,
                overallSummary = summary.overallSummary,
                topicKeywords = summary.topicKeywords,
                totalItems = summary.totalItems
            )
        }

        return BriefingListResponse(briefings = briefings)
    }
}
