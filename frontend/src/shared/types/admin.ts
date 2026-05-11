/**
 * Re-export hub for admin types.
 *
 * Types are canonically defined in `@/types/*` files.
 * This module re-exports them so that existing `import { X } from "@/shared/types/admin"` statements
 * continue to work without changes.
 */

// ── Category ──
export type { Category, CategoryRule } from "@/types/category";

// ── Review ──
export type { ReviewQueueItem, ReviewItemAudit } from "@/types/review";

// ── Persona ──
export type { Persona } from "@/types/persona";

// ── Source ──
export type { Source } from "@/types/source";

// ── Dashboard / Pipeline ──
export type {
  ClippingSetting,
  DigestItemResult,
  DigestResult,
  CollectCategoryResult,
  CollectResult,
  SummarizeCategoryResult,
  SummarizeResult,
  PipelineRunResult,
  PipelineStepTrace,
} from "@/types/dashboard";

// ── Runtime ──
export type {
  RuntimeSettings,
  RuntimeSettingAudit,
  SlackConnectionVerifyResult,
  SlackBlockKitPreviewResult,
  SlackBlockKitTestSendResult,
} from "@/types/runtime";

// ── Insight / Stats ──
export type {
  StatRow,
  DailyOperationalKpiRow,
  UserMonthlyStatRow,
  HotFeedbackItem,
  HotFeedbackResult,
  QualitySummary,
  ArticleHistoryItem,
  ArticleHistoryPage,
  BookmarkToggleResult,
  ArticleDetail,
} from "@/types/insight";

// ── User ──
export type {
  DeliveryPreset,
  DeliverySchedule,
  DeliveryScheduleRequest,
  UserClippingRequest,
  UserSubscriptionPreference,
  UserAccountApproval,
} from "@/types/user";

// ── Visual Cards / Trends ──
export type {
  TrendSnapshot,
  TrendVisualCard,
  ReportReleaseItem,
} from "@/types/visualCard";

// ── News Report / Intelligence ──
export type {
  BriefingItem,
  DailyCount,
  KeywordTrendItem,
  KeywordTrendResponse,
  CompetitorSnapshotItem,
  CompetitorTimelineItem,
  SovShareItem,
  SovResponse,
  TopArticleItem,
  TopArticlesResponse,
  SentimentDailyCount,
  SentimentSummary,
  SentimentTrendResponse,
  CompetitorSentimentItem,
  CompetitorSentimentResponse,
  ReportSettings,
  KeywordEntityItem,
  KeywordEntityResponse,
  AiQaRelatedArticle,
  AiQaResponse,
} from "@/types/newsReport";

