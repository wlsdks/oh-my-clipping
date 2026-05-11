package com.clipping.mcpserver.admin.dto

import com.clipping.mcpserver.support.EditingSession

/**
 * 편집 세션 heartbeat 요청 DTO.
 */
data class EditingHeartbeatRequest(
    val resourceType: String,
    val resourceId: String,
)

/**
 * 편집 세션 release 요청 DTO.
 */
data class EditingReleaseRequest(
    val resourceType: String,
    val resourceId: String,
)

/**
 * 활성 편집 세션 응답 DTO.
 */
data class EditingSessionResponse(
    val userId: String,
    val displayName: String,
    val startedAt: String,
) {
    companion object {
        fun from(session: EditingSession): EditingSessionResponse =
            EditingSessionResponse(
                userId = session.userId,
                displayName = session.displayName,
                startedAt = session.startedAt.toString(),
            )
    }
}
