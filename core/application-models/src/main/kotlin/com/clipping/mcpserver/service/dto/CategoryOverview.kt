package com.clipping.mcpserver.service.dto

/**
 * 카테고리 개요 서비스 결과.
 * 소스 수, 구독자 수, 최근 7일 기사 수, 평균 중요도 등 집계형 지표를 제공한다.
 */
data class CategoryOverview(
    val id: String,
    val name: String,
    val sourceCount: Int,
    val subscriberCount: Int,
    val recentItemCount7Days: Int,
    val avgImportance7Days: Double,
    val lastUpdatedAt: String?,
)

