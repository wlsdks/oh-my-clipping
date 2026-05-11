package com.ohmyclipping.support

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * GraphemeTruncator 의 grapheme cluster 기반 truncation 검증.
 * 한글 조합 문자와 이모지 ZWJ sequence 가 중간에 잘리지 않고,
 * 평문 문자열은 기존 `take(n)` 동작과 동등한지 회귀 테스트한다.
 */
class GraphemeTruncatorTest {

    @Nested
    inner class `한글 텍스트 truncation` {

        @Test
        fun `11자 중 5개 grapheme 만 남기고 ellipsis`() {
            GraphemeTruncator.truncateByGrapheme("안녕하세요반갑습니다", 5) shouldBe "안녕하세요…"
        }

        @Test
        fun `이미 허용 범위 안이면 ellipsis 없이 반환`() {
            GraphemeTruncator.truncateByGrapheme("안녕", 5) shouldBe "안녕"
        }

        @Test
        fun `정확히 경계일 때 ellipsis 없음`() {
            GraphemeTruncator.truncateByGrapheme("안녕하세요", 5) shouldBe "안녕하세요"
        }
    }

    @Nested
    inner class `이모지 ZWJ sequence` {

        @Test
        fun `가족 이모지가 한 grapheme 으로 유지된다`() {
            val family = "👨\u200D👩\u200D👧\u200D👦"
            // maxGraphemes=1 이면 가족 이모지 1개만 남고 ellipsis 붙음 없음(원본 전체 소비)
            GraphemeTruncator.truncateByGrapheme(family + "다음", 1) shouldBe "$family…"
        }

        @Test
        fun `스킨톤 modifier 가 분리되지 않는다`() {
            val thumbsUp = "\uD83D\uDC4D\uD83C\uDFFD" // 👍🏽
            val result = GraphemeTruncator.truncateByGrapheme(thumbsUp + "후속", 1)
            // ZWJ 없는 modifier sequence 도 한 단위로 유지되어야 한다
            result shouldBe "$thumbsUp…"
        }

        @Test
        fun `이모지 ZWJ 중간에서 자르지 않는다`() {
            val family = "👨\u200D👩\u200D👧\u200D👦"
            // 가족 이모지 1 grapheme + "안" 1 grapheme = 총 2 grapheme까지 유지
            val result = GraphemeTruncator.truncateByGrapheme(family + "녕하세요", 2)
            // 완성된 가족 이모지가 그대로 포함되어야 한다 (중간에 잘리면 절대 안 됨)
            result shouldContain family
            // ellipsis 는 붙어 있어야 한다 (원본이 더 길었기 때문)
            result shouldEndWith "…"
        }
    }

    @Nested
    inner class `경계값 처리` {

        @Test
        fun `빈 문자열은 그대로`() {
            GraphemeTruncator.truncateByGrapheme("", 5) shouldBe ""
        }

        @Test
        fun `maxGraphemes 0 은 ellipsis 만`() {
            GraphemeTruncator.truncateByGrapheme("안녕하세요", 0) shouldBe "…"
        }

        @Test
        fun `maxGraphemes 음수는 ellipsis 만`() {
            GraphemeTruncator.truncateByGrapheme("hello", -1) shouldBe "…"
        }

        @Test
        fun `커스텀 ellipsis 사용`() {
            GraphemeTruncator.truncateByGrapheme("abcdefghij", 3, ellipsis = "...") shouldBe "abc..."
        }

        @Test
        fun `빈 ellipsis 는 잘림 표시 없이 자른다`() {
            GraphemeTruncator.truncateByGrapheme("abcdefghij", 3, ellipsis = "") shouldBe "abc"
        }
    }

    @Nested
    inner class `평문 영문 텍스트` {

        @Test
        fun `ASCII 문자열은 take 와 동등하게 동작`() {
            GraphemeTruncator.truncateByGrapheme("hello world", 5) shouldBe "hello…"
        }

        @Test
        fun `영문 + 한글 혼합`() {
            val result = GraphemeTruncator.truncateByGrapheme("hi 안녕 world", 5)
            result shouldEndWith "…"
            result.length shouldBe 6 // 5 grapheme + ellipsis
        }
    }
}
