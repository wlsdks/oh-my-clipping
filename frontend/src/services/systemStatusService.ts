import { api } from "@/lib/kyInstance";
import type { SystemStatusResponse } from "@/types/systemStatus";

export const systemStatusService = {
  /** 시스템 전체 상태(서버·DB·Slack·스케줄러)를 조회한다. */
  getStatus: (): Promise<SystemStatusResponse> =>
    api.get("admin/system/status").json(),
};
