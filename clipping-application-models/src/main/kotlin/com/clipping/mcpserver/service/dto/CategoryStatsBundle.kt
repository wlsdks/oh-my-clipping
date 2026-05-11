package com.clipping.mcpserver.service.dto

import java.time.Instant

/**
 * 카테고리 목록 API에서 카테고리별 모니터링 통계를 일괄 전달하는 번들.
 */
data class CategoryStatsBundle(
    val subscriberCounts: Map<String, Int>,
    val errorSourceCounts: Map<String, Int>,
    val lastDeliveryAts: Map<String, Instant>
)
