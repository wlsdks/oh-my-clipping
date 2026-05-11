package com.ohmyclipping.store

import com.ohmyclipping.repository.UserEventRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate

/**
 * JpaUserEventStore 집계 쿼리 단위 테스트.
 */
class JpaUserEventStoreTest {

    private val repository = mockk<UserEventRepository>(relaxed = true)
    private val jdbc = mockk<JdbcTemplate>()
    private val store = JpaUserEventStore(repository, jdbc)

    @Test
    fun `countByEventTypeForDays는 날짜별 CASE 집계 결과를 LocalDate 맵으로 변환한다`() {
        val day1 = LocalDate.of(2026, 4, 16)
        val day2 = LocalDate.of(2026, 4, 17)
        every {
            jdbc.queryForMap(match { it.contains("SUM(CASE WHEN created_at") }, *anyVararg())
        } returns mapOf(
            "c0" to 3L,
            "c1" to 5L
        )

        val result = store.countByEventTypeForDays("article_click", listOf(day1, day2))

        result shouldBe mapOf(day1 to 3L, day2 to 5L)
        verify(exactly = 1) {
            jdbc.queryForMap(match { it.contains("event_type = ?") }, *anyVararg())
        }
    }

    @Test
    fun `countByEventTypeForDays는 null 또는 누락된 CASE 집계 값을 0으로 처리한다`() {
        val day1 = LocalDate.of(2026, 4, 16)
        val day2 = LocalDate.of(2026, 4, 17)
        val day3 = LocalDate.of(2026, 4, 18)
        every {
            jdbc.queryForMap(match { it.contains("SUM(CASE WHEN created_at") }, *anyVararg())
        } returns mapOf(
            "c0" to null,
            "c2" to 7L
        )

        val result = store.countByEventTypeForDays("article_click", listOf(day1, day2, day3))

        result shouldBe mapOf(
            day1 to 0L,
            day2 to 0L,
            day3 to 7L,
        )
    }
}
