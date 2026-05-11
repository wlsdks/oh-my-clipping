// ── 뉴스 브리핑 ──

export interface BriefingItem {
  categoryId: string;
  categoryName: string;
  summaryDate: string;
  title: string;
  overallSummary: string;
  topicKeywords: string[];
  totalItems: number;
}

export interface DailyCount {
  date: string;
  count: number;
}

export interface KeywordTrendItem {
  keyword: string;
  dailyCounts: DailyCount[];
  totalCount: number;
  changeRate: number;
}

export interface KeywordTrendResponse {
  period: { from: string; to: string };
  keywords: KeywordTrendItem[];
}

export interface CompetitorSnapshotItem {
  summaryId: string;
  competitorName: string;
  title: string;
  sourceLink: string;
  importanceScore: number;
  createdAt: string;
}

export interface Competitor {
  id: string;
  name: string;
  keywords: string[];
  tier: "DIRECT" | "ADJACENT" | "GLOBAL";
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CompetitorTimelineItem {
  summaryId: string;
  competitorId: string;
  competitorName: string;
  title: string;
  summary: string;
  sourceLink: string;
  importanceScore: number;
  eventType: string | null;
  sentiment: string | null;
  createdAt: string;
}

export interface SovShareItem {
  competitorId: string | null;
  name: string;
  count: number;
  share: number;
}

export interface SovResponse {
  period: { from: string; to: string };
  totalArticles: number;
  shares: SovShareItem[];
}

export interface TopArticleItem {
  summaryId: string;
  title: string;
  sourceLink: string;
  importanceScore: number;
  keywords: string[];
  sentiment: string | null;
  eventType: string | null;
  createdAt: string;
}

export interface TopArticlesResponse {
  items: TopArticleItem[];
}

export interface SentimentDailyCount {
  date: string;
  positive: number;
  neutral: number;
  negative: number;
  total: number;
}

export interface SentimentSummary {
  positiveRate: number;
  neutralRate: number;
  negativeRate: number;
  dominantSentiment: string | null;
  changeFromPrevious: number;
}

export interface SentimentTrendResponse {
  period: { from: string; to: string };
  daily: SentimentDailyCount[];
  summary: SentimentSummary;
}

export interface ReportSettings {
  weeklyEnabled: boolean;
  weeklyDay: string;
  weeklyHour: number;
  weeklySlackChannelId: string | null;
  weeklyIncludeKeywordTrend: boolean;
  weeklyIncludeCompetitor: boolean;
  weeklyIncludeTopArticles: boolean;
  weeklyIncludeSentiment: boolean;
  monthlyEnabled: boolean;
  monthlyHour: number;
  monthlySlackChannelId: string | null;
}

// ── 경쟁사 논조 비교 ──

export interface CompetitorSentimentItem {
  competitorId: string;
  competitorName: string;
  positive: number;
  neutral: number;
  negative: number;
  total: number;
  positiveRate: number;
}

export interface CompetitorSentimentResponse {
  competitors: CompetitorSentimentItem[];
}

// ── 키워드 엔티티 분류 ──

export interface KeywordEntityItem {
  keyword: string;
  category: "PERSON" | "ORG" | "TECH" | "TOPIC" | "LOCATION";
  count: number;
}

export interface KeywordEntityResponse {
  items: KeywordEntityItem[];
}

// ── AI Q&A ──

export interface AiQaRelatedArticle {
  summaryId: string;
  title: string;
  sourceLink: string;
  relevanceReason: string;
}

export interface AiQaResponse {
  question: string;
  answer: string;
  relatedArticles: AiQaRelatedArticle[];
  contextArticleCount: number;
}
