package com.clipping.mcpserver.service.dto

/**
 * 벌크 되돌리기 개별 항목.
 * id: summaryId, previousStatus: 복원할 상태 (REVIEW / INCLUDE / EXCLUDE)
 */
data class BulkRevertItem(
    val id: String,
    val previousStatus: String
)
