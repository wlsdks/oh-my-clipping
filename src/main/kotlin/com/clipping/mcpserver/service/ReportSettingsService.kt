package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.service.dto.ReportSettingsResponse
import com.clipping.mcpserver.service.dto.ReportSettingsUpdateRequest
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.ReportSettingsStore
import com.clipping.mcpserver.support.SlackChannelIdNormalizer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

/**
 * 자동 리포트 설정 서비스.
 * report_settings 테이블의 key-value 쌍을 구조화된 DTO로 변환하여 제공한다.
 * 설정 변경 시 audit_log에 변경 이력을 기록한다.
 */
@Service
class ReportSettingsService(
    private val store: ReportSettingsStore,
    private val auditLogStore: AuditLogStore,
    private val auditActorResolver: AuditActorResolver
) {

    companion object {
        private val VALID_DAYS = setOf(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
        )
    }

    /**
     * 전체 리포트 설정을 조회한다.
     */
    @Transactional(readOnly = true)
    fun getSettings(): ReportSettingsResponse {
        val all = store.findAll()
        return ReportSettingsResponse(
            weeklyEnabled = all["weekly_enabled"].toBoolean(),
            weeklyDay = all["weekly_day"] ?: "MONDAY",
            weeklyHour = all["weekly_hour"]?.toIntOrNull() ?: 9,
            weeklySlackChannelId = all["weekly_slack_channel_id"]?.trim()?.ifBlank { null },
            weeklyIncludeKeywordTrend = all["weekly_include_keyword_trend"]?.toBoolean() ?: true,
            weeklyIncludeCompetitor = all["weekly_include_competitor"]?.toBoolean() ?: true,
            weeklyIncludeTopArticles = all["weekly_include_top_articles"]?.toBoolean() ?: true,
            weeklyIncludeSentiment = all["weekly_include_sentiment"].toBoolean(),
            monthlyEnabled = all["monthly_enabled"].toBoolean(),
            monthlyHour = all["monthly_hour"]?.toIntOrNull() ?: 9,
            monthlySlackChannelId = all["monthly_slack_channel_id"]?.trim()?.ifBlank { null }
        )
    }

    /**
     * 리포트 설정을 부분 업데이트한다.
     * null이 아닌 필드만 변경하고, 나머지는 기존 값을 유지한다.
     *
     * @return 변경 후 전체 설정
     */
    /**
     * 리포트 설정을 부분 업데이트한다.
     * 채널 ID 형식 검증, 변경 감사 로그 기록을 포함한다.
     */
    @Transactional
    fun updateSettings(request: ReportSettingsUpdateRequest, actorUsername: String? = null): ReportSettingsResponse {
        val before = getSettings()
        val changes = mutableListOf<String>()

        // 각 필드를 개별 key-value로 upsert하고 변경 내역을 추적한다.
        request.weeklyEnabled?.let {
            store.upsert("weekly_enabled", it.toString())
            if (it != before.weeklyEnabled) changes.add("weeklyEnabled: ${before.weeklyEnabled} → $it")
        }
        request.weeklyDay?.let { day ->
            val normalized = day.uppercase()
            ensureValid(normalized in VALID_DAYS) { "유효하지 않은 요일입니다: $day" }
            store.upsert("weekly_day", normalized)
            if (normalized != before.weeklyDay) changes.add("weeklyDay: ${before.weeklyDay} → $normalized")
        }
        request.weeklyHour?.let { hour ->
            ensureValid(hour in 0..23) { "시간은 0~23 범위여야 합니다: $hour" }
            store.upsert("weekly_hour", hour.toString())
            if (hour != before.weeklyHour) changes.add("weeklyHour: ${before.weeklyHour} → $hour")
        }
        request.weeklySlackChannelId?.let { raw ->
            val validated = validateAndNormalizeChannelId(raw)
            store.upsert("weekly_slack_channel_id", validated.orEmpty())
            if (validated != before.weeklySlackChannelId) changes.add("weeklySlackChannelId changed")
        }
        request.weeklyIncludeKeywordTrend?.let { store.upsert("weekly_include_keyword_trend", it.toString()) }
        request.weeklyIncludeCompetitor?.let { store.upsert("weekly_include_competitor", it.toString()) }
        request.weeklyIncludeTopArticles?.let { store.upsert("weekly_include_top_articles", it.toString()) }
        request.weeklyIncludeSentiment?.let { store.upsert("weekly_include_sentiment", it.toString()) }
        request.monthlyEnabled?.let {
            store.upsert("monthly_enabled", it.toString())
            if (it != before.monthlyEnabled) changes.add("monthlyEnabled: ${before.monthlyEnabled} → $it")
        }
        request.monthlyHour?.let { hour ->
            ensureValid(hour in 0..23) { "시간은 0~23 범위여야 합니다: $hour" }
            store.upsert("monthly_hour", hour.toString())
            if (hour != before.monthlyHour) changes.add("monthlyHour: ${before.monthlyHour} → $hour")
        }
        request.monthlySlackChannelId?.let { raw ->
            val validated = validateAndNormalizeChannelId(raw)
            store.upsert("monthly_slack_channel_id", validated.orEmpty())
            if (validated != before.monthlySlackChannelId) changes.add("monthlySlackChannelId changed")
        }

        // 변경이 있으면 감사 로그에 기록한다
        if (changes.isNotEmpty()) {
            val actor = auditActorResolver.resolve(actorUsername)
            auditLogStore.log(
                actorId = actor.id,
                actorName = actor.name,
                targetType = "REPORT_SETTINGS",
                targetId = "global",
                action = "UPDATE",
                detail = changes.joinToString(", ")
            )
            log.info { "Report settings updated by ${actor.name}: ${changes.joinToString(", ")}" }
        }

        return getSettings()
    }

    /**
     * Slack 채널 ID를 검증하고 정규화한다.
     * blank → null, 비어있지 않으면 SlackChannelIdNormalizer로 형식을 검증한다.
     */
    private fun validateAndNormalizeChannelId(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        return SlackChannelIdNormalizer.normalize(trimmed)
            ?: throw InvalidInputException("올바르지 않은 Slack 채널 ID 형식입니다: $value")
    }
}
