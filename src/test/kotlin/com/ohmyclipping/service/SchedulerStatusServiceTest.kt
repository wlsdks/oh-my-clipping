package com.ohmyclipping.service

import com.ohmyclipping.observability.SchedulerRunTracker
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SchedulerStatusServiceTest {

    private val fixedInstant = Instant.parse("2026-04-10T10:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Nested
    inner class `list 메서드 - 기본 목록 구조` {

        @Test
        fun `등록된 모든 스케줄러를 반환한다`() {
            val tracker = mockk<SchedulerRunTracker>()
            every { tracker.getLastRun(any()) } returns null
            val service = SchedulerStatusService(tracker, clock)

            val result = service.list()

            // 최소한 SystemStatus에 있던 13개 스케줄러가 보이는지 확인
            result.size shouldBe 13
        }

        @Test
        fun `미실행 스케줄러는 status IDLE로 반환한다`() {
            val tracker = mockk<SchedulerRunTracker>()
            every { tracker.getLastRun(any()) } returns null
            val service = SchedulerStatusService(tracker, clock)

            val result = service.list()

            result.all { it.status == "IDLE" } shouldBe true
            result.all { it.lastRunAt == null } shouldBe true
            result.all { it.lastResult == null } shouldBe true
        }
    }

    @Nested
    inner class `lastRun이 있는 경우` {

        @Test
        fun `성공 기록이 있는 스케줄러는 lastResult success + status IDLE`() {
            val tracker = mockk<SchedulerRunTracker>()
            every { tracker.getLastRun(any()) } returns null
            every { tracker.getLastRun("auto_report") } returns SchedulerRunTracker.SchedulerRunRecord(
                lastRunAt = Instant.parse("2026-04-10T09:00:00Z"),
                success = true,
                durationMs = 350,
                lastError = null
            )
            val service = SchedulerStatusService(tracker, clock)

            val result = service.list()
            val autoReport = result.first { it.trackerKey == "auto_report" }

            autoReport.lastResult shouldBe "success"
            autoReport.status shouldBe "IDLE"
            autoReport.lastDurationMs shouldBe 350
            autoReport.lastError.shouldBeNull()
            autoReport.stalenessSeconds shouldBe 3600
        }

        @Test
        fun `실패 기록이 있으면 status FAILED + lastError 포함`() {
            val tracker = mockk<SchedulerRunTracker>()
            every { tracker.getLastRun(any()) } returns null
            every { tracker.getLastRun("data_cleanup") } returns SchedulerRunTracker.SchedulerRunRecord(
                lastRunAt = Instant.parse("2026-04-10T03:00:00Z"),
                success = false,
                durationMs = 120,
                lastError = "Database connection timed out"
            )
            val service = SchedulerStatusService(tracker, clock)

            val result = service.list()
            val cleanup = result.first { it.trackerKey == "data_cleanup" }

            cleanup.lastResult shouldBe "failure"
            cleanup.status shouldBe "FAILED"
            cleanup.lastError shouldBe "Database connection timed out"
        }
    }

    @Nested
    inner class `다음 실행 시각 계산` {

        @Test
        fun `cron 기반 스케줄러는 nextRunAt을 계산해서 반환한다`() {
            val tracker = mockk<SchedulerRunTracker>()
            every { tracker.getLastRun(any()) } returns null
            val service = SchedulerStatusService(tracker, clock)

            val result = service.list()
            // "매시 정각" 스케줄러(auto_report)는 cron이 있어서 다음 실행 시각이 계산되어야 함
            val autoReport = result.first { it.trackerKey == "auto_report" }

            autoReport.nextRunAt.shouldNotBeNull()
        }

        @Test
        fun `fixedDelay 기반 스케줄러는 nextRunAt이 null`() {
            val tracker = mockk<SchedulerRunTracker>()
            every { tracker.getLastRun(any()) } returns null
            val service = SchedulerStatusService(tracker, clock)

            val result = service.list()
            // fixedDelay 기반 (async_clip_job, slack_digest 등)은 예측 불가
            val asyncJob = result.first { it.trackerKey == "async_clip_job" }

            asyncJob.nextRunAt.shouldBeNull()
        }
    }

    @Nested
    inner class `응답 필드 구조 보장` {

        @Test
        fun `모든 스케줄러는 name과 description을 포함한다`() {
            val tracker = mockk<SchedulerRunTracker>()
            every { tracker.getLastRun(any()) } returns null
            val service = SchedulerStatusService(tracker, clock)

            val result = service.list()

            result.all { it.name.isNotBlank() } shouldBe true
            result.all { it.description.isNotBlank() } shouldBe true
            result.all { it.schedule.isNotBlank() } shouldBe true
        }

        @Test
        fun `AutoReportScheduler는 trackerKey가 auto_report이다`() {
            val tracker = mockk<SchedulerRunTracker>()
            every { tracker.getLastRun(any()) } returns null
            val service = SchedulerStatusService(tracker, clock)

            val trackerKeys = service.list().mapNotNull { it.trackerKey }
            trackerKeys shouldContain "auto_report"
            trackerKeys shouldContain "data_cleanup"
            trackerKeys shouldContain "async_clip_job"
        }
    }
}
