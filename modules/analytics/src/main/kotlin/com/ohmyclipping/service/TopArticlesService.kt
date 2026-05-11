package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.service.dto.TopArticleItem
import com.ohmyclipping.service.dto.TopArticlesResponse
import com.ohmyclipping.store.BatchSummaryStore
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

/**
 * 중요도 기준 상위 기사를 제공하는 서비스.
 * batch_summaries에서 importanceScore가 높은 기사를 조회한다.
 */
@Service
class TopArticlesService(private val batchSummaryStore: BatchSummaryStore) {

    /**
     * 지정 기간 내 중요도 상위 기사를 조회한다.
     * sentiment, eventType, keyword, date 필터를 선택적으로 적용한다.
     *
     * @param days 조회 기간 일수 (1~90 범위로 보정)
     * @param limit 최대 결과 수 (1~100 범위로 보정)
     * @param categoryId 카테고리 필터 (null이면 전체)
     * @param sentiment 논조 필터: POSITIVE/NEUTRAL/NEGATIVE (null이면 전체)
     * @param eventType 이벤트 유형 필터 (null이면 전체)
     * @param keyword 키워드 포함 필터 (null이면 전체)
     * @param date 특정 날짜 필터: YYYY-MM-DD (null이면 전체)
     * @return 중요도 순 정렬된 상위 기사 응답
     */
    fun getTopArticles(
        days: Int,
        limit: Int = 10,
        categoryId: String? = null,
        sentiment: String? = null,
        eventType: String? = null,
        keyword: String? = null,
        date: String? = null
    ): TopArticlesResponse {
        // 외부 요청값이 음수/과대 입력이어도 조회 기간이 깨지지 않도록 보정한다.
        val safeDays = days.coerceIn(1, 90)
        val zone = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(zone)
        val fromDate = today.minusDays(safeDays.toLong() - 1)

        return getTopArticlesByRange(
            fromDate = fromDate,
            toDate = today,
            limit = limit,
            categoryId = categoryId,
            sentiment = sentiment,
            eventType = eventType,
            keyword = keyword,
            date = date
        )
    }

    /**
     * 지정한 날짜 범위 내 중요도 상위 기사를 조회한다.
     * from/to 기반 월간 조회처럼 고정된 과거 기간을 조회할 때 사용한다.
     *
     * @param fromDate 시작 날짜 (포함)
     * @param toDate 종료 날짜 (포함)
     * @param limit 최대 결과 수 (1~100 범위로 보정)
     * @param categoryId 카테고리 필터 (null이면 전체)
     * @param sentiment 논조 필터: POSITIVE/NEUTRAL/NEGATIVE (null이면 전체)
     * @param eventType 이벤트 유형 필터 (null이면 전체)
     * @param keyword 키워드 포함 필터 (null이면 전체)
     * @param date 특정 날짜 필터: YYYY-MM-DD (null이면 전체)
     * @return 중요도 순 정렬된 상위 기사 응답
     */
    fun getTopArticlesByRange(
        fromDate: LocalDate,
        toDate: LocalDate,
        limit: Int = 10,
        categoryId: String? = null,
        sentiment: String? = null,
        eventType: String? = null,
        keyword: String? = null,
        date: String? = null
    ): TopArticlesResponse {
        if (fromDate.isAfter(toDate)) {
            throw InvalidInputException("fromDate must be before or equal to toDate")
        }
        val zone = ZoneId.of("Asia/Seoul")
        val targetDate = date?.let(LocalDate::parse)
        val effectiveFromDate = targetDate ?: fromDate
        val effectiveToDate = targetDate ?: toDate
        if (effectiveFromDate.isAfter(effectiveToDate)) {
            throw InvalidInputException("fromDate must be before or equal to toDate")
        }

        // 조건/정렬/limit을 DB에 위임해 긴 기간의 batch_summaries 전체 로딩을 피한다.
        val summaries = batchSummaryStore.findTopArticles(
            from = effectiveFromDate.atStartOfDay(zone).toInstant(),
            to = effectiveToDate.plusDays(1).atStartOfDay(zone).toInstant(),
            categoryId = categoryId,
            sentiment = sentiment,
            eventType = eventType,
            keyword = keyword,
            limit = limit.coerceIn(1, 100),
        )

        val topItems = summaries.map { summary ->
            TopArticleItem(
                summaryId = summary.id,
                title = summary.translatedTitle ?: summary.originalTitle,
                sourceLink = summary.sourceLink,
                importanceScore = summary.importanceScore,
                keywords = summary.keywords,
                sentiment = summary.sentiment,
                eventType = summary.eventType,
                createdAt = summary.createdAt.toString()
            )
        }

        return TopArticlesResponse(items = topItems)
    }
}
