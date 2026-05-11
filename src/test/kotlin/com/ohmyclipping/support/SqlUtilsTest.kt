package com.ohmyclipping.support

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SqlUtilsTest {

    @Nested
    inner class `escapeLike 테스트` {

        @Test
        fun `일반 문자열은 그대로 반환한다`() {
            SqlUtils.escapeLike("hello world") shouldBe "hello world"
        }

        @Test
        fun `퍼센트 기호를 이스케이프한다`() {
            SqlUtils.escapeLike("100%") shouldBe "100\\%"
        }

        @Test
        fun `밑줄을 이스케이프한다`() {
            SqlUtils.escapeLike("user_name") shouldBe "user\\_name"
        }

        @Test
        fun `백슬래시를 이스케이프한다`() {
            SqlUtils.escapeLike("path\\to\\file") shouldBe "path\\\\to\\\\file"
        }

        @Test
        fun `모든 와일드카드가 동시에 존재하면 전부 이스케이프한다`() {
            SqlUtils.escapeLike("100%_test\\end") shouldBe "100\\%\\_test\\\\end"
        }

        @Test
        fun `빈 문자열은 빈 문자열을 반환한다`() {
            SqlUtils.escapeLike("") shouldBe ""
        }

        @Test
        fun `한글이 포함된 입력은 그대로 유지한다`() {
            SqlUtils.escapeLike("뉴스_검색%") shouldBe "뉴스\\_검색\\%"
        }
    }
}
