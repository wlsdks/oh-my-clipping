export interface SourceQualityRow {
  sourceId: string | null;
  sourceName: string;
  delivered: number;
  uniqueUserClicks: number;
  clickRatePct: number | null;
  likes: number;
  dislikes: number;
  likeRatePct: number | null;
  statusLabel: "normal" | "review" | "default";
  // 비활성 소스 기본 숨김 (D6) + activate/deactivate mutation 의 expectedUpdatedAt 용.
  // 수동 URL (sourceId = null) 은 서버에서 true / EPOCH("1970-01-01T00:00:00Z") fallback.
  isActive: boolean;
  updatedAt: string; // ISO 8601
}

export interface SourceQualitySummary {
  sourceQuality: SourceQualityRow[];
}

export type SourceQualityPeriod = "7d" | "14d" | "28d" | "90d";
