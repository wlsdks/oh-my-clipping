import { api } from "@/lib/kyInstance";
import type { ReportDeliveryHistoryItem } from "@/types/reportDeliveryHistory";

export interface ListHistoryParams {
  reportType?: "WEEKLY" | "MONTHLY";
  limit?: number;
}

/**
 * 관리자 리포트 발송 이력 API.
 * 관리자 자동 리포트 탭에서 주간/월간 발송 이력을 조회할 때 사용한다.
 */
export const reportDeliveryHistoryService = {
  list: (params: ListHistoryParams = {}): Promise<ReportDeliveryHistoryItem[]> => {
    const searchParams: Record<string, string> = {};
    if (params.reportType) searchParams.reportType = params.reportType;
    if (params.limit != null) searchParams.limit = String(params.limit);
    return api.get("admin/reports/history", { searchParams }).json();
  },
};
