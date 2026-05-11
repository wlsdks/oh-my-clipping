package com.clipping.mcpserver.mcp.dto

/**
 * MCP 응답용 북마크 처리 결과.
 * 북마크 추가/제거 후 해당 요약 ID와 처리 상태를 반환한다.
 */
data class BookmarkResult(
    val summaryId: String,
    val status: String,
)
