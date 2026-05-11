package com.clipping.mcpserver.service

import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.config.EncryptionService
import com.clipping.mcpserver.config.SlackProperties
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.RuntimeSetting
import com.clipping.mcpserver.model.RuntimeSettingAudit
import com.clipping.mcpserver.service.port.NotificationRuntimeSettings
import com.clipping.mcpserver.service.port.NotificationRuntimeSettingsPort
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.RuntimeSettingAuditStore
import com.clipping.mcpserver.store.RuntimeSettingStore
import com.clipping.mcpserver.support.SlackChannelIdNormalizer
import com.clipping.mcpserver.support.SlackMentionGuard
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.scheduling.support.CronExpression
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID

/** 운영 알림 발송 프로파일. */
enum class OpsNotificationProfile { FULL, BATCHED, CRITICAL_ONLY }

/**
 * 런타임 설정 조회/수정과 감사 이력 기록을 담당한다.
 */
@Service
class RuntimeSettingService(
    private val runtimeSettingStore: RuntimeSettingStore,
    private val runtimeSettingAuditStore: RuntimeSettingAuditStore,
    private val properties: ClippingMcpServerProperties,
    private val slackProperties: SlackProperties,
    private val slackBlockKitTemplateService: SlackBlockKitTemplateService,
    private val encryptionService: EncryptionService,
    private val auditLogStore: AuditLogStore,
    private val auditActorResolver: AuditActorResolver
) : NotificationRuntimeSettingsPort {

data class RuntimeSettings(
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
        val slackDigestBlockKitTemplate: String,
        val slackAutoDigestEnabled: Boolean,
        val slackDigestCron: String,
        val slackAutoDigestMaxItems: Int,
        val slackAutoDigestUnsentOnly: Boolean,
        val slackDailyChannelMessageLimit: Int,
        val ralphOrchestrationEnabled: Boolean = false,
        val ralphLoopEnabled: Boolean = true,
        val ralphLoopMaxIterations: Int = 4,
        val ralphLoopStopPhrase: String = "RALPH_STOP",
        val maintenanceMode: Boolean = false,
        val maintenanceMessage: String = "",
        val opsLogChannelId: String = "",
        val opsRequestChannelId: String = "",
        /**
         * 토큰 만료·quota 소진 등 CRITICAL severity 알림 전용 채널 ID.
         * 빈 값이면 [opsLogChannelId]로 폴백한다. 존재 시 5분 dedup을 우회해 즉시 발송된다.
         */
        val securityAlertChannelId: String = "",
        val competitorWeeklyEnabled: Boolean = false,
        val competitorWeeklyChannelId: String = "",
        val competitorWeeklyDmMode: String = "off",
        val competitorWeeklyDmUserIds: String = "[]",
        val competitorWeeklyDay: String = "MONDAY",
        val competitorWeeklyHour: Int = 9,
        /**
         * 경쟁사 주간 설정(day/hour/channel/dm)이 마지막으로 변경된 시각 (ISO-8601 UTC).
         * 같은 주에 이미 발송한 슬롯이 있더라도, 이 시각 이후라면 재발송이 허용된다.
         * 빈 문자열이면 자동 갱신 이전 상태(레거시)이며, 일반 중복 방지가 적용된다.
         */
        val competitorWeeklyConfigChangedAt: String = "",
        /**
         * 뉴스 검토 일괄(배치) UX 기능 플래그.
         * true면 관리자 뉴스 검토 페이지에서 일괄 승인/제외/재검토 UI를 노출한다.
         * 기본값 false — PR D 배포 시 점진적으로 켠다. 크래시 발생 시 즉시 off로 내려 격리할 수 있다.
         */
        val reviewBatchUxEnabled: Boolean = false,
        /**
         * 뉴스 검토 페이지의 카테고리별 top-N 샘플링 기본값.
         * 전체 조회 시 각 카테고리에서 이 수만큼 뽑아 공평한 노출을 만든다.
         * 0은 샘플링 비활성(기존 최근 N건 동작). 1..100 범위.
         */
        val defaultReviewPerCategory: Int = 20,
        val opsNotificationProfile: OpsNotificationProfile = OpsNotificationProfile.FULL,
        val opsDailyForecastHour: Int = 8,
        val opsWeeklyReportDay: DayOfWeek = DayOfWeek.MONDAY,
        val opsWeeklyReportHour: Int = 9,
        val opsPipelineCooldownMinutes: Int = 60,
        val opsIncidentWindowMinutes: Int = 5,
        val opsIncidentThresholdCategories: Int = 3,
        val opsScheduleMissGraceMinutes: Int = 10,
        val opsBudgetWarnPct: Int = 80,
        val opsBudgetCriticalPct: Int = 90,
        val opsAdminBaseUrl: String? = null,
        val opsSilentHoursEnabled: Boolean = true,
        val opsRecoveryStreakThreshold: Int = 3,
        val opsLogsEnabled: Boolean = true,
        /**
         * rss_items 테이블 보관 기간(일). DataCleanupScheduler 가 이 값을 기준으로 오래된 행을 삭제한다.
         * 기본값 30일, 허용 범위 7..365일.
         */
        val retentionRssItemsDays: Int = 30,
        /**
         * batch_summaries 테이블 보관 기간(일). DataCleanupScheduler 가 이 값을 기준으로 오래된 행을 삭제한다.
         * 기본값 90일, 허용 범위 7..730일.
         */
        val retentionBatchSummariesDays: Int = 90,
        val updatedAt: String?
    )

data class RuntimeSettingsUpdate(
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
        /** 토큰 만료 등 CRITICAL severity 알림 전용 채널 ID. 빈 값은 opsLogChannelId 폴백. */
        val securityAlertChannelId: String? = null,
        val competitorWeeklyEnabled: Boolean? = null,
        val competitorWeeklyChannelId: String? = null,
        val competitorWeeklyDmMode: String? = null,
        val competitorWeeklyDmUserIds: String? = null,
        val competitorWeeklyDay: String? = null,
        val competitorWeeklyHour: Int? = null,
        val reviewBatchUxEnabled: Boolean? = null,
        /** 뉴스 검토 페이지의 카테고리별 top-N 샘플링 기본값(0..100). */
        val defaultReviewPerCategory: Int? = null,
        val opsNotificationProfile: OpsNotificationProfile? = null,
        val opsDailyForecastHour: Int? = null,
        val opsWeeklyReportDay: DayOfWeek? = null,
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
        /** rss_items 보관 기간(일). null 이면 변경 없음. 허용 범위 7..365. */
        val retentionRssItemsDays: Int? = null,
        /** batch_summaries 보관 기간(일). null 이면 변경 없음. 허용 범위 7..730. */
        val retentionBatchSummariesDays: Int? = null
    )

    data class RuntimeSettingAuditInfo(
        val settingKey: String,
        val oldValue: String?,
        val newValue: String?,
        val action: String,
        val changedBy: String,
        val changedAt: String
    )

    fun current(): RuntimeSettings {
        val entries = runtimeSettingStore.list()
        val values = entries.associateBy({ it.key }, { it.value })

        return RuntimeSettings(
            defaultHoursBack = intSetting(values, KEY_DEFAULT_HOURS_BACK, 1, 168, properties.defaultHoursBack),
            summaryInputMaxChars = intSetting(values, KEY_SUMMARY_INPUT_MAX_CHARS, 500, 20_000, properties.maxContentLength),
            digestMinImportanceScore = floatSetting(
                values,
                KEY_DIGEST_MIN_IMPORTANCE_SCORE,
                0f,
                1f,
                properties.digestMinImportanceScore
            ),
            digestDefaultMaxItems = intSetting(
                values,
                KEY_DIGEST_DEFAULT_MAX_ITEMS,
                1,
                5,
                properties.digestDefaultMaxItems
            ),
            digestMaxMessageChars = intSetting(
                values,
                KEY_DIGEST_MAX_MESSAGE_CHARS,
                500,
                3900,
                properties.digestMaxMessageChars
            ),
            digestItemSummaryMaxChars = intSetting(
                values,
                KEY_DIGEST_ITEM_SUMMARY_MAX_CHARS,
                240,
                2200,
                properties.digestItemSummaryMaxChars
            ),
            digestKeywordMaxCount = intSetting(
                values,
                KEY_DIGEST_KEYWORD_MAX_COUNT,
                1,
                10,
                properties.digestKeywordMaxCount
            ),
            // Slack 봇 토큰은 암호화되어 저장되므로 조회 시 복호화한다.
            slackBotToken = encryptionService.decrypt(
                stringSetting(values, KEY_SLACK_BOT_TOKEN, slackProperties.botToken)
            ),
            slackDigestBlockKitTemplate = stringSetting(
                values,
                KEY_SLACK_DIGEST_BLOCK_KIT_TEMPLATE,
                ""
            ),
            slackAutoDigestEnabled = boolSetting(
                values,
                KEY_SLACK_AUTO_DIGEST_ENABLED,
                slackProperties.autoDigestEnabled
            ),
            slackDigestCron = stringSetting(
                values,
                KEY_SLACK_DIGEST_CRON,
                slackProperties.digestCron
            ),
            slackAutoDigestMaxItems = intSetting(
                values,
                KEY_SLACK_AUTO_DIGEST_MAX_ITEMS,
                1,
                7,
                slackProperties.autoDigestMaxItems
            ),
            slackAutoDigestUnsentOnly = boolSetting(
                values,
                KEY_SLACK_AUTO_DIGEST_UNSENT_ONLY,
                slackProperties.autoDigestUnsentOnly
            ),
            slackDailyChannelMessageLimit = intSetting(
                values,
                KEY_SLACK_DAILY_CHANNEL_MESSAGE_LIMIT,
                1,
                1000,
                slackProperties.dailyChannelMessageLimit
            ),
            ralphOrchestrationEnabled = boolSetting(
                values,
                KEY_RALPH_ORCHESTRATION_ENABLED,
                properties.ralphOrchestrationEnabled
            ),
            ralphLoopEnabled = boolSetting(
                values,
                KEY_RALPH_LOOP_ENABLED,
                properties.ralphLoopEnabled
            ),
            ralphLoopMaxIterations = intSetting(
                values,
                KEY_RALPH_LOOP_MAX_ITERATIONS,
                1,
                30,
                properties.ralphLoopMaxIterations
            ),
            ralphLoopStopPhrase = stringSetting(
                values,
                KEY_RALPH_LOOP_STOP_PHRASE,
                properties.ralphLoopStopPhrase
            ).take(120),
            maintenanceMode = boolSetting(values, KEY_MAINTENANCE_MODE, false),
            maintenanceMessage = stringSetting(values, KEY_MAINTENANCE_MESSAGE, ""),
            opsLogChannelId = stringSetting(
                values,
                KEY_OPS_LOG_CHANNEL_ID,
                System.getenv("OPS_LOG_CHANNEL_ID").orEmpty()
            ),
            opsRequestChannelId = stringSetting(
                values,
                KEY_OPS_REQUEST_CHANNEL_ID,
                System.getenv("OPS_REQUEST_CHANNEL_ID").orEmpty()
            ),
            // CRITICAL severity(토큰 만료/quota 소진) 전용 채널. 미설정 시 상위 로직이 opsLogChannelId로 폴백한다.
            securityAlertChannelId = stringSetting(
                values,
                KEY_SECURITY_ALERT_CHANNEL_ID,
                System.getenv("SECURITY_ALERT_CHANNEL_ID").orEmpty()
            ),
            competitorWeeklyEnabled = boolSetting(values, KEY_COMPETITOR_WEEKLY_ENABLED, false),
            competitorWeeklyChannelId = stringSetting(values, KEY_COMPETITOR_WEEKLY_CHANNEL_ID, ""),
            competitorWeeklyDmMode = stringSetting(values, KEY_COMPETITOR_WEEKLY_DM_MODE, "off"),
            competitorWeeklyDmUserIds = stringSetting(values, KEY_COMPETITOR_WEEKLY_DM_USER_IDS, "[]"),
            competitorWeeklyDay = stringSetting(values, KEY_COMPETITOR_WEEKLY_DAY, "MONDAY"),
            competitorWeeklyHour = intSetting(values, KEY_COMPETITOR_WEEKLY_HOUR, 0, 23, 9),
            competitorWeeklyConfigChangedAt = stringSetting(values, KEY_COMPETITOR_WEEKLY_CONFIG_CHANGED_AT, ""),
            // 뉴스 검토 일괄(배치) UX 플래그 — 기본 off로 시작해 점진 롤아웃한다.
            reviewBatchUxEnabled = boolSetting(values, KEY_REVIEW_BATCH_UX_ENABLED, false),
            // 뉴스 검토 페이지 카테고리별 top-N 기본값 — 관리자가 운영 중 즉시 조절 가능.
            defaultReviewPerCategory = intSetting(
                values,
                KEY_DEFAULT_REVIEW_PER_CATEGORY,
                0,
                100,
                DEFAULT_REVIEW_PER_CATEGORY
            ),
            opsNotificationProfile = enumSetting(
                values,
                KEY_OPS_NOTIFICATION_PROFILE,
                OpsNotificationProfile.FULL
            ),
            opsDailyForecastHour = intSetting(values, KEY_OPS_DAILY_FORECAST_HOUR, 0, 23, 8),
            opsWeeklyReportDay = dayOfWeekSetting(values, KEY_OPS_WEEKLY_REPORT_DAY, DayOfWeek.MONDAY),
            opsWeeklyReportHour = intSetting(values, KEY_OPS_WEEKLY_REPORT_HOUR, 0, 23, 9),
            opsPipelineCooldownMinutes = intSetting(values, KEY_OPS_PIPELINE_COOLDOWN_MINUTES, 1, 1440, 60),
            opsIncidentWindowMinutes = intSetting(values, KEY_OPS_INCIDENT_WINDOW_MINUTES, 1, 60, 5),
            opsIncidentThresholdCategories = intSetting(values, KEY_OPS_INCIDENT_THRESHOLD_CATEGORIES, 2, 20, 3),
            opsScheduleMissGraceMinutes = intSetting(values, KEY_OPS_SCHEDULE_MISS_GRACE_MINUTES, 1, 60, 10),
            opsBudgetWarnPct = intSetting(values, KEY_OPS_BUDGET_WARN_PCT, 1, 100, 80),
            opsBudgetCriticalPct = intSetting(values, KEY_OPS_BUDGET_CRITICAL_PCT, 1, 100, 90),
            opsAdminBaseUrl = nullableStringSetting(values, KEY_OPS_ADMIN_BASE_URL),
            opsSilentHoursEnabled = boolSetting(values, KEY_OPS_SILENT_HOURS_ENABLED, true),
            opsRecoveryStreakThreshold = intSetting(values, KEY_OPS_RECOVERY_STREAK_THRESHOLD, 2, 10, 3),
            opsLogsEnabled = boolSetting(values, KEY_OPS_LOGS_ENABLED, true),
            // 데이터 보관 기간 — DataCleanupScheduler 가 소비하는 설정값을 읽어온다.
            retentionRssItemsDays = intSetting(
                values,
                KEY_RETENTION_RSS_ITEMS_DAYS,
                RETENTION_RSS_ITEMS_DAYS_MIN,
                RETENTION_RSS_ITEMS_DAYS_MAX,
                RETENTION_RSS_ITEMS_DAYS_DEFAULT
            ),
            retentionBatchSummariesDays = intSetting(
                values,
                KEY_RETENTION_BATCH_SUMMARIES_DAYS,
                RETENTION_BATCH_SUMMARIES_DAYS_MIN,
                RETENTION_BATCH_SUMMARIES_DAYS_MAX,
                RETENTION_BATCH_SUMMARIES_DAYS_DEFAULT
            ),
            jobWorkerBatchSize = intSetting(
                values,
                KEY_JOB_WORKER_BATCH_SIZE,
                1,
                50,
                properties.jobWorkerBatchSize
            ),
            jobMaxAttempts = intSetting(
                values,
                KEY_JOB_MAX_ATTEMPTS,
                1,
                10,
                properties.jobMaxAttempts
            ),
            jobInitialBackoffSeconds = intSetting(
                values,
                KEY_JOB_INITIAL_BACKOFF_SECONDS,
                1,
                900,
                properties.jobInitialBackoffSeconds
            ),
            updatedAt = entries.maxByOrNull { it.updatedAt }?.updatedAt?.toString()
        )
    }

    override fun currentNotificationSettings(): NotificationRuntimeSettings {
        val settings = current()
        return NotificationRuntimeSettings(
            opsLogChannelId = settings.opsLogChannelId,
            opsRequestChannelId = settings.opsRequestChannelId,
            slackBotToken = settings.slackBotToken,
        )
    }

    /**
     * 전달된 변경 항목만 검증 후 저장하고 감사 이력을 남긴다.
     * 키별 수집 로직은 도메인 그룹별 collector 함수로 위임하고,
     * cross-field 검증(budget warn < critical)과 파생 값(competitor configChangedAt)은 마지막에 반영한다.
     */
    @Transactional
    fun update(update: RuntimeSettingsUpdate, changedBy: String = "system"): RuntimeSettings {
        val existing = runtimeSettingStore.list().associateBy { it.key }
        val saves = mutableListOf<RuntimeSetting>()

        // 도메인 그룹별로 update 항목을 saves 에 축적한다. 각 collector 내부에서 검증을 수행한다.
        collectDigestAndJobUpdates(update, saves)
        collectSlackUpdates(update, saves)
        collectRalphAndMaintenanceUpdates(update, saves)
        collectChannelUpdates(update, saves)
        collectCompetitorWeeklyUpdates(update, saves)
        collectReviewFlagUpdates(update, saves)
        collectOpsScheduleUpdates(update, saves)
        collectOpsBudgetUpdates(update, saves)
        collectOpsMiscUpdates(update, saves)
        collectRetentionUpdates(update, saves)

        // 경쟁사 주간 설정이 실제로 바뀌면 configChangedAt 타임스탬프를 자동 추가한다.
        appendCompetitorWeeklyChangedAtIfTouched(saves, existing)

        val changed = saves.filter { candidate -> existing[candidate.key]?.value != candidate.value }
        if (changed.isNotEmpty()) {
            persistAndAudit(changed, existing, changedBy)
        }

        return current()
    }

    /** 변경된 settings 를 한 번에 저장하고 감사 이력 2 종(RuntimeSettingAudit + AuditLog)을 남긴다. */
    private fun persistAndAudit(
        changed: List<RuntimeSetting>,
        existing: Map<String, RuntimeSetting>,
        changedBy: String
    ) {
        // 한 번의 변경 묶음은 동일한 시각/작성자로 감사 이력을 남긴다.
        val auditChangedAt = Instant.now()
        val normalizedActor = normalizeActor(changedBy)
        runtimeSettingStore.saveAll(changed)
        runtimeSettingAuditStore.saveAll(
            changed.map { changedSetting ->
                toAuditRecord(
                    settingKey = changedSetting.key,
                    oldValue = existing[changedSetting.key]?.value,
                    newValue = changedSetting.value,
                    action = ACTION_UPDATE,
                    changedBy = normalizedActor,
                    changedAt = auditChangedAt
                )
            }
        )
        // 통합 감사 로그에도 각 변경을 한 건씩 기록한다 — 키별로 AuditLogStore.log 호출.
        val resolvedActor = auditActorResolver.resolve(normalizedActor)
        changed.forEach { changedSetting ->
            auditLogStore.log(
                actorId = resolvedActor.id,
                actorName = resolvedActor.name,
                action = ACTION_RUNTIME_SETTING_UPDATED,
                targetType = TARGET_TYPE_RUNTIME_SETTING,
                targetId = changedSetting.key,
                targetName = null,
                detail = buildAuditDetail(
                    key = changedSetting.key,
                    oldValue = existing[changedSetting.key]?.value,
                    newValue = changedSetting.value
                )
            )
        }
    }

    /** 기사 수집/요약/다이제스트/잡 워커 관련 숫자 설정을 수집한다. */
    private fun collectDigestAndJobUpdates(update: RuntimeSettingsUpdate, saves: MutableList<RuntimeSetting>) {
        addIntSetting(update.defaultHoursBack, KEY_DEFAULT_HOURS_BACK, 1..168, "defaultHoursBack", saves)
        addIntSetting(update.summaryInputMaxChars, KEY_SUMMARY_INPUT_MAX_CHARS, 500..20_000, "summaryInputMaxChars", saves)
        addFloatSetting(
            update.digestMinImportanceScore, KEY_DIGEST_MIN_IMPORTANCE_SCORE, 0f..1f, "digestMinImportanceScore", saves
        )
        addIntSetting(update.digestDefaultMaxItems, KEY_DIGEST_DEFAULT_MAX_ITEMS, 1..5, "digestDefaultMaxItems", saves)
        addIntSetting(update.digestMaxMessageChars, KEY_DIGEST_MAX_MESSAGE_CHARS, 500..3900, "digestMaxMessageChars", saves)
        addIntSetting(
            update.digestItemSummaryMaxChars, KEY_DIGEST_ITEM_SUMMARY_MAX_CHARS, 240..2200, "digestItemSummaryMaxChars", saves
        )
        addIntSetting(update.digestKeywordMaxCount, KEY_DIGEST_KEYWORD_MAX_COUNT, 1..10, "digestKeywordMaxCount", saves)
        addIntSetting(update.jobWorkerBatchSize, KEY_JOB_WORKER_BATCH_SIZE, 1..50, "jobWorkerBatchSize", saves)
        addIntSetting(update.jobMaxAttempts, KEY_JOB_MAX_ATTEMPTS, 1..10, "jobMaxAttempts", saves)
        addIntSetting(update.jobInitialBackoffSeconds, KEY_JOB_INITIAL_BACKOFF_SECONDS, 1..900, "jobInitialBackoffSeconds", saves)
    }

    /** Slack 발송/토큰/템플릿 관련 설정을 수집한다. */
    private fun collectSlackUpdates(update: RuntimeSettingsUpdate, saves: MutableList<RuntimeSetting>) {
        update.slackBotToken?.let {
            val trimmed = it.trim()
            if (trimmed.isNotEmpty()) {
                // Slack 봇 토큰은 DB 저장 전에 암호화한다.
                saves += RuntimeSetting(KEY_SLACK_BOT_TOKEN, encryptionService.encrypt(trimmed))
            }
        }
        update.slackDigestBlockKitTemplate?.let {
            val trimmed = it.trim()
            if (trimmed.isNotEmpty()) {
                // 샘플 컨텍스트로 렌더 시도해 형식 오류를 조기 검출한다.
                slackBlockKitTemplateService.renderTemplate(trimmed, slackBlockKitTemplateService.sampleContext())
            }
            saves += RuntimeSetting(KEY_SLACK_DIGEST_BLOCK_KIT_TEMPLATE, trimmed)
        }
        addBoolSetting(update.slackAutoDigestEnabled, KEY_SLACK_AUTO_DIGEST_ENABLED, saves)
        update.slackDigestCron?.let {
            val trimmed = it.trim()
            ensureValid(trimmed.isNotBlank() || trimmed == "-") { "slackDigestCron must not be empty" }
            ensureValid(trimmed == "-" || isValidCron(trimmed)) { "slackDigestCron must be '-' or a valid cron expression" }
            saves += RuntimeSetting(KEY_SLACK_DIGEST_CRON, trimmed)
        }
        addIntSetting(update.slackAutoDigestMaxItems, KEY_SLACK_AUTO_DIGEST_MAX_ITEMS, 1..5, "slackAutoDigestMaxItems", saves)
        addBoolSetting(update.slackAutoDigestUnsentOnly, KEY_SLACK_AUTO_DIGEST_UNSENT_ONLY, saves)
        addIntSetting(
            update.slackDailyChannelMessageLimit,
            KEY_SLACK_DAILY_CHANNEL_MESSAGE_LIMIT,
            1..1000,
            "slackDailyChannelMessageLimit",
            saves
        )
    }

    /** Ralph 오케스트레이션/루프 설정과 점검 모드(maintenance) 관련 설정을 수집한다. */
    private fun collectRalphAndMaintenanceUpdates(update: RuntimeSettingsUpdate, saves: MutableList<RuntimeSetting>) {
        addBoolSetting(update.ralphOrchestrationEnabled, KEY_RALPH_ORCHESTRATION_ENABLED, saves)
        addBoolSetting(update.ralphLoopEnabled, KEY_RALPH_LOOP_ENABLED, saves)
        addIntSetting(update.ralphLoopMaxIterations, KEY_RALPH_LOOP_MAX_ITERATIONS, 1..30, "ralphLoopMaxIterations", saves)
        update.ralphLoopStopPhrase?.let {
            // 점검 문구는 관리자 입력이지만 Ralph 로그/Slack 운영 로그에 흘러들 수 있어 멘션 패턴을 중립화한다.
            val normalized = SlackMentionGuard.neutralize(it.trim().ifBlank { "RALPH_STOP" }.take(120))
            saves += RuntimeSetting(KEY_RALPH_LOOP_STOP_PHRASE, normalized)
        }
        addBoolSetting(update.maintenanceMode, KEY_MAINTENANCE_MODE, saves)
        update.maintenanceMessage?.let {
            val msg = it.trim()
            // generic IllegalArgumentException 대신 도메인 예외로 거부한다.
            ensureValid(msg.length <= 500) { "점검 메시지는 500자 이하여야 합니다." }
            // 점검 페이지/알림 채널에 그대로 노출되므로 Slack 멘션 패턴을 중립화한다.
            saves += RuntimeSetting(KEY_MAINTENANCE_MESSAGE, SlackMentionGuard.neutralize(msg))
        }
    }

    /** 운영 로그/요청/보안 Slack 채널 ID 설정을 수집한다. 빈 문자열은 '채널 비우기'로 허용한다. */
    private fun collectChannelUpdates(update: RuntimeSettingsUpdate, saves: MutableList<RuntimeSetting>) {
        addChannelIdSetting(update.opsLogChannelId, KEY_OPS_LOG_CHANNEL_ID, "운영 로그 채널 ID", saves)
        addChannelIdSetting(update.opsRequestChannelId, KEY_OPS_REQUEST_CHANNEL_ID, "운영 요청 알림 채널 ID", saves)
        // 보안 알림 채널 빈 입력은 opsLogChannelId 폴백으로 허용한다.
        addChannelIdSetting(update.securityAlertChannelId, KEY_SECURITY_ALERT_CHANNEL_ID, "보안 알림 채널 ID", saves)
    }

    /** 경쟁사 주간 다이제스트의 활성화/채널/DM/요일/시간 설정을 수집한다. */
    private fun collectCompetitorWeeklyUpdates(update: RuntimeSettingsUpdate, saves: MutableList<RuntimeSetting>) {
        addBoolSetting(update.competitorWeeklyEnabled, KEY_COMPETITOR_WEEKLY_ENABLED, saves)
        addChannelIdSetting(
            update.competitorWeeklyChannelId, KEY_COMPETITOR_WEEKLY_CHANNEL_ID, "경쟁사 주간 발송 채널 ID", saves
        )
        update.competitorWeeklyDmMode?.let {
            val trimmed = it.trim()
            ensureValid(trimmed in setOf("off", "all", "selected")) {
                "competitorWeeklyDmMode must be one of: off, all, selected"
            }
            saves += RuntimeSetting(KEY_COMPETITOR_WEEKLY_DM_MODE, trimmed)
        }
        update.competitorWeeklyDmUserIds?.let {
            saves += RuntimeSetting(KEY_COMPETITOR_WEEKLY_DM_USER_IDS, it.trim())
        }
        update.competitorWeeklyDay?.let {
            val trimmed = it.trim().uppercase()
            ensureValid(trimmed in VALID_DAYS_OF_WEEK) {
                "competitorWeeklyDay must be a valid day of week (MONDAY–SUNDAY)"
            }
            saves += RuntimeSetting(KEY_COMPETITOR_WEEKLY_DAY, trimmed)
        }
        addIntSetting(update.competitorWeeklyHour, KEY_COMPETITOR_WEEKLY_HOUR, 0..23, "competitorWeeklyHour", saves)
    }

    /** 뉴스 검토(review) 관련 플래그/샘플링 설정을 수집한다. */
    private fun collectReviewFlagUpdates(update: RuntimeSettingsUpdate, saves: MutableList<RuntimeSetting>) {
        // 플래그는 단순 boolean이므로 추가 검증 없이 저장한다.
        addBoolSetting(update.reviewBatchUxEnabled, KEY_REVIEW_BATCH_UX_ENABLED, saves)
        addIntSetting(
            update.defaultReviewPerCategory, KEY_DEFAULT_REVIEW_PER_CATEGORY, 0..100, "defaultReviewPerCategory", saves
        )
    }

    /** Ops 알림 프로파일/일일 예보/주간 리포트/인시던트/스케줄 감지 등 스케줄링 관련 설정을 수집한다. */
    private fun collectOpsScheduleUpdates(update: RuntimeSettingsUpdate, saves: MutableList<RuntimeSetting>) {
        update.opsNotificationProfile?.let {
            saves += RuntimeSetting(KEY_OPS_NOTIFICATION_PROFILE, it.name)
        }
        addIntSetting(update.opsDailyForecastHour, KEY_OPS_DAILY_FORECAST_HOUR, 0..23, "opsDailyForecastHour", saves)
        update.opsWeeklyReportDay?.let {
            saves += RuntimeSetting(KEY_OPS_WEEKLY_REPORT_DAY, it.name)
        }
        addIntSetting(update.opsWeeklyReportHour, KEY_OPS_WEEKLY_REPORT_HOUR, 0..23, "opsWeeklyReportHour", saves)
        addIntSetting(
            update.opsPipelineCooldownMinutes, KEY_OPS_PIPELINE_COOLDOWN_MINUTES, 1..1440, "opsPipelineCooldownMinutes", saves
        )
        addIntSetting(
            update.opsIncidentWindowMinutes, KEY_OPS_INCIDENT_WINDOW_MINUTES, 1..60, "opsIncidentWindowMinutes", saves
        )
        addIntSetting(
            update.opsIncidentThresholdCategories,
            KEY_OPS_INCIDENT_THRESHOLD_CATEGORIES,
            2..20,
            "opsIncidentThresholdCategories",
            saves
        )
        addIntSetting(
            update.opsScheduleMissGraceMinutes,
            KEY_OPS_SCHEDULE_MISS_GRACE_MINUTES,
            1..60,
            "opsScheduleMissGraceMinutes",
            saves
        )
    }

    /**
     * opsBudgetWarnPct / opsBudgetCriticalPct 설정을 수집한다.
     * 두 값은 함께 검증해야 하므로(warn < critical) 개별 범위 검증 + cross-field 검증 후 저장한다.
     */
    private fun collectOpsBudgetUpdates(update: RuntimeSettingsUpdate, saves: MutableList<RuntimeSetting>) {
        update.opsBudgetWarnPct?.let {
            ensureValid(it in 1..100) { "opsBudgetWarnPct must be 1..100" }
        }
        update.opsBudgetCriticalPct?.let {
            ensureValid(it in 1..100) { "opsBudgetCriticalPct must be 1..100" }
        }
        val effectiveBudgetWarn = update.opsBudgetWarnPct ?: -1
        val effectiveBudgetCritical = update.opsBudgetCriticalPct ?: -1
        if (effectiveBudgetWarn >= 0 && effectiveBudgetCritical >= 0) {
            ensureValid(effectiveBudgetWarn < effectiveBudgetCritical) {
                "opsBudgetWarnPct($effectiveBudgetWarn) must be less than opsBudgetCriticalPct($effectiveBudgetCritical)"
            }
        }
        update.opsBudgetWarnPct?.let { saves += RuntimeSetting(KEY_OPS_BUDGET_WARN_PCT, it.toString()) }
        update.opsBudgetCriticalPct?.let { saves += RuntimeSetting(KEY_OPS_BUDGET_CRITICAL_PCT, it.toString()) }
    }

    /** 관리자 base URL / silent hours / 복구 임계값 / 로그 플래그 등 잔여 Ops 설정을 수집한다. */
    private fun collectOpsMiscUpdates(update: RuntimeSettingsUpdate, saves: MutableList<RuntimeSetting>) {
        update.opsAdminBaseUrl?.let { raw ->
            // 빈 문자열은 URL 제거로 허용한다.
            val normalized = raw.trimEnd('/')
            if (normalized.isNotEmpty()) {
                ensureValid(normalized.startsWith("https://")) { "opsAdminBaseUrl must start with https://" }
            }
            saves += RuntimeSetting(KEY_OPS_ADMIN_BASE_URL, normalized)
        }
        addBoolSetting(update.opsSilentHoursEnabled, KEY_OPS_SILENT_HOURS_ENABLED, saves)
        addIntSetting(
            update.opsRecoveryStreakThreshold, KEY_OPS_RECOVERY_STREAK_THRESHOLD, 2..10, "opsRecoveryStreakThreshold", saves
        )
        addBoolSetting(update.opsLogsEnabled, KEY_OPS_LOGS_ENABLED, saves)
    }

    /** rss_items / batch_summaries 보관 기간(일) 설정을 수집한다. */
    private fun collectRetentionUpdates(update: RuntimeSettingsUpdate, saves: MutableList<RuntimeSetting>) {
        addIntSetting(
            update.retentionRssItemsDays,
            KEY_RETENTION_RSS_ITEMS_DAYS,
            RETENTION_RSS_ITEMS_DAYS_MIN..RETENTION_RSS_ITEMS_DAYS_MAX,
            "retentionRssItemsDays",
            saves
        )
        addIntSetting(
            update.retentionBatchSummariesDays,
            KEY_RETENTION_BATCH_SUMMARIES_DAYS,
            RETENTION_BATCH_SUMMARIES_DAYS_MIN..RETENTION_BATCH_SUMMARIES_DAYS_MAX,
            "retentionBatchSummariesDays",
            saves
        )
    }

    /**
     * 경쟁사 주간 설정(day/hour/channel/dmMode/dmUserIds/enabled) 중 하나라도 바뀌면
     * configChangedAt 타임스탬프를 자동 갱신한다. 스케줄러는 이 시각보다 마지막 발송이 이전이면
     * 같은 주차에도 재발송을 허용한다 — 관리자가 시간을 바꾸면 그에 맞춰 다시 발송됨.
     */
    private fun appendCompetitorWeeklyChangedAtIfTouched(
        saves: MutableList<RuntimeSetting>,
        existing: Map<String, RuntimeSetting>
    ) {
        val competitorWeeklyTouched = saves.any { candidate ->
            candidate.key in COMPETITOR_WEEKLY_KEYS && existing[candidate.key]?.value != candidate.value
        }
        if (competitorWeeklyTouched) {
            saves += RuntimeSetting(KEY_COMPETITOR_WEEKLY_CONFIG_CHANGED_AT, Instant.now().toString())
        }
    }

    /** 정수형 설정을 범위 검증 후 saves 에 추가한다. value 가 null 이면 아무 것도 하지 않는다. */
    private fun addIntSetting(
        value: Int?,
        key: String,
        range: IntRange,
        fieldLabel: String,
        saves: MutableList<RuntimeSetting>
    ) {
        value ?: return
        ensureValid(value in range) { "$fieldLabel must be between ${range.first} and ${range.last}" }
        saves += RuntimeSetting(key, value.toString())
    }

    /** 실수형 설정을 범위 검증 후 saves 에 추가한다. value 가 null 이면 아무 것도 하지 않는다. */
    private fun addFloatSetting(
        value: Float?,
        key: String,
        range: ClosedFloatingPointRange<Float>,
        fieldLabel: String,
        saves: MutableList<RuntimeSetting>
    ) {
        value ?: return
        ensureValid(value in range) { "$fieldLabel must be between ${range.start} and ${range.endInclusive}" }
        saves += RuntimeSetting(key, value.toString())
    }

    /** Boolean 설정을 saves 에 추가한다. value 가 null 이면 아무 것도 하지 않는다. */
    private fun addBoolSetting(value: Boolean?, key: String, saves: MutableList<RuntimeSetting>) {
        value ?: return
        saves += RuntimeSetting(key, value.toString())
    }

    /**
     * Slack 채널 ID 설정을 정규화 후 saves 에 추가한다. 빈 입력은 '채널 비우기'로 허용된다.
     * 형식 검증 실패 시 InvalidInputException 을 던져 사용자에게 한국어 안내를 노출한다.
     */
    private fun addChannelIdSetting(
        value: String?,
        key: String,
        fieldLabel: String,
        saves: MutableList<RuntimeSetting>
    ) {
        value ?: return
        val normalized = normalizeChannelIdInput(value, fieldLabel)
        saves += RuntimeSetting(key, normalized)
    }

    /**
     * 저장된 런타임 설정을 모두 지우고 기본값으로 되돌린다.
     */
    @Transactional
    fun resetAll(changedBy: String = "system"): RuntimeSettings {
        val existing = runtimeSettingStore.list()
        if (existing.isEmpty()) {
            return current()
        }

        // 전체 초기화도 하나의 시점으로 감사 이력을 맞춘다.
        val auditChangedAt = Instant.now()
        val normalizedActor = normalizeActor(changedBy)
        runtimeSettingStore.deleteAll()
        runtimeSettingAuditStore.saveAll(
            existing.map {
                toAuditRecord(
                    settingKey = it.key,
                    oldValue = it.value,
                    newValue = null,
                    action = ACTION_RESET,
                    changedBy = normalizedActor,
                    changedAt = auditChangedAt
                )
            }
        )
        // 통합 감사 로그에 초기화 이력을 기록한다 (단일 엔트리 + 키 목록을 detail로 요약).
        val resolvedActor = auditActorResolver.resolve(normalizedActor)
        auditLogStore.log(
            actorId = resolvedActor.id,
            actorName = resolvedActor.name,
            action = ACTION_RUNTIME_SETTING_RESET,
            targetType = TARGET_TYPE_RUNTIME_SETTING,
            targetId = null,
            targetName = null,
            detail = "reset keys=${existing.size}"
        )

        return current()
    }

    /**
     * 점검 모드 상태와 메시지를 반환한다. 인증 없이 사용 가능.
     */
    fun maintenanceStatus(): MaintenanceStatus {
        val entries = runtimeSettingStore.list()
        val values = entries.associateBy({ it.key }, { it.value })
        return MaintenanceStatus(
            active = boolSetting(values, KEY_MAINTENANCE_MODE, false),
            message = stringSetting(values, KEY_MAINTENANCE_MESSAGE, "")
        )
    }

    data class MaintenanceStatus(
        val active: Boolean,
        val message: String
    )

    /**
     * 지정된 키의 런타임 설정 값을 원시 문자열로 조회한다.
     * 시스템 내부 상태 보관에 사용하며 감사 로그를 남기지 않는다.
     *
     * @param key 조회할 설정 키
     * @return 저장된 값 또는 null (미존재 시)
     */
    fun getStringSetting(key: String): String? =
        runtimeSettingStore.findByKey(key)?.value

    /**
     * 지정된 키에 값을 저장한다.
     * 시스템 내부 상태 보관에 사용하며 감사 로그를 남기지 않는다.
     *
     * @param key 저장할 설정 키
     * @param value 저장할 값
     */
    fun setStringSetting(key: String, value: String) {
        runtimeSettingStore.save(RuntimeSetting(key = key, value = value))
    }

    fun audits(limit: Int = 30): List<RuntimeSettingAuditInfo> =
        runtimeSettingAuditStore.list(limit.coerceIn(1, 200)).map {
            RuntimeSettingAuditInfo(
                settingKey = it.settingKey,
                oldValue = it.oldValue,
                newValue = it.newValue,
                action = it.action,
                changedBy = it.changedBy,
                changedAt = it.changedAt.toString()
            )
        }

    private fun normalizeActor(actor: String): String =
        actor.trim().ifBlank { "system" }.take(120)

    /**
     * Slack 채널 ID 입력을 표준 형식으로 정규화한다.
     * 빈 문자열은 '채널 비우기'로 허용하고, 그렇지 않은 입력이 형식에 맞지 않으면
     * 도메인 예외를 던져 사용자에게 한국어 안내를 노출한다.
     */
    private fun normalizeChannelIdInput(raw: String, fieldLabel: String): String {
        if (raw.isBlank()) return ""
        return SlackChannelIdNormalizer.normalize(raw)
            ?: throw InvalidInputException("$fieldLabel 형식이 올바르지 않습니다 (예: C0123456789)")
    }

    /**
     * 런타임 설정 변경 감사 레코드를 마스킹 규칙과 함께 생성한다.
     */
    private fun toAuditRecord(
        settingKey: String,
        oldValue: String?,
        newValue: String?,
        action: String,
        changedBy: String,
        changedAt: Instant
    ) = RuntimeSettingAudit(
        id = UUID.randomUUID().toString(),
        settingKey = settingKey,
        oldValue = maskAuditValue(settingKey, oldValue),
        newValue = maskAuditValue(settingKey, newValue),
        action = action,
        changedBy = changedBy,
        changedAt = changedAt
    )

    private fun intSetting(
        values: Map<String, String>,
        key: String,
        min: Int,
        max: Int,
        fallback: Int
    ): Int {
        val parsed = values[key]?.toIntOrNull() ?: return fallback
        return parsed.coerceIn(min, max)
    }

    private fun floatSetting(
        values: Map<String, String>,
        key: String,
        min: Float,
        max: Float,
        fallback: Float
    ): Float {
        val parsed = values[key]?.toFloatOrNull() ?: return fallback
        return parsed.coerceIn(min, max)
    }

    private fun boolSetting(values: Map<String, String>, key: String, fallback: Boolean): Boolean {
        val parsed = values[key]?.trim()?.lowercase()
        return when (parsed) {
            "true", "1", "yes", "y" -> true
            "false", "0", "no", "n" -> false
            null -> fallback
            else -> fallback
        }
    }

    private fun stringSetting(values: Map<String, String>, key: String, fallback: String): String {
        val value = values[key]?.trim().orEmpty()
        return value.ifBlank { fallback }
    }

    private fun nullableStringSetting(values: Map<String, String>, key: String): String? {
        val value = values[key]?.trim()
        return if (value.isNullOrBlank()) null else value
    }

    private inline fun <reified T : Enum<T>> enumSetting(
        values: Map<String, String>,
        key: String,
        fallback: T
    ): T {
        val raw = values[key]?.trim()?.uppercase() ?: return fallback
        return try { enumValueOf<T>(raw) } catch (_: IllegalArgumentException) { fallback }
    }

    private fun dayOfWeekSetting(
        values: Map<String, String>,
        key: String,
        fallback: DayOfWeek
    ): DayOfWeek {
        val raw = values[key]?.trim()?.uppercase() ?: return fallback
        return try { DayOfWeek.valueOf(raw) } catch (_: IllegalArgumentException) { fallback }
    }

    private fun isValidCron(cron: String): Boolean {
        return try {
            CronExpression.parse(cron)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun maskAuditValue(key: String, value: String?): String? {
        if (value == null) return null
        return when (key) {
            KEY_SLACK_BOT_TOKEN -> "***"
            KEY_SLACK_DIGEST_BLOCK_KIT_TEMPLATE ->
                if (value.isBlank()) value else "<block-kit-template:${value.length}chars>"
            else -> value
        }
    }

    /**
     * 통합 감사 로그 detail 문자열을 생성한다.
     * 토큰/템플릿 같은 민감 값은 동일한 마스킹 규칙을 적용해 평문 노출을 막는다.
     */
    private fun buildAuditDetail(key: String, oldValue: String?, newValue: String?): String {
        val maskedOld = maskAuditValue(key, oldValue) ?: "<null>"
        val maskedNew = maskAuditValue(key, newValue) ?: "<null>"
        return "$key: $maskedOld -> $maskedNew"
    }

    companion object {
        private const val ACTION_UPDATE = "UPDATE"
        private const val ACTION_RESET = "RESET"
        private const val ACTION_RUNTIME_SETTING_UPDATED = "RUNTIME_SETTING_UPDATED"
        private const val ACTION_RUNTIME_SETTING_RESET = "RUNTIME_SETTING_RESET"
        private const val TARGET_TYPE_RUNTIME_SETTING = "RUNTIME_SETTING"

        // competitorWeeklyDay 입력으로 허용되는 요일 문자열 목록.
        private val VALID_DAYS_OF_WEEK: Set<String> = setOf(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
        )

        private const val KEY_DEFAULT_HOURS_BACK = "default_hours_back"
        private const val KEY_SUMMARY_INPUT_MAX_CHARS = "summary_input_max_chars"
        private const val KEY_DIGEST_MIN_IMPORTANCE_SCORE = "digest_min_importance_score"
        private const val KEY_DIGEST_DEFAULT_MAX_ITEMS = "digest_default_max_items"
        private const val KEY_DIGEST_MAX_MESSAGE_CHARS = "digest_max_message_chars"
        private const val KEY_DIGEST_ITEM_SUMMARY_MAX_CHARS = "digest_item_summary_max_chars"
        private const val KEY_DIGEST_KEYWORD_MAX_COUNT = "digest_keyword_max_count"
        private const val KEY_JOB_WORKER_BATCH_SIZE = "job_worker_batch_size"
        private const val KEY_JOB_MAX_ATTEMPTS = "job_max_attempts"
        private const val KEY_JOB_INITIAL_BACKOFF_SECONDS = "job_initial_backoff_seconds"
        private const val KEY_SLACK_BOT_TOKEN = "slack_bot_token"
        private const val KEY_SLACK_DIGEST_BLOCK_KIT_TEMPLATE = "slack_digest_block_kit_template"
        private const val KEY_SLACK_AUTO_DIGEST_ENABLED = "slack_auto_digest_enabled"
        private const val KEY_SLACK_DIGEST_CRON = "slack_digest_cron"
        private const val KEY_SLACK_AUTO_DIGEST_MAX_ITEMS = "slack_auto_digest_max_items"
        private const val KEY_SLACK_AUTO_DIGEST_UNSENT_ONLY = "slack_auto_digest_unsent_only"
        private const val KEY_SLACK_DAILY_CHANNEL_MESSAGE_LIMIT = "slack_daily_channel_message_limit"
        private const val KEY_RALPH_ORCHESTRATION_ENABLED = "ralph_orchestration_enabled"
        private const val KEY_RALPH_LOOP_ENABLED = "ralph_loop_enabled"
        private const val KEY_RALPH_LOOP_MAX_ITERATIONS = "ralph_loop_max_iterations"
        private const val KEY_RALPH_LOOP_STOP_PHRASE = "ralph_loop_stop_phrase"
        private const val KEY_MAINTENANCE_MODE = "maintenance_mode"
        private const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"
        private const val KEY_OPS_LOG_CHANNEL_ID = "ops_log_channel_id"
        private const val KEY_OPS_REQUEST_CHANNEL_ID = "ops_request_channel_id"
        private const val KEY_SECURITY_ALERT_CHANNEL_ID = "security_alert_channel_id"
        private const val KEY_COMPETITOR_WEEKLY_ENABLED = "competitor_weekly_enabled"
        private const val KEY_COMPETITOR_WEEKLY_CHANNEL_ID = "competitor_weekly_channel_id"
        private const val KEY_COMPETITOR_WEEKLY_DM_MODE = "competitor_weekly_dm_mode"
        private const val KEY_COMPETITOR_WEEKLY_DM_USER_IDS = "competitor_weekly_dm_user_ids"
        private const val KEY_COMPETITOR_WEEKLY_DAY = "competitor_weekly_day"
        private const val KEY_COMPETITOR_WEEKLY_HOUR = "competitor_weekly_hour"
        private const val KEY_COMPETITOR_WEEKLY_CONFIG_CHANGED_AT = "competitor_weekly_config_changed_at"

        /**
         * 경쟁사 주간 설정 중 하나라도 변경되면 configChangedAt 을 자동 갱신하기 위한 감시 키 집합.
         * [KEY_COMPETITOR_WEEKLY_CONFIG_CHANGED_AT] 자체는 포함하지 않는다 — 그건 파생값이다.
         */
        private val COMPETITOR_WEEKLY_KEYS: Set<String> = setOf(
            KEY_COMPETITOR_WEEKLY_ENABLED,
            KEY_COMPETITOR_WEEKLY_CHANNEL_ID,
            KEY_COMPETITOR_WEEKLY_DM_MODE,
            KEY_COMPETITOR_WEEKLY_DM_USER_IDS,
            KEY_COMPETITOR_WEEKLY_DAY,
            KEY_COMPETITOR_WEEKLY_HOUR
        )

        private const val KEY_REVIEW_BATCH_UX_ENABLED = "review_batch_ux_enabled"
        private const val KEY_DEFAULT_REVIEW_PER_CATEGORY = "default_review_per_category"
        private const val DEFAULT_REVIEW_PER_CATEGORY = 20

        private const val KEY_OPS_NOTIFICATION_PROFILE = "ops_notification_profile"
        private const val KEY_OPS_DAILY_FORECAST_HOUR = "ops_daily_forecast_hour"
        private const val KEY_OPS_WEEKLY_REPORT_DAY = "ops_weekly_report_day"
        private const val KEY_OPS_WEEKLY_REPORT_HOUR = "ops_weekly_report_hour"
        private const val KEY_OPS_PIPELINE_COOLDOWN_MINUTES = "ops_pipeline_cooldown_minutes"
        private const val KEY_OPS_INCIDENT_WINDOW_MINUTES = "ops_incident_window_minutes"
        private const val KEY_OPS_INCIDENT_THRESHOLD_CATEGORIES = "ops_incident_threshold_categories"
        private const val KEY_OPS_SCHEDULE_MISS_GRACE_MINUTES = "ops_schedule_miss_grace_minutes"
        private const val KEY_OPS_BUDGET_WARN_PCT = "ops_budget_warn_pct"
        private const val KEY_OPS_BUDGET_CRITICAL_PCT = "ops_budget_critical_pct"
        private const val KEY_OPS_ADMIN_BASE_URL = "ops_admin_base_url"
        private const val KEY_OPS_SILENT_HOURS_ENABLED = "ops_silent_hours_enabled"
        private const val KEY_OPS_RECOVERY_STREAK_THRESHOLD = "ops_recovery_streak_threshold"
        private const val KEY_OPS_LOGS_ENABLED = "ops_logs_enabled"

        // ── 데이터 보관 기간 설정 ──────────────────────────────────────────────
        const val KEY_RETENTION_RSS_ITEMS_DAYS = "retention_rss_items_days"
        const val KEY_RETENTION_BATCH_SUMMARIES_DAYS = "retention_batch_summaries_days"

        /**
         * DB 크기 임계 초과 Slack 알림 마지막 발송 수준 키.
         * 유효값: "ok" | "warning" | "critical"
         * 시스템이 관리하는 내부 상태이며, 관리자가 직접 편집하지 않는다.
         */
        const val KEY_DB_SIZE_ALERT_LAST_LEVEL = "db_size_alert_last_level"

        const val RETENTION_RSS_ITEMS_DAYS_MIN = 7
        const val RETENTION_RSS_ITEMS_DAYS_MAX = 365
        const val RETENTION_RSS_ITEMS_DAYS_DEFAULT = 30

        const val RETENTION_BATCH_SUMMARIES_DAYS_MIN = 7
        const val RETENTION_BATCH_SUMMARIES_DAYS_MAX = 730
        const val RETENTION_BATCH_SUMMARIES_DAYS_DEFAULT = 90
    }
}
