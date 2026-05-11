package com.clipping.mcpserver.store

import com.clipping.mcpserver.repository.UserClippingRequestRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigInteger

/**
 * User clipping request aggregate projection coercion tests.
 */
class JpaUserClippingRequestStoreAggregationTest {

    private val repository = mockk<UserClippingRequestRepository>()
    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val store = JpaUserClippingRequestStore(repository, jdbc)

    @Test
    fun `countApprovedGroupByCategoryId는 Number 계열 COUNT 타입을 안전하게 변환한다`() {
        every { repository.countApprovedGroupByCategoryId() } returns listOf(
            arrayOf("cat-1", 2),
            arrayOf("cat-2", BigInteger.valueOf(3)),
        )

        store.countApprovedGroupByCategoryId() shouldBe mapOf(
            "cat-1" to 2,
            "cat-2" to 3,
        )
    }

    @Test
    fun `countApprovedGroupByRequester는 null requester 또는 null count row를 제외한다`() {
        @Suppress("UNCHECKED_CAST")
        val rows = listOf(
            arrayOf<Any?>("user-1", 4L) as Array<Any>,
            arrayOf<Any?>(null, 2L) as Array<Any>,
            arrayOf<Any?>("user-2", null) as Array<Any>,
        )
        every { repository.countApprovedGroupByRequester() } returns listOf(
            *rows.toTypedArray()
        )

        store.countApprovedGroupByRequester() shouldBe mapOf("user-1" to 4)
    }
}
