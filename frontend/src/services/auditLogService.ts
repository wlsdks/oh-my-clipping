import { api } from "@/lib/kyInstance";
import type { AuditLogPage, AuditLogFilters } from "@/types/auditLog";

export const auditLogService = {
  /** 감사 로그를 필터 조건으로 페이지네이션 조회한다. */
  getAll: (params?: URLSearchParams): Promise<AuditLogPage> => {
    const suffix = params?.toString() ? `?${params.toString()}` : "";
    return api.get(`admin/audit-log${suffix}`).json();
  },

  /** 필터 드롭다운에 표시할 액션/대상 유형 목록을 조회한다. */
  getFilters: (): Promise<AuditLogFilters> =>
    api.get("admin/audit-log/filters").json(),
};
