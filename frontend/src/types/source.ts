export interface Source {
  id: string;
  name: string;
  url: string;
  sourceRegion: "GLOBAL" | "DOMESTIC" | "UNKNOWN";
  emoji?: string | null;
  isActive: boolean;
  crawlApproved: boolean;
  approvedBy?: string | null;
  approvedAt?: string | null;
  legalBasis: string;
  summaryAllowed: boolean;
  fulltextAllowed: boolean;
  termsReviewedAt?: string | null;
  /** 저작권 재검토 예정일 — termsReviewedAt + 180일 */
  expectedReviewAt?: string | null;
  reviewNotes?: string | null;
  verificationStatus: string;
  reliabilityScore: number;
  lastCrawlError?: string | null;
  crawlFailCount: number;
  lastSuccessAt?: string | null;
  curated: boolean;
  categoryId: string;
  createdAt: string;
  updatedAt: string;
}

/** 저작권 검토 상태 필터 — 서버 /api/admin/sources 의 complianceStatus 쿼리 파라미터 */
export type SourceComplianceStatus =
  | "EXPIRED"
  | "EXPIRING_SOON"
  | "NEVER_REVIEWED"
  | "VALID";

export interface SourcePage {
  content: Source[];
  totalCount: number;
  page: number;
  size: number;
}

export interface SourceAnalyticsResponse {
  sourceId: string;
  sourceName: string;
  days: number;
  totalArticles: number;
  avgArticlesPerDay: number;
  reliabilityScore: number;
  lastSuccessAt: string | null;
  crawlFailCount: number;
  dailyArticleCounts: { date: string; count: number }[];
}

export interface SourceCompliance {
  sourceId: string;
  legalBasis: string;
  summaryAllowed: boolean;
  fulltextAllowed: boolean;
  reviewNotes?: string | null;
  termsReviewedAt?: string | null;
}
