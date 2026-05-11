package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsNotificationEvent

import com.ohmyclipping.service.notification.OperationsNotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * 키워드 급변 감지 및 Slack 알림 서비스.
 * 최근 14일간 키워드 변화율이 ±30% 이상인 항목을 탐지하여 알림을 전송한다.
 */
@Service
class KeywordAlertService(
    private val keywordTrendService: KeywordTrendService,
    private val notificationService: OperationsNotificationService
) {

    companion object {
        const val CHANGE_THRESHOLD = 0.3
        const val TREND_DAYS = 14
        const val TREND_TOP = 20
    }

    /**
     * 키워드 변화율을 점검하고, 임계치를 초과하는 항목이 있으면 Slack 알림을 전송한다.
     *
     * @param channelId 알림을 보낼 Slack 채널 ID
     * @return 알림 대상 키워드 수 (0이면 알림 미전송)
     */
    fun checkAndAlert(): Int {
        // 최근 14일 상위 20개 키워드 트렌드를 조회한다.
        val trendResponse = keywordTrendService.getKeywordTrend(
            days = TREND_DAYS,
            top = TREND_TOP,
            categoryId = null
        )

        // 변화율 임계치(±30%) 이상인 키워드를 필터링한다.
        val alertKeywords = trendResponse.keywords.filter { item ->
            item.changeRate >= CHANGE_THRESHOLD || item.changeRate <= -CHANGE_THRESHOLD
        }

        if (alertKeywords.isEmpty()) {
            logger.debug { "키워드 급변 감지 항목 없음" }
            return 0
        }

        // Slack 메시지를 포맷팅한다.
        val message = buildString {
            appendLine(":rotating_light: *키워드 급변 알림*")
            appendLine("최근 ${TREND_DAYS}일 기준, 변화율 ±${(CHANGE_THRESHOLD * 100).toInt()}% 이상 키워드:")
            appendLine()
            for (item in alertKeywords.sortedByDescending { kotlin.math.abs(it.changeRate) }) {
                val direction = if (item.changeRate > 0) ":chart_with_upwards_trend:" else ":chart_with_downwards_trend:"
                val sign = if (item.changeRate > 0) "+" else ""
                appendLine("$direction *${item.keyword}* — ${sign}${(item.changeRate * 100).toInt()}% (총 ${item.totalCount}건)")
            }
        }

        // 중앙 알림 서비스를 통해 전송한다.
        notificationService.sendOps(
            OpsNotificationEvent.KEYWORD_VOLATILITY, message,
            mapOf("date" to java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString())
        )
        logger.info { "키워드 급변 알림 전송 완료: ${alertKeywords.size}건" }

        return alertKeywords.size
    }
}
