package com.ohmyclipping.mcp

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class McpSecretRedactorTest {

    @Nested
    inner class `민감 키 감지` {

        @Test
        fun `camelCase snake_case webhook token key variants are detected`() {
            listOf("apiKey", "USER_API_KEY", "slackWebhook", "private_key", "bearerToken")
                .forEach { key -> McpSecretRedactor.isSensitiveKey(key).shouldBeTrue() }
        }

        @Test
        fun `non-sensitive identifiers are not detected`() {
            listOf("categoryId", "userId", "slackChannelId", "limit")
                .forEach { key -> McpSecretRedactor.isSensitiveKey(key).shouldBeFalse() }
        }
    }

    @Nested
    inner class `임베디드 secret 마스킹` {

        @Test
        fun `key-value tokens are redacted without leaking actual value`() {
            val masked = McpSecretRedactor.scrubEmbeddedSecrets("callback token=sk-live-secret")

            masked shouldContain "token=***REDACTED***"
            masked shouldNotContain "sk-live-secret"
        }

        @Test
        fun `authorization bearer values are redacted as a single secret`() {
            val masked = McpSecretRedactor.scrubEmbeddedSecrets("curl -H 'authorization bearer sk-live-secret'")

            masked shouldContain "authorization=***REDACTED***"
            masked shouldNotContain "bearer sk-live-secret"
            masked shouldNotContain "sk-live-secret"
        }

        @Test
        fun `safe strings are returned unchanged`() {
            McpSecretRedactor.scrubEmbeddedSecrets("categoryId=cat-1 limit=10") shouldBe
                "categoryId=cat-1 limit=10"
        }
    }
}
