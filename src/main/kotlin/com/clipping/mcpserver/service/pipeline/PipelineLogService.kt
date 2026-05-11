package com.clipping.mcpserver.service.pipeline

import com.clipping.mcpserver.service.IncidentWindowTracker
import com.clipping.mcpserver.service.RuntimeSettingService
import com.clipping.mcpserver.service.dto.clipping.PipelineRunResult
import com.clipping.mcpserver.service.dto.clipping.PipelineStepStatus
import com.clipping.mcpserver.service.port.PipelineRunOpsEvent
import com.clipping.mcpserver.service.port.PipelineStepTraceOpsEvent
import com.clipping.mcpserver.service.port.OpsLogNotifier
import com.clipping.mcpserver.service.port.SlackDeliveryPort
import com.clipping.mcpserver.service.tx.publishAfterCommit
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

private val log = KotlinLogging.logger {}

/**
 * 파이프라인 실행 완료 후 OpsLogNotifier 포트를 통해 운영 알림을 발송한다.
 * 채널 미설정 시 조용히 스킵하며, 전송 실패 시에도 파이프라인 흐름을 중단하지 않는다.
 *
 * Slack 직접 호출 제거: 모든 파이프라인 라이프사이클 알림은 OpsLogNotifier를 통해 처리한다.
 */
