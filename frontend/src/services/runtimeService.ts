import { api } from "@/lib/kyInstance";
import type {
  RuntimeSettings,
  RuntimeSettingAudit,
  SlackConnectionVerifyResult,
  SlackBlockKitPreviewResult,
  SlackBlockKitTestSendResult,
  BlockedSlackChannel,
  SlackChannelItem,
  SlackChannelListResponse
} from "@/types/runtime";

export interface RuntimeSettingsUpdateRequest {
  defaultHoursBack?: number;
  summaryInputMaxChars?: number;
  digestMinImportanceScore?: number;
  digestDefaultMaxItems?: number;
  digestMaxMessageChars?: number;
  digestItemSummaryMaxChars?: number;
  digestKeywordMaxCount?: number;
  jobWorkerBatchSize?: number;
  jobMaxAttempts?: number;
  jobInitialBackoffSeconds?: number;
  slackBotToken?: string;
  slackDigestBlockKitTemplate?: string;
  slackAutoDigestEnabled?: boolean;
  slackDigestCron?: string;
  slackAutoDigestMaxItems?: number;
  slackAutoDigestUnsentOnly?: boolean;
  slackDailyChannelMessageLimit?: number;
  ralphOrchestrationEnabled?: boolean;
  ralphLoopEnabled?: boolean;
  ralphLoopMaxIterations?: number;
  ralphLoopStopPhrase?: string;
  opsLogChannelId?: string;
  opsRequestChannelId?: string;
  /** 토큰 만료·쿼터 소진 등 CRITICAL 전용 채널 (F8). 빈 값이면 opsLogChannelId 폴백. */
  securityAlertChannelId?: string;
  competitorWeeklyEnabled?: boolean;
  competitorWeeklyChannelId?: string;
  competitorWeeklyDmMode?: string;
  competitorWeeklyDmUserIds?: string;
  competitorWeeklyDay?: string;
  competitorWeeklyHour?: number;
  /** 뉴스 검토 일괄 UX 기능 플래그 토글 값 (PR D 이전에는 UI 노출 없음). */
  reviewBatchUxEnabled?: boolean;
  /** 뉴스 검토 페이지 카테고리별 top-N 기본값(0..100, 0=비활성). */
  defaultReviewPerCategory?: number;
  /** 원본 RSS 기사 보관 기간 (일). 7..365. */
  retentionRssItemsDays?: number;
  /** AI 요약 보관 기간 (일). 7..730. */
  retentionBatchSummariesDays?: number;
  // ── 운영 알림 프로필 ──────────────────────────────────────────────────────
  opsNotificationProfile?: "FULL" | "BATCHED" | "CRITICAL_ONLY";
  opsDailyForecastHour?: number;
  opsWeeklyReportDay?: string;
  opsWeeklyReportHour?: number;
  opsPipelineCooldownMinutes?: number;
  opsIncidentWindowMinutes?: number;
  opsIncidentThresholdCategories?: number;
  opsScheduleMissGraceMinutes?: number;
  opsBudgetWarnPct?: number;
  opsBudgetCriticalPct?: number;
  opsAdminBaseUrl?: string | null;
  opsSilentHoursEnabled?: boolean;
  opsRecoveryStreakThreshold?: number;
  opsLogsEnabled?: boolean;
}

export interface SlackVerifyRequest {
  slackBotToken?: string;
  slackChannelId?: string;
}

export interface SlackBlockKitPreviewRequest {
  template?: string;
  slackChannelId?: string;
}

export interface SlackBlockKitTestSendRequest {
  template?: string;
  slackChannelId?: string;
  slackBotToken?: string;
}

export const runtimeService = {
  getSettings: (): Promise<RuntimeSettings> => api.get("admin/runtime-settings").json(),

  updateSettings: (data: RuntimeSettingsUpdateRequest): Promise<RuntimeSettings> =>
    api.put("admin/runtime-settings", { json: data }).json(),

  resetSettings: (): Promise<RuntimeSettings> => api.post("admin/runtime-settings/reset", { json: {} }).json(),

  verifySlackConnection: (data: SlackVerifyRequest): Promise<SlackConnectionVerifyResult> =>
    api.post("admin/runtime-settings/slack/verify", { json: data }).json(),

  verifyUserSetupSlackConnection: (data: SlackVerifyRequest): Promise<SlackConnectionVerifyResult> =>
    api.post("user/setup/slack/verify", { json: data }).json(),

  previewSlackBlockKit: (data: SlackBlockKitPreviewRequest): Promise<SlackBlockKitPreviewResult> =>
    api.post("admin/runtime-settings/slack/block-kit/preview", { json: data }).json(),

  testSendSlackBlockKit: (data: SlackBlockKitTestSendRequest): Promise<SlackBlockKitTestSendResult> =>
    api.post("admin/runtime-settings/slack/block-kit/test-send", { json: data }).json(),

  listAudits: (limit = 30): Promise<RuntimeSettingAudit[]> =>
    api.get(`admin/runtime-settings/audits?limit=${encodeURIComponent(String(limit))}`).json(),

  listUserSetupSlackChannels: (
    type: "public_channel" | "private_channel",
    refresh = false
  ): Promise<SlackChannelListResponse> =>
    api.get(`user/setup/slack/channels?type=${encodeURIComponent(type)}${refresh ? "&refresh=true" : ""}`).json(),

  listAdminSlackChannels: (type: "public_channel" | "private_channel"): Promise<SlackChannelListResponse> =>
    api.get(`admin/slack/blocked-channels/available-channels?type=${encodeURIComponent(type)}`).json(),

  getUserSetupSlackChannelInfo: (channelId: string): Promise<SlackChannelItem> =>
    api.get(`user/setup/slack/channels/${encodeURIComponent(channelId)}`).json(),

  listBlockedChannels: (): Promise<BlockedSlackChannel[]> =>
    api.get("admin/slack/blocked-channels").json(),

  blockChannel: (
    channelId: string,
    channelName: string,
    isPrivate: boolean,
    reason?: string | null
  ): Promise<BlockedSlackChannel> =>
    api
      .post("admin/slack/blocked-channels", {
        json: { channelId, channelName, isPrivate, reason: reason ?? null }
      })
      .json(),

  unblockChannel: (channelId: string): Promise<void> =>
    api.delete(`admin/slack/blocked-channels/${encodeURIComponent(channelId)}`).then(() => undefined)
};
