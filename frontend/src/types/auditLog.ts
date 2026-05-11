export interface AuditLogEntry {
  id: number;
  actorId: string;
  actorName: string;
  action: string;
  targetType: string;
  targetId?: string | null;
  targetName?: string | null;
  detail?: string | null;
  createdAt: string;
}

export interface AuditLogPage {
  content: AuditLogEntry[];
  totalCount: number;
  page: number;
  size: number;
}

export interface AuditLogFilters {
  actions: string[];
  targetTypes: string[];
}
