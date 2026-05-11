package com.ohmyclipping.admin.dto

/**
 * Admin 전용 부서 응답. timestamps 는 ISO-8601 문자열.
 * 공개 엔드포인트는 [com.ohmyclipping.user.dto.PublicDepartmentTreeResponse] 등
 * 별도의 최소 스키마를 사용한다.
 */
data class DepartmentAdminResponse(
    val id: String,
    val name: String,
    val displayOrder: Int,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String
)

/** Admin 전용 팀 응답. */
data class TeamAdminResponse(
    val id: String,
    val departmentId: String,
    val name: String,
    val displayOrder: Int,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String
)

/** 트리 노드 — 부서 + 하위 팀 (비활성 포함). */
data class DepartmentTreeAdminNode(
    val department: DepartmentAdminResponse,
    val teams: List<TeamAdminResponse>
)

/** 트리 응답 루트. */
data class DepartmentTreeAdminResponse(
    val content: List<DepartmentTreeAdminNode>,
    val totalCount: Int
)

/** 부서 생성 요청. */
data class CreateDepartmentRequest(
    val name: String,
    val displayOrder: Int? = null
)

/** 부서 수정 요청. null 필드는 변경 없음. */
data class UpdateDepartmentRequest(
    val name: String? = null,
    val displayOrder: Int? = null
)

/** 부서 활성/비활성 토글 요청. */
data class SetActiveRequest(
    val isActive: Boolean
)

/** 팀 생성 요청. */
data class CreateTeamRequest(
    val name: String,
    val displayOrder: Int? = null
)

/** 팀 수정 요청. [departmentId] 를 바꾸면 부서 간 이동으로 처리한다. */
data class UpdateTeamRequest(
    val name: String? = null,
    val displayOrder: Int? = null,
    val departmentId: String? = null
)
