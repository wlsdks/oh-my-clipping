/**
 * Shared DTO interfaces used across multiple services and the admin API.
 * These were previously duplicated in userService.ts, sourceService.ts,
 * and shared/api/adminApi.ts.
 */

export interface CreateUserClippingRequestPayload {
  requestName: string;
  sourceName: string;
  sourceUrl: string;
  slackChannelId: string;
  personaName: string;
  personaPrompt: string;
  summaryStyle?: string | null;
  targetAudience?: string | null;
  selectedPresetId?: string | null;
  requestNote?: string | null;
}

export interface SourceCreateRequest {
  name: string;
  url: string;
  sourceRegion?: "GLOBAL" | "DOMESTIC" | "UNKNOWN" | null;
  emoji?: string | null;
  categoryId: string;
  legalBasis?: string | null;
  summaryAllowed?: boolean;
  fulltextAllowed?: boolean;
  reviewNotes?: string | null;
  crawlApproved?: boolean;
  approvedBy?: string | null;
}

export interface SourceApproveRequest {
  approved: boolean;
  approvedBy?: string | null;
  legalBasis?: string | null;
  summaryAllowed?: boolean;
  reviewNotes?: string | null;
  expectedUpdatedAt?: string | null;
}

export interface ReviewUserClippingRequestPayload {
  reviewNote?: string | null;
}

export interface ReviewUserAccountPayload {
  reviewNote?: string | null;
}

export interface CategoryCreateRequest {
  name: string;
  description?: string | null;
  slackChannelId?: string | null;
  maxItems?: number;
  personaId?: string | null;
}

// Account-Based News Intelligence — submit-with-entries API types

export interface EntryDto {
  value: string;
  type: "keyword" | "company";
  stockCode?: string;
}

export interface SubmitWithEntriesRequest {
  categoryName: string;
  entries: EntryDto[];
  description?: string;
  /** 사용자가 위자드에서 고른 발송 프리셋 — WEEKDAYS / EVERYDAY / CUSTOM */
  deliveryPreset?: string;
  /** 프리셋이 CUSTOM 일 때 선택 요일, 또는 명시적 요일 지정. */
  deliveryDays?: string[];
  /** 발송 시각 (0~23, KST 기준 시). */
  deliveryHour?: number;
}

export interface EntryError {
  index: number;
  value: string;
  reason: string; // open string — Backend may add new reasons; localizeReason() has unknown fallback
}

export const ENTRY_ERROR_REASONS = {
  COMPETITOR_WATCHLIST_CONFLICT: "COMPETITOR_WATCHLIST_CONFLICT",
  INVALID_STOCK_CODE: "INVALID_STOCK_CODE",
  DUPLICATE_IN_REQUEST: "DUPLICATE_IN_REQUEST",
  RATE_LIMITED: "RATE_LIMITED",
  VALIDATION_FAILED: "VALIDATION_FAILED",
} as const;

export type EntryErrorReason = (typeof ENTRY_ERROR_REASONS)[keyof typeof ENTRY_ERROR_REASONS];

export interface SubmitWithEntriesResponse {
  requestId: string;
  status: "submitted" | "partial" | "rejected";
  errors: EntryError[];
}
