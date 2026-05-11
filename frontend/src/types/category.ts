export type CategoryStatus = "ACTIVE" | "PAUSED";

/**
 * Phase 3 PR1: 구독 목적 분류.
 * 백엔드 `batch_categories.purpose` CHECK 제약과 일치.
 */
export type CategoryPurpose = "SALES" | "RESEARCH" | "COMPETITIVE" | "CUSTOMER_CARE" | "OTHER";

/** 사용자에게 노출할 purpose 레이블 — 수정 폼 Select 에서 사용. */
export const CATEGORY_PURPOSE_LABELS: Record<CategoryPurpose, string> = {
  SALES: "영업",
  RESEARCH: "리서치",
  COMPETITIVE: "경쟁사 모니터링",
  CUSTOMER_CARE: "고객 케어",
  OTHER: "기타"
};

export interface Category {
  id: string;
  name: string;
  description?: string | null;
  slackChannelId?: string | null;
  isActive: boolean;
  isPublic: boolean;
  maxItems: number;
  personaId?: string | null;
  sourceCount: number;
  subscriberCount: number;
  lastDeliveryAt?: string | null;
  errorSourceCount: number;
  createdAt: string;
  updatedAt: string;
  status: CategoryStatus;
  pausedAt?: string | null;
  /** V123(Phase 3 PR1): 분석 목적 분류 (optional). */
  purpose?: CategoryPurpose | null;
  /** V123(Phase 3 PR1): 구독 배경 (자유 텍스트, optional). */
  background?: string | null;
  /** V123(Phase 3 PR1): 해결하려는 문제 (자유 텍스트, optional). */
  problemStatement?: string | null;
}

export interface CategoryRule {
  categoryId: string;
  includeKeywords: string[];
  excludeKeywords: string[];
  riskTags: string[];
  /**
   * 자동 EXCLUDE 대상 event_type 블랙리스트. 빈 배열이면 룰 비활성.
   * 백엔드 `CategoryRuleResponse.excludeEventTypes` 와 1:1. 자동 제외 감사 드로어의
   * 룰 근거 섹션에서 현재 차단 중인 타입을 표시할 때 사용한다.
   */
  excludeEventTypes: string[];
  includeThreshold: number;
  reviewThreshold: number;
  uncertainToReview: boolean;
  autoExcludeEnabled: boolean;
  revision: number;
  updatedBy?: string | null;
  updatedAt: string;
}

export interface CategoryPage {
  content: Category[];
  totalCount: number;
  page: number;
  size: number;
}

// ── Rule Stats Types ──

export interface RuleStatsResponse {
  totalIncluded: number;
  totalReview: number;
  totalExcluded: number;
  perCategory: CategoryRuleStat[];
}

export interface CategoryRuleStat {
  categoryId: string;
  categoryName: string;
  included: number;
  review: number;
  excluded: number;
  hasRule: boolean;
}

export interface ExcludedItemsResponse {
  total: number;
  items: ExcludedItem[];
}

export interface ExcludedItem {
  title: string;
  reason: string;
  matchedKeyword: string | null;
  score: number;
  excludedAt: string;
}

// ── Category CRUD Types ──

export interface CreateCategoryRequest {
  name: string;
  description?: string | null;
  slackChannelId?: string | null;
  maxItems?: number;
  personaId?: string | null;
  /** V123(Phase 3 PR1): optional metadata fields. */
  purpose?: CategoryPurpose | null;
  background?: string | null;
  problemStatement?: string | null;
}

export interface UpdateCategoryRequest {
  name?: string;
  description?: string | null;
  slackChannelId?: string | null;
  isActive?: boolean;
  isPublic?: boolean;
  maxItems?: number;
  expectedUpdatedAt?: string | null;
  /**
   * V123(Phase 3 PR1): 메타데이터 수정.
   * 의미: `undefined` / 필드 미포함 → 변경 없음. `""` (빈 문자열) → 해당 필드 초기화(null 저장).
   */
  purpose?: CategoryPurpose | "" | null;
  background?: string | null;
  problemStatement?: string | null;
}
