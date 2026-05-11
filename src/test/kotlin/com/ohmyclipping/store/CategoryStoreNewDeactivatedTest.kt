package com.ohmyclipping.store

import com.ohmyclipping.entity.CategoryEntity
import com.ohmyclipping.repository.CategoryRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * CategoryStore.countNewSince() / countDeactivatedSince() 단위 테스트.
 * JpaCategoryStore의 repository 위임을 검증한다.
 */
class CategoryStoreNewDeactivatedTest {

    private val repository = mockk<CategoryRepository>()
    private val rssSourceRepository = mockk<com.ohmyclipping.repository.RssSourceRepository>()
    private val em = mockk<jakarta.persistence.EntityManager>()

    private val store = JpaCategoryStore(repository, rssSourceRepository, em)

    @Nested
    inner class `countNewSince 메서드` {

        @Test
        fun `since 이후 생성된 카테고리 수를 반환한다`() {
            val since = Instant.now().minusSeconds(24 * 3600)
            every { repository.countByCreatedAtGreaterThanEqual(since) } returns 3L

            store.countNewSince(since) shouldBe 3L
        }

        @Test
        fun `생성된 카테고리가 없으면 0을 반환한다`() {
            val since = Instant.now()
            every { repository.countByCreatedAtGreaterThanEqual(since) } returns 0L

            store.countNewSince(since) shouldBe 0L
        }
    }

    @Nested
    inner class `countDeactivatedSince 메서드` {

        @Test
        fun `since 이후 비활성화된 카테고리 수를 반환한다`() {
            val since = Instant.now().minusSeconds(7 * 24 * 3600)
            every { repository.countByUpdatedAtGreaterThanEqualAndIsActiveFalse(since) } returns 2L

            store.countDeactivatedSince(since) shouldBe 2L
        }

        @Test
        fun `비활성화된 카테고리가 없으면 0을 반환한다`() {
            val since = Instant.now()
            every { repository.countByUpdatedAtGreaterThanEqualAndIsActiveFalse(since) } returns 0L

            store.countDeactivatedSince(since) shouldBe 0L
        }
    }
}
