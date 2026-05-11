package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.RuntimeSettingAuditResponse
import com.clipping.mcpserver.admin.dto.RuntimeSettingsResponse
import com.clipping.mcpserver.admin.dto.SlackBlockKitPreviewRequest
import com.clipping.mcpserver.admin.dto.SlackBlockKitPreviewResponse
import com.clipping.mcpserver.admin.dto.SlackBlockKitTestSendRequest
import com.clipping.mcpserver.admin.dto.SlackBlockKitTestSendResponse
import com.clipping.mcpserver.admin.dto.SlackConnectionVerifyRequest
import com.clipping.mcpserver.admin.dto.SlackConnectionVerifyResponse
import com.clipping.mcpserver.admin.dto.SlackSocketConnectionVerifyRequest
import com.clipping.mcpserver.admin.dto.SlackSocketConnectionVerifyResponse
import com.clipping.mcpserver.admin.dto.SlackSocketModeStatusResponse
import com.clipping.mcpserver.admin.dto.UpdateRuntimeSettingsRequest
import com.clipping.mcpserver.config.RedisRateLimitService
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.RateLimitExceededException
import com.clipping.mcpserver.service.RuntimeSettingService
import com.clipping.mcpserver.service.SlackBlockKitTemplateService
import com.clipping.mcpserver.service.SlackMessageSender
import com.clipping.mcpserver.service.SlackSocketModeService
import com.clipping.mcpserver.support.SlackChannelIdNormalizer
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 런타임 운영 설정을 관리하는 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/admin/runtime-settings")
class RuntimeSettingsAdminController(
    private val runtimeSettingService: RuntimeSettingService,
    private val slackBlockKitTemplateService: SlackBlockKitTemplateService,
    private val slackMessageSender: SlackMessageSender,
    private val slackSocketModeService: SlackSocketModeService,
    private val redisRateLimitService: RedisRateLimitService
) {

    companion object {
        /** Slack 검증/테스트 전송 엔드포인트 공통 분당 호출 한도. */
        private const val SLACK_ACTION_MAX_PER_MINUTE = 10
        private const val SLACK_ACTION_WINDOW_SECONDS = 60L
        private const val SLACK_ACTION_KEY_PREFIX = "rl:admin:runtime-settings:slack:"
    }
    /**
     * 설정 조회 API.
     */

    @GetMapping
    fun getSettings(): RuntimeSettingsResponse =
        runtimeSettingService.current().toResponse()

    /**
     * 설정을 부분 수정합니다.
     */
    @PutMapping
    fun updateSettings(
        @RequestBody request: UpdateRuntimeSettingsRequest,
        authentication: Authentication
    ): RuntimeSettingsResponse {
        return runtimeSettingService.update(
            RuntimeSettingService.RuntimeSettingsUpdate(
                defaultHoursBack = request.defaultHoursBack,
                summaryInputMaxChars = request.summaryInputMaxChars,
                digestMinImportanceScore = request.digestMinImportanceScore,
                digestDefaultMaxItems = request.digestDefaultMaxItems,
                digestMaxMessageChars = request.digestMaxMessageChars,
                digestItemSummaryMaxChars = request.digestItemSummaryMaxChars,
                digestKeywordMaxCount = request.digestKeywordMaxCount,
                jobWorkerBatchSize = request.jobWorkerBatchSize,
                jobMaxAttempts = request.jobMaxAttempts,
                jobInitialBackoffSeconds = request.jobInitialBackoffSeconds,
                slackBotToken = request.slackBotToken,
                slackDigestBlockKitTemplate = request.slackDigestBlockKitTemplate,
                slackAutoDigestEnabled = request.slackAutoDigestEnabled,
                slackDigestCron = request.slackDigestCron,
                slackAutoDigestMaxItems = request.slackAutoDigestMaxItems,
                slackAutoDigestUnsentOnly = request.slackAutoDigestUnsentOnly,
                slackDailyChannelMessageLimit = request.slackDailyChannelMessageLimit,
                ralphOrchestrationEnabled = request.ralphOrchestrationEnabled,
                ralphLoopEnabled = request.ralphLoopEnabled,
                ralphLoopMaxIterations = request.ralphLoopMaxIterations,
                ralphLoopStopPhrase = request.ralphLoopStopPhrase,
                maintenanceMode = request.maintenanceMode,
                maintenanceMessage = request.maintenanceMessage,
                opsLogChannelId = request.opsLogChannelId,
                opsRequestChannelId = request.opsRequestChannelId,
                securityAlertChannelId = request.securityAlertChannelId,
                opsNotificationProfile = request.opsNotificationProfile?.let {
                    runCatching { com.clipping.mcpserver.service.OpsNotificationProfile.valueOf(it) }.getOrNull()
                },
                opsDailyForecastHour = request.opsDailyForecastHour,
                opsWeeklyReportDay = request.opsWeeklyReportDay?.let {
                    runCatching { java.time.DayOfWeek.valueOf(it) }.getOrNull()
                },
                opsWeeklyReportHour = request.opsWeeklyReportHour,
                opsPipelineCooldownMinutes = request.opsPipelineCooldownMinutes,
                opsIncidentWindowMinutes = request.opsIncidentWindowMinutes,
                opsIncidentThresholdCategories = request.opsIncidentThresholdCategories,
                opsScheduleMissGraceMinutes = request.opsScheduleMissGraceMinutes,
                opsBudgetWarnPct = request.opsBudgetWarnPct,
                opsBudgetCriticalPct = request.opsBudgetCriticalPct,
                opsAdminBaseUrl = request.opsAdminBaseUrl,
                opsSilentHoursEnabled = request.opsSilentHoursEnabled,
                opsRecoveryStreakThreshold = request.opsRecoveryStreakThreshold,
                opsLogsEnabled = request.opsLogsEnabled,
                competitorWeeklyEnabled = request.competitorWeeklyEnabled,
                competitorWeeklyChannelId = request.competitorWeeklyChannelId,
                competitorWeeklyDmMode = request.competitorWeeklyDmMode,
                competitorWeeklyDmUserIds = request.competitorWeeklyDmUserIds,
                competitorWeeklyDay = request.competitorWeeklyDay,
                competitorWeeklyHour = request.competitorWeeklyHour,
                reviewBatchUxEnabled = request.reviewBatchUxEnabled,
                defaultReviewPerCategory = request.defaultReviewPerCategory,
                retentionRssItemsDays = request.retentionRssItemsDays,
                retentionBatchSummariesDays = request.retentionBatchSummariesDays
            ),
            changedBy = actor(authentication)
            ).toResponse()
    }

    /**
     * Slack 연결을 검증합니다.
     * 관리자별 분당 [SLACK_ACTION_MAX_PER_MINUTE]회로 제한되며, 초과 시 429를 반환합니다.
     */
    @PostMapping("/slack/verify")
    fun verifySlackConnection(
        @RequestBody request: SlackConnectionVerifyRequest,
        authentication: Authentication
    ): SlackConnectionVerifyResponse {
        // 연결 검증 남용 방지를 위해 actor 단위 분당 10회 제한을 걸어 둔다.
        enforceSlackActionRateLimit("verify", authentication)
        val runtime = runtimeSettingService.current()
        val token = request.slackBotToken?.trim()?.ifBlank { null } ?: runtime.slackBotToken
        val normalizedChannel = normalizeSlackChannelId(request.slackChannelId)
        val result = slackMessageSender.testConnection(token.takeIf { it.isNotBlank() }, normalizedChannel)

        return SlackConnectionVerifyResponse(
            ok = result.ok,
            botUser = result.botUser,
            team = result.team,
            channelId = result.channelId,
            channelName = result.channelName,
            neededScopes = result.neededScopes,
            providedScopes = result.providedScopes,
            message = if (result.ok) "Slack 연결이 정상입니다." else result.rawError ?: "Slack 연결 검증에 실패했습니다.",
            warning = result.warning
        )
    }

    /**
     * Slack Socket Mode 연결을 검증합니다.
     * 관리자별 분당 [SLACK_ACTION_MAX_PER_MINUTE]회로 제한되며, 초과 시 429를 반환합니다.
     */
    @PostMapping("/slack/socket/verify")
    fun verifySlackSocketConnection(
        @RequestBody request: SlackSocketConnectionVerifyRequest,
        authentication: Authentication
    ): SlackSocketConnectionVerifyResponse {
        // Socket Mode 검증도 외부 API를 호출하므로 동일한 분당 한도를 적용한다.
        enforceSlackActionRateLimit("socket-verify", authentication)
        val result = slackMessageSender.testSocketModeConnection(request.slackAppToken)
        return SlackSocketConnectionVerifyResponse(
            ok = result.ok,
            appId = result.appId,
            socketUrl = result.socketUrl,
            message = if (result.ok) {
                "Slack Socket Mode 연결이 정상입니다."
            } else {
                result.rawError ?: "Slack Socket Mode 연결 검증에 실패했습니다."
            },
            warning = result.warning
        )
    }

    /**
     * Socket Mode 연결 상태를 조회합니다.
     */
    @GetMapping("/slack/socket/status")
    fun getSlackSocketModeStatus(): SlackSocketModeStatusResponse {
        val status = slackSocketModeService.currentStatus()
        return SlackSocketModeStatusResponse(
            enabled = status.enabled,
            configured = status.configured,
            connected = status.connected,
            appId = status.appId,
            socketUrl = status.socketUrl,
            lastEnvelopeId = status.lastEnvelopeId,
            lastError = status.lastError
        )
    }

    /**
     * Block Kit 템플릿을 샘플 데이터로 렌더링합니다.
     */
    @PostMapping("/slack/block-kit/preview")
    fun previewSlackBlockKit(@RequestBody request: SlackBlockKitPreviewRequest): SlackBlockKitPreviewResponse {
        val runtime = runtimeSettingService.current()
        val defaultTemplate = slackBlockKitTemplateService.defaultTemplate()
        val templateFromRuntime = runtime.slackDigestBlockKitTemplate.trim()
        val templateToRender = request.template
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: templateFromRuntime.takeIf { it.isNotBlank() }
            ?: defaultTemplate
        val sampleChannel = normalizeSlackChannelId(request.slackChannelId)
            ?: "C0123456789"
        val context = slackBlockKitTemplateService.sampleContext(channelId = sampleChannel)
        val rendered = slackBlockKitTemplateService.renderTemplate(templateToRender, context)

        return SlackBlockKitPreviewResponse(
            valid = true,
            message = "Block Kit 템플릿 렌더링이 완료되었습니다.",
            renderedText = rendered.renderedText,
            blocks = rendered.blocks,
            placeholders = slackBlockKitTemplateService.supportedPlaceholders(),
            templateUsed = templateToRender,
            defaultTemplate = defaultTemplate
        )
    }

    /**
     * Block Kit 템플릿을 Slack 채널로 테스트 전송합니다.
     * 관리자별 분당 [SLACK_ACTION_MAX_PER_MINUTE]회로 제한되며, 초과 시 429를 반환합니다.
     */
    @PostMapping("/slack/block-kit/test-send")
    fun testSendSlackBlockKit(
        @RequestBody request: SlackBlockKitTestSendRequest,
        authentication: Authentication
    ): SlackBlockKitTestSendResponse {
        // 실제 Slack 메시지를 전송하므로 가장 엄격하게 남용 방지를 건다.
        enforceSlackActionRateLimit("test-send", authentication)
        val runtime = runtimeSettingService.current()
        val defaultTemplate = slackBlockKitTemplateService.defaultTemplate()
        val templateToRender = request.template
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: runtime.slackDigestBlockKitTemplate.trim().takeIf { it.isNotBlank() }
            ?: defaultTemplate
        val channelId = normalizeSlackChannelId(request.slackChannelId)
            ?: throw InvalidInputException("테스트 전송 채널 ID를 입력해주세요.")

        val context = slackBlockKitTemplateService.sampleContext(channelId = channelId)
        val rendered = slackBlockKitTemplateService.renderTemplate(templateToRender, context)
        val sendResult = slackMessageSender.sendMessage(
            channelId = channelId,
            text = rendered.renderedText,
            blocks = rendered.blocks,
            botToken = request.slackBotToken?.trim()?.ifBlank { null } ?: runtime.slackBotToken
        )

        return SlackBlockKitTestSendResponse(
            ok = true,
            message = "Slack 테스트 전송이 완료되었습니다.",
            channelId = channelId,
            messageTs = sendResult.ts.ifEmpty { null },
            renderedText = rendered.renderedText,
            blocks = rendered.blocks
        )
    }

    /**
     * 설정을 기본값으로 초기화합니다.
     */
    @PostMapping("/reset")
    fun resetSettings(authentication: Authentication): RuntimeSettingsResponse =
        runtimeSettingService.resetAll(changedBy = actor(authentication)).toResponse()

    /**
     * 최근 설정 변경 이력을 조회합니다.
     */
    @GetMapping("/audits")
    fun getAudits(@RequestParam(required = false) limit: Int?): List<RuntimeSettingAuditResponse> {
        val safeLimit = limit ?: 30
        if (safeLimit !in 1..1000) throw InvalidInputException("limit는 1~1000 범위여야 합니다")
        return runtimeSettingService.audits(safeLimit).map { it.toResponse() }
    }

    private fun RuntimeSettingService.RuntimeSettings.toResponse() = RuntimeSettingsResponse(
        defaultHoursBack = defaultHoursBack,
        summaryInputMaxChars = summaryInputMaxChars,
        digestMinImportanceScore = digestMinImportanceScore,
        digestDefaultMaxItems = digestDefaultMaxItems,
        digestMaxMessageChars = digestMaxMessageChars,
        digestItemSummaryMaxChars = digestItemSummaryMaxChars,
        digestKeywordMaxCount = digestKeywordMaxCount,
        jobWorkerBatchSize = jobWorkerBatchSize,
        jobMaxAttempts = jobMaxAttempts,
        jobInitialBackoffSeconds = jobInitialBackoffSeconds,
        slackBotToken = if (slackBotToken.isBlank()) "" else "********",
        slackBotTokenConfigured = slackBotToken.isNotBlank(),
        slackDigestBlockKitTemplate = slackDigestBlockKitTemplate,
        slackAutoDigestEnabled = slackAutoDigestEnabled,
        slackDigestCron = slackDigestCron,
        slackAutoDigestMaxItems = slackAutoDigestMaxItems,
        slackAutoDigestUnsentOnly = slackAutoDigestUnsentOnly,
        slackDailyChannelMessageLimit = slackDailyChannelMessageLimit,
        ralphOrchestrationEnabled = ralphOrchestrationEnabled,
        ralphLoopEnabled = ralphLoopEnabled,
        ralphLoopMaxIterations = ralphLoopMaxIterations,
        ralphLoopStopPhrase = ralphLoopStopPhrase,
        maintenanceMode = maintenanceMode,
        maintenanceMessage = maintenanceMessage,
        opsLogChannelId = opsLogChannelId,
        opsRequestChannelId = opsRequestChannelId,
        securityAlertChannelId = securityAlertChannelId,
        opsNotificationProfile = opsNotificationProfile.name,
        opsDailyForecastHour = opsDailyForecastHour,
        opsWeeklyReportDay = opsWeeklyReportDay.name,
        opsWeeklyReportHour = opsWeeklyReportHour,
        opsPipelineCooldownMinutes = opsPipelineCooldownMinutes,
        opsIncidentWindowMinutes = opsIncidentWindowMinutes,
        opsIncidentThresholdCategories = opsIncidentThresholdCategories,
        opsScheduleMissGraceMinutes = opsScheduleMissGraceMinutes,
        opsBudgetWarnPct = opsBudgetWarnPct,
        opsBudgetCriticalPct = opsBudgetCriticalPct,
        opsAdminBaseUrl = opsAdminBaseUrl,
        opsSilentHoursEnabled = opsSilentHoursEnabled,
        opsRecoveryStreakThreshold = opsRecoveryStreakThreshold,
        opsLogsEnabled = opsLogsEnabled,
        competitorWeeklyEnabled = competitorWeeklyEnabled,
        competitorWeeklyChannelId = competitorWeeklyChannelId,
        competitorWeeklyDmMode = competitorWeeklyDmMode,
        competitorWeeklyDmUserIds = competitorWeeklyDmUserIds,
        competitorWeeklyDay = competitorWeeklyDay,
        competitorWeeklyHour = competitorWeeklyHour,
        reviewBatchUxEnabled = reviewBatchUxEnabled,
        defaultReviewPerCategory = defaultReviewPerCategory,
        retentionRssItemsDays = retentionRssItemsDays,
        retentionBatchSummariesDays = retentionBatchSummariesDays,
        updatedAt = updatedAt
    )

    private fun RuntimeSettingService.RuntimeSettingAuditInfo.toResponse() = RuntimeSettingAuditResponse(
        settingKey = settingKey,
        oldValue = oldValue,
        newValue = newValue,
        action = action,
        changedBy = changedBy,
        changedAt = changedAt
    )

    private fun actor(authentication: Authentication): String =
        authentication.name.trim().ifBlank { "unknown-admin" }

    private fun normalizeSlackChannelId(raw: String?): String? = SlackChannelIdNormalizer.normalize(raw)

    /**
     * Slack 검증/테스트 전송 엔드포인트에 공통 rate limit(분당 10회)을 적용한다.
     * 초과 시 [RateLimitExceededException]을 던져 GlobalExceptionHandler가 HTTP 429로 응답한다.
     */
    private fun enforceSlackActionRateLimit(action: String, authentication: Authentication) {
        val key = "$SLACK_ACTION_KEY_PREFIX$action:${actor(authentication)}"
        val limited = redisRateLimitService.isRateLimited(
            key = key,
            maxRequests = SLACK_ACTION_MAX_PER_MINUTE,
            windowSeconds = SLACK_ACTION_WINDOW_SECONDS
        )
        if (limited) {
            throw RateLimitExceededException(
                message = "Slack 검증 요청이 너무 많아요. 잠시 후 다시 시도해 주세요 (분당 $SLACK_ACTION_MAX_PER_MINUTE 회 제한).",
                retryAfterSeconds = SLACK_ACTION_WINDOW_SECONDS
            )
        }
    }
}
