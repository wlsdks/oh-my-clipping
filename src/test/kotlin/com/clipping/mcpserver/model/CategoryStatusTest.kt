package com.clipping.mcpserver.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CategoryStatusTest {

    @Nested
    inner class `isOperational 속성` {

        @Test
        fun `ACTIVE는 운영 중이다`() {
            CategoryStatus.ACTIVE.isOperational shouldBe true
        }

        @Test
        fun `PAUSED는 운영 중이 아니다`() {
            CategoryStatus.PAUSED.isOperational shouldBe false
        }
    }

    @Nested
    inner class `occupiesChannel 속성` {

        @Test
        fun `ACTIVE는 채널을 점유한다`() {
            CategoryStatus.ACTIVE.occupiesChannel shouldBe true
        }

        @Test
        fun `PAUSED는 채널을 점유한다`() {
            CategoryStatus.PAUSED.occupiesChannel shouldBe true
        }
    }
}
