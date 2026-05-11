export interface StatRow {
  id: string;
  categoryId: string;
  statDate: string;
  itemsCollected: number;
  itemsDuplicates: number;
  itemsSummarized: number;
  itemsSent: number;
  slackSendAttempts: number;
  slackSendSuccesses: number;
  topKeywords: string[];
  avgImportanceScore: number;
}

export interface DailyOperationalKpiRow {
  statDate: string;
  categoryId?: string | null;
  itemsCollected: number;
  excludedCount: number;
  itemsDuplicates: number;
  noiseRate: number;
  duplicateRate: number;
  reviewLeadTimeHours: number;
  llmEstimatedCostUsd: number;
  sendAttempts: number;
  sendSuccesses: number;
  sendSuccessRate: number;
}

export interface UserMonthlyStatRow {
  id: string;
  categoryId: string;
  categoryName: string;
  statDate: string;
  itemsCollected: number;
  itemsSummarized: number;
  itemsSent: number;
  topKeywords: string[];
  avgImportanceScore: number;
}

export interface HotFeedbackItem {
  summaryId: string;
  title: string;
  sourceLink: string;
  likeCount: number;
  neutralCount: number;
  dislikeCount: number;
  totalCount: number;
  score: number;
}

export interface HotFeedbackResult {
  from: string;
  to: string;
  totalCandidates: number;
  items: HotFeedbackItem[];
}

export interface QualitySummary {
  from: string;
  to: string;
  days: number;
  itemsCollected: number;
  itemsSummarized: number;
  itemsSent: number;
  reviewPendingCount: number;
  reviewPendingRate: number;
  excludeRate: number;
  feedbackTotal: number;
  feedbackPositiveRate: number;
  sendSuccessRate: number;
  recommendations: string[];
}

export interface ArticleHistoryItem {
  id: string;
  title: string;
  summary: string;
  keywords: string[];
  importanceScore: number;
  sourceLink: string;
  categoryId: string;
  categoryName: string;
  isBookmarked: boolean;
  createdAt: string;
}

export interface ArticleHistoryPage {
  items: ArticleHistoryItem[];
  page: number;
  size: number;
  totalCount: number;
  totalPages: number;
}

export interface BookmarkToggleResult {
  summaryId: string;
  isBookmarked: boolean;
}

export interface UndeliveredArticle {
  summaryId: string;
  title: string;
  summary: string;
  sourceLink: string;
}

export interface UndeliveredDigest {
  deliveryLogId: string;
  categoryName: string;
  deliveryDate: string;
  deliveryTimeLabel: string;
  status: string;
  retryCount: number;
  maxRetries: number;
  articleCount: number;
  articles: UndeliveredArticle[];
}

export interface ArticleDetail {
  id: string;
  title: string;
  summary: string;
  insights: string | null;
  originalContent: string | null;
  keywords: string[];
  importanceScore: number;
  sourceLink: string;
  categoryId: string;
  categoryName: string;
  isBookmarked: boolean;
  createdAt: string;
  relatedArticles: ArticleHistoryItem[];
  // 경쟁사 기사 전용 (구독뉴스에서는 undefined)
  competitorName?: string;
  eventType?: string | null;
}
