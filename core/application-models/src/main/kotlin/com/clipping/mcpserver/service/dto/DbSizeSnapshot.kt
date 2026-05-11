package com.clipping.mcpserver.service.dto

import java.time.Instant

/**
 * DB 크기 스냅샷 응답 DTO.
 * 대시보드에서 DB 사용량, 임계 수준, 보존 대상 행 수, 일별 증가량을 한 번에 조회할 수 있다.
 *
 * 임계 수준 기준:
 * - ok: 사용률 < 80%
 * - warning: 80% ≤ 사용률 < 95%
 * - critical: 사용률 ≥ 95%
 */
data class DbSizeSnapshot(
    val databaseSizeBytes: Long,
    val databaseSizeMegabytes: Long,
    val databaseSizePercentOfLimit: Double,
    val limitBytes: Long,
    val thresholdLevel: String,
    val topTables: List<TableSizeEntry>,
    val retentionEligible: RetentionEligibleSummary,
    val dailyGrowth: DailyGrowthSummary,
    val lastRefreshedAt: Instant
)

/**
 * 테이블별 크기 정보.
 *
 * @param table 테이블 이름
 * @param bytes 인덱스 포함 전체 크기 (bytes)
 * @param rows 추정 행 수
 * @param pctOfDb DB 전체 대비 점유율 (%)
 */
data class TableSizeEntry(
    val table: String,
    val bytes: Long,
    val rows: Long,
    val pctOfDb: Double
)

/**
 * 보존 정책 적용 시 삭제 가능한 데이터 추정치.
 *
 * @param rssItemsOlderThanCutoff cutoff 이전 rss_items 행 수
 * @param batchSummariesOlderThanCutoffExcludingAnchored cutoff 이전 batch_summaries 중
 *        summary_feedback/bookmark 기준 anchor 제외 행 수
 * @param projectedBytesFreed 삭제 시 해방될 것으로 추정되는 바이트 (참고용, 정확하지 않을 수 있음)
 */
data class RetentionEligibleSummary(
    val rssItemsOlderThanCutoff: Long,
    val batchSummariesOlderThanCutoffExcludingAnchored: Long,
    val projectedBytesFreed: Long
)

/**
 * 최근 7일 일별 데이터 증가량 요약.
 *
 * @param lastSevenDaysBytes 날짜 오름차순 [day-6, day-5, ..., day-0] batch_summaries 생성량(bytes 추정)
 * @param avgDailyBytes 7일 평균 일별 증가량 (bytes)
 */
data class DailyGrowthSummary(
    val lastSevenDaysBytes: List<Long>,
    val avgDailyBytes: Long
)
