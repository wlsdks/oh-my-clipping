package com.ohmyclipping.admin.dto

/**
 * 변경 이력 응답 DTO (목록용).
 * snapshot은 크기가 커서 목록에서 제외하고, `GET /history/{revisionId}` 또는 restore 시에만 사용한다.
 */
data class RevisionSummaryResponse(
    val revisionId: String,
    val revisionNumber: Long,
    val editorId: String,
    val editorName: String,
    val changedFields: List<String>,
    val createdAt: String
)

/**
 * 이 버전으로 되돌리기 요청 DTO.
 *
 * @property revisionId 복원할 revision UUID
 * @property expectedUpdatedAt 현재 엔티티의 updatedAt. 낙관적 잠금에 사용.
 */
data class RestoreRevisionRequest(
    val revisionId: String,
    val expectedUpdatedAt: String
)
