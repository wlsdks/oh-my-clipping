package com.ohmyclipping.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [DepartmentNormalizer] 단위 테스트.
 * null/빈값 경계, 공백 축소, 소문자 변환, 특수 공백(탭/줄바꿈) 처리를 잠근다.
 */
class DepartmentNormalizerTest {

    @Nested
    inner class `null 과 빈 입력` {
        @Test
        fun `null 입력은 null 로 반환한다`() {
            DepartmentNormalizer.normalize(null) shouldBe null
        }

        @Test
        fun `빈 문자열 입력은 null 로 반환한다`() {
            DepartmentNormalizer.normalize("") shouldBe null
        }

        @Test
        fun `공백만 있는 입력도 null 로 반환한다`() {
            DepartmentNormalizer.normalize("   ") shouldBe null
            DepartmentNormalizer.normalize("\t\n ") shouldBe null
        }
    }

    @Nested
    inner class `정상 입력 정규화` {
        @Test
        fun `앞뒤 공백과 소문자 변환`() {
            DepartmentNormalizer.normalize("  Sales  ") shouldBe "sales"
        }

        @Test
        fun `연속 공백을 단일 공백으로 축소한다`() {
            DepartmentNormalizer.normalize("  영업   팀  ") shouldBe "영업 팀"
        }

        @Test
        fun `탭과 줄바꿈도 공백으로 간주되어 축소된다`() {
            DepartmentNormalizer.normalize("영업\t팀\n담당") shouldBe "영업 팀 담당"
        }

        @Test
        fun `이미 정규화된 값은 그대로 유지된다`() {
            DepartmentNormalizer.normalize("영업 팀") shouldBe "영업 팀"
            DepartmentNormalizer.normalize("ai플랫폼팀") shouldBe "ai플랫폼팀"
        }
    }

    @Nested
    inner class `대소문자 경계` {
        @Test
        fun `영문 대문자는 전부 소문자로 내려간다`() {
            DepartmentNormalizer.normalize("BACK-END") shouldBe "back-end"
        }

        @Test
        fun `한글은 대소문자 개념이 없어 그대로 유지된다`() {
            DepartmentNormalizer.normalize("러닝메이커솔루션팀") shouldBe "러닝메이커솔루션팀"
        }
    }
}
