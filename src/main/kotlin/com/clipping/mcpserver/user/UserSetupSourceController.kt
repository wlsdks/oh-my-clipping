package com.clipping.mcpserver.user

import com.clipping.mcpserver.admin.dto.ApproveRequest
import com.clipping.mcpserver.admin.dto.CreateSourceRequest
import com.clipping.mcpserver.admin.dto.CuratedSourceDto
import com.clipping.mcpserver.admin.dto.SourceResponse
import com.clipping.mcpserver.admin.dto.ValidateUrlRequest
import com.clipping.mcpserver.admin.parseExpectedUpdatedAt
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.service.UserSetupResourceService
import com.clipping.mcpserver.service.dto.KnownSourceSearchResult
import com.clipping.mcpserver.service.dto.SourceVerifyResponse
import com.clipping.mcpserver.service.dto.UrlValidationResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 self-serve RSS 소스 등록/검증 API를 제공한다.
 */
@RestController
@RequestMapping("/api/user/setup/sources")
class UserSetupSourceController(
    private val userSetupResourceService: UserSetupResourceService
) {

    /**
     * 이름, 별칭, 도메인으로 주요 뉴스소스를 검색한다.
     * 검색어가 없으면 전체 목록을 반환한다.
     */
    @GetMapping("/known-sources")
    fun searchKnownSources(
        @org.springframework.web.bind.annotation.RequestParam(required = false) q: String?,
        @org.springframework.web.bind.annotation.RequestParam(required = false) region: String?
    ): List<KnownSourceSearchResult> {
        // 서비스 계층을 통해 store에 접근하여 레이어 경계를 유지한다.
        val results = if (q.isNullOrBlank()) {
            userSetupResourceService.listAllKnownSources()
        } else {
            userSetupResourceService.searchKnownSources(q)
        }
        return results
            .filter { region.isNullOrBlank() || it.region.equals(region, ignoreCase = true) }
            .map { src ->
                KnownSourceSearchResult(
                    name = src.name,
                    domain = src.domain,
                    region = src.region,
                    aliases = src.aliases
                )
            }
    }

    /** 유저가 검증된(curated) 소스 목록을 조회한다. */
    @GetMapping("/curated")
    fun listCuratedSources(): List<CuratedSourceDto> {
        // 서비스를 통해 curated 소스만 조회
        return userSetupResourceService.listCuratedSources().map {
            CuratedSourceDto(
                id = it.id,
                name = it.name,
                url = it.url,
                region = it.sourceRegion.name,
                emoji = it.emoji
            )
        }
    }

    /**
     * 로그인 사용자의 setup 전용 RSS 소스를 생성한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @RequestBody request: CreateSourceRequest
    ): SourceResponse =
        userSetupResourceService.createOwnSource(
            requesterUsername = authentication.name,
            name = request.name,
            url = request.url,
            sourceRegionRaw = request.sourceRegion,
            emoji = request.emoji,
            categoryId = request.categoryId,
            legalBasisRaw = request.legalBasis,
            summaryAllowed = request.summaryAllowed,
            fulltextAllowed = request.fulltextAllowed,
            reviewNotes = request.reviewNotes
        ).toResponse()

    /**
     * URL만 입력받아 RSS 연결 가능 여부를 사전 검증한다.
     */
    @PostMapping("/validate-url")
    fun validateUrl(
        authentication: Authentication,
        @RequestBody request: ValidateUrlRequest
    ): UrlValidationResponse {
        val url = request.url
            ?: return UrlValidationResponse(valid = false, reason = "URL이 필요합니다.")
        val result = userSetupResourceService.validateOwnSourceUrl(authentication.name, url)
        return UrlValidationResponse(
            valid = result.valid,
            status = result.status,
            reason = result.reason
        )
    }

    /**
     * 로그인 사용자의 소스만 연결 상태를 검증한다.
     */
    @PostMapping("/{id}/verify")
    fun verify(
        authentication: Authentication,
        @PathVariable id: String
    ): SourceVerifyResponse =
        SourceVerifyResponse(
            status = userSetupResourceService.verifyOwnSource(
                requesterUsername = authentication.name,
                sourceId = id
            )
        )

    /**
     * 로그인 사용자의 소스만 빠른 세팅용 승인 상태로 전환한다.
     */
    @PostMapping("/{id}/approve")
    fun approve(
        authentication: Authentication,
        @PathVariable id: String,
        @RequestBody request: ApproveRequest
    ): SourceResponse =
        userSetupResourceService.approveOwnSource(
            requesterUsername = authentication.name,
            sourceId = id,
            approved = request.approved,
            legalBasisRaw = request.legalBasis,
            summaryAllowed = request.summaryAllowed,
            fulltextAllowed = request.fulltextAllowed,
            reviewNotes = request.reviewNotes,
            expectedUpdatedAt = parseExpectedUpdatedAt(request.expectedUpdatedAt, "expectedUpdatedAt")
        ).toResponse()

    private fun RssSource.toResponse() = SourceResponse(
        id = id,
        name = name,
        url = url,
        sourceRegion = sourceRegion.name,
        emoji = emoji,
        isActive = isActive,
        crawlApproved = crawlApproved,
        approvedBy = approvedBy,
        approvedAt = approvedAt?.toString(),
        legalBasis = legalBasis,
        summaryAllowed = summaryAllowed,
        fulltextAllowed = fulltextAllowed,
        termsReviewedAt = termsReviewedAt?.toString(),
        expectedReviewAt = expectedReviewAt?.toString(),
        reviewNotes = reviewNotes,
        verificationStatus = verificationStatus,
        reliabilityScore = reliabilityScore,
        lastCrawlError = lastCrawlError,
        crawlFailCount = crawlFailCount,
        lastSuccessAt = lastSuccessAt?.toString(),
        categoryId = categoryId,
        curated = curated,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}
