package com.clipping.mcpserver.support

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * SlackEscapeUtil 이중 방어 escape 유틸의 회귀 테스트.
 * 렌더링 출력 경계에서 HTML entity, mrkdwn 포맷 문자, 링크 label 치환, URL scheme 검증이
 * 모두 의도대로 동작하는지 확인한다.
 */
class SlackEscapeUtilTest {

    @Nested
    inner class `escapeHtml 은 Slack 지정 세 문자만 entity 로 치환한다` {

        @Test
        fun `세 문자 모두 순서대로 치환`() {
            SlackEscapeUtil.escapeHtml("<&>") shouldBe "&lt;&amp;&gt;"
        }

        @Test
        fun `빈 문자열은 그대로`() {
            SlackEscapeUtil.escapeHtml("") shouldBe ""
        }

        @Test
        fun `따옴표나 괄호는 escape 되지 않는다`() {
            SlackEscapeUtil.escapeHtml("\"'()[]{}") shouldBe "\"'()[]{}"
        }

        @Test
        fun `이미 entity 로 escape 된 문자열도 한 번 더 처리한다`() {
            // 이중 방어 관점에서는 의도된 동작이다 (입력 저장 시점에 이미 평문화된다고 가정)
            SlackEscapeUtil.escapeHtml("&amp;") shouldBe "&amp;amp;"
        }
    }

    @Nested
    inner class `escapeMrkdwn 은 포맷 문자 앞에 backslash 를 삽입한다` {

        @Test
        fun `별표는 escape`() {
            SlackEscapeUtil.escapeMrkdwn("*bold*") shouldBe "\\*bold\\*"
        }

        @Test
        fun `언더스코어는 escape`() {
            SlackEscapeUtil.escapeMrkdwn("_italic_") shouldBe "\\_italic\\_"
        }

        @Test
        fun `틸드는 escape`() {
            SlackEscapeUtil.escapeMrkdwn("~strike~") shouldBe "\\~strike\\~"
        }

        @Test
        fun `백틱은 escape`() {
            SlackEscapeUtil.escapeMrkdwn("`code`") shouldBe "\\`code\\`"
        }

        @Test
        fun `일반 텍스트는 변하지 않는다`() {
            SlackEscapeUtil.escapeMrkdwn("안녕하세요 world") shouldBe "안녕하세요 world"
        }
    }

    @Nested
    inner class `escapeForMrkdwnSection 은 HTML 과 mrkdwn 을 순서대로 적용한다` {

        @Test
        fun `HTML entity 와 별표가 모두 적용된다`() {
            SlackEscapeUtil.escapeForMrkdwnSection("<b>*hi*</b>") shouldBe "&lt;b&gt;\\*hi\\*&lt;/b&gt;"
        }
    }

    @Nested
    inner class `sanitizeLinkLabel 은 링크 종결자를 치환한다` {

        @Test
        fun `닫는 꺾쇠와 파이프를 공백으로 치환`() {
            SlackEscapeUtil.sanitizeLinkLabel("> text | more") shouldBe "  text   more"
        }

        @Test
        fun `종결자가 없는 경우 원문 유지`() {
            SlackEscapeUtil.sanitizeLinkLabel("normal label") shouldBe "normal label"
        }

        @Test
        fun `빈 문자열은 그대로`() {
            SlackEscapeUtil.sanitizeLinkLabel("") shouldBe ""
        }
    }

    @Nested
    inner class `neutralizeBidiOverride 는 bidi 제어 문자를 제거한다` {

        @Test
        fun `일반 텍스트는 그대로 반환한다`() {
            SlackEscapeUtil.neutralizeBidiOverride("normal text") shouldBe "normal text"
        }

        @Test
        fun `RLO로 방향 반전된 문자열은 제어 문자만 제거된다`() {
            // U+202E(RLO)는 이후 문자열을 우→좌로 뒤집어 'gnitekram'을 'marketing'처럼 보이게 만든다.
            SlackEscapeUtil.neutralizeBidiOverride("ABC\u202Egnitekram") shouldBe "ABCgnitekram"
        }

        @Test
        fun `LRE 제어 문자를 제거한다`() {
            SlackEscapeUtil.neutralizeBidiOverride("\u202ARTL") shouldBe "RTL"
        }

        @Test
        fun `FSI와 PDI 쌍을 제거한다`() {
            SlackEscapeUtil.neutralizeBidiOverride("\u2067isolate\u2069") shouldBe "isolate"
        }

        @Test
        fun `9개 코드포인트를 모두 제거한다`() {
            // U+202A~U+202E(5개) + U+2066~U+2069(4개) = 총 9개
            val input = "\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069X"
            SlackEscapeUtil.neutralizeBidiOverride(input) shouldBe "X"
        }

        @Test
        fun `escapeForMrkdwnSection 경로에서도 bidi 가 제거된다`() {
            // 저장 데이터에 bidi 가 섞여 있어도 렌더 경계에서 반드시 중성화되어야 한다.
            SlackEscapeUtil.escapeForMrkdwnSection("\u202EHELLO") shouldBe "HELLO"
        }
    }

    @Nested
    inner class `isSafeUrl 은 http 와 https 만 허용한다` {

        @Test
        fun `https 는 허용`() {
            SlackEscapeUtil.isSafeUrl("https://example.com") shouldBe true
        }

        @Test
        fun `http 는 허용`() {
            SlackEscapeUtil.isSafeUrl("http://example.com/path") shouldBe true
        }

        @Test
        fun `HTTPS 대문자도 허용`() {
            SlackEscapeUtil.isSafeUrl("HTTPS://example.com") shouldBe true
        }

        @Test
        fun `javascript scheme 차단`() {
            SlackEscapeUtil.isSafeUrl("javascript:alert(1)") shouldBe false
        }

        @Test
        fun `data scheme 차단`() {
            SlackEscapeUtil.isSafeUrl("data:text/html,<script>alert(1)</script>") shouldBe false
        }

        @Test
        fun `ftp scheme 차단`() {
            SlackEscapeUtil.isSafeUrl("ftp://example.com") shouldBe false
        }

        @Test
        fun `빈 값은 차단`() {
            SlackEscapeUtil.isSafeUrl("") shouldBe false
        }

        @Test
        fun `공백만 있는 값은 차단`() {
            SlackEscapeUtil.isSafeUrl("   ") shouldBe false
        }

        @Test
        fun `scheme 없는 상대 경로는 차단`() {
            SlackEscapeUtil.isSafeUrl("/path/only") shouldBe false
        }
    }
}
