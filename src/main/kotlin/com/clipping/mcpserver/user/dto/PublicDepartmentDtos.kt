package com.clipping.mcpserver.user.dto

/**
 * 공개 signup / ProfileEditModal 에서 사용하는 최소 부서+팀 응답.
 *
 * `display_order`, `is_active`, timestamps 등 내부 운영 필드는 포함하지 않는다.
 * 이는 공개 엔드포인트 recon 표면적을 줄이기 위한 whitelist 설계다.
 */
data class PublicDepartmentResponse(
    val id: String,
    val name: String,
    val teams: List<PublicTeamResponse>
)

/** 공개 signup 용 팀 최소 응답. 식별자와 표시 이름만 노출한다. */
data class PublicTeamResponse(
    val id: String,
    val name: String
)

/** `/api/public/departments/tree` 응답 루트. */
data class PublicDepartmentTreeResponse(
    val departments: List<PublicDepartmentResponse>
)
