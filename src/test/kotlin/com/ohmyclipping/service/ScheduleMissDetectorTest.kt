package com.ohmyclipping.service

import com.ohmyclipping.service.port.OpsLogNotifier
import com.ohmyclipping.store.PipelineRunStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ScheduleMissDetectorTest {

    private lateinit var autoCollectionScheduler: AutoCollectionScheduler
    private lateinit var pipelineRunStore: PipelineRunStore
    private lateinit var runtime: RuntimeSettingService
    private lateinit var notifier: OpsLogNotifier

    @BeforeEach
    fun setUp() {
        autoCollectionScheduler = mockk()
        pipelineRunStore = mockk(relaxed = true)
        runtime = mockk()
        notifier = mockk(relaxed = true)

        // 기본 cron: 03:05, 07:05, 11:05, 15:05, 19:05, 23:05 KST
        every { autoCollectionScheduler.cronExpression() } returns "0 5 3,7,11,15,19,23 * * *"
        // 기본값: 스케줄러가 아직 한 번도 실행되지 않은 상태
        every { autoCollectionScheduler.lastFiredAt } returns null
    }

    private fun defaultSettings(graceMins: Int = 10) =
        mockk<RuntimeSettingService.RuntimeSettings>(relaxed = true) {
            every { opsScheduleMissGraceMinutes } returns graceMins
        }

    /**
     * 테스트 기준 시각: 2026-04-15 03:20 KST (= 2026-04-14T18:20:00Z)
     * 직전 예정: 2026-04-15 03:05 KST (= 2026-04-14T18:05:00Z) — 15분 전 (grace=10 초과)
     */
    private val nowInstant = Instant.parse("2026-04-14T18:20:00Z")
    private val prevExpected = Instant.parse("2026-04-14T18:05:00Z")

    private fun makeDetector(nowClock: Clock): ScheduleMissDetector =
        ScheduleMissDetector(autoCollectionScheduler, pipelineRunStore, runtime, notifier, nowClock)

    @Nested
    inner class `prev expected이 부팅 이전인 경우` {

        @Test
        fun `부팅 이전 예정 시각은 dedup에 기록되고 알림은 발송 안 된다`() {
            // 서버가 03:20에 시작 → 03:05 예정 시각은 부팅 이전
            val clock = Clock.fixed(nowInstant, ZoneId.of("UTC"))
            val detector = makeDetector(clock)
            every { runtime.current() } returns defaultSettings()

            detector.runCheck()

            verify(exactly = 0) { notifier.postScheduleMiss(any(), any(), any()) }
            // dedup에는 기록된다
            assert(detector.dedupSnapshot().containsKey(prevExpected))
        }
    }

    @Nested
    inner class `grace 기간이 아직 지나지 않은 경우` {

        @Test
        fun `prev expected + grace 이내면 발송 안 함`() {
            // 서버가 03:00(KST) = 18:00Z에 시작
            val bootTime = Instant.parse("2026-04-14T18:00:00Z")
            // 현재 시각은 03:08(KST) = 18:08Z — grace=10분 이내 (3분만 지남)
            val now = Instant.parse("2026-04-14T18:08:00Z")

            val mockClock = mockk<Clock>()
            every { mockClock.instant() } returnsMany listOf(bootTime, now)
            val detector = ScheduleMissDetector(autoCollectionScheduler, pipelineRunStore, runtime, notifier, mockClock)
            every { runtime.current() } returns defaultSettings(graceMins = 10)

            detector.runCheck()

            verify(exactly = 0) { notifier.postScheduleMiss(any(), any(), any()) }
            verify(exactly = 0) { pipelineRunStore.hasRunStartedBetween(any(), any()) }
        }
    }

    @Nested
    inner class `grace 초과 + 실행 없는 경우` {

        @Test
        fun `prev expected + grace 경과 + 해당 창에 run 없으면 발송`() {
            // 서버가 03:00(KST)에 시작, 현재 03:20(KST) — grace=10분 초과 (15분)
            val bootTime = Instant.parse("2026-04-14T18:00:00Z")

            val mockClock = mockk<Clock>()
            every { mockClock.instant() } returnsMany listOf(bootTime, nowInstant)
            val detector = ScheduleMissDetector(autoCollectionScheduler, pipelineRunStore, runtime, notifier, mockClock)
            every { runtime.current() } returns defaultSettings(graceMins = 10)
            every { pipelineRunStore.hasRunStartedBetween(any(), any()) } returns false

            detector.runCheck()

            verify(exactly = 1) { notifier.postScheduleMiss("auto-collection", prevExpected, 10) }
            assert(detector.dedupSnapshot().containsKey(prevExpected))
        }

        @Test
        fun `같은 예정 시각에 두 번 runCheck 해도 알림은 1회만 발송된다`() {
            val bootTime = Instant.parse("2026-04-14T18:00:00Z")

            val mockClock = mockk<Clock>()
            every { mockClock.instant() } returnsMany listOf(
                bootTime,
                nowInstant,  // 1차 runCheck
                nowInstant,  // 2차 runCheck
            )
            val detector = ScheduleMissDetector(autoCollectionScheduler, pipelineRunStore, runtime, notifier, mockClock)
            every { runtime.current() } returns defaultSettings(graceMins = 10)
            every { pipelineRunStore.hasRunStartedBetween(any(), any()) } returns false

            detector.runCheck()
            detector.runCheck()

            verify(exactly = 1) { notifier.postScheduleMiss(any(), any(), any()) }
        }
    }

    @Nested
    inner class `grace 초과 + 실행 존재하는 경우` {

        @Test
        fun `prev expected + grace 경과 + run 존재하면 발송 안 하고 dedup에 기록`() {
            val bootTime = Instant.parse("2026-04-14T18:00:00Z")

            val mockClock = mockk<Clock>()
            every { mockClock.instant() } returnsMany listOf(bootTime, nowInstant)
            val detector = ScheduleMissDetector(autoCollectionScheduler, pipelineRunStore, runtime, notifier, mockClock)
            every { runtime.current() } returns defaultSettings(graceMins = 10)
            every { pipelineRunStore.hasRunStartedBetween(any(), any()) } returns true

            detector.runCheck()

            verify(exactly = 0) { notifier.postScheduleMiss(any(), any(), any()) }
            assert(detector.dedupSnapshot().containsKey(prevExpected))
        }
    }

    @Nested
    inner class `인메모리 lastFiredAt 마커 확인` {

        @Test
        fun `lastFiredAt이 예정 시각 근처이면 pipeline_runs 확인 없이 정상 처리`() {
            val bootTime = Instant.parse("2026-04-14T18:00:00Z")

            val mockClock = mockk<Clock>()
            every { mockClock.instant() } returnsMany listOf(bootTime, nowInstant)
            val detector = ScheduleMissDetector(autoCollectionScheduler, pipelineRunStore, runtime, notifier, mockClock)
            every { runtime.current() } returns defaultSettings(graceMins = 10)
            // 스케줄러가 예정 시각(18:05Z) 직후 18:05:01Z에 실행됨
            every { autoCollectionScheduler.lastFiredAt } returns Instant.parse("2026-04-14T18:05:01Z")

            detector.runCheck()

            // 알림 발송 안 됨
            verify(exactly = 0) { notifier.postScheduleMiss(any(), any(), any()) }
            // pipeline_runs 확인도 건너뜀
            verify(exactly = 0) { pipelineRunStore.hasRunStartedBetween(any(), any()) }
            // dedup에 기록됨
            assert(detector.dedupSnapshot().containsKey(prevExpected))
        }

        @Test
        fun `lastFiredAt이 예정 시각과 2분 이상 차이나면 pipeline_runs 폴백 확인`() {
            val bootTime = Instant.parse("2026-04-14T18:00:00Z")

            val mockClock = mockk<Clock>()
            every { mockClock.instant() } returnsMany listOf(bootTime, nowInstant)
            val detector = ScheduleMissDetector(autoCollectionScheduler, pipelineRunStore, runtime, notifier, mockClock)
            every { runtime.current() } returns defaultSettings(graceMins = 10)
            // 스케줄러가 이전 주기(15:05)에만 실행됨 — 현재 예정 시각(18:05)과 3시간 차이
            every { autoCollectionScheduler.lastFiredAt } returns Instant.parse("2026-04-14T15:05:00Z")
            every { pipelineRunStore.hasRunStartedBetween(any(), any()) } returns false

            detector.runCheck()

            // 인메모리 마커 불일치 → pipeline_runs 폴백 → 미발동 알림 발송
            verify(exactly = 1) { pipelineRunStore.hasRunStartedBetween(any(), any()) }
            verify(exactly = 1) { notifier.postScheduleMiss("auto-collection", prevExpected, 10) }
        }

        @Test
        fun `lastFiredAt이 null이면 pipeline_runs 폴백으로 확인`() {
            val bootTime = Instant.parse("2026-04-14T18:00:00Z")

            val mockClock = mockk<Clock>()
            every { mockClock.instant() } returnsMany listOf(bootTime, nowInstant)
            val detector = ScheduleMissDetector(autoCollectionScheduler, pipelineRunStore, runtime, notifier, mockClock)
            every { runtime.current() } returns defaultSettings(graceMins = 10)
            every { autoCollectionScheduler.lastFiredAt } returns null
            every { pipelineRunStore.hasRunStartedBetween(any(), any()) } returns true

            detector.runCheck()

            // pipeline_runs 폴백으로 확인하고 실행이 있으므로 알림 발송 안 됨
            verify(exactly = 1) { pipelineRunStore.hasRunStartedBetween(any(), any()) }
            verify(exactly = 0) { notifier.postScheduleMiss(any(), any(), any()) }
        }
    }
}
