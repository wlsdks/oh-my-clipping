package com.ohmyclipping.admin.dto

import com.ohmyclipping.model.SourceLegalBasis

/**
 * RSS 소스 조회/응답 DTO.
 */
data class SourceResponse(
    val id: String,
    val name: String,
    val url: String,
    val sourceRegion: String,
    val emoji: String?,
    val isActive: Boolean,
    val crawlApproved: Boolean,
    val approvedBy: String?,
    val approvedAt: String?,
    val legalBasis: SourceLegalBasis,
    val summaryAllowed: Boolean,
    val fulltextAllowed: Boolean,
    val termsReviewedAt: String?,
    val expectedReviewAt: String?,
    val reviewNotes: String?,
    val verificationStatus: String,
    val reliabilityScore: Int,
    val lastCrawlError: String?,
    val crawlFailCount: Int,
    val lastSuccessAt: String?,
    val categoryId: String,
    val curated: Boolean,
    val createdAt: String,
    val updatedAt: String
)

/**
 * RSS 소스 페이지네이션 응답 DTO.
 */
data class SourcePageResponse(
    val content: List<SourceResponse>,
    val totalCount: Int,
    val page: Int,
    val size: Int
)

/**
 * RSS 소스 생성 요청 DTO.
 */
data class CreateSourceRequest(
    val name: String,
    val url: String,
    val sourceRegion: String? = null,
    val emoji: String? = null,
    val categoryId: String,
    val legalBasis: String? = null,
    val summaryAllowed: Boolean? = null,
    val fulltextAllowed: Boolean? = null,
    val reviewNotes: String? = null,
    val responsibilityAcknowledged: Boolean? = null
)

/**
 * RSS 소스 수정 요청 DTO.
 */
data class UpdateSourceRequest(
    val name: String? = null,
    val url: String? = null,
    val sourceRegion: String? = null,
    val emoji: String? = null,
    val isActive: Boolean? = null,
    val categoryId: String? = null,
    val legalBasis: String? = null,
    val summaryAllowed: Boolean? = null,
    val fulltextAllowed: Boolean? = null,
    val reviewNotes: String? = null,
    val expectedUpdatedAt: String? = null
)

/**
 * RSS 승인 요청 DTO.
 */
data class ApproveRequest(
    val approved: Boolean,
    val approvedBy: String? = null,
    val legalBasis: String? = null,
    val summaryAllowed: Boolean? = null,
    val fulltextAllowed: Boolean? = null,
    val reviewNotes: String? = null,
    val expectedUpdatedAt: String? = null
)

/**
 * RSS 피드 자동 탐색 요청 DTO.
 */
data class DiscoverSourceRequest(val query: String)

/**
 * RSS 피드 자동 탐색 응답 DTO.
 */
data class DiscoverSourceResponse(
    val knownMatch: KnownMatchDto?,
    val discoveredFeeds: List<DiscoveredFeedDto>
)

/**
 * 매핑 테이블에서 매칭된 소스 정보.
 */
data class KnownMatchDto(
    val name: String,
    val rssUrl: String,
    val region: String
)

/**
 * 도메인 크롤링으로 발견된 RSS 피드 정보.
 */
data class DiscoveredFeedDto(
    val url: String,
    val title: String
)

/**
 * 검증된(curated) 소스 목록 응답 DTO.
 */
data class CuratedSourceDto(
    val id: String,
    val name: String,
    val url: String,
    val region: String,
    val emoji: String?
)

/**
 * URL 사전 검증 요청 DTO.
 */
data class ValidateUrlRequest(
    val url: String? = null
)

/**
 * 소스 벌크 액션 요청 DTO.
 * 여러 소스를 한 번에 검증하거나 보관 처리할 때 사용한다.
 */
data class BulkSourceActionRequest(
    val ids: List<String>
)

/**
 * 소스별 기사 수 응답 DTO.
 * 지정 기간(days) 동안 각 소스에서 수집된 기사 수를 반환한다.
 */
data class SourceArticleCountsResponse(
    val counts: Map<String, Int>,
    val days: Int
)

/**
 * 재검토가 필요한 소스 건수 응답 DTO.
 */
data class ComplianceSummaryResponse(val attentionCount: Int)

/**
 * 소스 커버리지 갭 목록 응답 DTO.
 */
data class CoverageGapsResponse(
    val gaps: List<com.ohmyclipping.service.dto.CoverageGapDto>,
)
