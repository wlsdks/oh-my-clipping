package com.clipping.mcpserver.admin.dto

/**
 * 클리핑 설정 조회 응답 DTO.
 */
data class ClippingSettingResponse(
    val categoryId: String,
    val categoryName: String,
    val categoryUpdatedAt: String,
    val isActive: Boolean,
    val slackChannelId: String?,
    val maxItems: Int,
    val retentionKeepDays: Int,
    val retentionEnabled: Boolean,
    val retentionSource: String
)

/**
 * 클리핑 설정 수정 요청 DTO.
 */
data class UpdateClippingSettingRequest(
    val isActive: Boolean? = null,
    val slackChannelId: String? = null,
    val maxItems: Int? = null,
    val retentionKeepDays: Int? = null,
    val retentionEnabled: Boolean? = null,
    val expectedCategoryUpdatedAt: String? = null
)

/**
 * 다이제스트 실행 요청 DTO.
 */
data class RunDigestRequest(
    val maxItems: Int? = null,
    val unsentOnly: Boolean? = null,
    val sendToSlack: Boolean? = null,
    val slackChannelId: String? = null
)

/**
 * 카테고리 단위 파이프라인 실행 요청 DTO.
 */
data class RunPipelineRequest(
    val hoursBack: Int? = null,
    val maxItems: Int? = null,
    val unsentOnly: Boolean? = true,
    val sendToSlack: Boolean? = false,
    val slackChannelId: String? = null,
    val ralphLoopEnabled: Boolean? = null,
    val ralphLoopMaxIterations: Int? = null,
    val ralphLoopStopPhrase: String? = null
)

/**
 * 카테고리 지정 파이프라인 실행 요청 DTO.
 */
data class RunPipelineByCategoryRequest(
    val categoryId: String,
    val hoursBack: Int? = null,
    val maxItems: Int? = null,
    val unsentOnly: Boolean? = true,
    val sendToSlack: Boolean? = false,
    val slackChannelId: String? = null,
    val ralphLoopEnabled: Boolean? = null,
    val ralphLoopMaxIterations: Int? = null,
    val ralphLoopStopPhrase: String? = null
)
