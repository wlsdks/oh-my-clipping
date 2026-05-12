package com.ohmyclipping.service.digest

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SectionSilencePolicyTest {
    @Nested
    inner class `sectionSilenceCopy` {
        @Test
        fun `1일차 일반 empty copy`() {
            val copy = sectionSilenceCopy(1, sectionLabel = "주제", actionUrl = "https://app/subscriptions/c1/edit")

            copy.text shouldContain "오늘"
            copy.text shouldContain "없었어요"
            copy.actionUrl shouldBe null
        }

        @Test
        fun `3일차부터 escalation copy`() {
            val copy = sectionSilenceCopy(3, sectionLabel = "주제", actionUrl = "https://app/subscriptions/c1/edit")

            copy.text shouldContain "3일째 없어요"
            copy.actionLabel shouldBe "주제 수정하기"
            copy.actionUrl shouldBe "https://app/subscriptions/c1/edit"
        }

        @Test
        fun `음수 emptyDays 는 엔진 입력 오류로 거부한다`() {
            shouldThrow<EngineInvalidInputException> {
                sectionSilenceCopy(-1, sectionLabel = "주제", actionUrl = "https://app/subscriptions/c1/edit")
            }.message shouldBe "emptyDays must be non-negative"
        }

        @Test
        fun `빈 sectionLabel 은 엔진 입력 오류로 거부한다`() {
            shouldThrow<EngineInvalidInputException> {
                sectionSilenceCopy(1, sectionLabel = " ", actionUrl = "https://app/subscriptions/c1/edit")
            }.message shouldBe "sectionLabel must not be blank"
        }

        @Test
        fun `sectionLabel 은 trim 해서 문구에 반영한다`() {
            val copy = sectionSilenceCopy(1, sectionLabel = " 주제 ", actionUrl = "https://app/subscriptions/c1/edit")

            copy.text shouldBe "오늘 주제 관련 뉴스는 없었어요"
        }
    }
}
