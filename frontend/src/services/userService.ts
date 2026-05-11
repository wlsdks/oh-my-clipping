import { api } from "@/lib/kyInstance";
import type {
  UserAccountApproval,
  UserClippingRequest,
  UserSubscriptionPreference,
  DeliverySchedule,
  DeliveryScheduleRequest,
  BulkReviewRequest,
  BulkReviewResult
} from "@/types/user";
import type { Source } from "@/types/source";
import type { Category } from "@/types/category";
import type {
  CreateUserClippingRequestPayload,
  SourceCreateRequest,
  SourceApproveRequest,
  CategoryCreateRequest,
  ReviewUserClippingRequestPayload,
  ReviewUserAccountPayload,
  SubmitWithEntriesRequest,
  SubmitWithEntriesResponse,
} from "@/types/adminDto";

export type {
  CreateUserClippingRequestPayload,
  SourceCreateRequest,
  SourceApproveRequest,
  CategoryCreateRequest,
  ReviewUserClippingRequestPayload,
  ReviewUserAccountPayload,
} from "@/types/adminDto";

export interface ApproveClippingRequestData {
  legalBasis: "QUOTATION_ONLY" | "OPEN_LICENSE" | "LICENSED" | "PROHIBITED";
  summaryAllowed: boolean;
  fulltextAllowed: boolean;
  reviewNotes: string | null;
  responsibilityAcknowledged: boolean;
  /** 관리자가 승인 시점에 채널을 재지정할 경우 사용. undefined면 신청자가 선택한 채널을 사용한다. */
  overrideSlackChannelId?: string;
}

export interface RegisterWizardOwnershipPayload {
  requestName: string;
  sourceName: string;
  sourceUrl: string;
  slackChannelId: string;
  personaName: string;
  personaPrompt: string;
  summaryStyle?: string | null;
  targetAudience?: string | null;
  selectedPresetId?: string | null;
  categoryId: string;
  personaId?: string | null;
  sourceId?: string | null;
}

export interface UpdateUserSubscriptionPreferenceRequest {
  isActive?: boolean;
  maxItems?: number;
  excludeKeywords?: string[];
  includeThreshold?: number;
  deliveryDays?: string[];
  deliveryHour?: number;
  deliveryPreset?: string;
}

export interface CategoryBrowseItem {
  id: string;
  name: string;
  description: string | null;
  slackChannelId: string | null;
  subscriberCount: number;
  isSubscribed: boolean;
  deliveryHour: number | null;
  maxItems: number;
}

export interface SubscribeResponse {
  requestId: string;
  categoryId: string;
  status: string;
}

export interface UserAccountSummary {
  pendingCount: number;
  rejectedCount: number;
  weeklyProcessedCount: number;
}

