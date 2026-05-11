package com.clipping.mcpserver.store

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigInteger
import java.time.Instant

/**
 * Source crawl uptime aggregate coercion tests.
 */
class JdbcSourceCrawlLogStoreTest {

    private val jdbc = mockk<JdbcTemplate>()
    private val store = JdbcSourceCrawlLogStore(jdbc)

    @Test
    fun `getUptimePercent는 Number 계열 aggregate 타입을 안전하게 변환한다`() {
        every {
            jdbc.queryForMap(match { it.contains("COUNT(*) AS total") }, *anyVararg())
        } returns mapOf(
            "total" to BigInteger.valueOf(3),
            "success_count" to 2,
        )

        store.getUptimePercent("src-1", Instant.parse("2026-04-20T00:00:00Z")) shouldBe 66.7
    }

    @Test
    fun `getUptimePercent는 total aggregate가 null이면 null을 반환한다`() {
        every {
            jdbc.queryForMap(match { it.contains("COUNT(*) AS total") }, *anyVararg())
        } returns mapOf(
            "total" to null,
            "success_count" to 2L,
        )

        store.getUptimePercent("src-1", Instant.parse("2026-04-20T00:00:00Z")) shouldBe null
    }

    @Test
    fun `getUptimePercent는 success aggregate가 null이면 0 percent로 계산한다`() {
        every {
            jdbc.queryForMap(match { it.contains("COUNT(*) AS total") }, *anyVararg())
        } returns mapOf(
            "total" to 5L,
            "success_count" to null,
        )

        store.getUptimePercent("src-1", Instant.parse("2026-04-20T00:00:00Z")) shouldBe 0.0
    }
}
