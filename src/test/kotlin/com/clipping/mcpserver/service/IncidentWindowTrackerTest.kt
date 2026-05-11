package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.IncidentWindowState
import com.clipping.mcpserver.service.port.OpsLogNotifier
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class IncidentWindowTrackerTest {

    private lateinit var notifier: OpsLogNotifier
    private lateinit var runtime: RuntimeSettingService
    private lateinit var clock: Clock
    private lateinit var tracker: IncidentWindowTracker

    @BeforeEach
    fun setUp() {
        notifier = mockk(relaxed = true)
        runtime = mockk()
        clock = Clock.fixed(Instant.parse("2026-04-15T06:00:00Z"), ZoneId.of("Asia/Seoul"))
        tracker = IncidentWindowTracker(runtime, notifier, clock)
    }

    /** 테스트용 기본 설정: windowMinutes=5, threshold=3 */
    private fun defaultSettings() = mockk<RuntimeSettingService.RuntimeSettings>(relaxed = true) {
        every { opsIncidentWindowMinutes } returns 5
        every { opsIncidentThresholdCategories } returns 3
    }

    @Nested
    inner class `recordFailure` {

        @Test
        fun `임계값 미만은 Incident를 발동하지 않는다`() {
            every { runtime.current() } returns defaultSettings()

            tracker.recordFailure("c1", "run1")
            tracker.recordFailure("c2", "run2")

            verify(exactly = 0) { notifier.postIncident(any()) }
        }

        @Test
        fun `distinct categoryId 3건 도달 시 postIncident 1회 호출`() {
            every { runtime.current() } returns defaultSettings()

            tracker.recordFailure("c1", "run1")
            tracker.recordFailure("c2", "run2")
            tracker.recordFailure("c3", "run3")

            verify(exactly = 1) { notifier.postIncident(any()) }
        }

        @Test
        fun `같은 categoryId 중복은 Incident 발동에 카운트되지 않는다`() {
            every { runtime.current() } returns defaultSettings()

            tracker.recordFailure("c1", "run1")
            tracker.recordFailure("c1", "run2")
            tracker.recordFailure("c1", "run3")

            verify(exactly = 0) { notifier.postIncident(any()) }
        }

        @Test
        fun `Incident 모드 진입 후 추가 실패는 notifier를 재호출하지 않는다`() {
            every { runtime.current() } returns defaultSettings()
            // postIncident 호출 시 state.parentTs를 설정해 Incident 모드 진입을 시뮬레이션한다
            every { notifier.postIncident(any()) } answers {
                firstArg<IncidentWindowState>().parentTs = "1000.1"
            }

            tracker.recordFailure("c1", "run1")
            tracker.recordFailure("c2", "run2")
            tracker.recordFailure("c3", "run3")  // Incident 진입
            tracker.recordFailure("c4", "run4")  // 추가 실패 — 재호출 금지

            verify(exactly = 1) { notifier.postIncident(any()) }
        }

        @Test
        fun `창 상태에 failedRuns가 올바르게 누적된다`() {
            every { runtime.current() } returns defaultSettings()

            tracker.recordFailure("c1", "run1")
            tracker.recordFailure("c2", "run2")

            val snapshot = tracker.windowsSnapshot()
            val windowKey = Instant.parse("2026-04-15T06:00:00Z").epochSecond / 60 / 5
            val state = snapshot[windowKey]!!

            assert(state.failedRuns.containsAll(listOf("run1", "run2")))
            assert(state.categories.containsAll(listOf("c1", "c2")))
        }
    }

    @Nested
    inner class `runSweeper` {

        @Test
        fun `현재 창은 만료 대상이 아니므로 스위퍼가 notifier를 호출하지 않는다`() {
            every { runtime.current() } returns defaultSettings()

            tracker.recordFailure("c1", "run1")
            tracker.recordFailure("c2", "run2")
            tracker.recordFailure("c3", "run3")  // Incident 진입

            // 동일한 clock이므로 현재 창은 만료되지 않는다
            tracker.runSweeper()

            // recordFailure에서의 1회 postIncident만 확인
            verify(exactly = 1) { notifier.postIncident(any()) }
        }

        @Test
        fun `만료된 창에 parentTs가 있으면 스위퍼가 notifier를 재호출 후 창을 제거한다`() {
            every { runtime.current() } returns defaultSettings()

            // mockk Clock으로 시간 이동을 시뮬레이션한다
            // recordFailure 3회: 05:55 창에서 실행 → sweeper 호출: 06:10으로 이동 → 05:55 창 만료
            val mockClock = mockk<Clock>()
            var callCount = 0
            every { mockClock.instant() } answers {
                if (callCount++ < 3) Instant.parse("2026-04-15T05:55:00Z")
                else Instant.parse("2026-04-15T06:10:00Z")
            }

            val freshNotifier = mockk<OpsLogNotifier>(relaxed = true)
            every { freshNotifier.postIncident(any()) } answers {
                firstArg<IncidentWindowState>().parentTs = "1000.1"
            }

            val movingTracker = IncidentWindowTracker(runtime, freshNotifier, mockClock)
            movingTracker.recordFailure("c1", "r1")  // call 0 → 05:55 창
            movingTracker.recordFailure("c2", "r2")  // call 1 → 05:55 창
            movingTracker.recordFailure("c3", "r3")  // call 2 → Incident 진입, parentTs 설정

            // sweeper 호출 시 clock이 06:10으로 이동 → windowKey(05:55) < currentWindowKey(06:10)
            movingTracker.runSweeper()

            // recordFailure에서 1회 + sweeper에서 만료된 창 final update 1회 = 2회
            verify(exactly = 2) { freshNotifier.postIncident(any()) }
            // 창이 evict됐는지 확인
            assert(movingTracker.windowsSnapshot().isEmpty())
        }

        @Test
        fun `만료된 창에 parentTs가 없으면(임계값 미도달) 조용히 제거한다`() {
            every { runtime.current() } returns defaultSettings()

            val mockClock = mockk<Clock>()
            var callCount = 0
            every { mockClock.instant() } answers {
                if (callCount++ < 2) Instant.parse("2026-04-15T05:55:00Z")
                else Instant.parse("2026-04-15T06:10:00Z")
            }
            val movingTracker = IncidentWindowTracker(runtime, notifier, mockClock)

            movingTracker.recordFailure("c1", "r1")  // call 1
            movingTracker.recordFailure("c2", "r2")  // call 2 — 임계값 미달 (2 < 3)
            movingTracker.runSweeper()               // call 3+ → 창 만료

            // 임계값 미달이므로 postIncident 호출 없음
            verify(exactly = 0) { notifier.postIncident(any()) }
            assert(movingTracker.windowsSnapshot().isEmpty())
        }
    }
}
