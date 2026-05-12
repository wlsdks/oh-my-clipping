package com.ohmyclipping.adapter.out.slack

import com.ohmyclipping.service.port.DailyForecast
import com.ohmyclipping.service.port.DigestFailure
import com.ohmyclipping.service.port.HourlyBatchSummary
import com.ohmyclipping.service.port.IncidentWindowState
import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.service.port.OpsNotificationEvent
import com.ohmyclipping.service.port.PipelineRunOpsEvent
import com.ohmyclipping.service.port.PipelineStepTraceOpsEvent
import com.ohmyclipping.service.port.WeeklyActionReport
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * OSS 기본 [OpsLogNotifier] 구현. 운영 환경에서는 Slack 채널로 발송하는 구현으로 교체한다.
 *
 * 본 stub 의 의도는 [SlackApiMessageSender] 와 동일하다 — Spring DI 가 끊기지 않게 빈을 채워
 * `@SpringBootTest` 컨텍스트 로딩을 가능하게 한다. 호출 시 debug 로그만 남기고 외부 발송은 하지 않는다.
 *
 * OSS sanitization 으로 원본 구현이 제거됐기 때문에 본 stub 이 필요해졌다. 자세한 맥락은 ADR-044 참고.
 */
@Component
class SlackOpsLogNotifier : OpsLogNotifier {

    init {
        log.warn {
            "SlackOpsLogNotifier stub is active — ops alerts are no-op. " +
                "Provide a production OpsLogNotifier bean to override (see ADR-044)."
        }
    }

    override fun postPipelineSuccess(run: PipelineRunOpsEvent) {
        log.debug { "ops stub postPipelineSuccess: ${run.id}" }
    }

    override fun postPipelineFailure(run: PipelineRunOpsEvent, failedSteps: List<PipelineStepTraceOpsEvent>) {
        log.debug { "ops stub postPipelineFailure: ${run.id} (failedSteps=${failedSteps.size})" }
    }

    override fun postPipelineRecovery(categoryId: String, run: PipelineRunOpsEvent, streak: Int) {
        log.debug { "ops stub postPipelineRecovery: $categoryId streak=$streak" }
    }

    override fun postIncident(window: IncidentWindowState) {
        log.debug { "ops stub postIncident: $window" }
    }

    override fun postScheduleMiss(scheduleName: String, expectedAt: Instant, graceMinutes: Int) {
        log.debug { "ops stub postScheduleMiss: $scheduleName expectedAt=$expectedAt grace=${graceMinutes}m" }
    }

    override fun postDigestFailures(failures: List<DigestFailure>) {
        log.debug { "ops stub postDigestFailures: ${failures.size} failures" }
    }

    override fun postOpsEvent(event: OpsNotificationEvent, context: Map<String, Any?>) {
        log.debug { "ops stub postOpsEvent: $event" }
    }

    override fun postHourlyBatch(summary: HourlyBatchSummary) {
        log.debug { "ops stub postHourlyBatch: $summary" }
    }

    override fun postDailyForecast(forecast: DailyForecast) {
        log.debug { "ops stub postDailyForecast: $forecast" }
    }

    override fun postWeeklyActionReport(report: WeeklyActionReport) {
        log.debug { "ops stub postWeeklyActionReport: $report" }
    }

    override fun postDbSizeCritical(databaseSizeMegabytes: Long, limitMegabytes: Long, percent: Double) {
        log.debug { "ops stub postDbSizeCritical: ${databaseSizeMegabytes}MB / ${limitMegabytes}MB (${percent}%)" }
    }
}
