package com.ohmyclipping.admin.dto

import com.ohmyclipping.service.dto.RssFeedInput

/**
 * 경쟁사 생성 요청 DTO.
 */
data class CreateCompetitorRequest(
    val name: String,
    val aliases: List<String> = emptyList(),
    val excludeKeywords: List<String> = emptyList(),
    val tier: String = "DIRECT",
    val rssFeeds: List<RssFeedInput> = emptyList(),
)

/**
 * 경쟁사 수정 요청 DTO.
 */
data class UpdateCompetitorRequest(
    val name: String? = null,
    val aliases: List<String>? = null,
    val excludeKeywords: List<String>? = null,
    val tier: String? = null,
    val isActive: Boolean? = null,
    val rssFeeds: List<RssFeedInput>? = null,
)
