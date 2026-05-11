/** DB 메트릭 스냅샷 — GET /api/admin/ops/db-metrics */

export interface TableSizeEntry {
  table: string;
  bytes: number;
  rows: number;
  pctOfDb: number;
}

export interface RetentionEligibleSummary {
  rssItemsOlderThanCutoff: number;
  batchSummariesOlderThanCutoffExcludingAnchored: number;
  projectedBytesFreed: number;
}

export interface DailyGrowthSummary {
  lastSevenDaysBytes: number[];
  avgDailyBytes: number;
}

/** DB 임계값 수준 */
export type ThresholdLevel = "ok" | "warning" | "critical";

export interface DbMetricsSnapshot {
  databaseSizeBytes: number;
  databaseSizeMegabytes: number;
  databaseSizePercentOfLimit: number;
  limitBytes: number;
  thresholdLevel: ThresholdLevel;
  topTables: TableSizeEntry[];
  retentionEligible: RetentionEligibleSummary;
  dailyGrowth: DailyGrowthSummary;
  lastRefreshedAt: string;
}
