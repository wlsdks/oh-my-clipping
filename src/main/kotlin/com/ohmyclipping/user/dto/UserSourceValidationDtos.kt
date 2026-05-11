package com.ohmyclipping.user.dto

/**
 * 사용자 RSS URL 검증 요청 DTO.
 */
data class ValidateUrlRequest(val url: String)

/**
 * 사용자 RSS URL 검증 결과 응답 DTO.
 */
data class ValidateUrlResponse(
    val rssValid: Boolean,
    val robotsAllowed: Boolean,
    val domainBlocked: Boolean,
    val blockReason: String?,
    val existingSource: ExistingSourceResponse?,
)

/**
 * 이미 등록된 소스 요약 응답 DTO.
 */
data class ExistingSourceResponse(
    val name: String,
    val legalBasis: String,
)
