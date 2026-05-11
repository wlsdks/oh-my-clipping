package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.section.SectionSilenceEscalator
import com.clipping.mcpserver.store.CategorySectionSilenceLogStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SectionSilenceEscalatorTest {
    private val store = mockk<CategorySectionSilenceLogStore>(relaxed = true)
    private val escalator = SectionSilenceEscalator(store, appBaseUrl = "http://localhost:8086")

    @Nested
    inner class `onEmptySection 카피` {
        @Test
        fun `첫 호출 1일차 일반 empty copy`() {
            every { store.incrementAndGet("c1", "topic") } returns 1
            val copy = escalator.onEmptySection("c1", "topic", sectionLabel = "주제")
            copy.text shouldContain "오늘"
            copy.text shouldContain "없었어요"
            copy.actionUrl shouldBe null
        }

        @Test
        fun `2일차도 일반 copy`() {
            every { store.incrementAndGet("c1", "topic") } returns 2
            val copy = escalator.onEmptySection("c1", "topic", sectionLabel = "주제")
            copy.actionUrl shouldBe null
        }

        @Test
        fun `3일차부터 escalation + deeplink`() {
            every { store.incrementAndGet("c1", "topic") } returns 3
            val copy = escalator.onEmptySection("c1", "topic", sectionLabel = "주제")
            copy.text shouldContain "3일째 없어요"
            copy.actionLabel shouldBe "주제 수정하기"
            copy.actionUrl shouldBe "http://localhost:8086/user/subscriptions/c1/edit"
        }

        @Test
        fun `5일차 escalation 계속 with 일수 표시`() {
            every { store.incrementAndGet("c1", "account") } returns 5
            val copy = escalator.onEmptySection("c1", "account", sectionLabel = "기업")
            copy.text shouldContain "5일째"
        }
    }

    @Nested
    inner class `onSectionMatch 리셋` {
        @Test
        fun `store reset 호출`() {
            escalator.onSectionMatch("c1", "topic")
            verify { store.reset("c1", "topic") }
        }
    }
}
