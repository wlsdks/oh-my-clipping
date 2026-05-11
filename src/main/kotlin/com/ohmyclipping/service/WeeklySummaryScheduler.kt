package com.ohmyclipping.service

import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.service.port.SlackDeliveryPort
import com.ohmyclipping.store.DeliveryLogStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger {}

/**
 * 매주 월요일 09:05 KST에 운영 로그 채널로 주간 요약을 전송한다.
 * 지난 7일 동안의 DAU 평균, 발송 건수, 기사 클릭 수, 클릭률, 활성 사용자 수를 포함한다.
 */
@Service
class WeeklySummaryScheduler(
    private val slackMessageSender: SlackDeliveryPort,
    private val runtimeSettingService: RuntimeSettingService,
    private val userEventService: UserEventService,
    private val deliveryLogStore: DeliveryLogStore,
    private val metrics: ClippingMetrics
) {

    /**
     * 매주 월요일 09:05 KST에 주간 요약을 운영 로그 채널에 전송한다.
     * 채널 미설정 시 조용히 스킵한다.
     */
    @Scheduled(cron = "0 5 9 * * MON", zone = "Asia/Seoul")
    fun sendWeeklySummary() = metrics.recordSchedulerRun("weekly_summary") {
        try {
            val channelId = resolveOpsLogChannelId() ?: return@recordSchedulerRun

            val now = Instant.now()
            val weekAgo = now.minus(7, ChronoUnit.DAYS)
            val seoulZone = ZoneId.of("Asia/Seoul")
            val today = LocalDate.now(seoulZone)
            val weekAgoDate = today.minusDays(7)

            // DAU 평균을 계산한다
            val dauCounts = userEventService.dailyActiveUsers(weekAgo, now)
            val avgDau = if (dauCounts.isNotEmpty()) {
                dauCounts.sumOf { it.count } / dauCounts.size
            } else {
                0L
            }

            // 발송 건수를 집계한다
            val dailyStats = deliveryLogStore.dailyStats(weekAgoDate, today)
            val totalDeliveries = dailyStats.sumOf { it.sent }
            val totalFailed = dailyStats.sumOf { it.failed }

            // 기사 클릭 수를 집계한다
            val totalClicks = userEventService.countByEventType("article_click", weekAgo, now)

            // 클릭률을 계산한다
            val clickRate = if (totalDeliveries > 0) {
                (totalClicks.toDouble() / totalDeliveries * 100)
                    .let { Math.round(it * 100) / 100.0 }
            } else {
                0.0
            }

            // 활성 사용자 수(기간 내 고유)를 조회한다
            val activeUsers = userEventService.countDistinctUsers(weekAgo, now)

            // 메시지를 포맷한다
            val message = buildString {
                appendLine("📋 주간 요약 리포트 (${weekAgoDate} ~ ${today.minusDays(1)})")
                appendLine()
                appendLine("DAU 평균: ${avgDau}명")
                appendLine("발송: ${totalDeliveries}건 (실패 ${totalFailed}건)")
                appendLine("기사 클릭: ${totalClicks}회")
                appendLine("클릭률: ${clickRate}%")
                append("활성 사용자: ${activeUsers}명")
            }

            slackMessageSender.sendMessage(channelId, message)
            log.info { "주간 요약 리포트 전송 완료: channel=$channelId" }
        } catch (e: Exception) {
            log.warn(e) { "주간 요약 리포트 전송 실패 (무시)" }
        }
    }

    /** 운영 로그 채널 ID를 조회한다. 미설정이면 null. */
    private fun resolveOpsLogChannelId(): String? {
        val channelId = runtimeSettingService.current().opsLogChannelId.trim()
        return channelId.ifBlank { null }
    }
}
