export interface TrendSnapshot {
  id: string;
  periodType: "WEEKLY" | "MONTHLY";
  snapshotFrom: string;
  snapshotTo: string;
  categoryId?: string | null;
  categoryName: string;
  regionType: "ALL" | "GLOBAL" | "DOMESTIC";
  title: string;
  summary: string;
  keySignals: string[];
  actionItems: string[];
  sourceCount: number;
  itemCount: number;
  status: "DRAFT" | "PUBLISHED";
  templateType?: "BRIEFING" | "DETAILED" | "COMPETITOR";
  generatedBy?: string | null;
  publishedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TrendVisualCard {
  id: string;
  snapshotId: string;
  cardType: "INFO_CARD" | "COMIC_4" | "COMIC_8";
  title: string;
  summary: string;
  panels: string[];
  reviewStatus: "PENDING" | "APPROVED" | "REJECTED";
  reviewNote?: string | null;
  generatedBy?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  published: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ReportReleaseItem {
  summaryId: string;
  categoryId: string;
  title: string;
  sourceLink: string;
  importanceScore: number;
  releaseType: string;
  detectionReason: string;
  createdAt: string;
}
