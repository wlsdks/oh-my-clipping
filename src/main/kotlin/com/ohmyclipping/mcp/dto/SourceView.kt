package com.ohmyclipping.mcp.dto

import com.ohmyclipping.service.dto.clipping.SourceInfo

/** MCP 응답용 소스(RSS/뉴스) 뷰 모델. */
data class SourceView(
    val id: String,
    val name: String,
    val url: String,
    val categoryId: String,
    val categoryName: String?,
    val emoji: String?,
) {
    companion object {
        fun from(info: SourceInfo) = SourceView(
            id = info.id, name = info.name, url = info.url,
            categoryId = info.categoryId, categoryName = info.categoryName, emoji = info.emoji,
        )
    }
}
