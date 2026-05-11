package com.ohmyclipping.service

import com.ohmyclipping.observability.ClippingMetrics
import io.github.oshai.kotlinlogging.KotlinLogging
import com.ohmyclipping.service.competitor.CompetitorWatchlistService
import com.ohmyclipping.service.dto.ReportSettingsResponse
import com.ohmyclipping.service.port.SlackDeliveryPort
import com.ohmyclipping.store.ReportDeliveryLogStore
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * 자동 리포트 스케줄러.
 * 매시 정각에 실행되어 주간/월간 설정과 현재 시각을 대조하고,
 * 조건이 맞으면 트렌드 스냅샷 + 부가 데이터를 수집하여 Slack으로 발송한다.
 */
@Service
class AutoReportScheduler(
    private val reportSettingsService: ReportSettingsService,
    private val snapshotService: AdminTrendSnapshotService,
    private val keywordTrendService: KeywordTrendService,
    private val competitorWatchlistService: CompetitorWatchlistService,
    private val slackMessageSender: SlackDeliveryPort,
    private val keywordAlertService: KeywordAlertService,
    private val reportDeliveryLogStore: ReportDeliveryLogStore,
    private val runtimeSettingService: RuntimeSettingService,
    private val metrics: ClippingMetrics
) {

    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

    /**
     * 매시 정각에 실행된다.
     * 설정에 따라 주간/월간 리포트 생성 여부를 판단하고, 조건 충족 시 발송한다.
     */
    @Scheduled(cron = "0 0 * * * *")
    fun tick() = metrics.recordSchedulerRun("auto_report") {
        logger.info { "AutoReportScheduler started" }
        val start = System.nanoTime()
        tick(LocalDateTime.now(seoulZone))
        val elapsed = (System.nanoTime() - start) / 1_000_000
        logger.info { "AutoReportScheduler completed in ${elapsed}ms" }
    }

    /**
     * 지정 시각 기준으로 자동 리포트 발송 여부를 평가한다.
     * 테스트에서는 고정 시간을 넣어 멱등성과 스케줄 매칭을 검증한다.
     */
    internal fun tick(now: LocalDateTime) {
        try {
            // 점검 모드에서는 리포트 발송을 중지한다
            if (runtimeSettingService.current().maintenanceMode) return
            val settings = reportSettingsService.getSettings()
            val today = now.toLocalDate()
            val currentHour = now.hour
            val currentDayOfWeek = now.dayOfWeek

            // 현재 시각이 주간 리포트 조건에 맞는지 확인한 뒤 예약 기반으로 발송한다.
            dispatchWeeklyIfDue(settings, today, currentDayOfWeek, currentHour)

            // 현재 시각이 월간 리포트 조건에 맞는지 확인한 뒤 예약 기반으로 발송한다.
            dispatchMonthlyIfDue(settings, today, currentHour)
        } catch (e: Exception) {
            logger.error(e) { "자동 리포트 스케줄러 실행 중 오류 발생" }
        }
    }

    /**
     * 주간 리포트를 생성하고 성공/실패 상태를 영속 로그에 기록한다.
     * 실행 소요시간(duration_ms)을 함께 저장해 관리자 이력 UI에서 확인할 수 있게 한다.
     */
    internal fun generateWeeklyReport(logId: String, channelId: String, settings: ReportSettingsResponse) {
        var snapshotId: String? = null
        val startNanos = System.nanoTime()
        try {
            val snapshot = snapshotService.runSnapshot(
                periodTypeRaw = "WEEKLY",
                categoryId = null,
                regionTypeRaw = "ALL",
                generatedBy = "auto-scheduler"
            )
            snapshotId = snapshot.id

            val message = buildWeeklyMessage(snapshot, settings)
            val sendResult = slackMessageSender.sendMessage(channelId = channelId, text = message)
            val duration = (System.nanoTime() - startNanos) / 1_000_000
            reportDeliveryLogStore.markSent(
                id = logId,
                snapshotId = snapshot.id,
                slackMessageTs = sendResult.ts.ifEmpty { null },
                durationMs = duration
            )
            logger.info { "주간 리포트 발송 완료 → $channelId (${duration}ms)" }

            // 본 리포트 전송 성공 후 보조 알림 실패는
            // 발송 자체를 실패로 되돌리지 않는다.
            runCatching { keywordAlertService.checkAndAlert() }
                .onFailure { logger.warn(it) { "주간 리포트 후속 키워드 알림 실패" } }
        } catch (e: Exception) {
            // 리포트 생성/전송 실패를 영속 로그에 남겨 재시작 후에도 중복 발송을 막는다.
            val duration = (System.nanoTime() - startNanos) / 1_000_000
            reportDeliveryLogStore.markFailed(logId, snapshotId, resolveFailureMessage(e), duration)
            logger.error(e) { "주간 리포트 생성/발송 실패 (${duration}ms)" }
        }
    }

    /**
     * 월간 리포트를 생성하고 성공/실패 상태를 영속 로그에 기록한다.
     * 실행 소요시간(duration_ms)을 함께 저장해 관리자 이력 UI에서 확인할 수 있게 한다.
     */
    internal fun generateMonthlyReport(logId: String, channelId: String, settings: ReportSettingsResponse) {
        var snapshotId: String? = null
        val startNanos = System.nanoTime()
        try {
            val snapshot = snapshotService.runSnapshot(
                periodTypeRaw = "MONTHLY",
                categoryId = null,
                regionTypeRaw = "ALL",
                generatedBy = "auto-scheduler"
            )
            snapshotId = snapshot.id

            val message = buildMonthlyMessage(snapshot)
            val sendResult = slackMessageSender.sendMessage(channelId = channelId, text = message)
            val duration = (System.nanoTime() - startNanos) / 1_000_000
            reportDeliveryLogStore.markSent(
                id = logId,
                snapshotId = snapshot.id,
                slackMessageTs = sendResult.ts.ifEmpty { null },
                durationMs = duration
            )
            logger.info { "월간 리포트 발송 완료 → $channelId (${duration}ms)" }
        } catch (e: Exception) {
            // 월간 리포트도 동일한 멱등성 로그를 사용해 실패 상태를 남긴다.
            val duration = (System.nanoTime() - startNanos) / 1_000_000
            reportDeliveryLogStore.markFailed(logId, snapshotId, resolveFailureMessage(e), duration)
            logger.error(e) { "월간 리포트 생성/발송 실패 (${duration}ms)" }
        }
    }

    /**
     * 현재 요일/시간이 주간 설정과 일치하는지 확인한다.
     */
    internal fun matchesWeekly(
        settingDay: String,
        settingHour: Int,
        currentDay: DayOfWeek,
        currentHour: Int
    ): Boolean {
        val targetDay = try {
            DayOfWeek.valueOf(settingDay.uppercase())
        } catch (_: IllegalArgumentException) {
            return false
        }
        return currentDay == targetDay && currentHour == settingHour
    }

    /**
     * 현재 날짜/시간이 월간 설정(매월 1일)과 일치하는지 확인한다.
     */
    internal fun matchesMonthly(settingHour: Int, today: LocalDate, currentHour: Int): Boolean {
        return today.dayOfMonth == 1 && currentHour == settingHour
    }

    /**
     * 같은 주간 리포트가 재시작/다중 인스턴스에서
     * 중복 발송되지 않도록 주차 키를 계산한다.
     */
    internal fun weeklyPeriodKey(today: LocalDate): String {
        val weekFields = WeekFields.ISO
        val weekBasedYear = today.get(weekFields.weekBasedYear())
        val week = today.get(weekFields.weekOfWeekBasedYear())
        return String.format(Locale.ROOT, "%04d-W%02d", weekBasedYear, week)
    }

    /**
     * 같은 월간 리포트가 재시작/다중 인스턴스에서
     * 중복 발송되지 않도록 월 키를 계산한다.
     */
    internal fun monthlyPeriodKey(today: LocalDate): String {
        return String.format(Locale.ROOT, "%04d-%02d", today.year, today.monthValue)
    }

    /** 주간 리포트 스케줄 조건과 영속 dedupe 예약을 함께 처리한다. */
    private fun dispatchWeeklyIfDue(
        settings: ReportSettingsResponse,
        today: LocalDate,
        currentDayOfWeek: DayOfWeek,
        currentHour: Int
    ) {
        if (!settings.weeklyEnabled) return
        if (!matchesWeekly(settings.weeklyDay, settings.weeklyHour, currentDayOfWeek, currentHour)) return

        // 빈 채널은 예약하지 않고 즉시 스킵해야 불필요한 실패 로그가 쌓이지 않는다.
        val channelId = settings.weeklySlackChannelId?.trim()?.takeIf { it.isNotEmpty() }
        if (channelId == null) {
            logger.warn { "주간 리포트 Slack 채널 미설정 — 발송 생략" }
            return
        }

        val periodKey = weeklyPeriodKey(today)
        val logId = reportDeliveryLogStore.tryReserve(WEEKLY_REPORT, periodKey, channelId)
        if (logId == null) {
            logger.debug { "주간 리포트 이미 예약/발송됨 — 스킵 ($periodKey, $channelId)" }
            return
        }

        generateWeeklyReport(logId, channelId, settings)
    }

    /** 월간 리포트 스케줄 조건과 영속 dedupe 예약을 함께 처리한다. */
    private fun dispatchMonthlyIfDue(
        settings: ReportSettingsResponse,
        today: LocalDate,
        currentHour: Int
    ) {
        if (!settings.monthlyEnabled) return
        if (!matchesMonthly(settings.monthlyHour, today, currentHour)) return

        // 빈 채널은 예약하지 않고 즉시 스킵해야 불필요한 실패 로그가 쌓이지 않는다.
        val channelId = settings.monthlySlackChannelId?.trim()?.takeIf { it.isNotEmpty() }
        if (channelId == null) {
            logger.warn { "월간 리포트 Slack 채널 미설정 — 발송 생략" }
            return
        }

        val periodKey = monthlyPeriodKey(today)
        val logId = reportDeliveryLogStore.tryReserve(MONTHLY_REPORT, periodKey, channelId)
        if (logId == null) {
            logger.debug { "월간 리포트 이미 예약/발송됨 — 스킵 ($periodKey, $channelId)" }
            return
        }

        generateMonthlyReport(logId, channelId, settings)
    }

    /** 주간 자동 리포트 본문을 조립한다. */
    private fun buildWeeklyMessage(
        snapshot: TrendSnapshotResult,
        settings: ReportSettingsResponse
    ): String {
        return buildString {
            appendLine(":newspaper: *주간 자동 리포트*")
            appendLine()
            appendLine("*${snapshot.title}*")
            appendLine(snapshot.summary)
            appendLine()
            if (snapshot.keySignals.isNotEmpty()) {
                appendLine(":key: *핵심 신호:* ${snapshot.keySignals.joinToString(", ")}")
            }
            if (snapshot.actionItems.isNotEmpty()) {
                appendLine()
                appendLine(":pushpin: *액션 아이템:*")
                snapshot.actionItems.forEach { appendLine("• $it") }
            }

            // 선택 섹션은 개별 실패를 삼키고 본문을 축소해 발송한다.
            if (settings.weeklyIncludeKeywordTrend) {
                appendKeywordTrendSection(this)
            }
            if (settings.weeklyIncludeCompetitor) {
                appendCompetitorSection(this)
            }
        }
    }

    /** 월간 자동 리포트 본문을 조립한다. */
    private fun buildMonthlyMessage(snapshot: TrendSnapshotResult): String {
        return buildString {
            appendLine(":calendar: *월간 자동 리포트*")
            appendLine()
            appendLine("*${snapshot.title}*")
            appendLine(snapshot.summary)
            appendLine()
            if (snapshot.keySignals.isNotEmpty()) {
                appendLine(":key: *핵심 신호:* ${snapshot.keySignals.joinToString(", ")}")
            }
            if (snapshot.actionItems.isNotEmpty()) {
                appendLine()
                appendLine(":pushpin: *액션 아이템:*")
                snapshot.actionItems.forEach { appendLine("• $it") }
            }
        }
    }

    /** 저장 가능한 길이로 실패 메시지를 정리한다. */
    private fun resolveFailureMessage(exception: Exception): String {
        val baseMessage = exception.message?.takeIf { it.isNotBlank() }
            ?: exception::class.simpleName
            ?: "Unknown error"
        return baseMessage.take(MAX_ERROR_MESSAGE_LENGTH)
    }

    private fun appendKeywordTrendSection(sb: StringBuilder) {
        try {
            val trend = keywordTrendService.getKeywordTrend(days = 7, top = 5, categoryId = null)
            if (trend.keywords.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine(":bar_chart: *키워드 트렌드 (7일):*")
                trend.keywords.forEach { item ->
                    val sign = if (item.changeRate > 0) "+" else ""
                    val changeRatePercent = (item.changeRate * 100).toInt()
                    sb.appendLine("• ${item.keyword}: ${item.totalCount}건 (${sign}${changeRatePercent}%)")
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "키워드 트렌드 섹션 추가 실패" }
        }
    }

    private fun appendCompetitorSection(sb: StringBuilder) {
        try {
            val sov = competitorWatchlistService.getShareOfVoice(days = 7)
            if (sov.shares.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine(":eyes: *경쟁사 점유율 (7일):*")
                sov.shares.take(5).forEach { item ->
                    sb.appendLine("• ${item.name}: ${item.count}건 (${(item.share * 100).toInt()}%)")
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "경쟁사 섹션 추가 실패" }
        }
    }

    companion object {
        private const val WEEKLY_REPORT = "WEEKLY"
        private const val MONTHLY_REPORT = "MONTHLY"
        private const val MAX_ERROR_MESSAGE_LENGTH = 500
    }
}
