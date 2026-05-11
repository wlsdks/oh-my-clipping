package com.clipping.mcpserver.service.digest

import com.clipping.mcpserver.service.port.DigestFailure
import com.clipping.mcpserver.service.port.OpsLogNotifier
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId

private val log = KotlinLogging.logger {}

/**
 * 다이제스트 발송 결과를 운영 채널에 전송한다.
 * 실패가 있는 경우에만 OpsLogNotifier를 통해 M6 알림을 발송하고,
 * 성공 전용 요약은 더 이상 Slack으로 전송하지 않는다.
 */
@Component
class DigestOpsNotifier(
    private val opsLogNotifier: OpsLogNotifier,
) {

    /** 발송 성공 시 운영 로그를 남긴다. */
    fun notifyDelivered(categoryName: String, targetChannelId: String, itemCount: Int) {
        val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        val timestamp = "%02d/%02d %02d:%02d".format(
            now.monthValue, now.dayOfMonth, now.hour, now.minute
        )
        val targetLabel = if (targetChannelId.startsWith("D") || targetChannelId.startsWith("U")) "DM" else "#channel"
        log.info { "다이제스트 발송 완료 — $timestamp | 주제: $categoryName | 대상: $targetLabel · ${itemCount}건" }
    }

    /**
     * tick 종료 시 실패 목록이 있으면 OpsLogNotifier를 통해 M6 알림을 발송한다.
     * 실패가 없으면 Slack 알림을 발송하지 않는다 (성공 전용 요약 제거).
     *
     * @param failures 실패한 다이제스트 발송 목록
     */
    fun notifyTickSummary(failures: List<DigestFailure>) {
        // 실패가 없으면 Slack 알림을 발송하지 않는다
        if (failures.isEmpty()) return

        runCatching {
            opsLogNotifier.postDigestFailures(failures)
        }.onFailure { e ->
            log.warn(e) { "다이제스트 실패 알림 전송 실패" }
        }
    }
}
