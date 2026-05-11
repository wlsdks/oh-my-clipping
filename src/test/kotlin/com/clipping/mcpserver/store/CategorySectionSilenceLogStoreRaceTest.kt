package com.clipping.mcpserver.store

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

class CategorySectionSilenceLogStoreRaceTest {

    private val jdbc = mockk<JdbcTemplate>()
    private val store = CategorySectionSilenceLogStore(jdbc)

    @Test
    fun `incrementAndGet은 최초 INSERT race 발생 시 UPDATE로 재시도하고 현재 카운트를 반환한다`() {
        every {
            jdbc.update(
                match<String> {
                    it.contains("UPDATE category_section_silence_log") &&
                        it.contains("consecutive_empty_days = consecutive_empty_days + 1")
                },
                "cat-1",
                "topic",
            )
        } returnsMany listOf(0, 1)
        every {
            jdbc.update(
                match<String> { it.contains("INSERT INTO category_section_silence_log") },
                any<String>(),
                "cat-1",
                "topic",
            )
        } throws DataIntegrityViolationException("duplicate category section silence log")
        every {
            jdbc.query(
                match<String> { it.contains("SELECT consecutive_empty_days") },
                any<org.springframework.jdbc.core.RowMapper<Int>>(),
                "cat-1",
                "topic",
            )
        } returns listOf(2)

        val result = store.incrementAndGet("cat-1", "topic")

        result shouldBe 2
        verify(exactly = 2) {
            jdbc.update(
                match<String> { it.contains("UPDATE category_section_silence_log") },
                "cat-1",
                "topic",
            )
        }
        verify(exactly = 1) {
            jdbc.update(
                match<String> { it.contains("INSERT INTO category_section_silence_log") },
                any<String>(),
                "cat-1",
                "topic",
            )
        }
    }
}
