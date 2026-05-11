export type DeliveryPreset = "WEEKDAYS" | "EVERYDAY" | "CUSTOM";

export interface DeliverySchedule {
  deliveryDays: string[];
  deliveryHour: number;
  preset: DeliveryPreset;
  updatedAt: string;
}

export interface DeliveryScheduleRequest {
  deliveryDays: string[];
  deliveryHour: number;
  preset: DeliveryPreset;
}

export interface UserAccountApproval {
  id: string;
  username: string;
  displayName?: string | null;
  department?: string | null;
  isActive: boolean;
  approvalStatus: "PENDING" | "APPROVED" | "REJECTED";
  approvalNote?: string | null;
  approvedByUserId?: string | null;
  approvedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  lastLoginAt?: string | null;
  subscriptionCount?: number;
  recentActivitySummary?: string | null;
  role: string;
  approvedByUsername?: string | null;
}

export interface BulkReviewRequest {
  ids: string[];
  reviewNote?: string | null;
}

export interface BulkReviewResult {
  succeeded: string[];
  failed: Array<{ id: string; reason: string; code: string }>;
}

export interface UserClippingRequest {
  id: string;
  requesterUserId: string;
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
  status: "PENDING" | "APPROVED" | "REJECTED" | "WITHDRAWN";
  reviewNote?: string | null;
  reviewedByUserId?: string | null;
  reviewedAt?: string | null;
  approvedCategoryId?: string | null;
  approvedCategoryName?: string | null;
  approvedPersonaId?: string | null;
  approvedSourceId?: string | null;
  createdAt: string;
  updatedAt: string;
  deliveryState:
    | "PENDING_REVIEW"
    | "REJECTED"
    | "WITHDRAWN"
    | "ACTIVE"
    | "PAUSED"
    | "VERIFYING_SOURCE"
    | "ACTION_REQUIRED";
  collectingReady: boolean;
  totalSourceCount: number;
  readySourceCount: number;
  representativeSourceVerificationStatus?: string | null;
}

export interface UserSubscriptionPreference {
  requestId: string;
  categoryId: string;
  requestName: string;
  isActive: boolean;
  maxItems: number;
  excludeKeywords: string[];
  includeThreshold: number;
  deliveryDays: string[] | null;
  deliveryHour: number | null;
  deliveryPreset: string | null;
  updatedAt: string;
}
