package com.ohmyclipping.service.port

import com.ohmyclipping.service.port.OpsNotificationEvent
import java.time.Instant

/**
 * 운영 로그 알림 포트 인터페이스.
 *
 * 서비스 계층이 Slack 등 외부 채널에 운영 알림을 발송할 때 사용하는 계약.
 * 구현체는 adapter/out/slack 패키지에 위치하며, 서비스는 이 인터페이스에만 의존한다.
 *
 * 메시지 유형(M1~M13)은 설계 문서를 참고한다.
 */
interface OpsLogNotifier {

    /** M1 — 파이프라인 실행 성공 알림 */
    fun postPipelineSuccess(run: PipelineRunOpsEvent)

    /** M2 — 파이프라인 실행 실패 알림 (실패한 스텝 목록 포함) */
    fun postPipelineFailure(run: PipelineRunOpsEvent, failedSteps: List<PipelineStepTraceOpsEvent>)

    /** M3 — 파이프라인 연속 성공 복구 알림 (streak: 연속 성공 횟수) */
    fun postPipelineRecovery(categoryId: String, run: PipelineRunOpsEvent, streak: Int)

    /** M4 — 인시던트 윈도우 집계 알림 */
    fun postIncident(window: IncidentWindowState)

    /** M5 — 스케줄 미발동 알림 (graceMinutes 이후에도 실행 안 된 경우) */
    fun postScheduleMiss(scheduleName: String, expectedAt: Instant, graceMinutes: Int)

    /** M6 — 다이제스트 발송 실패 목록 알림 */
    fun postDigestFailures(failures: List<DigestFailure>)

    /** M7~M10 — 범용 운영 이벤트 알림 (context: 이벤트별 추가 파라미터) */
    fun postOpsEvent(event: OpsNotificationEvent, context: Map<String, Any?>)

    /** M11 — 시간대별 배치 처리 결과 요약 알림 */
    fun postHourlyBatch(summary: HourlyBatchSummary)

    /** M12 — 일별 운영 예측 리포트 알림 */
    fun postDailyForecast(forecast: DailyForecast)

    /** M13 — 주간 액션 리포트 알림 */
    fun postWeeklyActionReport(report: WeeklyActionReport)

    /**
     * DB 크기 95% 임계 초과 CRITICAL 알림.
     * opsLogsEnabled kill switch, Silent Hours(CRITICAL이므로 무시), admin 버튼 URL을 자동으로 적용한다.
     *
     * @param databaseSizeMegabytes 현재 DB 크기 (MB)
     * @param limitMegabytes DB 한도 크기 (MB)
     * @param percent 현재 사용률 (0.0~100.0)
     */
    fun postDbSizeCritical(databaseSizeMegabytes: Long, limitMegabytes: Long, percent: Double)
}
