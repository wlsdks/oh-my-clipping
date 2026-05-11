package com.ohmyclipping.service.digest

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DigestModeResolverTest {

    @Nested
    inner class `resolveDigestMode 경계` {
        @Test fun `0,0 → IllegalState`() {
            shouldThrow<IllegalStateException> { resolveDigestMode(0, 0) }
        }
        @Test fun `1,0 → TOPIC_ONLY`() { resolveDigestMode(1, 0) shouldBe DigestMode.TOPIC_ONLY }
        @Test fun `N,0 → TOPIC_ONLY`() { resolveDigestMode(5, 0) shouldBe DigestMode.TOPIC_ONLY }
        @Test fun `0,1 → ACCOUNT_ONLY`() { resolveDigestMode(0, 1) shouldBe DigestMode.ACCOUNT_ONLY }
        @Test fun `0,N → ACCOUNT_ONLY`() { resolveDigestMode(0, 7) shouldBe DigestMode.ACCOUNT_ONLY }
        @Test fun `1,1 → CROSSFILTER`() { resolveDigestMode(1, 1) shouldBe DigestMode.CROSSFILTER }
        @Test fun `1,N → CROSSFILTER`() { resolveDigestMode(1, 3) shouldBe DigestMode.CROSSFILTER }
        @Test fun `N,1 → CROSSFILTER`() { resolveDigestMode(4, 1) shouldBe DigestMode.CROSSFILTER }
        @Test fun `N,N → DUAL_SECTION`() { resolveDigestMode(3, 3) shouldBe DigestMode.DUAL_SECTION }
        @Test fun `2,2 → DUAL_SECTION`() { resolveDigestMode(2, 2) shouldBe DigestMode.DUAL_SECTION }
    }

    @Nested
    inner class `splitBudget 분배` {
        @Test fun `budget 5 → 3,2`() { splitBudget(5) shouldBe (3 to 2) }
        @Test fun `budget 3 → 2,1`() { splitBudget(3) shouldBe (2 to 1) }
        @Test fun `budget 1 → 1,0`() { splitBudget(1) shouldBe (1 to 0) }
        @Test fun `budget 7 fallback → 4,3`() { splitBudget(7) shouldBe (4 to 3) }
        @Test fun `budget 10 fallback → 5,5`() { splitBudget(10) shouldBe (5 to 5) }
    }
}
