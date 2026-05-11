import { api } from "@/lib/kyInstance";
import type {
  SourceQualitySummary,
  SourceQualityPeriod,
} from "@/types/sourceQuality";

// 백엔드 엔드포인트 경로(content-levers)는 변경 범위 밖이므로 그대로 사용한다.
export const sourceQualityService = {
  getSummary: (period: SourceQualityPeriod): Promise<SourceQualitySummary> =>
    api
      .get("admin/analytics/content-levers/summary", {
        searchParams: { period },
      })
      .json<SourceQualitySummary>(),
};
