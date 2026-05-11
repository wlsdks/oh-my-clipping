package com.ohmyclipping.service

import com.ohmyclipping.store.CategoryFeatureFlagStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FeatureFlagsServiceTest {
    private val store = mockk<CategoryFeatureFlagStore>()

    @Nested
    inner class `isAccountBasedDigestEnabled` {
        @Test
        fun `글로벌 true 면 카테고리 값 무관하게 true`() {
            val service = FeatureFlagsService(store, globalAccountBasedDigestEnabled = true)
            every { store.isAccountBasedDigestEnabled(any()) } returns false
            service.isAccountBasedDigestEnabled("cat-1") shouldBe true
        }

        @Test
        fun `글로벌 false 이고 카테고리 true 면 true`() {
            val service = FeatureFlagsService(store, globalAccountBasedDigestEnabled = false)
            every { store.isAccountBasedDigestEnabled("cat-1") } returns true
            service.isAccountBasedDigestEnabled("cat-1") shouldBe true
        }

        @Test
        fun `둘 다 false 면 false`() {
            val service = FeatureFlagsService(store, globalAccountBasedDigestEnabled = false)
            every { store.isAccountBasedDigestEnabled("cat-1") } returns false
            service.isAccountBasedDigestEnabled("cat-1") shouldBe false
        }

        @Test
        fun `categoryId null 이면 글로벌만 반영`() {
            val service = FeatureFlagsService(store, globalAccountBasedDigestEnabled = true)
            service.isAccountBasedDigestEnabled(null) shouldBe true
        }

        @Test
        fun `글로벌 false 이고 categoryId null 이면 false`() {
            val service = FeatureFlagsService(store, globalAccountBasedDigestEnabled = false)
            service.isAccountBasedDigestEnabled(null) shouldBe false
        }
    }
}
