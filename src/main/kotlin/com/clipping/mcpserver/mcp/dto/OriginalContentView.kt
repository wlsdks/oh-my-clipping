package com.clipping.mcpserver.mcp.dto

/**
 * MCP 응답용 원문 콘텐츠 뷰 모델.
 * 원문 미리보기(Markdown)와 저작권 안내를 포함하며,
 * 전체 본문 대신 프리뷰만 제공하여 저작권 보호와 프롬프트 크기를 관리한다.
 */
data class OriginalContentView(
    val summaryId: String,
    val title: String,
    val sourceName: String,
    val author: String?,
    val publishedAt: String?,
    val canonicalUrl: String,
    val previewMarkdown: String,
    val copyrightNotice: String,
)
