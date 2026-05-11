import { api } from "@/lib/kyInstance";
import type {
  BackfillResult,
  LiveSnapshotResponse,
  PersonaBatchRunDto,
  SignalsResponse,
  WeeklyTrendsResponse,
} from "@/types/personaAnalytics";

/**
 * 페르소나 분석 관리자 API 호출 서비스.
 *
 * Slice 별 점진 확장:
 *   - Slice 1: getLive() (이 파일)
 *   - Slice 2: getTrends, getBatchRuns, runBackfill 추가
 *   - Slice 3: getAnomalies, resolveAnomaly, triggerBatch 추가
 *   - Slice 4: getCustomKeywords 추가
 *   - Slice 5: getClusters, getPresetCandidates, reviewCandidate,
 *              acceptCandidate, rejectCandidate 추가
 */
export const personaAnalyticsService = {
  /**
   * GET /api/admin/analytics/personas/live
   * 5 분 캐시 (백엔드 Caffeine).
   */
  getLive: (): Promise<LiveSnapshotResponse> =>
    api.get("admin/analytics/personas/live").json<LiveSnapshotResponse>(),

  /**
   * GET /api/admin/analytics/personas/trends?weeks={weeks}
   * 주간 트렌드 시계열 데이터를 반환한다.
   */
  getTrends: (weeks = 12): Promise<WeeklyTrendsResponse> =>
    api
      .get("admin/analytics/personas/trends", { searchParams: { weeks } })
      .json<WeeklyTrendsResponse>(),

  /**
   * GET /api/admin/analytics/personas/batch-runs?limit={limit}
   * 최근 배치 실행 이력을 반환한다.
   */
  getBatchRuns: (limit = 10): Promise<PersonaBatchRunDto[]> =>
    api
      .get("admin/analytics/personas/batch-runs", { searchParams: { limit } })
      .json<PersonaBatchRunDto[]>(),

  /**
   * POST /api/admin/analytics/personas/backfill?weeks={weeks}
   * 과거 데이터를 소급 집계한다.
   */
  runBackfill: (weeks: number): Promise<BackfillResult> =>
    api
      .post("admin/analytics/personas/backfill", { searchParams: { weeks } })
      .json<BackfillResult>(),

  /**
   * GET /api/admin/analytics/personas/signals?lookbackWeeks={lookback}
   * 이번 주차의 위험/성장 신호를 내려준다.
   */
  getSignals: (lookbackWeeks = 4): Promise<SignalsResponse> =>
    api
      .get("admin/analytics/personas/signals", {
        searchParams: { lookbackWeeks },
      })
      .json<SignalsResponse>(),
};
