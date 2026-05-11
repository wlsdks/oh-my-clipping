package com.clipping.mcpserver.observability

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SchedulerRunTrackerTest {
    private val fixedInstant = Instant.parse("2026-04-10T09:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val tracker = SchedulerRunTracker(clock)

    @Nested
    inner class `getLastRun 메서드` {
        @Test
        fun `기록이 없으면 null을 반환한다`() { tracker.getLastRun("unknown").shouldBeNull() }

        @Test
        fun `성공 기록 후 조회하면 성공 레코드를 반환한다`() {
            tracker.record("test_scheduler", true)
            val result = tracker.getLastRun("test_scheduler")
            result.shouldNotBeNull()
            result.lastRunAt shouldBe fixedInstant
            result.success shouldBe true
        }

        @Test
        fun `실패 기록 후 조회하면 실패 레코드를 반환한다`() {
            tracker.record("test_scheduler", false)
            tracker.getLastRun("test_scheduler")!!.success shouldBe false
        }

        @Test
        fun `같은 스케줄러를 두 번 기록하면 마지막 기록으로 덮어쓴다`() {
            tracker.record("test_scheduler", false)
            tracker.record("test_scheduler", true)
            tracker.getLastRun("test_scheduler")!!.success shouldBe true
        }
    }

    @Nested
    inner class `여러 스케줄러` {
        @Test
        fun `서로 다른 스케줄러는 독립적으로 추적된다`() {
            tracker.record("scheduler_a", true)
            tracker.record("scheduler_b", false)
            tracker.getLastRun("scheduler_a")!!.success shouldBe true
            tracker.getLastRun("scheduler_b")!!.success shouldBe false
        }
    }

    @Nested
    inner class `record with duration과 error` {
        @Test
        fun `성공 시 duration이 저장되고 lastError는 null`() {
            tracker.record("scheduler_x", success = true, durationMs = 120)
            val record = tracker.getLastRun("scheduler_x")!!
            record.durationMs shouldBe 120
            record.lastError shouldBe null
        }

        @Test
        fun `실패 시 lastError 메시지가 200자로 잘려 저장된다`() {
            val longMessage = "에러".repeat(200)
            tracker.record("scheduler_y", success = false, durationMs = 50, lastError = longMessage)
            val record = tracker.getLastRun("scheduler_y")!!
            record.success shouldBe false
            record.lastError!!.length shouldBe 200
        }

        @Test
        fun `durationMs가 음수이면 0으로 보정된다`() {
            tracker.record("scheduler_z", success = true, durationMs = -10)
            tracker.getLastRun("scheduler_z")!!.durationMs shouldBe 0
        }
    }

    @Nested
    inner class `allRecords 메서드` {
        @Test
        fun `기록이 없으면 빈 맵 반환`() {
            tracker.allRecords().isEmpty() shouldBe true
        }

        @Test
        fun `여러 스케줄러 기록 시 모두 반환`() {
            tracker.record("a", true)
            tracker.record("b", false)
            val all = tracker.allRecords()
            all.size shouldBe 2
            all.keys.contains("a") shouldBe true
            all.keys.contains("b") shouldBe true
        }

        @Test
        fun `반환된 맵은 방어적 복사본이라 tracker를 변경하지 않는다`() {
            tracker.record("a", true)
            val snapshot = tracker.allRecords()
            tracker.record("b", false)
            // snapshot은 시점 기준이므로 b가 추가되더라도 크기 변동 없음
            snapshot.size shouldBe 1
        }
    }
}
