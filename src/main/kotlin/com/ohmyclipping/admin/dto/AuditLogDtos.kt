package com.ohmyclipping.admin.dto

/**
 * 감사 로그 단건 응답 DTO.
 * AuditLogEntry 엔티티를 API 응답 형태로 변환한다.
 */
data class AuditLogEntryResponse(
    val id: Long,
    // V117 이후 탈퇴 관리자/외부 principal 의 로그는 actorId 가 NULL 로 기록될 수 있다.
    val actorId: String?,
    val actorName: String,
    val action: String,
    val targetType: String,
    val targetId: String?,
    val targetName: String?,
    val detail: String?,
    val createdAt: String
)

/**
 * 감사 로그 페이지네이션 응답 DTO.
 */
data class AuditLogPageResponse(
    val content: List<AuditLogEntryResponse>,
    val totalCount: Int,
    val page: Int,
    val size: Int
)

/**
 * 감사 로그 필터 옵션 응답 DTO.
 * 프론트엔드 드롭다운 구성에 사용된다.
 */
data class AuditLogFiltersResponse(
    val actions: List<String>,
    val targetTypes: List<String>
)
