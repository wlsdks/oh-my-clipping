package com.clipping.mcpserver.mcp.dto

/**
 * MCP 응답용 키워드 빈도 뷰 모델.
 * 특정 기간 내 키워드 출현 빈도와 전 기간 대비 변화율을 담는다.
 */
data class KeywordFrequency(
    val keyword: String,
    val count: Int,
    val changeRate: Double?,
)
