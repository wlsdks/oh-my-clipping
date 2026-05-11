package com.ohmyclipping.admin

import com.ohmyclipping.service.KeywordTrendService
import com.ohmyclipping.service.dto.analytics.KeywordTrendResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 사용자용 키워드 트렌드 조회 API.
 * 일반 사용자(ROLE_USER)가 키워드 트렌드 데이터를 조회할 수 있다.
 * from/to 파라미터로 월 단위 정확한 기간을 지정할 수 있다.
 */
@RestController
@RequestMapping("/api/user/keywords")
class UserKeywordTrendController(
    private val keywordTrendService: KeywordTrendService
) {

    /**
     * 키워드 트렌드를 조회한다.
     *
     * @param from 시작 날짜 (YYYY-MM-DD, 지정 시 days 무시)
     * @param to 종료 날짜 (YYYY-MM-DD, 지정 시 days 무시)
     * @param days 조회 기간 일수 (from/to 미지정 시 사용, 기본 7)
     * @param top 상위 키워드 개수 (기본 10)
     * @param categoryId 카테고리 ID (null이면 전체)
     */
    @GetMapping("/trend")
    fun getKeywordTrend(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "10") top: Int,
        @RequestParam(required = false) categoryId: String?
    ): KeywordTrendResponse {
        // from/to가 지정되면 해당 기간으로 조회한다.
        if (from != null && to != null) {
            val fromDate = LocalDate.parse(from)
            val toDate = LocalDate.parse(to)
            return keywordTrendService.getKeywordTrendByRange(fromDate, toDate, top, categoryId)
        }
        return keywordTrendService.getKeywordTrend(days, top, categoryId)
    }
}
