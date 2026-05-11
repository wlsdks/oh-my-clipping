import { api } from "@/lib/kyInstance";
import type { DbMetricsSnapshot } from "@/types/dbMetrics";

export const dbMetricsService = {
  /**
   * DB 메트릭 스냅샷을 조회한다.
   * @param forceRefresh true 면 캐시를 무시하고 DB에서 직접 재계산한다.
   */
  getSnapshot: (forceRefresh = false): Promise<DbMetricsSnapshot> =>
    api
      .get("admin/ops/db-metrics", {
        searchParams: { forceRefresh: String(forceRefresh) }
      })
      .json<DbMetricsSnapshot>()
};
