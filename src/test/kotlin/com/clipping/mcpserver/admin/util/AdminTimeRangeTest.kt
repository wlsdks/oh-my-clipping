package com.clipping.mcpserver.admin.util

import com.clipping.mcpserver.error.InvalidInputException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class AdminTimeRangeTest {

    @Nested
    @DisplayName("parseWithin")
    inner class ParseWithin {

        @Test
        fun `null 이면 null 반환`() {
            AdminTimeRange.parseWithin(null, Clock.systemUTC()) shouldBe null
        }

        @Test
        fun `1d 는 오늘 KST 자정 Instant 반환`() {
            // 2026-04-18 03:00 UTC = 2026-04-18 12:00 KST
            val fixedClock = Clock.fixed(Instant.parse("2026-04-18T03:00:00Z"), ZoneId.of("UTC"))
            val result = AdminTimeRange.parseWithin("1d", fixedClock)
            // 2026-04-18 00:00 KST = 2026-04-17 15:00 UTC
            result shouldBe Instant.parse("2026-04-17T15:00:00Z")
        }

        @Test
        fun `KST 새벽 경계 - 00-30 KST 에서도 오늘 KST 자정 반환`() {
            // 2026-04-18 15:30 UTC = 2026-04-19 00:30 KST (막 자정 지난 시점)
            val fixedClock = Clock.fixed(Instant.parse("2026-04-18T15:30:00Z"), ZoneId.of("UTC"))
            val result = AdminTimeRange.parseWithin("1d", fixedClock)
            // 2026-04-19 00:00 KST = 2026-04-18 15:00 UTC
            result shouldBe Instant.parse("2026-04-18T15:00:00Z")
        }

        @Test
        fun `7d 는 오늘 KST 자정의 6일 전 (오늘 포함 7일)`() {
            val fixedClock = Clock.fixed(Instant.parse("2026-04-18T03:00:00Z"), ZoneId.of("UTC"))
            val result = AdminTimeRange.parseWithin("7d", fixedClock)
            // 2026-04-17 15:00 UTC 의 6일 전 = 2026-04-11 15:00 UTC
            result shouldBe Instant.parse("2026-04-11T15:00:00Z")
        }

        @Test
        fun `허용되지 않는 값은 InvalidInputException`() {
            val ex = assertThrows<InvalidInputException> {
                AdminTimeRange.parseWithin("2d", Clock.systemUTC())
            }
            ex.message shouldBe "within 파라미터는 '1d' 또는 '7d' 만 허용합니다"
        }

        @Test
        fun `빈 문자열도 예외`() {
            assertThrows<InvalidInputException> {
                AdminTimeRange.parseWithin("", Clock.systemUTC())
            }
        }

        @Test
        fun `7d 연-월 경계 - 2026-01-03 KST 에서 7d 는 2025-12-28 KST 자정`() {
            // 2026-01-03 04:00 UTC = 2026-01-03 13:00 KST
            val fixedClock = Clock.fixed(Instant.parse("2026-01-03T04:00:00Z"), ZoneId.of("UTC"))
            val result = AdminTimeRange.parseWithin("7d", fixedClock)
            // 2026-01-03 00:00 KST = 2026-01-02 15:00 UTC, 그 6일 전 = 2025-12-27 15:00 UTC
            result shouldBe Instant.parse("2025-12-27T15:00:00Z")
        }
    }
}
