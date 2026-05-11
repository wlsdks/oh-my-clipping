package com.ohmyclipping.admin

import com.ohmyclipping.service.competitor.CompetitorSnapshotService
import com.ohmyclipping.service.competitor.CompetitorWatchlistService
import com.ohmyclipping.service.dto.user.ArticleDetailView
import com.ohmyclipping.service.dto.analytics.CompetitorSnapshotResponse
import com.ohmyclipping.service.dto.analytics.CompetitorTimelineResponse
import com.ohmyclipping.service.dto.analytics.SovResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자용 경쟁사 정보 조회 API (읽기 전용).
 * 일반 사용자(ROLE_USER)가 경쟁사 스냅샷, 타임라인, Share of Voice를 조회할 수 있다.
 * CRUD 기능은 제공하지 않는다.
 */
@RestController
@RequestMapping("/api/user/competitors")
class UserCompetitorViewController(
    private val competitorSnapshotService: CompetitorSnapshotService,
    private val competitorWatchlistService: CompetitorWatchlistService
) {

    /**
     * 경쟁사 스냅샷을 조회한다.
     *
     * @param days 조회 기간 일수 (기본 7)
     * @param limit 반환 개수 제한 (기본 5)
     */
    @GetMapping("/snapshot")
    fun getSnapshot(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "5") limit: Int
    ): CompetitorSnapshotResponse = competitorSnapshotService.getSnapshot(days, limit)

    /**
     * 경쟁사 타임라인을 조회한다.
     *
     * @param days 조회 기간 일수 (기본 30)
     * @param competitorId 특정 경쟁사 ID (null이면 전체)
     * @param eventType 이벤트 유형 필터 (null이면 전체)
     */
    @GetMapping("/timeline")
    fun getTimeline(
        @RequestParam(defaultValue = "30") days: Int,
        @RequestParam(required = false) competitorId: String?,
        @RequestParam(required = false) eventType: String?
    ): CompetitorTimelineResponse =
        competitorWatchlistService.getTimeline(days, competitorId, eventType)

    /**
     * Share of Voice 데이터를 조회한다.
     *
     * @param days 조회 기간 일수 (기본 30)
     */
    @GetMapping("/sov")
    fun getShareOfVoice(
        @RequestParam(defaultValue = "30") days: Int
    ): SovResponse = competitorWatchlistService.getShareOfVoice(days)

    /**
     * 경쟁사 기사 상세 정보를 조회한다.
     * 카테고리 접근 제한 없이 summaryId로 직접 조회한다.
     * 현재 사용자의 북마크 상태를 포함해 반환한다.
     *
     * @param summaryId 기사(BatchSummary) ID
     * @param authentication 현재 인증 정보 (userId 추출용)
     */
    @GetMapping("/articles/{summaryId}")
    fun getArticleDetail(
        @PathVariable summaryId: String,
        authentication: Authentication
    ): ResponseEntity<ArticleDetailView> {
        val detail = competitorWatchlistService
            .getCompetitorArticleDetail(summaryId, authentication.name)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail)
    }
}
