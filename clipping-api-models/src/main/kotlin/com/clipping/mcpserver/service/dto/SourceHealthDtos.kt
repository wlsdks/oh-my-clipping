package com.clipping.mcpserver.service.dto

import java.time.Instant

/**
 * RSS 소스 헬스 요약 응답.
 * 어드민 대시보드에서 사용한다.
 */
data class SourceHealthResponse(
    val totalCount: Int,
    val healthyCount: Int,
    val unhealthy: List<UnhealthySource>,
)

/**
 * 정상 동작하지 않는 소스 정보.
 */
data class UnhealthySource(
    val id: String,
    val name: String,
    val lastSuccessAt: Instant?,
    val crawlFailCount: Int,
    val reason: String,
)
