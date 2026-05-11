export interface Competitor {
  id: string;
  name: string;
  aliases: string[];
  excludeKeywords: string[];
  tier: "DIRECT" | "ADJACENT" | "GLOBAL";
  isActive: boolean;
  rssFeeds: CompetitorRssFeed[];
  articleCount: number;
  last24hCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CompetitorRssFeed {
  id: string;
  feedUrl: string;
  label: string | null;
}

export interface CreateCompetitorRequest {
  name: string;
  aliases?: string[];
  excludeKeywords?: string[];
  tier?: string;
  rssFeeds?: { url: string; label?: string }[];
}

export interface UpdateCompetitorRequest {
  name?: string;
  aliases?: string[];
  excludeKeywords?: string[];
  tier?: string;
  isActive?: boolean;
  rssFeeds?: { url: string; label?: string }[];
}

export interface CompetitorTimelineItem {
  summaryId: string;
  competitorId: string;
  competitorName: string;
  title: string;
  summary: string;
  sourceLink: string;
  importanceScore: number;
  sentiment: string | null;
  eventType: string | null;
  createdAt: string;
}

export interface CompetitorTimelineResponse {
  items: CompetitorTimelineItem[];
}

export interface SovShareItem {
  competitorId: string;
  competitorName: string;
  articleCount: number;
  sharePercent: number;
}

export interface SovResponse {
  totalArticles: number;
  shares: SovShareItem[];
}

export interface KeywordPreviewItem {
  title: string;
  link: string;
  publishedAt: string | null;
}

export interface KeywordPreviewResponse {
  items: KeywordPreviewItem[];
  message: string;
}
