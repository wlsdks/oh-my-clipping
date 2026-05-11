package com.clipping.mcpserver.service.dto

/** 카테고리별 RSS 소스 커버리지 갭 분석 결과. */
data class CoverageGapDto(
    val categoryId: String,
    val categoryName: String,
    val type: String,
    val detail: String,
    val severity: String
)
