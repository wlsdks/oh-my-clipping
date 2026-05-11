package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.OpsScheduleConfig.Companion.toCronToken
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.scheduling.support.CronTrigger
import org.springframework.scheduling.support.SimpleTriggerContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

class OpsScheduleConfigTest {

    @Test
    fun `opsDailyForecastHour=8이면 다음 발동은 KST 08_00`() {
        val trigger = CronTrigger("0 0 8 * * *", ZoneId.of("Asia/Seoul"))
        // KST 2026-04-15 19:00 = UTC 2026-04-15T10:00:00Z (lastCompletion 기준으로 다음 실행 계산)
        val now = Instant.parse("2026-04-15T10:00:00Z")
        val ctx = SimpleTriggerContext(null, null, now)
        val nextInstant: Instant = trigger.nextExecution(ctx)!!
        val kst = nextInstant.atZone(ZoneId.of("Asia/Seoul"))
        kst.hour shouldBe 8
        kst.dayOfMonth shouldBe 16 // 다음 날
    }

    @Test
    fun `opsWeeklyReportDay=MONDAY hour=9이면 다음 발동은 월요일 KST 09_00`() {
        val trigger = CronTrigger("0 0 9 * * MON", ZoneId.of("Asia/Seoul"))
        // KST 2026-04-15 수요일 19:00 = UTC 2026-04-15T10:00:00Z (lastCompletion 기준)
        val now = Instant.parse("2026-04-15T10:00:00Z")
        val nextInstant: Instant = trigger.nextExecution(SimpleTriggerContext(null, null, now))!!
        val kst = nextInstant.atZone(ZoneId.of("Asia/Seoul"))
        kst.dayOfWeek shouldBe DayOfWeek.MONDAY
        kst.hour shouldBe 9
    }

    @Test
    fun `toCronToken은 모든 DayOfWeek를 올바른 cron 토큰으로 변환한다`() {
        DayOfWeek.MONDAY.toCronToken() shouldBe "MON"
        DayOfWeek.TUESDAY.toCronToken() shouldBe "TUE"
        DayOfWeek.WEDNESDAY.toCronToken() shouldBe "WED"
        DayOfWeek.THURSDAY.toCronToken() shouldBe "THU"
        DayOfWeek.FRIDAY.toCronToken() shouldBe "FRI"
        DayOfWeek.SATURDAY.toCronToken() shouldBe "SAT"
        DayOfWeek.SUNDAY.toCronToken() shouldBe "SUN"
    }

    @Test
    fun `opsDailyForecastHour=23 경계값은 자정 직전에 발동한다`() {
        val trigger = CronTrigger("0 0 23 * * *", ZoneId.of("Asia/Seoul"))
        val now = Instant.parse("2026-04-15T14:01:00Z") // KST 23:01 → 다음날 23:00
        val nextInstant: Instant = trigger.nextExecution(SimpleTriggerContext(null, null, now))!!
        val kst = nextInstant.atZone(ZoneId.of("Asia/Seoul"))
        kst.hour shouldBe 23
    }
}
