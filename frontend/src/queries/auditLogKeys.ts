export const auditLogKeys = {
  all: ["auditLog"] as const,
  list: (params?: Record<string, unknown>) =>
    [...auditLogKeys.all, "list", params] as const,
  filters: () => [...auditLogKeys.all, "filters"] as const,
};
