/**
 * 관리자 리포트 발송 이력 쿼리 키.
 */
export const reportDeliveryHistoryKeys = {
  all: ["reportDeliveryHistory"] as const,
  list: (params: { reportType?: string; limit?: number }) =>
    [...reportDeliveryHistoryKeys.all, "list", params] as const,
};
