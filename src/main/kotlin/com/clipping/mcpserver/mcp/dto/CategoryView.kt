package com.clipping.mcpserver.mcp.dto

import com.clipping.mcpserver.service.dto.clipping.CategoryInfo

/** MCP 응답용 카테고리 뷰 모델. */
data class CategoryView(
    val id: String,
    val name: String,
    val description: String?,
) {
    companion object {
        fun from(info: CategoryInfo) = CategoryView(id = info.id, name = info.name, description = info.description)
    }
}
