package com.clipping.mcpserver.support

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/**
 * SlackTextNormalizer 의 특수 유니코드 문자 정규화 검증.
 */
class SlackTextNormalizerTest {

    @Test
    fun `CRLF 는 LF 로 치환`() {
        SlackTextNormalizer.normalizeForSlack("a\r\nb") shouldBe "a\nb"
    }

    @Test
    fun `단독 CR 은 LF 로 치환`() {
        SlackTextNormalizer.normalizeForSlack("a\rb") shouldBe "a\nb"
    }

    @Test
    fun `U+2028 line separator 는 LF 로 치환`() {
        SlackTextNormalizer.normalizeForSlack("a\u2028b") shouldBe "a\nb"
    }

    @Test
    fun `U+2029 paragraph separator 는 LF 로 치환`() {
        SlackTextNormalizer.normalizeForSlack("a\u2029b") shouldBe "a\nb"
    }

    @Test
    fun `zero-width space 는 제거`() {
        val result = SlackTextNormalizer.normalizeForSlack("a\u200Bb")
        result shouldBe "ab"
        result shouldNotContain "\u200B"
    }

    @Test
    fun `BOM 은 제거`() {
        SlackTextNormalizer.normalizeForSlack("\uFEFFhello") shouldBe "hello"
    }

    @Test
    fun `전각 공백은 반각 공백으로`() {
        SlackTextNormalizer.normalizeForSlack("a\u3000b") shouldBe "a b"
    }

    @Test
    fun `여러 문자가 섞여 있으면 모두 정규화`() {
        val input = "a\r\nb\u2028c\u200Bd\u3000e"
        SlackTextNormalizer.normalizeForSlack(input) shouldBe "a\nb\ncd e"
    }

    @Test
    fun `정상 텍스트는 변경 없음`() {
        SlackTextNormalizer.normalizeForSlack("안녕하세요\n반갑습니다") shouldBe "안녕하세요\n반갑습니다"
    }

    @Test
    fun `빈 문자열은 빈 문자열 반환`() {
        SlackTextNormalizer.normalizeForSlack("") shouldBe ""
    }
}
