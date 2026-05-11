import { api } from "@/lib/kyInstance";
import type { TrendSnapshot, TrendVisualCard } from "@/types/visualCard";

export interface RunTrendSnapshotRequest {
  periodType: "WEEKLY" | "MONTHLY";
  categoryId?: string | null;
  regionType?: "ALL" | "GLOBAL" | "DOMESTIC";
  generatedBy?: string | null;
}

export interface GenerateVisualCardRequest {
  cardType: "INFO_CARD" | "COMIC_4" | "COMIC_8";
  generatedBy?: string | null;
}

export interface ReviewVisualCardRequest {
  approved: boolean;
  reviewNote?: string | null;
  reviewedBy?: string | null;
  publish?: boolean;
}

export const visualCardService = {
  runTrendSnapshot: (data: RunTrendSnapshotRequest): Promise<TrendSnapshot> =>
    api.post("admin/trend-snapshots/run", { json: data, timeout: 120_000 }).json(),

  listTrendSnapshots: (params?: {
    periodType?: string;
    categoryId?: string;
    regionType?: string;
    status?: string;
    limit?: number;
  }): Promise<TrendSnapshot[]> => {
    const query = new URLSearchParams();
    if (params?.periodType) query.set("periodType", params.periodType);
    if (params?.categoryId) query.set("categoryId", params.categoryId);
    if (params?.regionType) query.set("regionType", params.regionType);
    if (params?.status) query.set("status", params.status);
    if (typeof params?.limit === "number") query.set("limit", String(params.limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return api.get(`admin/trend-snapshots${suffix}`).json();
  },

  publishTrendSnapshot: (snapshotId: string, publishedBy?: string | null): Promise<TrendSnapshot> =>
    api
      .post(`admin/trend-snapshots/${encodeURIComponent(snapshotId)}/publish`, {
        json: { publishedBy: publishedBy ?? null }
      })
      .json(),

  generateTrendVisual: (snapshotId: string, data: GenerateVisualCardRequest): Promise<TrendVisualCard> =>
    api.post(`admin/trend-snapshots/${encodeURIComponent(snapshotId)}/generate-visual`, { json: data, timeout: 120_000 }).json(),

  listTrendVisualCards: (params?: {
    snapshotId?: string;
    reviewStatus?: string;
    limit?: number;
  }): Promise<TrendVisualCard[]> => {
    const query = new URLSearchParams();
    if (params?.reviewStatus) query.set("reviewStatus", params.reviewStatus);
    if (typeof params?.limit === "number") query.set("limit", String(params.limit));
    if (params?.snapshotId) {
      const suffix = query.toString() ? `?${query.toString()}` : "";
      return api.get(`admin/trend-snapshots/${encodeURIComponent(params.snapshotId)}/visuals${suffix}`).json();
    }
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return api.get(`admin/visual-cards${suffix}`).json();
  },

  reviewTrendVisualCard: (cardId: string, data: ReviewVisualCardRequest): Promise<TrendVisualCard> =>
    api.post(`admin/visual-cards/${encodeURIComponent(cardId)}/review`, { json: data }).json()
};
