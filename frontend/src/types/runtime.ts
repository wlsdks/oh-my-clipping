export interface RuntimeSettings {
  defaultHoursBack: number;
  summaryInputMaxChars: number;
  digestMinImportanceScore: number;
  digestDefaultMaxItems: number;
  digestMaxMessageChars: number;
  digestItemSummaryMaxChars: number;
  digestKeywordMaxCount: number;
  jobWorkerBatchSize: number;
  jobMaxAttempts: number;
  jobInitialBackoffSeconds: number;
  slackBotToken: string;
  slackBotTokenConfigured: boolean;
  slackDigestBlockKitTemplate: string;
  slackAutoDigestEnabled: boolean;
  slackDigestCron: string;
  slackAutoDigestMaxItems: number;
  slackAutoDigestUnsentOnly: boolean;
  slackDailyChannelMessageLimit: number;
  ralphOrchestrationEnabled: boolean;
  ralphLoopEnabled: boolean;
  ralphLoopMaxIterations: number;
  ralphLoopStopPhrase: string;
  maintenanceMode: boolean;
  maintenanceMessage: string;
  opsLogChannelId: string;
  opsRequestChannelId: string;
  /** 토큰 만료·쿼터 소진 등 CRITICAL 알림 전용 채널 (F8). 빈 값이면 opsLogChannelId로 폴백. */
  securityAlertChannelId: string;
  competitorWeeklyEnabled: boolean;
  competitorWeeklyChannelId: string;
  competitorWeeklyDmMode: "off" | "all" | "selected";
  competitorWeeklyDmUserIds: string;
  competitorWeeklyDay: string;
  competitorWeeklyHour: number;
  /**
   * 뉴스 검토 일괄 UX 기능 플래그 (PR D 점진 롤아웃용 킬 스위치).
   * 현재는 백엔드 토글만 제공되며, 관리자 UI는 PR D에서 도입한다.
   */
  reviewBatchUxEnabled: boolean;
  /**
   * 뉴스 검토 페이지 카테고리별 top-N 샘플링 기본값 (0..100, 0=비활성).
   * 전체 조회 시 각 카테고리에서 이 수만큼 뽑아 공평한 노출을 만든다.
   */
  defaultReviewPerCategory: number;
  /** 원본 RSS 기사 보관 기간 (일). 7..365. 북마크·피드백 있는 요약은 무기한 보관. */
  retentionRssItemsDays: number;
  /** AI 요약 보관 기간 (일). 7..730. 북마크·피드백 있는 요약은 무기한 보관. */
  retentionBatchSummariesDays: number;

  // ── 운영 알림 프로필 ─────────────────────────────────────────────────────
  /** 알림 전송 프로필 (FULL / BATCHED / CRITICAL_ONLY) */
  opsNotificationProfile?: "FULL" | "BATCHED" | "CRITICAL_ONLY";
  /** 일일 Forecast Slack 알림 발송 시각 (KST, 0–23) */
  opsDailyForecastHour?: number;
  /** 주간 리포트 발송 요일 */
  opsWeeklyReportDay?: "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
  /** 주간 리포트 발송 시각 (KST, 0–23) */
  opsWeeklyReportHour?: number;
  /** 파이프라인 알림 쿨다운 (분) */
  opsPipelineCooldownMinutes?: number;
  /** 인시던트 감지 윈도우 (분) */
  opsIncidentWindowMinutes?: number;
  /** 인시던트 임계 카테고리 수 */
  opsIncidentThresholdCategories?: number;
  /** 스케줄 미스 허용 유예 시간 (분) */
  opsScheduleMissGraceMinutes?: number;
  /** 예산 경고 임계 비율 (%) */
  opsBudgetWarnPct?: number;
  /** 예산 위험 임계 비율 (%) */
  opsBudgetCriticalPct?: number;
  /** 관리자 대시보드 Base URL (null이면 Slack 버튼 생략) */
  opsAdminBaseUrl?: string | null;
  /** Silent Hours 활성 (22:00–08:00 KST, 주말 알림 억제) */
  opsSilentHoursEnabled?: boolean;
  /** 연속 성공 복구 판정 임계 횟수 */
  opsRecoveryStreakThreshold?: number;
  /** 운영 알림 전체 Kill Switch (false = 어떤 알림도 발송 안 함) */
  opsLogsEnabled?: boolean;

  updatedAt?: string | null;
}

export interface RuntimeSettingAudit {
  settingKey: string;
  oldValue?: string | null;
  newValue?: string | null;
  action: string;
  changedBy: string;
  changedAt: string;
}

export interface SlackConnectionVerifyResult {
  ok: boolean;
  botUser?: string | null;
  team?: string | null;
  channelId?: string | null;
  channelName?: string | null;
  /** missing_scope 응답 시 백엔드가 안내하는 필요한 OAuth 스코프 (콤마 구분) */
  neededScopes?: string | null;
  /** 현재 토큰이 가진 OAuth 스코프 (콤마 구분) */
  providedScopes?: string | null;
  message: string;
  warning?: string | null;
}

export interface SlackBlockKitPreviewResult {
  valid: boolean;
  message: string;
  renderedText: string;
  blocks: Record<string, unknown>[];
  placeholders: string[];
  templateUsed: string;
  defaultTemplate: string;
}

export interface SlackBlockKitTestSendResult {
  ok: boolean;
  message: string;
  channelId: string;
  messageTs?: string | null;
  renderedText: string;
  blocks: Record<string, unknown>[];
}

export interface SlackChannelItem {
  id: string;
  name: string;
  isPrivate: boolean;
}

export interface SlackChannelListResponse {
  channels: SlackChannelItem[];
  slackConnectRequired: boolean;
  totalBeforeFilter?: number;
}

export interface BlockedSlackChannel {
  id: string;
  channelId: string;
  channelName: string;
  isPrivate: boolean;
  blockedByUserId: string;
  blockedAt: string;
  reason?: string | null;
}
