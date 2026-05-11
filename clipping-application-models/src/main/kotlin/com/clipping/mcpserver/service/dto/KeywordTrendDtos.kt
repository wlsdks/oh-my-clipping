package com.clipping.mcpserver.service.dto

/**
 * 키워드 트렌드 조회 응답 DTO.
 */
data class KeywordTrendResponse(
    val period: KeywordTrendPeriod,
    val keywords: List<KeywordTrendItem>
)

data class KeywordTrendPeriod(
    val from: String,
    val to: String
)

data class KeywordTrendItem(
    val keyword: String,
    val dailyCounts: List<KeywordDailyCount>,
    val totalCount: Int,
    val changeRate: Double
)

data class KeywordDailyCount(
    val date: String,
    val count: Int
)
