import { api } from "@/lib/kyInstance";
import type {
  PipelineRunRecord,
  PipelineRunsPage,
  PipelineExecuteResponse,
} from "@/types/pipeline";

export interface PipelineExecuteRequest {
  categoryId: string;
  hoursBack?: number;
  maxItems?: number;
  unsentOnly?: boolean;
  sendToSlack?: boolean;
  slackChannelId?: string | null;
  ralphLoopEnabled?: boolean;
  ralphLoopMaxIterations?: number;
  ralphLoopStopPhrase?: string;
}

export const pipelineService = {
  /** 파이프라인을 실행하고 runId를 반환한다. */
  execute: (data: PipelineExecuteRequest): Promise<PipelineExecuteResponse> =>
    api.post("admin/pipeline/execute", { json: data, timeout: 120_000 }).json(),

  /** 파이프라인 실행 이력을 페이지네이션으로 조회한다. within 이 있으면 해당 기간 이내 레코드만 반환한다. */
  listRuns: (params?: URLSearchParams, within?: "1d" | "7d"): Promise<PipelineRunsPage> => {
    const merged = new URLSearchParams(params);
    if (within) merged.set("within", within);
    const suffix = merged.toString() ? `?${merged.toString()}` : "";
    return api.get(`admin/pipeline/runs${suffix}`).json();
  },

  /** 특정 파이프라인 실행 상세를 조회한다. */
  getRunDetail: (runId: string): Promise<PipelineRunRecord> =>
    api.get(`admin/pipeline/runs/${encodeURIComponent(runId)}`).json(),

  /** 카테고리별 가장 최근 파이프라인 실행을 조회한다. */
  getLatest: (categoryId: string): Promise<PipelineRunRecord | null> =>
    api
      .get(`admin/pipeline/latest`, {
        searchParams: { categoryId },
      })
      .json<PipelineRunRecord | null>()
      .catch(() => null),

};
