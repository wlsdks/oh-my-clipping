/**
 * 관리자 리포트 발송 이력 API 응답 타입.
 * `GET /api/admin/reports/history` 응답과 1:1 매핑된다.
 */
export interface ReportDeliveryHistoryItem {
  id: string;
  reportType: "WEEKLY" | "MONTHLY" | string;
  periodKey: string;
  channelId: string;
  /** "RESERVED" | "SENT" | "FAILED" */
  status: "RESERVED" | "SENT" | "FAILED" | string;
  durationMs: number | null;
  itemsProcessed: number | null;
  errorMessage: string | null;
  /** ISO-8601 */
  startedAt: string;
  /** ISO-8601 */
  finishedAt: string;
}
