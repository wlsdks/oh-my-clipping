package com.ohmyclipping.support

import com.ohmyclipping.error.InvalidInputException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InputSanitizerTest {

    @Nested
    inner class `sanitizeOptional 동작` {

        @Test
        fun `null 입력은 null을 반환한다`() {
            InputSanitizer.sanitizeOptional(null, "설명", 100).shouldBeNull()
        }

        @Test
        fun `빈 문자열은 null을 반환한다`() {
            InputSanitizer.sanitizeOptional("", "설명", 100).shouldBeNull()
        }

        @Test
        fun `공백만 있는 문자열은 null을 반환한다`() {
            InputSanitizer.sanitizeOptional("   ", "설명", 100).shouldBeNull()
        }

        @Test
        fun `앞뒤 공백을 trim한다`() {
            InputSanitizer.sanitizeOptional("  hello  ", "설명", 100) shouldBe "hello"
        }

        @Test
        fun `내부 공백은 유지한다`() {
            InputSanitizer.sanitizeOptional("hello world", "설명", 100) shouldBe "hello world"
        }

        @Test
        fun `탭과 개행은 보존한다`() {
            // 탭, LF, CR은 의도적 포맷팅 문자이므로 제거하지 않는다.
            val input = "line1\n\tline2\r\nline3"
            InputSanitizer.sanitizeOptional(input, "본문", 100) shouldBe input
        }

        @Test
        fun `null byte를 제거한다`() {
            // U+0000 null byte는 DB 저장 시 문제를 일으키므로 제거한다.
            InputSanitizer.sanitizeOptional("abc\u0000def", "설명", 100) shouldBe "abcdef"
        }

        @Test
        fun `제어 문자 U+0001~0008을 제거한다`() {
            val input = "a\u0001b\u0004c\u0008d"
            InputSanitizer.sanitizeOptional(input, "설명", 100) shouldBe "abcd"
        }

        @Test
        fun `제어 문자 U+000B U+000C를 제거한다`() {
            // 수직 탭(\u000B), 폼피드(\u000C)는 일반 입력에서는 발생하지 않아야 한다.
            InputSanitizer.sanitizeOptional("a\u000Bb\u000Cc", "설명", 100) shouldBe "abc"
        }

        @Test
        fun `제어 문자 U+000E~001F를 제거한다`() {
            InputSanitizer.sanitizeOptional("a\u000Eb\u001Fc", "설명", 100) shouldBe "abc"
        }

        @Test
        fun `DEL 문자 U+007F를 제거한다`() {
            InputSanitizer.sanitizeOptional("a\u007Fb", "설명", 100) shouldBe "ab"
        }

        @Test
        fun `bidi 오버라이드 문자를 제거한다`() {
            // U+202E(RLO)가 섞이면 이후 문자열 방향이 반전되어 UI spoofing으로 이어지므로 저장 전에 제거한다.
            InputSanitizer.sanitizeOptional("페르소나\u202Eevil", "이름", 100) shouldBe "페르소나evil"
        }

        @Test
        fun `bidi 오버라이드만 들어온 optional 입력은 null을 반환한다`() {
            // trim + 제어 문자 + bidi 제거 후 빈 문자열이 되면 optional 의미에 맞게 null을 반환한다.
            InputSanitizer.sanitizeOptional("\u202A\u2067\u2069  ", "설명", 100).shouldBeNull()
        }

        @Test
        fun `bidi 와 다른 제어 문자가 혼재해도 모두 제거한다`() {
            // null byte, 탭(보존), bidi(RLO), 일반 문자 조합. 탭은 보존되고 나머지 오염만 제거.
            val input = "a\u0000b\tc\u202Ed"
            InputSanitizer.sanitizeOptional(input, "본문", 100) shouldBe "ab\tcd"
        }

        @Test
        fun `최대 길이 경계값은 허용한다`() {
            val input = "a".repeat(100)
            InputSanitizer.sanitizeOptional(input, "설명", 100) shouldBe input
        }

        @Test
        fun `최대 길이 + 1은 InvalidInputException을 던진다`() {
            val input = "a".repeat(101)
            shouldThrow<InvalidInputException> {
                InputSanitizer.sanitizeOptional(input, "설명", 100)
            }.message shouldContain "설명"
        }

        @Test
        fun `한글도 글자 수 기준으로 길이를 계산한다`() {
            val input = "가".repeat(100)
            InputSanitizer.sanitizeOptional(input, "이름", 100) shouldBe input
            // 101글자는 거부된다.
            val overflow = "가".repeat(101)
            shouldThrow<InvalidInputException> {
                InputSanitizer.sanitizeOptional(overflow, "이름", 100)
            }
        }

        @Test
        fun `이모지 혼용 입력도 지원한다`() {
            // String#length는 UTF-16 code unit 수 기준이므로 surrogate pair 이모지는 2로 센다.
            val input = "hello😀world"
            InputSanitizer.sanitizeOptional(input, "설명", 50) shouldBe input
        }

        @Test
        fun `에러 메시지에 최대 길이가 포함된다`() {
            val input = "a".repeat(11)
            shouldThrow<InvalidInputException> {
                InputSanitizer.sanitizeOptional(input, "이름", 10)
            }.message shouldContain "10자"
        }
    }

    @Nested
    inner class `sanitizeRequired 동작` {

        @Test
        fun `null 입력은 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                InputSanitizer.sanitizeRequired(null, "이름", 100)
            }.message shouldContain "이름"
        }

        @Test
        fun `빈 문자열은 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                InputSanitizer.sanitizeRequired("", "이름", 100)
            }.message shouldContain "이름"
        }

        @Test
        fun `공백만 있는 문자열은 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                InputSanitizer.sanitizeRequired("   ", "이름", 100)
            }
        }

        @Test
        fun `정상 입력은 trim된 값을 반환한다`() {
            InputSanitizer.sanitizeRequired("  페르소나  ", "이름", 200) shouldBe "페르소나"
        }

        @Test
        fun `최소 길이 경계값은 허용한다`() {
            InputSanitizer.sanitizeRequired("ab", "코드", 100, minLength = 2) shouldBe "ab"
        }

        @Test
        fun `최소 길이 - 1은 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                InputSanitizer.sanitizeRequired("a", "코드", 100, minLength = 2)
            }.message shouldContain "최소"
        }

        @Test
        fun `최대 길이 초과는 InvalidInputException을 던진다`() {
            val input = "a".repeat(201)
            shouldThrow<InvalidInputException> {
                InputSanitizer.sanitizeRequired(input, "이름", 200)
            }
        }

        @Test
        fun `null byte 제거 후 길이를 측정한다`() {
            // 전체 원문은 10자이지만 null byte 5개를 제거하면 5자여서 통과해야 한다.
            val input = "abcde\u0000\u0000\u0000\u0000\u0000"
            InputSanitizer.sanitizeRequired(input, "이름", 5) shouldBe "abcde"
        }

        @Test
        fun `제어 문자 제거 후 빈 문자열이 되면 필수 검증이 실패한다`() {
            // 제어 문자만 있는 입력은 sanitize 후 빈 문자열이 되어 필수 검증에 걸린다.
            shouldThrow<InvalidInputException> {
                InputSanitizer.sanitizeRequired("\u0000\u0001", "이름", 100)
            }
        }

        @Test
        fun `bidi 오버라이드만 있는 필수 입력은 InvalidInputException을 던진다`() {
            // bidi 제어 문자만 들어와도 실 콘텐츠가 없는 것이므로 필수 검증에 걸려야 한다.
            shouldThrow<InvalidInputException> {
                InputSanitizer.sanitizeRequired("\u202E\u2066\u2069", "이름", 100)
            }
        }

        @Test
        fun `bidi 제거 후 trim된 실 콘텐츠가 반환된다`() {
            // 앞뒤 공백과 bidi 제어 문자가 섞여도 실제 내용만 정상적으로 반환되어야 한다.
            InputSanitizer.sanitizeRequired("  \u202E페르소나\u202C  ", "이름", 100) shouldBe "페르소나"
        }
    }
}
