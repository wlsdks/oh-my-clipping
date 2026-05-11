package com.ohmyclipping.support

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class PaginationUtilsTest {

    @Test
    fun `safeOffset calculates normal page offset`() {
        PaginationUtils.safeOffset(page = 3, size = 50) shouldBe 150
    }

    @TestFactory
    fun `safeOffset bad case matrix`(): List<DynamicTest> {
        val cases = listOf(
            OffsetCase("negative page", page = -1, size = 50, expected = 0),
            OffsetCase("negative size", page = 3, size = -50, expected = 0),
            OffsetCase("both negative", page = -3, size = -50, expected = 0),
            OffsetCase("zero size", page = 3, size = 0, expected = 0),
            OffsetCase("int overflow", page = Int.MAX_VALUE, size = 50, expected = Int.MAX_VALUE),
            OffsetCase("large multiplication overflow", page = 100_000_000, size = 100_000_000, expected = Int.MAX_VALUE)
        )

        return cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                PaginationUtils.safeOffset(case.page, case.size) shouldBe case.expected
            }
        }
    }

    private data class OffsetCase(
        val name: String,
        val page: Int,
        val size: Int,
        val expected: Int
    )
}
