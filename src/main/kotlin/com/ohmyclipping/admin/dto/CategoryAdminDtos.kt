package com.ohmyclipping.admin.dto

/**
 * 카테고리 조회/응답 DTO.
 */
data class CategoryResponse(
    val id: String,
    val name: String,
    val description: String?,
    val slackChannelId: String?,
    val isActive: Boolean,
    val isPublic: Boolean,
    val maxItems: Int,
    val personaId: String?,
    val sourceCount: Int,
    val subscriberCount: Int,
    val lastDeliveryAt: String?,
    val errorSourceCount: Int,
    val status: String,
    val pausedAt: String?,
    val createdAt: String,
    val updatedAt: String,
    /** V123(Phase 3 PR1): 구독 목적. SALES/RESEARCH/COMPETITIVE/CUSTOMER_CARE/OTHER 혹은 null. */
    val purpose: String? = null,
    /** V123(Phase 3 PR1): 구독을 만든 배경 (자유 텍스트, 선택). */
    val background: String? = null,
    /** V123(Phase 3 PR1): 구독이 해결하려는 문제 서술 (자유 텍스트, 선택). */
    val problemStatement: String? = null
)

/**
 * 카테고리 페이지네이션 응답 DTO.
 */
data class CategoryPageResponse(
    val content: List<CategoryResponse>,
    val totalCount: Int,
    val page: Int,
    val size: Int
)

/**
 * 카테고리 생성 요청 DTO.
 */
data class CreateCategoryRequest(
    val name: String,
    val description: String? = null,
    val slackChannelId: String? = null,
    val isPublic: Boolean = true,
    val maxItems: Int = 5,
    val personaId: String? = null,
    /** V123(Phase 3 PR1): 선택 — 구독 목적 분류. enum 값 또는 null. */
    val purpose: String? = null,
    /** V123(Phase 3 PR1): 선택 — 구독 배경 설명. */
    val background: String? = null,
    /** V123(Phase 3 PR1): 선택 — 해결하려는 문제 설명. */
    val problemStatement: String? = null
)

/**
 * 카테고리 수정 요청 DTO.
 */
data class UpdateCategoryRequest(
    val name: String? = null,
    val description: String? = null,
    val slackChannelId: String? = null,
    val isActive: Boolean? = null,
    val isPublic: Boolean? = null,
    val maxItems: Int? = null,
    val personaId: String? = null,
    val expectedUpdatedAt: String? = null,
    /** V123(Phase 3 PR1): null 은 변경 없음. 빈 문자열은 해당 필드 초기화(null 저장)로 해석. */
    val purpose: String? = null,
    val background: String? = null,
    val problemStatement: String? = null
)
