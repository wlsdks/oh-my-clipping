package com.ohmyclipping.support

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SlackChannelIdNormalizerTest {

    @Test
    fun `normalize should accept direct channel id`() {
        SlackChannelIdNormalizer.normalize("c123test01") shouldBe "C123TEST01"
    }

    @Test
    fun `normalize should parse lowercase id prefix`() {
        SlackChannelIdNormalizer.normalize("id:c123test01") shouldBe "C123TEST01"
    }

    @Test
    fun `normalize should parse url encoded query parameter`() {
        val raw = "https://app.slack.com/client/T0000?channel=c123test01%20"
        SlackChannelIdNormalizer.normalize(raw) shouldBe "C123TEST01"
    }

    @Test
    fun `normalize should accept user id with U prefix`() {
        SlackChannelIdNormalizer.normalize("U01AB2CD3EF") shouldBe "U01AB2CD3EF"
    }

    @Test
    fun `normalize should accept lowercase user id with U prefix`() {
        SlackChannelIdNormalizer.normalize("u01ab2cd3ef") shouldBe "U01AB2CD3EF"
    }

    @Test
    fun `normalize should accept DM channel id with D prefix`() {
        SlackChannelIdNormalizer.normalize("D01AB2CD3EF") shouldBe "D01AB2CD3EF"
    }

    @Test
    fun `normalize should accept lowercase DM channel id with D prefix`() {
        SlackChannelIdNormalizer.normalize("d01ab2cd3ef") shouldBe "D01AB2CD3EF"
    }

    @Test
    fun `normalize should return null for invalid value`() {
        SlackChannelIdNormalizer.normalize("not-a-channel") shouldBe null
    }
}
