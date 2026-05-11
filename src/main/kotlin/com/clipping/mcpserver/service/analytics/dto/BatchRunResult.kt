package com.clipping.mcpserver.service.analytics.dto

/**
 * 배치 실행 결과를 나타내는 경량 DTO.
 *
 * PersonaAnalyticsMondayBatch.run() 반환값으로 사용된다.
 */
data class BatchRunResult(val runId: String, val overallStatus: String)
