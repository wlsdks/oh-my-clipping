package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.ApproveRequest
import com.ohmyclipping.admin.dto.BulkSourceActionRequest
import com.ohmyclipping.admin.dto.ComplianceSummaryResponse
import com.ohmyclipping.admin.dto.CoverageGapsResponse
import com.ohmyclipping.admin.dto.CreateSourceRequest
import com.ohmyclipping.admin.dto.DiscoverSourceRequest
import com.ohmyclipping.admin.dto.DiscoverSourceResponse
import com.ohmyclipping.admin.dto.DiscoveredFeedDto
import com.ohmyclipping.admin.dto.KnownMatchDto
import com.ohmyclipping.service.dto.BulkSourceActionResponse
import com.ohmyclipping.service.dto.CrawlHistoryResponse
import com.ohmyclipping.service.dto.SourceAiCostsResponse
import com.ohmyclipping.service.dto.SourceAnalyticsResponse
import com.ohmyclipping.admin.dto.SourceArticleCountsResponse
import com.ohmyclipping.admin.dto.SourcePageResponse
import com.ohmyclipping.admin.dto.SourceResponse
import com.ohmyclipping.admin.dto.UpdateSourceRequest
import com.ohmyclipping.admin.dto.ValidateUrlRequest
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.model.SourceComplianceStatus
import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.service.AdminSourceService
import com.ohmyclipping.service.source.RssFeedDiscoveryService
import com.ohmyclipping.service.source.SourceCoverageAnalyzer
import com.ohmyclipping.service.source.SourceHealthService
import com.ohmyclipping.service.source.SourceVerificationClient
import com.ohmyclipping.service.source.VerificationResult
import com.ohmyclipping.service.dto.CoverageGapDto
import com.ohmyclipping.service.dto.SourceHealthResponse
import com.ohmyclipping.service.dto.SourceVerifyResponse
import com.ohmyclipping.service.dto.UrlValidationResponse
import com.ohmyclipping.support.IdempotencyKeyService
import com.ohmyclipping.support.PaginationUtils
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * RSS 소스 관리 API를 제공하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/admin/sources")
class SourceAdminController(
    private val adminSourceService: AdminSourceService,
    private val urlSafetyValidator: UrlSafetyValidator,
    private val sourceVerificationClient: SourceVerificationClient,
    private val rssFeedDiscoveryService: RssFeedDiscoveryService,
    private val sourceHealthService: SourceHealthService,
    private val sourceCoverageAnalyzer: SourceCoverageAnalyzer,
    private val idempotencyKeyService: IdempotencyKeyService
) {
    /**
     * 소스 헬스 요약을 반환합니다.
     * 어드민 대시보드의 RSS 소스 상태 카드에서 사용합니다.
     */
    @GetMapping("/health")
    fun getHealth(): SourceHealthResponse = sourceHealthService.getHealth()

    /**
     * 카테고리별 소스 커버리지 갭을 분석합니다.
     * 소스 부족, 지역 편중 등을 감지하여 경고 목록을 반환합니다.
     */
    @GetMapping("/coverage-gaps")
    fun getCoverageGaps(): CoverageGapsResponse =
        CoverageGapsResponse(gaps = sourceCoverageAnalyzer.analyze())

    /**
     * 소스별 기사 수집 건수를 반환합니다.
     *
     * @param days 조회 기간 (일 수, 기본 7일, 최대 90일)
     */
    @GetMapping("/stats/article-counts")
    fun getArticleCounts(
        @RequestParam(defaultValue = "7") days: Int
    ): SourceArticleCountsResponse {
        val counts = adminSourceService.getArticleCounts(days)
        return SourceArticleCountsResponse(counts = counts, days = days.coerceIn(1, 90))
    }

    /**
     * 특정 소스의 수집 통계(analytics)를 반환합니다.
     *
     * @param id 소스 ID
     * @param days 조회 기간 (일 수, 기본 30일, 최대 90일)
     */
    @GetMapping("/{id}/analytics")
    fun getAnalytics(
        @PathVariable id: String,
        @RequestParam(defaultValue = "30") days: Int
    ): SourceAnalyticsResponse = adminSourceService.getSourceAnalytics(id, days)

    /**
     * 특정 소스의 크롤 이력과 가동률 통계를 반환합니다.
     *
     * @param id 소스 ID
     * @param days 조회 기간 (일 수, 기본 30일, 최대 90일)
     */
    @GetMapping("/{id}/crawl-history")
    fun getCrawlHistory(
        @PathVariable id: String,
        @RequestParam(defaultValue = "30") days: Int
    ): CrawlHistoryResponse = adminSourceService.getCrawlHistory(id, days)

    /**
     * 소스별 AI 비용 통계를 반환합니다.
     *
     * @param days 조회 기간 (일 수, 기본 30일, 최대 90일)
     */
    @GetMapping("/stats/ai-costs")
    fun getAiCosts(
        @RequestParam(defaultValue = "30") days: Int
    ): SourceAiCostsResponse = adminSourceService.getAiCosts(days)

    /**
     * RSS 소스 목록을 페이지네이션으로 조회합니다.
     *
     * @param categoryId 카테고리 ID 필터 (선택)
     * @param q 이름/URL 검색어 (선택)
     * @param complianceStatus 저작권 검토 상태 필터: EXPIRED | EXPIRING_SOON | NEVER_REVIEWED | VALID (선택)
     * @param page 페이지 번호 (기본 0)
     * @param size 페이지 크기 (기본 30, 최대 500)
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) complianceStatus: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int
    ): SourcePageResponse {
        // 페이지 크기 상한을 500 으로 상향하여 프론트 가상 스크롤이 한 번에 받아볼 수 있게 한다.
        val safeSize = size.coerceIn(1, 500)
        // 음수 페이지가 DB OFFSET 음수로 전달되지 않도록 첫 페이지로 보정한다.
        val safePage = page.coerceAtLeast(0)
        val offset = PaginationUtils.safeOffset(safePage, safeSize)

        // 저작권 필터 값을 파싱한다. 빈 값/null 은 필터 없음, 알 수 없는 값은 400 을 반환한다.
        val complianceFilter = parseComplianceStatus(complianceStatus)

        // 필터 조건으로 소스를 페이지네이션 조회한다.
        val sources = adminSourceService.findAllPaged(
            categoryId = categoryId,
            search = q,
            complianceStatus = complianceFilter,
            offset = offset,
            limit = safeSize
        )

        // 총 건수를 조회한다.
        val totalCount = adminSourceService.countAll(
            categoryId = categoryId,
            search = q,
            complianceStatus = complianceFilter
        )

        return SourcePageResponse(
            content = sources.map { it.toResponse() },
            totalCount = totalCount,
            page = safePage,
            size = safeSize
        )
    }

    /**
     * 재검토가 필요한 소스 건수를 반환한다. 사이드바 뱃지에서 사용한다.
     */
    @GetMapping("/compliance-summary")
    fun getComplianceSummary(): ComplianceSummaryResponse =
        ComplianceSummaryResponse(attentionCount = adminSourceService.countComplianceAttention())

    /** 저작권 필터 쿼리 파라미터를 enum 으로 파싱한다. 빈 값은 null 로 취급한다. */
    private fun parseComplianceStatus(raw: String?): SourceComplianceStatus? {
        if (raw.isNullOrBlank()) return null
        return SourceComplianceStatus.fromRaw(raw)
            ?: throw InvalidInputException("Unsupported complianceStatus: $raw")
    }

    /**
     * RSS 소스 단건을 조회합니다.
     */
    @GetMapping("/{id}")
    fun get(@PathVariable id: String): SourceResponse =
        adminSourceService.getSource(id).toResponse()

    /**
     * RSS 소스를 등록합니다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateSourceRequest): SourceResponse =
        adminSourceService.createSource(
            name = request.name,
            url = request.url,
            sourceRegionRaw = request.sourceRegion,
            emoji = request.emoji,
            categoryId = request.categoryId,
            legalBasisRaw = request.legalBasis,
            summaryAllowed = request.summaryAllowed,
            fulltextAllowed = request.fulltextAllowed,
            reviewNotes = request.reviewNotes,
            responsibilityAcknowledged = request.responsibilityAcknowledged
        ).toResponse()

    /**
     * RSS 소스를 수정합니다.
     *
     * `Idempotency-Key` 헤더가 제공되면 같은 키의 재전송은 DB 를 다시 건드리지 않고 첫 응답을 그대로 재사용한다.
     */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody request: UpdateSourceRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        authentication: Authentication
    ): SourceResponse =
        idempotencyKeyService.executeIfKeyPresent(
            actor = authentication.name,
            key = idempotencyKey,
            resultClass = SourceResponse::class.java
        ) {
            adminSourceService.updateSource(
                id = id,
                name = request.name,
                url = request.url,
                sourceRegionRaw = request.sourceRegion,
                emoji = request.emoji,
                isActive = request.isActive,
                categoryId = request.categoryId,
                legalBasisRaw = request.legalBasis,
                summaryAllowed = request.summaryAllowed,
                fulltextAllowed = request.fulltextAllowed,
                reviewNotes = request.reviewNotes,
                expectedUpdatedAt = parseExpectedUpdatedAt(request.expectedUpdatedAt, "expectedUpdatedAt"),
                actorUsername = authentication.name
            ).toResponse()
        }

    /**
     * RSS 소스를 삭제합니다.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String, authentication: org.springframework.security.core.Authentication) {
        adminSourceService.deleteSource(id, deletedByUsername = authentication.name)
    }

    /**
     * RSS 소스 접근/파싱 유효성 검증을 수행합니다.
     */
    @PostMapping("/{id}/verify")
    fun verify(@PathVariable id: String): SourceVerifyResponse =
        SourceVerifyResponse(status = adminSourceService.verifySource(id))

    /**
     * URL이 유효한 RSS 피드인지 사전 검증합니다.
     * 소스를 등록하지 않고 URL만 확인하는 용도입니다.
     */
    @PostMapping("/validate-url")
    fun validateUrl(@RequestBody request: ValidateUrlRequest): UrlValidationResponse {
        val url = request.url
            ?: return UrlValidationResponse(valid = false, reason = "URL이 필요합니다.")

        return try {
            // URL 형식 및 보안 검증
            val uri = urlSafetyValidator.validatePublicHttpUrl(url)
            // RSS 피드 접근 및 파싱 검증
            val result = sourceVerificationClient.verify(uri)
            UrlValidationResponse(
                valid = (result.name == "VERIFIED"),
                status = result.name,
                reason = when (result) {
                    VerificationResult.VERIFIED ->
                        "RSS 피드를 정상적으로 확인했어요."
                    VerificationResult.FEED_ERROR ->
                        "이 주소에서 RSS 피드를 찾지 못했어요. URL을 다시 확인해 주세요."
                    VerificationResult.ROBOTS_BLOCKED ->
                        "이 사이트가 자동 수집을 차단하고 있어요."
                    VerificationResult.TIMEOUT ->
                        "응답이 너무 오래 걸려요. 잠시 후 다시 시도해 주세요."
                    VerificationResult.BLOCKED_URL ->
                        "허용되지 않는 URL이에요."
                }
            )
        } catch (e: IllegalArgumentException) {
            UrlValidationResponse(valid = false, reason = e.message ?: "잘못된 URL 형식이에요.")
        } catch (e: Exception) {
            UrlValidationResponse(valid = false, reason = "URL 확인 중 오류가 발생했어요.")
        }
    }

    /**
     * RSS 소스를 승인 처리합니다.
     */
    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: String, @RequestBody request: ApproveRequest): SourceResponse =
        adminSourceService.approveSource(
            id = id,
            approved = request.approved,
            approvedBy = request.approvedBy,
            legalBasisRaw = request.legalBasis,
            summaryAllowed = request.summaryAllowed,
            fulltextAllowed = request.fulltextAllowed,
            reviewNotes = request.reviewNotes,
            expectedUpdatedAt = parseExpectedUpdatedAt(request.expectedUpdatedAt, "expectedUpdatedAt")
        ).toResponse()

    /**
     * 여러 소스의 접근/파싱 유효성 검증을 일괄 수행합니다.
     * 개별 실패가 전체 요청을 중단시키지 않으며, 부분 성공 결과를 반환합니다.
     */
    @PostMapping("/bulk/verify")
    fun bulkVerify(@RequestBody request: BulkSourceActionRequest): BulkSourceActionResponse =
        adminSourceService.bulkVerify(request.ids)

    /**
     * 여러 소스를 일괄 보관(반려) 처리합니다.
     * 개별 실패가 전체 요청을 중단시키지 않으며, 부분 성공 결과를 반환합니다.
     */
    @PostMapping("/bulk/archive")
    fun bulkArchive(@RequestBody request: BulkSourceActionRequest): BulkSourceActionResponse =
        adminSourceService.bulkArchive(request.ids)

    /** RSS 피드를 자동 탐색한다. 사이트명, 도메인, URL을 입력받는다. */
    @PostMapping("/discover")
    fun discoverFeeds(@RequestBody request: DiscoverSourceRequest): DiscoverSourceResponse {
        // 매핑 테이블 검색 + 도메인 RSS 경로 크롤링
        val result = rssFeedDiscoveryService.discover(request.query)
        return DiscoverSourceResponse(
            knownMatch = result.knownMatch?.let {
                KnownMatchDto(name = it.name, rssUrl = it.rssUrl, region = it.region)
            },
            discoveredFeeds = result.discoveredFeeds.map {
                DiscoveredFeedDto(url = it.url, title = it.title)
            }
        )
    }

    private fun RssSource.toResponse() = SourceResponse(
        id = id, name = name, url = url, sourceRegion = sourceRegion.name, emoji = emoji,
        isActive = isActive, crawlApproved = crawlApproved,
        approvedBy = approvedBy, approvedAt = approvedAt?.toString(),
        legalBasis = legalBasis,
        summaryAllowed = summaryAllowed,
        fulltextAllowed = fulltextAllowed,
        termsReviewedAt = termsReviewedAt?.toString(),
        expectedReviewAt = expectedReviewAt?.toString(),
        reviewNotes = reviewNotes,
        verificationStatus = verificationStatus,
        reliabilityScore = reliabilityScore,
        lastCrawlError = lastCrawlError, crawlFailCount = crawlFailCount,
        lastSuccessAt = lastSuccessAt?.toString(),
        categoryId = categoryId, curated = curated,
        createdAt = createdAt.toString(), updatedAt = updatedAt.toString()
    )
}
