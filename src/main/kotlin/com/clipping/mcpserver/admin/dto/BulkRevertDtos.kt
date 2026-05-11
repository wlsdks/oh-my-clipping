package com.clipping.mcpserver.admin.dto

import com.clipping.mcpserver.service.dto.BulkRevertItem

/**
 * 벌크 되돌리기 요청 DTO.
 * 각 항목별로 복원할 이전 상태(previousStatus)를 지정한다.
 */
data class BulkRevertRequest(
    val reverts: List<BulkRevertItem>
)
