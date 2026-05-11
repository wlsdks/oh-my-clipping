package com.ohmyclipping.config

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

/**
 * `SlackRequestVerificationFilter.validateConfig` 의 부팅-시 검증 동작 테스트.
 *
 * HMAC 계산/요청 필터링 로직 자체는 운영 환경에서 Slack 서명 헤더가 존재하는
 * 요청으로 통합 검증되므로 여기서는 구성값 검증만 커버한다.
 */
class SlackRequestVerificationFilterFailFastTest {

    @Test
    fun `fail-fast=true 이고 signing secret 미설정이면 validateConfig 가 예외를 던진다`() {
        val filter = SlackRequestVerificationFilter(
            signingSecret = "",
            failFast = true
        )

        val ex = shouldThrow<IllegalStateException> { filter.validateConfig() }
        ex.message!! shouldContain "SLACK_SIGNING_SECRET"
        ex.message!! shouldContain "fail-fast"
    }

    @Test
    fun `fail-fast=false 이고 signing secret 미설정이면 경고만 남기고 통과한다 (기본 동작)`() {
        val filter = SlackRequestVerificationFilter(
            signingSecret = "",
            failFast = false
        )

        shouldNotThrow<Exception> { filter.validateConfig() }
    }

    @Test
    fun `signing secret 이 설정되어 있으면 fail-fast 플래그 무관하게 통과한다`() {
        val withFlag = SlackRequestVerificationFilter(
            signingSecret = "test-secret",
            failFast = true
        )
        val withoutFlag = SlackRequestVerificationFilter(
            signingSecret = "test-secret",
            failFast = false
        )

        shouldNotThrow<Exception> { withFlag.validateConfig() }
        shouldNotThrow<Exception> { withoutFlag.validateConfig() }
    }
}
