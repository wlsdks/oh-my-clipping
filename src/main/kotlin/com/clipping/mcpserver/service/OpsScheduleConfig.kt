package com.clipping.mcpserver.service

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronTrigger
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId

/**
 * 운영 리포트 스케줄러 동적 cron 등록 설정.
 *
 * RuntimeSettingService에서 매 트리거 시점마다 최신 설정을 읽어
 * daily forecast / weekly action report 스케줄을 동적으로 적용한다.
 * 설정이 바뀌면 다음 발동 시각부터 자동 반영된다.
 */
@Configuration
class OpsScheduleConfig(
    private val runtime: RuntimeSettingService,
    private val dailyScheduler: DailyOpsForecastScheduler,
    private val weeklyScheduler: WeeklyOpsActionReportScheduler,
    private val clock: Clock,
) : SchedulingConfigurer {

    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")

    override fun configureTasks(registrar: ScheduledTaskRegistrar) {
        // 일별 운영 예측 — opsDailyForecastHour 시 정각 KST
        registrar.addTriggerTask(
            Runnable { dailyScheduler.runOnce(clock.instant()) },
            { ctx ->
                val hour = runtime.current().opsDailyForecastHour.coerceIn(0, 23)
                CronTrigger("0 0 $hour * * *", seoul).nextExecution(ctx)
            }
        )

        // 주간 액션 리포트 — opsWeeklyReportDay 요일, opsWeeklyReportHour 시 정각 KST
        registrar.addTriggerTask(
            Runnable { weeklyScheduler.runOnce(clock.instant()) },
            { ctx ->
                val settings = runtime.current()
                val day = settings.opsWeeklyReportDay.toCronToken()
                val hour = settings.opsWeeklyReportHour.coerceIn(0, 23)
                CronTrigger("0 0 $hour * * $day", seoul).nextExecution(ctx)
            }
        )
    }

    companion object {
        /**
         * DayOfWeek를 Spring CronTrigger 요일 토큰으로 변환한다.
         * ScheduleMissDetector 등 다른 컴포넌트에서도 import 가능하다.
         */
        fun DayOfWeek.toCronToken(): String = when (this) {
            DayOfWeek.MONDAY -> "MON"
            DayOfWeek.TUESDAY -> "TUE"
            DayOfWeek.WEDNESDAY -> "WED"
            DayOfWeek.THURSDAY -> "THU"
            DayOfWeek.FRIDAY -> "FRI"
            DayOfWeek.SATURDAY -> "SAT"
            DayOfWeek.SUNDAY -> "SUN"
        }
    }
}
