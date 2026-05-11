package com.clipping.mcpserver.admin.dto

/**
 * 런타임 설정 조회 응답 DTO.
 */
data class RuntimeSettingsResponse(
    val defaultHoursBack: Int,
    val summaryInputMaxChars: Int,
    val digestMinImportanceScore: Float,
    val digestDefaultMaxItems: Int,
    val digestMaxMessageChars: Int,
    val digestItemSummaryMaxChars: Int,
    val digestKeywordMaxCount: Int,
    val jobWorkerBatchSize: Int,
    val jobMaxAttempts: Int,
    val jobInitialBackoffSeconds: Int,
    val slackBotToken: String,
    val slackBotTokenConfigured: Boolean,
    val slackDigestBlockKitTemplate: String,
    val slackAutoDigestEnabled: Boolean,
    val slackDigestCron: String,
    val slackAutoDigestMaxItems: Int,
    val slackAutoDigestUnsentOnly: Boolean,
    val slackDailyChannelMessageLimit: Int,
    val ralphOrchestrationEnabled: Boolean,
    val ralphLoopEnabled: Boolean,
    val ralphLoopMaxIterations: Int,
    val ralphLoopStopPhrase: String,
    val maintenanceMode: Boolean,
    val maintenanceMessage: String,
    val opsLogChannelId: String,
    val opsRequestChannelId: String,
    /** CRITICAL severity(토큰 만료/quota) 전용 Slack 채널 ID. 빈 값이면 opsLog 폴백. */
    val securityAlertChannelId: String,
    val opsNotificationProfile: String,
    val opsDailyForecastHour: Int,
    val opsWeeklyReportDay: String,
    val opsWeeklyReportHour: Int,
    val opsPipelineCooldownMinutes: Int,
    val opsIncidentWindowMinutes: Int,
    val opsIncidentThresholdCategories: Int,
    val opsScheduleMissGraceMinutes: Int,
    val opsBudgetWarnPct: Int,
    val opsBudgetCriticalPct: Int,
    val opsAdminBaseUrl: String?,
    val opsSilentHoursEnabled: Boolean,
    val opsRecoveryStreakThreshold: Int,
    val opsLogsEnabled: Boolean,
    val competitorWeeklyEnabled: Boolean,
    val competitorWeeklyChannelId: String,
    val competitorWeeklyDmMode: String,
    val competitorWeeklyDmUserIds: String,
    val competitorWeeklyDay: String,
    val competitorWeeklyHour: Int,
    /** 뉴스 검토 일괄 UX 기능 플래그 (PR D 점진 롤아웃용 킬 스위치). */
    val reviewBatchUxEnabled: Boolean,
    /** 뉴스 검토 페이지 카테고리별 top-N 샘플링 기본값 (0..100, 0=비활성). */
    val defaultReviewPerCategory: Int,
    /** RSS 아이템 보관 기간(일). 기본값 30, 범위 7..365. */
    val retentionRssItemsDays: Int,
    /** 배치 요약 보관 기간(일). 기본값 90, 범위 7..730. */
    val retentionBatchSummariesDays: Int,
    val updatedAt: String?
)

/**
 * 런타임 설정 변경 요청 DTO.
 */
