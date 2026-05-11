package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.analytics.PersonaAnalyticsBackfillService
import com.clipping.mcpserver.service.analytics.PersonaAnalyticsService
import com.clipping.mcpserver.service.analytics.dto.BackfillResult
import com.clipping.mcpserver.service.analytics.dto.LiveSnapshotResponse
import com.clipping.mcpserver.service.analytics.dto.PersonaBatchRunResponse
import com.clipping.mcpserver.service.analytics.dto.SignalsResponse
import com.clipping.mcpserver.service.analytics.dto.WeeklyTrendsResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 페르소나 분석 관리자 API.
 *
 * /api/admin 하위 경로는 SecurityConfig 에서 ADMIN role 을 요구하므로 컨트롤러
 * 자체에 별도 어노테이션이 필요하지 않다.
 *
 * Slice 별 점진 확장:
 *   - Slice 1: GET /live (이 파일)
 *   - Slice 2: GET /trends, GET /batch-runs, POST /backfill 추가
 *   - Slice 3: GET /anomalies, POST /anomalies/{id}/resolve, POST /batch/run 추가
 *   - Slice 4: GET /custom-keywords 추가
 *   - Slice 5: GET /custom-clusters, GET /preset-candidates, POST /preset-candidates/{id}/(review/accept/reject) 추가
 */
@RestController
@RequestMapping("/api/admin/analytics/personas")
class PersonaAnalyticsAdminController(
    private val personaAnalyticsService: PersonaAnalyticsService,
    private val backfillService: PersonaAnalyticsBackfillService
) {

    /**
     * 실시간 페르소나 현황 스냅샷.
     * 5 분 캐시 (PersonaAnalyticsService 참고).
     */
    @GetMapping("/live")
    fun getLiveSnapshot(): LiveSnapshotResponse =
        personaAnalyticsService.getLiveSnapshot()

    /**
     * N주 기간의 페르소나별 주간 트렌드 시리즈.
     *
     * @param weeks 조회할 주 수 (기본 12, 1~52). 범위 외 입력 시 400 반환.
     */
    @GetMapping("/trends")
    fun getTrends(@RequestParam(defaultValue = "12") weeks: Int): WeeklyTrendsResponse =
        personaAnalyticsService.getWeeklyTrends(weeks)

    /**
     * 위험 + 성장 페르소나 신호. 운영 관점과 프로덕트 관점을 동시에 답한다.
     *
     * 5분 캐시. 주간 배치 완료 시 명시적으로 evict 된다.
     *
     * @param lookbackWeeks 유휴 판정·지속 주차 카운팅 범위 (기본 4, 1~12).
     *                      범위 초과 시 400 반환.
     */
    @GetMapping("/signals")
    fun getSignals(
        @RequestParam(defaultValue = "4") lookbackWeeks: Int
    ): SignalsResponse = personaAnalyticsService.getSignals(lookbackWeeks)

    /**
     * 과거 N주치 주간 스냅샷을 백필한다.
     *
     * principal 은 `SecurityContext` 에서 꺼내 서비스로 넘긴다. 예전처럼 쿼리
     * 파라미터로 받지 않는 이유는, 클라이언트가 임의 문자열을 넘겨 audit 로그에
     * 심을 수 있었기 때문이다 (PR #403 사후 리뷰 결과).
     *
     * @param weeks 처리할 주 수 (기본 12, 최대 52). 범위 초과 시 400 반환.
     * @return 처리 결과 (처리 주 수, 페르소나 수, 생성된 행 수, 소요 시간)
     */
    @PostMapping("/backfill")
    fun runBackfill(
        @RequestParam(defaultValue = "12") weeks: Int
    ): BackfillResult {
        val principal = org.springframework.security.core.context.SecurityContextHolder
            .getContext().authentication?.name
        return backfillService.backfill(weeks, principal)
    }

    /**
     * 최근 배치 실행 이력을 조회한다.
     *
     * @param limit 반환할 최대 건수 (기본 10)
     * @return started_at DESC 정렬된 배치 실행 목록
     */
    @GetMapping("/batch-runs")
    fun getBatchRuns(
        @RequestParam(defaultValue = "10") limit: Int
    ): List<PersonaBatchRunResponse> = personaAnalyticsService.getRecentBatchRuns(limit)
}
