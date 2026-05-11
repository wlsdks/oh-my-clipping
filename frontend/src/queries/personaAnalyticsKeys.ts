/**
 * 페르소나 분석 TanStack Query 키 팩토리.
 *
 * 모든 키는 "persona-analytics" 루트에서 파생되어 한 번에 invalidate 가 가능하다.
 *
 * Slice 별 점진 확장:
 *   - Slice 1: live (이 파일)
 *   - Slice 2: trends, batchRuns
 *   - Slice 3: anomalies
 *   - Slice 4: keywords
 *   - Slice 5: clusters, presetCandidates
 */
export const personaAnalyticsKeys = {
  all: ["persona-analytics"] as const,
  live: () => [...personaAnalyticsKeys.all, "live"] as const,
  trends: (weeks: number) => [...personaAnalyticsKeys.all, "trends", weeks] as const,
  batchRuns: () => [...personaAnalyticsKeys.all, "batch-runs"] as const,
  signals: (lookbackWeeks: number) =>
    [...personaAnalyticsKeys.all, "signals", lookbackWeeks] as const,
};