data class UpdateRuntimeSettingsRequest(
    val defaultHoursBack: Int? = null,
    val summaryInputMaxChars: Int? = null,
    val digestMinImportanceScore: Float? = null,
    val digestDefaultMaxItems: Int? = null,
    val digestMaxMessageChars: Int? = null,
    val digestItemSummaryMaxChars: Int? = null,
    val digestKeywordMaxCount: Int? = null,
    val jobWorkerBatchSize: Int? = null,
    val jobMaxAttempts: Int? = null,
    val jobInitialBackoffSeconds: Int? = null,
    val slackBotToken: String? = null,
    val slackDigestBlockKitTemplate: String? = null,
    val slackAutoDigestEnabled: Boolean? = null,
    val slackDigestCron: String? = null,
    val slackAutoDigestMaxItems: Int? = null,
    val slackAutoDigestUnsentOnly: Boolean? = null,
    val slackDailyChannelMessageLimit: Int? = null,
    val ralphOrchestrationEnabled: Boolean? = null,
    val ralphLoopEnabled: Boolean? = null,
    val ralphLoopMaxIterations: Int? = null,
    val ralphLoopStopPhrase: String? = null,
    val maintenanceMode: Boolean? = null,
    val maintenanceMessage: String? = null,
    val opsLogChannelId: String? = null,
    val opsRequestChannelId: String? = null,
    /** 토큰 만료 등 CRITICAL severity 알림 전용 채널 ID. 빈 문자열은 opsLog 폴백. */
    val securityAlertChannelId: String? = null,
    val opsNotificationProfile: String? = null,
    val opsDailyForecastHour: Int? = null,
    val opsWeeklyReportDay: String? = null,
    val opsWeeklyReportHour: Int? = null,
    val opsPipelineCooldownMinutes: Int? = null,
    val opsIncidentWindowMinutes: Int? = null,
    val opsIncidentThresholdCategories: Int? = null,
    val opsScheduleMissGraceMinutes: Int? = null,
    val opsBudgetWarnPct: Int? = null,
    val opsBudgetCriticalPct: Int? = null,
    val opsAdminBaseUrl: String? = null,
    val opsSilentHoursEnabled: Boolean? = null,
    val opsRecoveryStreakThreshold: Int? = null,
    val opsLogsEnabled: Boolean? = null,
    val competitorWeeklyEnabled: Boolean? = null,
    val competitorWeeklyChannelId: String? = null,
    val competitorWeeklyDmMode: String? = null,
    val competitorWeeklyDmUserIds: String? = null,
    val competitorWeeklyDay: String? = null,
    val competitorWeeklyHour: Int? = null,
    /** 뉴스 검토 일괄 UX 기능 플래그 토글 값. */
    val reviewBatchUxEnabled: Boolean? = null,
    /** 뉴스 검토 페이지 카테고리별 top-N 기본값(0..100). */
    val defaultReviewPerCategory: Int? = null,
    /** RSS 아이템 보관 기간(일). null이면 변경 없음. 유효 범위 7..365. */
    val retentionRssItemsDays: Int? = null,
    /** 배치 요약 보관 기간(일). null이면 변경 없음. 유효 범위 7..730. */
    val retentionBatchSummariesDays: Int? = null
)

/**
 * Slack 연결 테스트 요청 DTO.
 */
data class SlackConnectionVerifyRequest(
    val slackBotToken: String? = null,
    val slackChannelId: String? = null
)

/**
 * Slack 연결 테스트 응답 DTO.
 */
data class SlackConnectionVerifyResponse(
    val ok: Boolean,
    val botUser: String?,
    val team: String?,
    val channelId: String?,
    val channelName: String?,
    val neededScopes: String?,
    val providedScopes: String?,
    val message: String,
    val warning: String?
)

/**
 * Block Kit 미리보기 요청 DTO.
 */
data class SlackBlockKitPreviewRequest(
    val template: String? = null,
    val slackChannelId: String? = null
)

/**
 * Block Kit 미리보기 응답 DTO.
 */
data class SlackBlockKitPreviewResponse(
    val valid: Boolean,
    val message: String,
    val renderedText: String,
    val blocks: List<Map<String, Any?>>,
    val placeholders: List<String>,
    val templateUsed: String,
    val defaultTemplate: String
)

/**
 * Block Kit 테스트 전송 요청 DTO.
 */
data class SlackBlockKitTestSendRequest(
    val template: String? = null,
    val slackChannelId: String? = null,
    val slackBotToken: String? = null
)

/**
 * Block Kit 테스트 전송 응답 DTO.
 */
data class SlackBlockKitTestSendResponse(
    val ok: Boolean,
    val message: String,
    val channelId: String,
    val messageTs: String?,
    val renderedText: String,
    val blocks: List<Map<String, Any?>>
)

/**
 * Slack Socket Mode 연결 테스트 요청 DTO.
 */
data class SlackSocketConnectionVerifyRequest(
    val slackAppToken: String? = null
)

/**
 * Slack Socket Mode 연결 테스트 응답 DTO.
 */
data class SlackSocketConnectionVerifyResponse(
    val ok: Boolean,
    val appId: String?,
    val socketUrl: String?,
    val message: String,
    val warning: String? = null
)

/**
 * Slack Socket Mode 상태 조회 응답 DTO.
 */
data class SlackSocketModeStatusResponse(
    val enabled: Boolean,
    val configured: Boolean,
    val connected: Boolean,
    val appId: String?,
    val socketUrl: String?,
    val lastEnvelopeId: String?,
    val lastError: String?
)

/**
 * 런타임 설정 변경 이력 응답 DTO.
 */
data class RuntimeSettingAuditResponse(
    val settingKey: String,
    val oldValue: String?,
    val newValue: String?,
    val action: String,
    val changedBy: String,
    val changedAt: String
)
