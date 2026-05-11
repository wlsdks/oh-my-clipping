package com.clipping.mcpserver.admin.dto


/**
 * 페르소나 조회/응답 DTO.
 */
data class PersonaResponse(
    val id: String,
    val name: String,
    val description: String?,
    val systemPrompt: String,
    val summaryStyle: String?,
    val targetAudience: String?,
    val maxItems: Int,
    val language: String,
    val isActive: Boolean,
    val isPreset: Boolean = false,
    val previewTitle: String? = null,
    val previewSource: String? = null,
    val previewBody: String? = null,
    val currentVersion: Int = 1,
    val tone: String? = null,
    val lengthPref: String? = null,
    val createdAt: String,
    val updatedAt: String
)

/**
 * 페르소나 생성 요청 DTO.
 */
data class CreatePersonaRequest(
    val name: String,
    val description: String? = null,
    val systemPrompt: String,
    val summaryStyle: String? = null,
    val targetAudience: String? = null,
    val maxItems: Int = 5,
    val language: String = "ko",
    val isPreset: Boolean = false,
    val previewTitle: String? = null,
    val previewSource: String? = null,
    val previewBody: String? = null,
    val tone: String? = null,
    val lengthPref: String? = null
)

/**
 * 페르소나 수정 요청 DTO.
 */
data class UpdatePersonaRequest(
    val name: String? = null,
    val description: String? = null,
    val systemPrompt: String? = null,
    val summaryStyle: String? = null,
    val targetAudience: String? = null,
    val maxItems: Int? = null,
    val language: String? = null,
    val isActive: Boolean? = null,
    val previewTitle: String? = null,
    val previewSource: String? = null,
    val previewBody: String? = null,
    val tone: String? = null,
    val lengthPref: String? = null
)

/**
 * 페르소나 활성 상태만 부분 업데이트하기 위한 요청.
 * `PATCH /api/admin/personas/{id}/active` 전용.
 */
data class SetPersonaActiveRequest(
    val isActive: Boolean
)
