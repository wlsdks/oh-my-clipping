package com.clipping.mcpserver.store

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

class CategoryFeatureFlagStoreRaceTest {

    private val jdbc = mockk<JdbcTemplate>()
    private val store = CategoryFeatureFlagStore(jdbc)

    @Test
    fun `setAccountBasedDigestEnabled는 INSERT race 발생 시 UPDATE로 재시도한다`() {
        every {
            jdbc.update(
                match<String> { it.contains("UPDATE category_feature_flags") },
                true,
                "cat-1",
            )
        } returnsMany listOf(0, 1)
        every {
            jdbc.update(
                match<String> { it.contains("INSERT INTO category_feature_flags") },
                "cat-1",
                true,
            )
        } throws DataIntegrityViolationException("duplicate category feature flag")

        store.setAccountBasedDigestEnabled("cat-1", true)

        verify(exactly = 2) {
            jdbc.update(
                match<String> { it.contains("UPDATE category_feature_flags") },
                true,
                "cat-1",
            )
        }
        verify(exactly = 1) {
            jdbc.update(
                match<String> { it.contains("INSERT INTO category_feature_flags") },
                "cat-1",
                true,
            )
        }
    }
}
