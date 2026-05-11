import { api } from "@/lib/kyInstance";
import type { DeliverySummary, DeliveryLogsPage } from "@/types/delivery";

export const deliveryService = {
  /** 특정 날짜의 발송 요약 통계를 조회한다. */
  getSummary: (date?: string): Promise<DeliverySummary> => {
    const searchParams: Record<string, string> = {};
    if (date) searchParams.date = date;
    return api.get("admin/delivery/summary", { searchParams }).json();
  },

  /** 발송 이력을 필터 조건으로 페이지네이션 조회한다. within 이 있으면 해당 기간 이내 레코드만 반환한다. */
  listLogs: (params?: URLSearchParams, within?: "1d" | "7d"): Promise<DeliveryLogsPage> => {
    const merged = new URLSearchParams(params);
    if (within) merged.set("within", within);
    const suffix = merged.toString() ? `?${merged.toString()}` : "";
    return api.get(`admin/delivery/logs${suffix}`).json();
  },

  /** 실패 상태의 발송 건을 재발송 요청한다. */
  retry: (logId: string): Promise<{ success: boolean; logId: string }> =>
    api.post(`admin/delivery/${encodeURIComponent(logId)}/retry`).json(),
};
