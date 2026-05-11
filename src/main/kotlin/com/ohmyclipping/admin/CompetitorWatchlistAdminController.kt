package com.ohmyclipping.admin

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.admin.dto.CreateCompetitorRequest
import com.ohmyclipping.admin.dto.UpdateCompetitorRequest
import com.ohmyclipping.service.competitor.CompetitorArticleSummarizationService
import com.ohmyclipping.service.competitor.CompetitorSnapshotService
import com.ohmyclipping.service.competitor.CompetitorWatchlistService
import com.ohmyclipping.service.competitor.CompetitorCollectionService
import com.ohmyclipping.service.dto.analytics.CompetitorResponse
import com.ohmyclipping.service.dto.analytics.CompetitorSentimentResponse
import com.ohmyclipping.service.dto.analytics.CompetitorSnapshotResponse
import com.ohmyclipping.service.dto.analytics.CompetitorTimelineResponse
import com.ohmyclipping.service.dto.analytics.KeywordPreviewRequest
import com.ohmyclipping.service.dto.analytics.KeywordPreviewResponse
import com.ohmyclipping.service.dto.analytics.RssFeedInput
import com.ohmyclipping.service.dto.analytics.SovResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 경쟁사 워치리스트 관리 API.
 * CRUD, 타임라인, Share of Voice, 키워드 프리뷰 엔드포인트를 제공한다.
 */
@RestController
@RequestMapping("/api/admin/competitors")
class CompetitorWatchlistAdminController(
    private val competitorWatchlistService: CompetitorWatchlistService,
    private val competitorSnapshotService: CompetitorSnapshotService,
    private val competitorCollectionService: CompetitorCollectionService,
    private val competitorArticleSummarizationService: CompetitorArticleSummarizationService
) {

    companion object {
        private const val MAX_COMPETITORS = 20
        private const val MAX_ALIASES = 5
        private const val MAX_EXCLUDE_KEYWORDS = 10
        private const val MAX_RSS_FEEDS = 5
        private const val MAX_NAME_LENGTH = 50
        private const val MAX_KEYWORD_LENGTH = 30
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateCompetitorRequest): CompetitorResponse {
        // 등록 상한 검증
        val currentCount = competitorWatchlistService.list().size
        if (currentCount >= MAX_COMPETITORS) {
            throw InvalidInputException("경쟁사는 최대 ${MAX_COMPETITORS}개까지 등록할 수 있습니다")
        }
        validateCompetitorInput(request.name, request.aliases, request.excludeKeywords, request.rssFeeds)
        return competitorWatchlistService.create(
            name = request.name,
            aliases = request.aliases,
            excludeKeywords = request.excludeKeywords,
            tier = request.tier,
            rssFeeds = request.rssFeeds
        )
    }

    @GetMapping
    fun list(): List<CompetitorResponse> =
        competitorWatchlistService.list()

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody request: UpdateCompetitorRequest
    ): CompetitorResponse =
        competitorWatchlistService.update(
            id = id,
            name = request.name,
            aliases = request.aliases,
            excludeKeywords = request.excludeKeywords,
            tier = request.tier,
            isActive = request.isActive,
            rssFeeds = request.rssFeeds
        )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) {
        competitorWatchlistService.delete(id)
    }

    /**
     * 경쟁사 관련 기사 타임라인을 조회한다.
     * sentiment 필터를 선택적으로 적용할 수 있다.
     */
    @GetMapping("/timeline")
    fun timeline(
        @RequestParam(defaultValue = "30") days: Int,
        @RequestParam(required = false) competitorId: String?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) sentiment: String?,
        @RequestParam(defaultValue = "30") limit: Int
    ): CompetitorTimelineResponse {
        // days와 limit을 안전 범위로 클램핑한다.
        val safeDays = days.coerceIn(1, 365)
        val safeLimit = limit.coerceIn(1, 100)
        return competitorWatchlistService.getTimeline(
            safeDays, competitorId, eventType, sentiment, safeLimit
        )
    }

    @GetMapping("/sov")
    fun shareOfVoice(@RequestParam(defaultValue = "30") days: Int): SovResponse {
        val safeDays = days.coerceIn(1, 365)
        return competitorWatchlistService.getShareOfVoice(safeDays)
    }

    @GetMapping("/sentiment")
    fun competitorSentiment(
        @RequestParam(defaultValue = "7") days: Int
    ): CompetitorSentimentResponse {
        val safeDays = days.coerceIn(1, 365)
        return competitorWatchlistService.getCompetitorSentiment(safeDays)
    }

    @GetMapping("/snapshot")
    fun getSnapshot(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "5") limit: Int
    ): CompetitorSnapshotResponse = competitorSnapshotService.getSnapshot(days, limit)

    /**
     * 키워드로 Google News RSS 프리뷰를 조회한다.
     * 등록 전 키워드의 유효성을 확인하는 데 사용한다.
     */
    @PostMapping("/keyword-preview")
    fun previewKeywords(
        @RequestBody request: KeywordPreviewRequest
    ): KeywordPreviewResponse =
        competitorWatchlistService.previewKeywords(request.keywords)

    /** 경쟁사 뉴스를 수동으로 즉시 수집하고 AI 요약을 실행한다. */
    @PostMapping("/collect")
    fun triggerCollection(): Map<String, Any> {
        val results = competitorCollectionService.collectAll(hoursBack = 14)
        val totalNew = results.sumOf { it.newItemCount }
        // 미요약 기사를 AI로 요약한다 (신규 + 기존 미요약 모두)
        val summarized = runCatching {
            competitorArticleSummarizationService.summarizeUnsummarized()
        }.getOrDefault(0)
        return mapOf(
            "competitors" to results.size,
            "newArticles" to totalNew,
            "summarized" to summarized,
            "message" to "${results.size}개 경쟁사에서 ${totalNew}건 수집, ${summarized}건 AI 요약 완료"
        )
    }

    /**
     * 경쟁사 이름, 키워드, RSS 피드 입력값을 검증한다.
     */
    private fun validateCompetitorInput(
        name: String,
        aliases: List<String>,
        excludeKeywords: List<String>,
        rssFeeds: List<RssFeedInput>?
    ) {
        if (name.length > MAX_NAME_LENGTH) {
            throw InvalidInputException("이름은 ${MAX_NAME_LENGTH}자 이내로 입력하세요")
        }
        if (aliases.size > MAX_ALIASES) {
            throw InvalidInputException("별칭은 최대 ${MAX_ALIASES}개까지 입력할 수 있습니다")
        }
        aliases.forEach {
            if (it.length > MAX_KEYWORD_LENGTH) {
                throw InvalidInputException("별칭은 ${MAX_KEYWORD_LENGTH}자 이내로 입력하세요")
            }
        }
        if (excludeKeywords.size > MAX_EXCLUDE_KEYWORDS) {
            throw InvalidInputException("제외 키워드는 최대 ${MAX_EXCLUDE_KEYWORDS}개까지 입력할 수 있습니다")
        }
        excludeKeywords.forEach {
            if (it.length > MAX_KEYWORD_LENGTH) {
                throw InvalidInputException("제외 키워드는 ${MAX_KEYWORD_LENGTH}자 이내로 입력하세요")
            }
        }
        if ((rssFeeds?.size ?: 0) > MAX_RSS_FEEDS) {
            throw InvalidInputException("수동 RSS는 최대 ${MAX_RSS_FEEDS}개까지 추가할 수 있습니다")
        }
    }
}
