package com.ohmyclipping.mcp.dto

/**
 * MCP 응답용 요약 상세 뷰 모델.
 * 요약 본문, 키워드, 저작권 안내 등 클라이언트가 단건 상세 조회에서 필요로 하는 정보를 담는다.
 */
data class SummaryDetailView(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val title: String,
    val summary: String,
    val sourceLink: String,
    val sourceName: String,
    val publishedAt: String?,
    val importanceScore: Double?,
    val keywords: List<String>,
    val createdAt: String,
    val previewMarkdown: String,
    val copyrightNotice: String,
)
