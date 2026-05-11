import { api } from "@/lib/kyInstance";
import type { SourceHealthResponse } from "@/types/sourceHealth";

export const sourceHealthService = {
  getHealth: () => api.get("admin/sources/health").json<SourceHealthResponse>(),
};
