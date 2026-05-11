package com.ohmyclipping.service.dto

/**
 * 사용자 RSS URL 사전 검증 결과.
 */
data class UrlValidationResult(
    val rssValid: Boolean = false,
    val robotsAllowed: Boolean = true,
    val domainBlocked: Boolean = false,
    val blockReason: String? = null,
    val existingSource: ExistingSourceInfo? = null
)

/**
 * 검증 대상 URL과 같은 도메인으로 이미 등록된 소스 정보.
 */
data class ExistingSourceInfo(
    val name: String,
    val legalBasis: String
)
