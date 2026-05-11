package com.ohmyclipping.admin.dto

/**
 * 사용자 클리핑 요청 응답 DTO.
 */
data class UserClippingRequestResponse(
    val id: String,
    val requesterUserId: String,
    val requestName: String,
    val sourceName: String,
    val sourceUrl: String,
    val slackChannelId: String,
    val personaName: String,
    val personaPrompt: String,
    val summaryStyle: String?,
    val targetAudience: String?,
    val selectedPresetId: String? = null,
    val requestNote: String?,
    val status: String,
    val reviewNote: String?,
    val reviewedByUserId: String?,
    val reviewedAt: String?,
    val approvedCategoryId: String?,
    val approvedCategoryName: String?,
    val approvedPersonaId: String?,
    val approvedSourceId: String?,
    val createdAt: String,
    val updatedAt: String,
    val deliveryState: String,
    val collectingReady: Boolean,
    val totalSourceCount: Int,
    val readySourceCount: Int,
    val representativeSourceVerificationStatus: String?
)

/**
 * 사용자 클리핑 요청 생성 DTO.
 */
data class CreateUserClippingRequest(
    val requestName: String,
    val sourceName: String,
    val sourceUrl: String,
    val slackChannelId: String,
    val personaName: String,
    val personaPrompt: String,
    val summaryStyle: String? = null,
    val targetAudience: String? = null,
    val selectedPresetId: String? = null,
    val requestNote: String? = null
)

/**
 * 승인된 요청에 RSS 소스만 추가 요청하는 DTO.
 */
data class CreateUserRssSourceRequests(
    val baseRequestId: String,
    val sources: List<CreateUserRssSourceItem>,
    val requestNote: String? = null
)

data class CreateUserRssSourceItem(
    val sourceName: String,
    val sourceUrl: String
)

/**
 * 위자드에서 직접 생성한 리소스의 소유권을 등록하는 DTO.
 */
data class RegisterWizardOwnershipRequest(
    val requestName: String,
    val sourceName: String,
    val sourceUrl: String,
    val slackChannelId: String,
    val personaName: String,
    val personaPrompt: String,
    val summaryStyle: String? = null,
    val targetAudience: String? = null,
    val selectedPresetId: String? = null,
    val categoryId: String,
    val personaId: String? = null,
    val sourceId: String? = null
)

/**
 * 관리자 검토 메모 DTO.
 */
data class ReviewUserClippingRequest(
    val reviewNote: String? = null
)
