import { api } from "@/lib/kyInstance";

/**
 * 토큰 헬스 응답.
 *
 * - slackBot: `ok` | `expired` | `scope_mismatch` | `unknown`
 * - gemini: `ok` | `expired` | `quota_exhausted` | `unknown`
 * - ok: 둘 다 `ok`일 때만 true. 배너 노출 여부 판단용.
 */
export interface TokenHealthStatus {
  slackBot: string;
  gemini: string;
  ok: boolean;
}

export const tokenHealthService = {
  /** `/api/admin/system/token-health` — 관리자 인증 필요 */
  getStatus: (): Promise<TokenHealthStatus> =>
    api.get("admin/system/token-health").json(),
};
