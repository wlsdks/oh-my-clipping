package com.ohmyclipping.support

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * [GeminiErrorClassifier]의 분류 정확성을 검증한다.
 * - 401/403/invalid-auth → EXPIRED
 * - 429/quota/resource_exhausted → QUOTA_EXHAUSTED
 * - 5xx/timeout/unavailable → TRANSIENT
 * - 그 외 → UNKNOWN
 */
class GeminiErrorClassifierTest {

    @Nested
    inner class `EXPIRED 분류` {

        @Test
        fun `401 메시지는 EXPIRED로 분류된다`() {
            val ex = RuntimeException("HTTP 401 Unauthorized: invalid api key")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.EXPIRED
        }

        @Test
        fun `403 메시지는 EXPIRED로 분류된다`() {
            val ex = RuntimeException("status: 403 Forbidden")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.EXPIRED
        }

        @Test
        fun `permission_denied gRPC 상태는 EXPIRED로 분류된다`() {
            val ex = RuntimeException("PERMISSION_DENIED: API key not valid")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.EXPIRED
        }

        @Test
        fun `중첩된 cause의 401도 탐지된다`() {
            val cause = IOException("HTTP 401")
            val ex = RuntimeException("ChatClient failed", cause)
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.EXPIRED
        }
    }

    @Nested
    inner class `QUOTA_EXHAUSTED 분류` {

        @Test
        fun `429 메시지는 QUOTA_EXHAUSTED로 분류된다`() {
            val ex = RuntimeException("HTTP 429 Too Many Requests")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.QUOTA_EXHAUSTED
        }

        @Test
        fun `quota 키워드는 QUOTA_EXHAUSTED로 분류된다`() {
            val ex = RuntimeException("Quota exceeded for project ABC")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.QUOTA_EXHAUSTED
        }

        @Test
        fun `resource_exhausted gRPC 상태는 QUOTA_EXHAUSTED로 분류된다`() {
            val ex = RuntimeException("RESOURCE_EXHAUSTED: daily quota used")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.QUOTA_EXHAUSTED
        }

        @Test
        fun `rate limit 문구도 QUOTA_EXHAUSTED로 분류된다`() {
            val ex = RuntimeException("Rate limit reached")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.QUOTA_EXHAUSTED
        }
    }

    @Nested
    inner class `TRANSIENT 분류` {

        @Test
        fun `503 메시지는 TRANSIENT로 분류된다`() {
            val ex = RuntimeException("HTTP 503 Service Unavailable")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.TRANSIENT
        }

        @Test
        fun `504 gateway timeout은 TRANSIENT로 분류된다`() {
            val ex = RuntimeException("HTTP 504 Gateway Timeout")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.TRANSIENT
        }

        @Test
        fun `deadline_exceeded gRPC 상태는 TRANSIENT로 분류된다`() {
            val ex = RuntimeException("DEADLINE_EXCEEDED")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.TRANSIENT
        }

        @Test
        fun `SocketTimeoutException 메시지도 TRANSIENT로 분류된다`() {
            val ex = SocketTimeoutException("Read timed out")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.TRANSIENT
        }
    }

    @Nested
    inner class `UNKNOWN 분류` {

        @Test
        fun `null은 UNKNOWN이다`() {
            GeminiErrorClassifier.classify(null) shouldBe GeminiErrorCategory.UNKNOWN
        }

        @Test
        fun `분류되지 않는 일반 메시지는 UNKNOWN이다`() {
            val ex = RuntimeException("something else went wrong")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.UNKNOWN
        }

        @Test
        fun `메시지가 없는 예외는 UNKNOWN이다`() {
            val ex = RuntimeException()
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.UNKNOWN
        }
    }

    @Nested
    inner class `우선순위 규칙` {

        @Test
        fun `EXPIRED 키워드가 QUOTA 키워드보다 우선한다`() {
            // 401과 429가 모두 포함된 경우 EXPIRED로 분류되어야 한다 (만료가 더 치명적)
            val ex = RuntimeException("401 Unauthorized, previously 429 rate limit")
            GeminiErrorClassifier.classify(ex) shouldBe GeminiErrorCategory.EXPIRED
        }
    }
}