export const userService = {
  // ── 사용자 클리핑 신청 API ──
  listClippingRequests: (): Promise<UserClippingRequest[]> => api.get("user/requests").json(),

  createClippingRequest: (data: CreateUserClippingRequestPayload): Promise<UserClippingRequest> =>
    api.post("user/requests", { json: data }).json(),

  createRequestWithEntries: (
    body: SubmitWithEntriesRequest
  ): Promise<SubmitWithEntriesResponse> =>
    api.post("user/requests/with-entries", { json: body }).json(),

  registerWizardOwnership: (data: RegisterWizardOwnershipPayload): Promise<UserClippingRequest> =>
    api.post("user/requests/wizard-ownership", { json: data }).json(),

  withdrawClippingRequest: (id: string): Promise<UserClippingRequest> =>
    api.post(`user/requests/${encodeURIComponent(id)}/withdraw`).json(),

  unsubscribeClippingRequest: (id: string): Promise<UserClippingRequest> =>
    api.post(`user/requests/${encodeURIComponent(id)}/unsubscribe`).json(),

  deleteClippingRequest: (id: string): Promise<void> =>
    api.delete(`user/requests/${encodeURIComponent(id)}/remove`).then(() => undefined),

  renameClippingRequest: (id: string, name: string): Promise<UserClippingRequest> =>
    api.patch(`user/requests/${encodeURIComponent(id)}/name`, { json: { name } }).json(),

  // ── 사용자 구독 설정 API ──
  getSubscriptionPreferences: (requestId: string): Promise<UserSubscriptionPreference> =>
    api.get(`user/subscriptions/${encodeURIComponent(requestId)}/preferences`).json(),

  updateSubscriptionPreferences: (
    requestId: string,
    data: UpdateUserSubscriptionPreferenceRequest
  ): Promise<UserSubscriptionPreference> =>
    api.put(`user/subscriptions/${encodeURIComponent(requestId)}/preferences`, { json: data }).json(),

  // ── 배송 스케줄 API ──
  getDeliverySchedule: (): Promise<DeliverySchedule> => api.get("user/delivery-schedule").json(),

  updateDeliverySchedule: (data: DeliveryScheduleRequest): Promise<DeliverySchedule> =>
    api.put("user/delivery-schedule", { json: data }).json(),

  // ── 사용자용 카테고리/소스 설정 API ──
  createSetupCategory: (data: CategoryCreateRequest): Promise<Category> =>
    api.post("user/setup/categories", { json: data }).json(),

  createSetupSource: (data: SourceCreateRequest): Promise<Source> =>
    api.post("user/setup/sources", { json: data }).json(),

  verifySetupSource: (id: string): Promise<{ status: string }> =>
    api.post(`user/setup/sources/${encodeURIComponent(id)}/verify`, { json: {} }).json(),

  approveSetupSource: (id: string, data: SourceApproveRequest): Promise<Source> =>
    api.post(`user/setup/sources/${encodeURIComponent(id)}/approve`, { json: data }).json(),

  validateSetupSourceUrl: (url: string): Promise<{ valid: boolean; status?: string; reason: string }> =>
    api.post("user/setup/sources/validate-url", { json: { url } }).json(),

  /** 주요 뉴스소스를 이름/별칭/도메인으로 검색한다. */
  searchKnownSources: (q?: string, region?: string): Promise<{ name: string; domain: string; region: string; aliases: string[] }[]> => {
    const params = new URLSearchParams();
    if (q) params.set("q", q);
    if (region) params.set("region", region);
    const qs = params.toString();
    return api.get(`user/setup/sources/known-sources${qs ? `?${qs}` : ""}`).json();
  },

  // ── 어드민: 사용자 계정 관리 API ──
  getUserAccountSummary: (): Promise<UserAccountSummary> =>
    api.get("admin/user-accounts/summary").json(),

  listAdminUserAccounts: (
    status?: string,
    options?: { personaId?: string }
  ): Promise<UserAccountApproval[]> => {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (options?.personaId) params.set("personaId", options.personaId);
    const qs = params.toString();
    return api.get(`admin/user-accounts${qs ? `?${qs}` : ""}`).json();
  },

  approveAdminUserAccount: (id: string, data: ReviewUserAccountPayload = {}): Promise<UserAccountApproval> =>
    api.post(`admin/user-accounts/${encodeURIComponent(id)}/approve`, { json: data }).json(),

  rejectAdminUserAccount: (id: string, data: ReviewUserAccountPayload): Promise<UserAccountApproval> =>
    api.post(`admin/user-accounts/${encodeURIComponent(id)}/reject`, { json: data }).json(),

  withdrawAdminUserAccount: (id: string, data: ReviewUserAccountPayload = {}): Promise<UserAccountApproval> =>
    api.post(`admin/user-accounts/${encodeURIComponent(id)}/withdraw`, { json: data }).json(),

  bulkApproveAdminUserAccounts: (data: BulkReviewRequest): Promise<BulkReviewResult> =>
    api.post("admin/user-accounts/bulk-approve", { json: data }).json(),

  bulkRejectAdminUserAccounts: (data: BulkReviewRequest): Promise<BulkReviewResult> =>
    api.post("admin/user-accounts/bulk-reject", { json: data }).json(),

  resetAdminUserPassword: (id: string): Promise<{ tempPassword: string; message: string; slackDmSent: boolean }> =>
    api.post(`admin/user-accounts/${encodeURIComponent(id)}/reset-password`).json(),

  // ── 어드민: 사용자 신청 관리 API ──
  listAdminClippingRequests: (status?: string): Promise<UserClippingRequest[]> => {
    const query = status ? `?status=${encodeURIComponent(status)}` : "";
    return api.get(`admin/user-requests${query}`).json();
  },

  approveAdminClippingRequest: (
    id: string,
    data: ApproveClippingRequestData
  ): Promise<UserClippingRequest> =>
    api.post(`admin/user-requests/${encodeURIComponent(id)}/approve`, { json: data }).json(),

  rejectAdminClippingRequest: (id: string, data: ReviewUserClippingRequestPayload): Promise<UserClippingRequest> =>
    api.post(`admin/user-requests/${encodeURIComponent(id)}/reject`, { json: data }).json(),

  // ── 카테고리 탐색/구독 API ──
  browseCategories: (): Promise<CategoryBrowseItem[]> => api.get("user/categories/browse").json(),

  subscribeCategoryDm: (categoryId: string): Promise<SubscribeResponse> =>
    api.post(`user/categories/${encodeURIComponent(categoryId)}/subscribe`, { json: { slackChannelId: "" } }).json(),

  // ── 사용자 계정 설정 API ──
  updateSlackMemberId: (slackMemberId: string): Promise<{ slackMemberId: string }> =>
    api.patch("user/account/slack", { json: { slackMemberId } }).json(),

  /**
   * V129: 사용자 본인 프로필(부서/팀 FK) 수정.
   *
   * - `undefined` / 필드 미포함 → 변경 없음
   * - `""` (빈 문자열) → 해당 필드 초기화 (null 저장)
   * - 값이 있으면 서버에서 team.departmentId == departmentId 일관성 검증 후 저장하며
   *   legacy 이름 캐시 (department/team 문자열) 도 함께 동기화된다.
   *
   * 응답은 FK id + JOIN 된 이름을 함께 돌려준다.
   */
  updateProfile: (data: {
    departmentId?: string;
    teamId?: string;
  }): Promise<{
    departmentId: string | null;
    departmentName: string | null;
    teamId: string | null;
    teamName: string | null;
  }> =>
    api.patch("user/account/profile", { json: data }).json(),

  /**
   * 개인정보 export 다운로드. 서버가 Content-Disposition 헤더로 파일명을 지정하므로
   * Blob 으로 수신해 브라우저 다운로드를 트리거한다.
   *
   * @param format "json" 또는 "csv"
   */
  downloadPersonalData: async (format: "json" | "csv"): Promise<{ blob: Blob; filename: string }> => {
    const response = await api.get("user/account/data-export", {
      searchParams: { format },
      timeout: 30_000
    });
    const blob = await response.blob();
    // Content-Disposition: attachment; filename="personal_data_2026-04-17.json" 에서 파일명만 추출.
    const disposition = response.headers.get("content-disposition") ?? "";
    const match = disposition.match(/filename="?([^";]+)"?/i);
    const fallback = `personal_data.${format}`;
    return { blob, filename: match?.[1] ?? fallback };
  }
};
