package com.ohmyclipping.support

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Slack 에러 코드 5분류 처리 매트릭스 회귀 테스트.
 * 각 카테고리에 속한 대표 코드 3개와 null/빈 값/unknown 코드의 분류 결과를 검증한다.
 */
class SlackErrorClassifierTest {

    @Nested
    inner class `AUTH 카테고리` {
        @Test
        fun `invalid_auth 는 AUTH 로 분류된다`() {
            SlackErrorClassifier.classify("invalid_auth") shouldBe SlackErrorCategory.AUTH
        }

        @Test
        fun `not_authed 는 AUTH 로 분류된다`() {
            SlackErrorClassifier.classify("not_authed") shouldBe SlackErrorCategory.AUTH
        }

        @Test
        fun `token_revoked 는 AUTH 로 분류된다`() {
            SlackErrorClassifier.classify("token_revoked") shouldBe SlackErrorCategory.AUTH
        }

        @Test
        fun `token_expired 는 AUTH 로 분류된다`() {
            SlackErrorClassifier.classify("token_expired") shouldBe SlackErrorCategory.AUTH
        }

        @Test
        fun `account_inactive 는 AUTH 로 분류된다`() {
            SlackErrorClassifier.classify("account_inactive") shouldBe SlackErrorCategory.AUTH
        }
    }

    @Nested
    inner class `SCOPE 카테고리` {
        @Test
        fun `missing_scope 는 SCOPE 로 분류된다`() {
            SlackErrorClassifier.classify("missing_scope") shouldBe SlackErrorCategory.SCOPE
        }

        @Test
        fun `no_permission 은 SCOPE 로 분류된다`() {
            SlackErrorClassifier.classify("no_permission") shouldBe SlackErrorCategory.SCOPE
        }

        @Test
        fun `ekm_access_denied 는 SCOPE 로 분류된다`() {
            SlackErrorClassifier.classify("ekm_access_denied") shouldBe SlackErrorCategory.SCOPE
        }
    }

    @Nested
    inner class `CHANNEL 카테고리` {
        @Test
        fun `channel_not_found 는 CHANNEL 로 분류된다`() {
            SlackErrorClassifier.classify("channel_not_found") shouldBe SlackErrorCategory.CHANNEL
        }

        @Test
        fun `is_archived 는 CHANNEL 로 분류된다`() {
            SlackErrorClassifier.classify("is_archived") shouldBe SlackErrorCategory.CHANNEL
        }

        @Test
        fun `not_in_channel 은 CHANNEL 로 분류된다`() {
            SlackErrorClassifier.classify("not_in_channel") shouldBe SlackErrorCategory.CHANNEL
        }

        @Test
        fun `restricted_action_read_only_channel 은 CHANNEL 로 분류된다`() {
            SlackErrorClassifier.classify("restricted_action_read_only_channel") shouldBe SlackErrorCategory.CHANNEL
        }

        @Test
        fun `team_access_not_granted 는 CHANNEL 로 분류된다`() {
            SlackErrorClassifier.classify("team_access_not_granted") shouldBe SlackErrorCategory.CHANNEL
        }
    }

    @Nested
    inner class `PAYLOAD 카테고리` {
        @Test
        fun `msg_blocks_too_long 은 PAYLOAD 로 분류된다`() {
            SlackErrorClassifier.classify("msg_blocks_too_long") shouldBe SlackErrorCategory.PAYLOAD
        }

        @Test
        fun `invalid_blocks 는 PAYLOAD 로 분류된다`() {
            SlackErrorClassifier.classify("invalid_blocks") shouldBe SlackErrorCategory.PAYLOAD
        }

        @Test
        fun `markdown_text_conflict 는 PAYLOAD 로 분류된다`() {
            SlackErrorClassifier.classify("markdown_text_conflict") shouldBe SlackErrorCategory.PAYLOAD
        }

        @Test
        fun `too_many_attachments 는 PAYLOAD 로 분류된다`() {
            SlackErrorClassifier.classify("too_many_attachments") shouldBe SlackErrorCategory.PAYLOAD
        }

        @Test
        fun `no_text 는 PAYLOAD 로 분류된다`() {
            SlackErrorClassifier.classify("no_text") shouldBe SlackErrorCategory.PAYLOAD
        }
    }

    @Nested
    inner class `RATE 카테고리` {
        @Test
        fun `ratelimited 는 RATE 로 분류된다`() {
            SlackErrorClassifier.classify("ratelimited") shouldBe SlackErrorCategory.RATE
        }

        @Test
        fun `rate_limited 는 RATE 로 분류된다 — 언더스코어 변형도 허용`() {
            SlackErrorClassifier.classify("rate_limited") shouldBe SlackErrorCategory.RATE
        }

        @Test
        fun `message_limit_exceeded 는 RATE 로 분류된다`() {
            SlackErrorClassifier.classify("message_limit_exceeded") shouldBe SlackErrorCategory.RATE
        }
    }

    @Nested
    inner class `TRANSIENT 카테고리` {
        @Test
        fun `service_unavailable 은 TRANSIENT 로 분류된다`() {
            SlackErrorClassifier.classify("service_unavailable") shouldBe SlackErrorCategory.TRANSIENT
        }

        @Test
        fun `internal_error 는 TRANSIENT 로 분류된다`() {
            SlackErrorClassifier.classify("internal_error") shouldBe SlackErrorCategory.TRANSIENT
        }
    }

    @Nested
    inner class `경계값 및 미상 에러` {
        @Test
        fun `null 은 UNKNOWN 으로 분류된다`() {
            SlackErrorClassifier.classify(null) shouldBe SlackErrorCategory.UNKNOWN
        }

        @Test
        fun `빈 문자열은 UNKNOWN 으로 분류된다`() {
            SlackErrorClassifier.classify("") shouldBe SlackErrorCategory.UNKNOWN
        }

        @Test
        fun `공백 문자열은 UNKNOWN 으로 분류된다`() {
            SlackErrorClassifier.classify("   ") shouldBe SlackErrorCategory.UNKNOWN
        }

        @Test
        fun `매핑되지 않은 에러 코드는 UNKNOWN 으로 분류된다`() {
            SlackErrorClassifier.classify("some_new_error_code") shouldBe SlackErrorCategory.UNKNOWN
        }

        @Test
        fun `대문자 혼용 코드도 정상 분류된다 — 대소문자 무관`() {
            SlackErrorClassifier.classify("Invalid_Auth") shouldBe SlackErrorCategory.AUTH
            SlackErrorClassifier.classify("MISSING_SCOPE") shouldBe SlackErrorCategory.SCOPE
        }

        @Test
        fun `앞뒤 공백이 포함된 코드도 정상 분류된다`() {
            SlackErrorClassifier.classify("  ratelimited  ") shouldBe SlackErrorCategory.RATE
        }
    }
}
