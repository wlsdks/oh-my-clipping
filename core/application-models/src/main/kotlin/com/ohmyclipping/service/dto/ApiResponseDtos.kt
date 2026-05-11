package com.ohmyclipping.service.dto

/**
 * RSS 소스 접근/파싱 검증 결과 DTO.
 * SourceAdminController와 UserSetupSourceController에서 공통으로 사용한다.
 */
data class SourceVerifyResponse(
    val status: String
)

/**
 * URL RSS 피드 사전 검증 결과 DTO.
 * Map<String, Any> 대신 명확한 타입을 제공한다.
 */
data class UrlValidationResponse(
    val valid: Boolean,
    val status: String? = null,
    val reason: String? = null
)

/**
 * 발송 재시도 결과 DTO.
 */
data class RetryDeliveryResponse(
    val success: Boolean,
    val logId: String
)

/**
 * 검토함 액션(승인/제외/재검토) 결과 DTO.
 */
data class ReviewItemActionResponse(
    val summaryId: String,
    val status: String
)

/**
 * 주요 뉴스소스 검색 결과 DTO.
 * 이름, 도메인, 지역, 별칭 목록을 포함한다.
 */
data class KnownSourceSearchResult(
    val name: String,
    val domain: String,
    val region: String,
    val aliases: List<String>
)
