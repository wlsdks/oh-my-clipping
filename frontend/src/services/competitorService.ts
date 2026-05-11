import { api } from "@/lib/kyInstance";
import type {
  Competitor,
  CreateCompetitorRequest,
  UpdateCompetitorRequest,
  CompetitorTimelineResponse,
  SovResponse,
  KeywordPreviewResponse,
} from "@/types/competitor";

export const competitorService = {
  list: (): Promise<Competitor[]> =>
    api.get("admin/competitors").json(),

  create: (data: CreateCompetitorRequest): Promise<Competitor> =>
    api.post("admin/competitors", { json: data }).json(),

  update: (id: string, data: UpdateCompetitorRequest): Promise<Competitor> =>
    api.put(`admin/competitors/${encodeURIComponent(id)}`, { json: data }).json(),

  delete: (id: string): Promise<void> =>
    api.delete(`admin/competitors/${encodeURIComponent(id)}`).then(() => undefined),

  getTimeline: (params: { days?: number; competitorId?: string }): Promise<CompetitorTimelineResponse> => {
    const sp = new URLSearchParams();
    if (params.days) sp.set("days", String(params.days));
    if (params.competitorId) sp.set("competitorId", params.competitorId);
    const suffix = sp.toString() ? `?${sp.toString()}` : "";
    return api.get(`admin/competitors/timeline${suffix}`).json();
  },

  getSov: (days?: number): Promise<SovResponse> =>
    api.get(`admin/competitors/sov${days ? `?days=${days}` : ""}`).json(),

  previewKeywords: (keywords: string[]): Promise<KeywordPreviewResponse> =>
    api.post("admin/competitors/keyword-preview", { json: { keywords } }).json(),

  collect: (): Promise<{ competitors: number; newArticles: number; message: string }> =>
    api.post("admin/competitors/collect", { timeout: 120_000 }).json(),
};
