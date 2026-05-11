package com.ohmyclipping.support

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SlackMentionGuardTest {

    @Nested
    inner class `containsDangerousMention 감지` {

        @Test
        fun `채널 전체 멘션을 감지한다`() {
            SlackMentionGuard.containsDangerousMention("hey <!channel>") shouldBe true
        }

        @Test
        fun `here 멘션을 감지한다`() {
            SlackMentionGuard.containsDangerousMention("<!here> attention") shouldBe true
        }

        @Test
        fun `everyone 멘션을 감지한다`() {
            SlackMentionGuard.containsDangerousMention("<!everyone> notice") shouldBe true
        }

        @Test
        fun `user 멘션을 감지한다`() {
            SlackMentionGuard.containsDangerousMention("cc <@U01AB2CD3EF>") shouldBe true
        }

        @Test
        fun `channel link 멘션을 감지한다`() {
            SlackMentionGuard.containsDangerousMention("move to <#C01AB2CD3EF>") shouldBe true
        }

        @Test
        fun `subteam broadcast 멘션을 감지한다`() {
            SlackMentionGuard.containsDangerousMention("<!subteam^S01ABCDEF>") shouldBe true
        }

        @Test
        fun `일반 HTML 태그는 감지하지 않는다`() {
            SlackMentionGuard.containsDangerousMention("<div>hello</div>") shouldBe false
        }

        @Test
        fun `수식 부등호는 감지하지 않는다`() {
            SlackMentionGuard.containsDangerousMention("x < 3 and y > 2") shouldBe false
        }

        @Test
        fun `빈 문자열은 감지하지 않는다`() {
            SlackMentionGuard.containsDangerousMention("") shouldBe false
        }
    }

    @Nested
    inner class `neutralize 중립화` {

        @Test
        fun `채널 멘션을 zero-width space로 분리한다`() {
            val result = SlackMentionGuard.neutralize("hey <!channel> notice")
            result shouldContain "<\u200B!channel>"
            result shouldNotContain "<!channel>"
        }

        @Test
        fun `here 멘션을 중립화한다`() {
            SlackMentionGuard.neutralize("<!here>") shouldBe "<\u200B!here>"
        }

        @Test
        fun `user 멘션을 중립화한다`() {
            SlackMentionGuard.neutralize("cc <@U01AB2CD3EF>") shouldBe "cc <\u200B@U01AB2CD3EF>"
        }

        @Test
        fun `channel link 멘션을 중립화한다`() {
            SlackMentionGuard.neutralize("<#C01AB2CD3EF>") shouldBe "<\u200B#C01AB2CD3EF>"
        }

        @Test
        fun `여러 개의 멘션을 모두 중립화한다`() {
            val raw = "<!channel> and <!here> and <@U01AB2CD3EF>"
            val result = SlackMentionGuard.neutralize(raw)
            result shouldNotContain "<!channel>"
            result shouldNotContain "<!here>"
            result shouldNotContain "<@U01AB2CD3EF>"
            result shouldContain "<\u200B!channel>"
            result shouldContain "<\u200B!here>"
            result shouldContain "<\u200B@U01AB2CD3EF>"
        }

        @Test
        fun `일반 HTML 태그는 원본 그대로 반환한다`() {
            SlackMentionGuard.neutralize("<div>hello</div>") shouldBe "<div>hello</div>"
        }

        @Test
        fun `수식 부등호는 원본 그대로 반환한다`() {
            SlackMentionGuard.neutralize("x < 3 and y > 2") shouldBe "x < 3 and y > 2"
        }

        @Test
        fun `neutralizeOrNull에 null을 전달하면 null을 반환한다`() {
            SlackMentionGuard.neutralizeOrNull(null) shouldBe null
        }

        @Test
        fun `neutralizeOrNull에 빈 문자열을 전달하면 빈 문자열을 반환한다`() {
            SlackMentionGuard.neutralizeOrNull("") shouldBe ""
        }

        @Test
        fun `빈 문자열은 빈 문자열을 반환한다`() {
            SlackMentionGuard.neutralize("") shouldBe ""
        }

        @Test
        fun `멘션이 전혀 없는 텍스트는 참조 동일성을 유지한다`() {
            val input = "평범한 텍스트"
            // 패턴이 없으면 replace를 수행하지 않고 원본을 그대로 돌려준다.
            SlackMentionGuard.neutralize(input) shouldBe input
        }
    }
}
