package com.clipping.mcpserver.admin.dto

import jakarta.validation.constraints.Size

/**
 * Organization 응답 DTO.
 *
 * timestamp 는 ISO-8601 문자열로 내려 프론트에서 Date 파싱 편의를 제공한다.
 *
 * @property stockCode 한국 주식 종목 코드 (V134 신규).
 * @property aliases 조직 별칭 목록 (V134 신규).
 * @property origin 생성 경로: user_wizard / admin_created / competitor_mirror / backfill / legacy (V134 신규).
 * @property usageCount 연결된 카테고리 수. 목록 API 에서만 집계된다. 단건 조회 시 0.
 */
data class OrganizationResponse(
    val id: String,
    val tenantId: String,
    val name: String,
    val type: String,
    val domain: String?,
    val description: String?,
    val stockCode: String?,
    val aliases: List<String>,
    val origin: String?,
    val usageCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Organization 목록 응답.
 */
data class OrganizationListResponse(
    val content: List<OrganizationResponse>,
    val totalCount: Int,
)

/**
 * Organization 생성 요청.
 *
 * @property type "COMPETITOR" / "CUSTOMER" / "PARTNER" / "OTHER" 중 하나.
 */
data class CreateOrganizationRequest(
    val name: String,
    val type: String,
    val domain: String? = null,
    val description: String? = null,
)

/**
 * Organization 수정 요청. null 은 변경 없음.
 *
 * domain / description 은 빈 문자열이면 null 로 초기화 (기존 카테고리 UpdateDto 와 동일 규칙).
 * aliases 는 null 이면 변경 없음, non-null 이면 전체 교체.
 *
 * @property aliases 별칭 목록. `@field:Size(max = 20)` 은 개수만 검증한다 —
 *   개별 별칭 길이(최대 50자)는 Kotlin 의 Bean Validation 이 `List<@Size String>` 을
 *   type-use 로 표현할 수 없어 [com.clipping.mcpserver.service.OrganizationService.ALIAS_MAX_LENGTH]
 *   에서 서비스 레이어로 검증한다.
 *   이 annotation 은 컨트롤러의 `@Valid` 와 짝을 이뤄야 활성화된다.
 */
data class UpdateOrganizationRequest(
    val name: String? = null,
    val type: String? = null,
    val domain: String? = null,
    val description: String? = null,
    @field:Size(max = 20, message = "별칭은 최대 20개")
    val aliases: List<String>? = null,
)

/**
 * Category 에 연결할 Organization id 목록 교체 요청.
 */
data class SetCategoryOrganizationsRequest(
    val organizationIds: List<String>,
)
