package com.ohmyclipping.admin

import com.ohmyclipping.service.dto.analytics.ArticleRankingResponse
import com.ohmyclipping.service.dto.analytics.CategoryStatsResponse
import com.ohmyclipping.service.dto.analytics.ClickRateSummaryResponse
import com.ohmyclipping.service.dto.analytics.DauResponse
import com.ohmyclipping.service.dto.analytics.DeliveryMatrixResponse
import com.ohmyclipping.service.dto.analytics.PipelineDailyResponse
import com.ohmyclipping.service.dto.analytics.PipelineSummaryResponse
import com.ohmyclipping.service.dto.analytics.WizardFunnelResponse
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.service.AnalyticsService
import com.ohmyclipping.service.pipeline.PipelineAnalyticsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 유저 행동 분석 API를 제공하는 관리자 컨트롤러.
 * DAU, 위자드 퍼널, 기사 랭킹, 카테고리 통계를 조회한다.
 *
 * 모든 엔드포인트는 `from`/`to` ISO 날짜 문자열 또는 `days` 정수로 기간을 지정할 수 있다.
 * - `from`과 `to`를 모두 지정하면 해당 기간을 사용한다 (반개구간 `[from 00:00, to+1일 00:00)`).
 * - 하나만 지정하면 400 에러를 반환한다.
 * - 둘 다 없으면 `days` 파라미터로 폴백한다.
 * - raw 행동 이벤트 보존 기간과 맞추기 위해 최대 조회 범위는 90일이다.
 */
@RestController
@RequestMapping("/api/admin/analytics")
class AnalyticsAdminController(
    private val analyticsService: AnalyticsService,
    private val pipelineAnalyticsService: PipelineAnalyticsService
) {

    /**
     * 일별 활성 사용자 수(DAU)를 조회한다.
     */
    @GetMapping("/dau")
    fun dau(
        @RequestParam(defaultValue = "7") days: Int?,
        @RequestParam from: String? = null,
        @RequestParam to: String? = null
    ): DauResponse {
        val (start, end) = dateRange(days, from, to)
        return analyticsService.getDau(start, end)
    }

    /**
     * 위자드 퍼널 분석 데이터를 조회한다.
     */
    @GetMapping("/wizard-funnel")
    fun wizardFunnel(
        @RequestParam(defaultValue = "30") days: Int?,
        @RequestParam from: String? = null,
        @RequestParam to: String? = null
    ): WizardFunnelResponse {
        val (start, end) = dateRange(days, from, to)
        return analyticsService.getWizardFunnel(start, end)
    }

    /**
     * 기사 랭킹을 조회한다.
     * 클릭 수, CTR, 북마크 수 기준으로 정렬할 수 있다.
     */
    @GetMapping("/article-ranking")
    fun articleRanking(
        @RequestParam(defaultValue = "30") days: Int?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "clicks") sort: String,
        @RequestParam from: String? = null,
        @RequestParam to: String? = null
    ): ArticleRankingResponse {
        val (start, end) = dateRange(days, from, to)
        // 무한 조회를 방지하기 위해 limit을 1~100으로 클램핑한다.
        val safeLimit = limit.coerceIn(1, 100)
        return analyticsService.getArticleRanking(start, end, sort, safeLimit)
    }

    /**
     * 기사 클릭률 요약을 조회한다.
     * 기간 내 총 클릭 수, 발송 건수, 클릭률(%)을 반환한다.
     */
    @GetMapping("/click-rate")
    fun clickRateSummary(
        @RequestParam(defaultValue = "7") days: Int?,
        @RequestParam from: String? = null,
        @RequestParam to: String? = null
    ): ClickRateSummaryResponse {
        val (start, end) = dateRange(days, from, to)
        return analyticsService.getClickRateSummary(start, end)
    }

    /**
     * 카테고리별 클릭/노출/CTR/점유율 통계를 조회한다.
     */
    @GetMapping("/category-stats")
    fun categoryStats(
        @RequestParam(defaultValue = "30") days: Int?,
        @RequestParam from: String? = null,
        @RequestParam to: String? = null
    ): CategoryStatsResponse {
        val (start, end) = dateRange(days, from, to)
        return analyticsService.getCategoryStats(start, end)
    }

    // ── 파이프라인 분석 엔드포인트 ──

    /**
     * 오늘(KST) 파이프라인 전체 요약을 반환한다.
     * 수집/중복/요약/거절/실패/발송/비용을 한 번에 조회한다.
     */
    @GetMapping("/pipeline-summary")
    fun pipelineSummary(): PipelineSummaryResponse =
        pipelineAnalyticsService.getPipelineSummary()

    /**
     * 최근 N일간 일별 파이프라인 추이와 거절 사유 분포를 반환한다.
     *
     * @param days 조회 기간 (기본 7일, 1~30일 범위로 클램핑)
     */
    @GetMapping("/pipeline-daily")
    fun pipelineDaily(@RequestParam(defaultValue = "7") days: Int): PipelineDailyResponse =
        pipelineAnalyticsService.getPipelineDaily(days.coerceIn(1, 30))

    /**
     * 최근 N일간 사용자-카테고리별 발송 매트릭스를 반환한다.
     *
     * @param days 조회 기간 (기본 1일, 1~30일 범위로 클램핑)
     */
    @GetMapping("/delivery-matrix")
    fun deliveryMatrix(@RequestParam(defaultValue = "1") days: Int): DeliveryMatrixResponse =
        pipelineAnalyticsService.getDeliveryMatrix(days.coerceIn(1, 30))

    /**
     * from/to ISO 날짜 문자열 또는 days 파라미터로부터 (from, to) Instant 쌍을 생성한다.
     *
     * @param days 기간(일 수). from/to가 없을 때 폴백으로 사용된다.
     * @param from 시작 날짜 (ISO 형식, 예: "2026-03-03"). to와 함께 지정해야 한다.
     * @param to 종료 날짜 (ISO 형식, 예: "2026-03-09"). from과 함께 지정해야 한다.
     * @return 반개구간 [from 00:00 KST, to+1일 00:00 KST) 또는 [now-days, now)
     */
    private fun dateRange(
        days: Int?,
        from: String?,
        to: String?
    ): Pair<Instant, Instant> {
        // from과 to가 모두 제공된 경우 ISO 날짜를 파싱하여 KST 기준 반개구간을 생성한다.
        if (from != null && to != null) {
            val zoneId = ZoneId.of("Asia/Seoul")
            val fromDate = LocalDate.parse(from)
            val toDate = LocalDate.parse(to)
            ensureValid(!fromDate.isAfter(toDate)) {
                "from은 to보다 이후일 수 없습니다."
            }
            val inclusiveDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1
            ensureValid(inclusiveDays in 1..90) { "조회 기간은 1~90일 이내여야 합니다." }
            val fromInstant = fromDate.atStartOfDay(zoneId).toInstant()
            val toInstant = toDate.plusDays(1).atStartOfDay(zoneId).toInstant()
            return fromInstant to toInstant
        }
        // from 또는 to 중 하나만 제공된 경우 400 에러를 반환한다.
        if (from != null || to != null) {
            ensureValid(false) { "from과 to를 모두 지정해야 합니다." }
        }
        // from/to가 없으면 days 파라미터로 폴백한다.
        val d = days ?: 7
        ensureValid(d in 1..90) { "days는 1~90 사이여야 합니다." }
        val now = Instant.now()
        return now.minus(d.toLong(), ChronoUnit.DAYS) to now
    }
}
