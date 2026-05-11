package com.ohmyclipping.service.competitor

import com.ohmyclipping.model.AccountApprovalStatus
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.RuntimeSettingService
import com.ohmyclipping.service.toLlmCompetitorTimelineItem
import com.ohmyclipping.service.port.CompetitorWeeklyInsight
import com.ohmyclipping.service.port.LlmSummarizationPort
import com.ohmyclipping.service.port.SlackDeliveryPort
import com.ohmyclipping.service.dto.CompetitorTimelineItem
import com.ohmyclipping.service.dto.SovResponse
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.ReportDeliveryLogStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ohmyclipping.support.InterruptibleSleep
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * 경쟁사 주간 다이제스트 스케줄러.
 *
 * 매시 정각에 실행되어 RuntimeSettings의 경쟁사 주간 설정과 현재 시각을 대조하고,
 * 조건이 맞으면 SOV + 주요 기사 + AI 인사이트를 수집하여 Slack 채널/DM으로 발송한다.
 * 각 데이터 소스 실패 시 graceful degradation을 적용한다.
 */
@Component
class CompetitorWeeklyDigestScheduler(
    private val runtimeSettingService: RuntimeSettingService,
    private val competitorWatchlistService: CompetitorWatchlistService,
    private val clippingSummarizer: LlmSummarizationPort,
    private val slackMessageSender: SlackDeliveryPort,
    private val adminUserStore: AdminUserStore,
    private val reportDeliveryLogStore: ReportDeliveryLogStore,
    private val metrics: ClippingMetrics,
    private val objectMapper: ObjectMapper
) {

    /**
     * 매시 정각(서울 시간)에 실행된다.
     * 설정에 따라 경쟁사 주간 다이제스트 발송 여부를 판단한다.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    fun tick() = metrics.recordSchedulerRun("competitor_weekly_digest") {
        logger.info { "CompetitorWeeklyDigestScheduler started" }
        val start = System.nanoTime()
        tick(LocalDateTime.now(seoulZone))
        val elapsed = (System.nanoTime() - start) / 1_000_000
        logger.info { "CompetitorWeeklyDigestScheduler completed in ${elapsed}ms" }
    }

    /**
     * 지정 시각 기준으로 경쟁사 주간 다이제스트 발송 여부를 평가한다.
     * 테스트에서는 고정 시간을 넣어 멱등성과 스케줄 매칭을 검증한다.
     */
    internal fun tick(now: LocalDateTime) {
        try {
            val settings = runtimeSettingService.current()

            // 유지보수 모드이면 모든 발송을 건너뛴다.
            if (settings.maintenanceMode) return

            // 비활성화 상태이면 발송하지 않는다
            if (!settings.competitorWeeklyEnabled) return

            // 채널이 설정되지 않았으면 발송하지 않는다
            val channelId = settings.competitorWeeklyChannelId.trim().takeIf { it.isNotEmpty() }
            if (channelId == null) {
                logger.warn { "경쟁사 주간 다이제스트 Slack 채널 미설정 — 발송 생략" }
                return
            }

            // 요일/시간이 설정과 일치하는지 확인한다
            if (!matchesDayAndHour(settings.competitorWeeklyDay, settings.competitorWeeklyHour, now)) {
                return
            }

            // 같은 주에 이미 발송했는지 확인한다.
            // 단, 마지막 발송 이후 관리자가 day/hour/channel 등 설정을 변경했다면(configChangedAt 갱신)
            // 기존 슬롯을 삭제하고 다시 예약한다 — 관리자가 시간을 바꾸면 그에 맞춰 재발송된다.
            val periodKey = weeklyPeriodKey(now.toLocalDate())
            val logId = reserveOrResendAfterConfigChange(settings, periodKey, channelId)
            if (logId == null) {
                logger.debug { "경쟁사 주간 다이제스트 이미 예약/발송됨 — 스킵 ($periodKey, $channelId)" }
                // 기존 슬롯이 FAILED 상태일 수 있으므로 재시도 로직은 실행한다
                retryFailedCompetitorDigests(settings, periodKey, now.toLocalDate())
                return
            }

            // 데이터 수집 및 발송
            generateAndSend(logId, channelId, settings, now.toLocalDate())

            // 같은 주에 다른 채널의 FAILED된 슬롯이 있으면 재시도한다
            retryFailedCompetitorDigests(settings, periodKey, now.toLocalDate())
        } catch (e: Exception) {
            logger.error(e) { "경쟁사 주간 다이제스트 스케줄러 실행 중 오류 발생" }
        }
    }

    /**
     * 요일과 시간이 설정값과 일치하는지 확인한다.
     */
    internal fun matchesDayAndHour(settingDay: String, settingHour: Int, now: LocalDateTime): Boolean {
        return now.dayOfWeek.name == settingDay.uppercase() && now.hour == settingHour
    }

    /**
     * 슬롯을 예약하거나, 같은 주에 이미 SENT된 슬롯이 있더라도 관리자가 설정을 변경한
     * 이후라면 기존 슬롯을 삭제하고 새로 예약한다.
     *
     * @return 새로 발급된 slot ID, 재발송이 불가능하거나 기존 슬롯이 RESERVED 상태이면 null
     */
    private fun reserveOrResendAfterConfigChange(
        settings: RuntimeSettingService.RuntimeSettings,
        periodKey: String,
        channelId: String
    ): String? {
        // 1차 시도: 슬롯이 없으면 정상 예약
        val firstTry = reportDeliveryLogStore.tryReserve(REPORT_TYPE, periodKey, channelId)
        if (firstTry != null) return firstTry

        // 슬롯이 이미 존재한다 — 재발송 가능 여부 판단
        val existing = reportDeliveryLogStore.findByKey(REPORT_TYPE, periodKey, channelId)
            ?: return null  // 동시성으로 사라졌을 수 있다 — 그대로 스킵

        // RESERVED 상태(다른 인스턴스가 발송 중)는 건드리지 않는다
        if (existing.status != "SENT") return null

        // configChangedAt이 비어 있으면 레거시 동작(중복 방지 유지)
        val changedAt = parseConfigChangedAt(settings.competitorWeeklyConfigChangedAt) ?: return null

        // 마지막 발송 시각 < 설정 변경 시각이면 재발송 허용
        if (existing.updatedAt.isBefore(changedAt)) {
            logger.info {
                "경쟁사 주간 설정 변경 후 재발송 — 기존 slot ${existing.id} 삭제 ($periodKey, $channelId)"
            }
            reportDeliveryLogStore.deleteById(existing.id)
            return reportDeliveryLogStore.tryReserve(REPORT_TYPE, periodKey, channelId)
        }

        return null
    }

    /** ISO-8601 timestamp 문자열을 Instant로 파싱한다. 빈 문자열/잘못된 값은 null. */
    private fun parseConfigChangedAt(raw: String): java.time.Instant? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { java.time.Instant.parse(trimmed) }.getOrNull()
    }

    /**
     * 주차 기반 중복 방지 키를 생성한다.
     * ISO 주차 기준으로 "2026-W15" 형태를 반환한다.
     */
    internal fun weeklyPeriodKey(today: LocalDate): String {
        val weekFields = WeekFields.ISO
        val weekBasedYear = today.get(weekFields.weekBasedYear())
        val week = today.get(weekFields.weekOfWeekBasedYear())
        return String.format(Locale.ROOT, "%04d-W%02d", weekBasedYear, week)
    }

    /**
     * 데이터를 수집하고 Block Kit 메시지를 조립하여 채널 + DM으로 발송한다.
     * 개별 데이터 소스 실패 시 해당 섹션만 생략하는 graceful degradation을 적용한다.
     */
    private fun generateAndSend(
        logId: String,
        channelId: String,
        settings: RuntimeSettingService.RuntimeSettings,
        today: LocalDate
    ) {
        try {
            // 주요 기사 조회 — 실패 시 전체 발송을 중단한다 (핵심 데이터)
            val topArticles = competitorWatchlistService.getTopArticlesForWeeklyDigest()

            // 기사가 0건이면 발송하지 않는다
            if (topArticles.values.all { it.isEmpty() }) {
                reportDeliveryLogStore.markFailed(logId, null, "기사 0건 — 발송 생략")
                logger.info { "경쟁사 주간 다이제스트 기사 0건 — 발송 생략" }
                return
            }

            // SOV 조회 — 실패 시 null로 처리하여 해당 섹션만 생략
            val sov = runCatching {
                competitorWatchlistService.getShareOfVoiceWithDelta()
            }.onFailure {
                logger.warn(it) { "SOV 조회 실패 — SOV 섹션 없이 진행" }
            }.getOrNull()

            // AI 인사이트 생성 — 실패 시 null로 처리하여 해당 섹션만 생략
            val aiInsight = runCatching {
                clippingSummarizer.summarizeCompetitorWeekly(toLlmTimelineMap(topArticles), buildPeriodLabel(today))
            }.onFailure {
                logger.warn(it) { "AI 인사이트 생성 실패 — 인사이트 없이 진행" }
            }.getOrNull()

            val periodLabel = buildPeriodLabel(today)

            // Block Kit 메시지 조립
            val (text, blocks) = CompetitorWeeklyBlockKit.build(
                sov = sov,
                topArticles = topArticles,
                aiInsight = aiInsight,
                periodLabel = periodLabel
            )

            // 채널 발송
            val sendResult = slackMessageSender.sendMessage(
                channelId = channelId,
                text = text,
                blocks = blocks
            )
            reportDeliveryLogStore.markSent(logId, REPORT_TYPE, sendResult.ts.ifEmpty { null })
            logger.info { "경쟁사 주간 다이제스트 채널 발송 완료 → $channelId" }

            // DM 발송 (채널 발송 성공 후 — DM 실패는 전체를 실패로 처리하지 않는다)
            sendDms(settings, text, blocks)
        } catch (e: Exception) {
            reportDeliveryLogStore.markFailed(logId, null, resolveFailureMessage(e))
            logger.error(e) { "경쟁사 주간 다이제스트 생성/발송 실패" }
        }
    }

    /**
     * DM 모드에 따라 개별 사용자에게 다이제스트를 전달한다.
     *
     * - "off": DM 미발송
     * - "all": 승인된 전체 사용자 중 slackDmChannelId가 있는 사용자에게 발송
     * - "selected": 지정된 사용자 ID 목록 중 slackDmChannelId가 있는 사용자에게 발송
     */
    private fun sendDms(
        settings: RuntimeSettingService.RuntimeSettings,
        text: String,
        blocks: List<Map<String, Any?>>
    ) {
        val dmMode = settings.competitorWeeklyDmMode
        if (dmMode == "off") return

        val users = when (dmMode) {
            "all" -> adminUserStore.listByRole(AccountRole.USER, AccountApprovalStatus.APPROVED)
            "selected" -> {
                val userIds = parseSelectedUserIds(settings.competitorWeeklyDmUserIds)
                if (userIds.isEmpty()) return
                adminUserStore.findByIds(userIds)
            }
            else -> {
                logger.warn { "알 수 없는 DM 모드: $dmMode — DM 생략" }
                return
            }
        }

        // slackDmChannelId가 있는 사용자에게만 발송한다
        val targets = users.filter { !it.slackDmChannelId.isNullOrBlank() }
        targets.forEachIndexed { index, user ->
            val channelId = user.slackDmChannelId ?: return@forEachIndexed
            runCatching {
                slackMessageSender.sendMessage(
                    channelId = channelId,
                    text = text,
                    blocks = blocks
                )
            }.onFailure {
                logger.warn(it) { "경쟁사 주간 다이제스트 DM 발송 실패 — userId=${user.id}" }
            }
            // Slack API 레이트 리밋 보호 — 마지막 항목 이후에는 대기하지 않는다.
            if (index < targets.size - 1) {
                InterruptibleSleep.sleep(
                    delayMs = DM_RATE_LIMIT_DELAY_MS,
                    context = "CompetitorWeeklyDigest DM rate limit"
                )
            }
        }
    }

    /**
     * selected 모드에서 사용자 ID JSON 배열을 파싱한다.
     * 파싱 실패 시 빈 리스트를 반환한다.
     */
    internal fun parseSelectedUserIds(json: String): List<String> {
        return runCatching {
            objectMapper.readValue<List<String>>(json)
        }.getOrElse { emptyList() }
    }

    /** 발송 기간 레이블을 생성한다. 예: "2026-04-05 ~ 2026-04-11" */
    private fun buildPeriodLabel(today: LocalDate): String {
        val end = today
        val start = end.minusDays(6)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return "${start.format(formatter)} ~ ${end.format(formatter)}"
    }

    /** 저장 가능한 길이로 실패 메시지를 정리한다. */
    private fun resolveFailureMessage(exception: Exception): String {
        val baseMessage = exception.message?.takeIf { it.isNotBlank() }
            ?: exception::class.simpleName
            ?: "Unknown error"
        return baseMessage.take(MAX_ERROR_MESSAGE_LENGTH)
    }

    /**
     * FAILED 상태인 경쟁사 주간 다이제스트 슬롯을 최대 MAX_RETRY_PER_SLOT번까지 재시도한다.
     * 재시도 성공 시 SENT로 마킹하고, 실패 시 에러 메시지를 갱신한다.
     */
    private fun retryFailedCompetitorDigests(
        settings: RuntimeSettingService.RuntimeSettings,
        periodKey: String,
        today: LocalDate
    ) {
        val failedSlots = reportDeliveryLogStore.findFailedByTypeAndPeriod(REPORT_TYPE, periodKey)
        if (failedSlots.isEmpty()) return

        logger.info { "경쟁사 주간 다이제스트 FAILED 슬롯 ${failedSlots.size}건 재시도 시작" }

        // 데이터를 한 번만 수집하여 모든 재시도에 재사용한다
        val topArticles = runCatching {
            competitorWatchlistService.getTopArticlesForWeeklyDigest()
        }.onFailure {
            logger.warn(it) { "FAILED 슬롯 재시도용 기사 수집 실패 — 재시도 스킵" }
        }.getOrNull() ?: return

        if (topArticles.values.all { it.isEmpty() }) {
            logger.info { "FAILED 슬롯 재시도: 기사 0건 — 재시도 스킵" }
            return
        }

        val sov = runCatching {
            competitorWatchlistService.getShareOfVoiceWithDelta()
        }.getOrNull()

        val aiInsight = runCatching {
            clippingSummarizer.summarizeCompetitorWeekly(toLlmTimelineMap(topArticles), buildPeriodLabel(today))
        }.getOrNull()

        val periodLabel = buildPeriodLabel(today)
        val (text, blocks) = CompetitorWeeklyBlockKit.build(
            sov = sov,
            topArticles = topArticles,
            aiInsight = aiInsight,
            periodLabel = periodLabel
        )

        for (slot in failedSlots) {
            // 최대 재시도 횟수를 에러 메시지에서 추적한다 (간이 카운터)
            val retryCount = slot.errorMessage?.let { msg ->
                RETRY_COUNT_PATTERN.find(msg)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            } ?: 0

            if (retryCount >= MAX_RETRY_PER_SLOT) {
                logger.warn { "FAILED 슬롯 ${slot.id} 최대 재시도 초과 (${retryCount}회) — 스킵" }
                continue
            }

            runCatching {
                val sendResult = slackMessageSender.sendMessage(
                    channelId = slot.channelId,
                    text = text,
                    blocks = blocks
                )
                reportDeliveryLogStore.markSent(slot.id, REPORT_TYPE, sendResult.ts.ifEmpty { null })
                logger.info { "FAILED 슬롯 ${slot.id} 재시도 성공 → ${slot.channelId}" }
            }.onFailure { e ->
                val newMsg = "[retry=${retryCount + 1}] ${resolveFailureMessage(e as Exception)}"
                reportDeliveryLogStore.markFailed(slot.id, null, newMsg)
                logger.warn(e) { "FAILED 슬롯 ${slot.id} 재시도 실패 (${retryCount + 1}/${MAX_RETRY_PER_SLOT})" }
            }
        }
    }

    private fun toLlmTimelineMap(
        topArticles: Map<String, List<CompetitorTimelineItem>>
    ) = topArticles.mapValues { (_, items) ->
        items.map { it.toLlmCompetitorTimelineItem() }
    }

    companion object {
        private const val REPORT_TYPE = "COMPETITOR_WEEKLY"
        private const val MAX_ERROR_MESSAGE_LENGTH = 500
        /** FAILED 슬롯 당 최대 재시도 횟수. */
        private const val MAX_RETRY_PER_SLOT = 3
        /** 에러 메시지에서 재시도 횟수를 추출하는 패턴. */
        private val RETRY_COUNT_PATTERN = Regex("\\[retry=(\\d+)]")
        /** Slack API 레이트 리밋 보호용 DM 간 대기(ms). */
        private const val DM_RATE_LIMIT_DELAY_MS = 1100L
        private val seoulZone = ZoneId.of("Asia/Seoul")
    }
}