@Service
class PipelineLogService(
    private val slackMessageSender: SlackDeliveryPort,
    private val runtimeSettingService: RuntimeSettingService,
    private val opsLogNotifier: OpsLogNotifier,
    private val incidentWindowTracker: IncidentWindowTracker,
    private val recoveryDetector: RecoveryDetector,
) {

    /**
     * 파이프라인 실패 시 OpsLogNotifier를 통해 알림을 발송하고 인시던트 트래커에 등록한다.
     *
     * @param run 실패한 파이프라인 실행 운영 알림 DTO
     * @param failedSteps 실패한 단계 추적 운영 알림 DTO 목록
     */
    fun onPipelineFailure(run: PipelineRunOpsEvent, failedSteps: List<PipelineStepTraceOpsEvent>) {
        publishAfterCommit {
            try {
                opsLogNotifier.postPipelineFailure(run, failedSteps)
                incidentWindowTracker.recordFailure(run.categoryId, run.id)
            } catch (e: RuntimeException) {
                log.warn(e) { "파이프라인 실패 알림 전송 실패 (무시)" }
            }
        }
    }

    /**
     * 파이프라인 성공 완료 시 OpsLogNotifier를 통해 알림을 발송하고 복구 감지기에 알린다.
     *
     * @param run 완료된 파이프라인 실행 운영 알림 DTO
     */
    fun onPipelineCompleted(run: PipelineRunOpsEvent) {
        publishAfterCommit {
            try {
                opsLogNotifier.postPipelineSuccess(run)
                recoveryDetector.onRunCompleted(run)
            } catch (e: RuntimeException) {
                log.warn(e) { "파이프라인 완료 알림 전송 실패 (무시)" }
            }
        }
    }

    /**
     * 자유 형식 운영 로그를 채널로 전송한다.
     * 기존 비파이프라인 메시지(스케줄러 에러 등)에만 사용한다.
     */
    fun sendOpsLog(message: String) {
        try {
            val channelId = resolveOpsLogChannelId() ?: return
            slackMessageSender.sendMessage(channelId, message)
        } catch (e: RuntimeException) {
            log.warn(e) { "운영 로그 전송 실패 (무시)" }
        }
    }

    /** 파이프라인 시작 시 운영 로그 채널에 알린다. */
    fun sendPipelineStartLog(categoryName: String) {
        try {
            val channelId = resolveOpsLogChannelId() ?: return
            val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
            val timestamp = "%02d/%02d %02d:%02d".format(
                now.monthValue, now.dayOfMonth, now.hour, now.minute
            )
            val message = "🔄 파이프라인 시작 — $timestamp\n주제: $categoryName"
            slackMessageSender.sendMessage(channelId, message)
        } catch (e: RuntimeException) {
            log.warn(e) { "파이프라인 시작 로그 전송 실패 (무시)" }
        }
    }

    fun sendPipelineLog(result: PipelineRunResult, categoryName: String, durationMs: Long) {
        try {
            // 운영 로그 채널 ID 조회 — 미설정이면 전송 생략
            val channelId = resolveOpsLogChannelId() ?: return
            val message = formatMessage(result, categoryName, durationMs)
            slackMessageSender.sendMessage(channelId, message)
            log.info { "파이프라인 운영 로그 전송 완료: channel=$channelId" }
        } catch (e: RuntimeException) {
            log.warn(e) { "파이프라인 운영 로그 전송 실패 (무시)" }
        }
    }

    /**
     * 파이프라인 실패 시 에러 요약을 운영 로그 채널에 전송한다.
     *
     * @param categoryName 실행 대상 카테고리명
     * @param errorMessage 에러 메시지
     * @param durationMs 전체 파이프라인 소요 시간(밀리초)
     */
    fun sendPipelineFailureLog(categoryName: String, errorMessage: String?, durationMs: Long) {
        try {
            val channelId = resolveOpsLogChannelId() ?: return
            val message = formatFailureMessage(categoryName, errorMessage, durationMs)
            slackMessageSender.sendMessage(channelId, message)
            log.info { "파이프라인 실패 로그 전송 완료: channel=$channelId" }
        } catch (e: RuntimeException) {
            log.warn(e) { "파이프라인 실패 로그 전송 실패 (무시)" }
        }
    }

    /**
     * 운영 로그 채널 ID를 RuntimeSettings → 환경변수 순서로 조회한다.
     * 둘 다 미설정이면 null을 반환하여 전송을 생략한다.
     */
    internal fun resolveOpsLogChannelId(): String? {
        val settings = runtimeSettingService.current()
        val channelId = settings.opsLogChannelId.trim()
        return channelId.ifBlank { null }
    }

    /**
     * 파이프라인 성공 시 요약 메시지를 포맷한다.
     */
    internal fun formatMessage(result: PipelineRunResult, categoryName: String, durationMs: Long): String {
        val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        val timestamp = "%02d/%02d %02d:%02d".format(
            now.monthValue, now.dayOfMonth, now.hour, now.minute
        )
        val totalSeconds = durationMs / 1000

        // 수집 단계 통계
        val collect = result.collect
        val collectTopicCount = collect.categories.size
        val collectedItems = collect.totalCollected
        val duplicateItems = collect.duplicateSkipped

        // 수집 단계 소요 시간 계산
        val collectDurationSeconds = computeStepDurationSeconds(result, "COLLECT")

        // 요약 단계 통계
        val summarizedCount = result.summarize.totalSummarized
        val summarizeDurationSeconds = computeStepDurationSeconds(result, "SUMMARIZE")

        // 발송 단계 통계
        val deliveredCount = if (result.digest.postedToSlack) result.digest.selectedCount else 0
        val skippedCount = result.digest.totalCandidates - result.digest.selectedCount
        val digestDurationSeconds = computeStepDurationSeconds(result, "DIGEST")

        // 실패 단계 경고 수집
        val warnings = mutableListOf<String>()
        result.stepTraces.filter { it.status == PipelineStepStatus.FAILED }.forEach { trace ->
            val detail = trace.detail?.let { " ($it)" } ?: ""
            warnings.add("${stepKoreanName(trace.step)} 실패$detail")
        }
        result.orchestrationWarnings.forEach { warnings.add(it) }

        val sb = StringBuilder()
        sb.appendLine("📊 파이프라인 실행 완료 — $timestamp")
        sb.appendLine()
        sb.appendLine("주제: $categoryName · 총 ${totalSeconds}초")
        sb.appendLine("수집: ${collectTopicCount}개 소스 · ${collectedItems}건 수집 · ${duplicateItems}건 중복 · ${collectDurationSeconds}초")
        sb.appendLine("요약: ${summarizedCount}건 완료 · ${summarizeDurationSeconds}초")
        sb.append("발송: ${deliveredCount}건 발송 · ${skippedCount}건 건너뜀 · ${digestDurationSeconds}초")

        if (warnings.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine()
            warnings.forEach { sb.appendLine("⚠️ $it") }
        }

        return sb.toString().trimEnd()
    }

    /**
     * 파이프라인 실패 시 에러 요약 메시지를 포맷한다.
     */
    internal fun formatFailureMessage(categoryName: String, errorMessage: String?, durationMs: Long): String {
        val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        val timestamp = "%02d/%02d %02d:%02d".format(
            now.monthValue, now.dayOfMonth, now.hour, now.minute
        )
        val totalSeconds = durationMs / 1000
        val errorDetail = errorMessage?.take(200) ?: "알 수 없는 오류"

        return buildString {
            appendLine("❌ 파이프라인 실행 실패 — $timestamp")
            appendLine()
            appendLine("주제: $categoryName · ${totalSeconds}초 경과")
            append("오류: $errorDetail")
        }
    }

    /** 단계별 소요 시간(초)을 step trace에서 계산한다. */
    private fun computeStepDurationSeconds(result: PipelineRunResult, step: String): Long {
        val trace = result.stepTraces.find { it.step == step } ?: return 0
        return try {
            val duration = Duration.between(
                java.time.Instant.parse(trace.startedAt),
                java.time.Instant.parse(trace.endedAt)
            )
            duration.seconds
        } catch (_: DateTimeParseException) {
            0
        }
    }

    /** 파이프라인 단계 이름을 한국어로 변환한다. */
    private fun stepKoreanName(step: String): String = when (step) {
        "COLLECT" -> "수집"
        "SUMMARIZE" -> "요약"
        "DIGEST" -> "발송"
        else -> step
    }
}
