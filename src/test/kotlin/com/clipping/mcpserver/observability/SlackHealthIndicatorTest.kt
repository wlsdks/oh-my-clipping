package com.clipping.mcpserver.observability

import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status
import java.time.Instant

/**
 * SlackHealthIndicator 단위 테스트.
 *
 * 공유 [SlackHealthStatus] 값을 기반으로 Health UP/DOWN 을 결정하는 얇은 인디케이터이므로,
 * AtomicBoolean/AtomicReference 의 값에 따른 세 가지 분기를 모두 커버한다.
 */
class SlackHealthIndicatorTest {

    private fun newIndicator(status: SlackHealthStatus = SlackHealthStatus()): SlackHealthIndicator =
        SlackHealthIndicator(status)

    @Nested
    inner class `정상 상태 (UP)` {

        @Test
        fun `isHealthy=true이고 lastCheckTime이 있으면 UP과 lastCheckTime이 details에 포함된다`() {
            val status = SlackHealthStatus()
            status.isHealthy.set(true)
            val checkedAt = Instant.parse("2026-04-10T10:00:00Z")
            status.lastCheckTime.set(checkedAt)

            val health = newIndicator(status).health()

            health.status shouldBe Status.UP
            (health.details as Map<String, Any>).shouldContainKey("lastCheckTime")
            health.details["lastCheckTime"] shouldBe checkedAt.toString()
            // UP일 때는 reason 키가 붙지 않는다
            (health.details as Map<String, Any>).shouldNotContainKey("reason")
        }

        @Test
        fun `lastCheckTime이 아직 없어도 isHealthy만 true이면 UP을 반환한다`() {
            // 스케줄러가 아직 한 번도 안 돌았지만 초기값이 true인 경우는 거의 없지만 방어 검증.
            val status = SlackHealthStatus()
            status.isHealthy.set(true)
            status.lastCheckTime.set(null)

            val health = newIndicator(status).health()

            health.status shouldBe Status.UP
            (health.details as Map<String, Any>).shouldNotContainKey("lastCheckTime")
        }
    }

    @Nested
    inner class `장애 상태 (DOWN)` {

        @Test
        fun `초기 상태(isHealthy=false, lastCheckTime=null)는 DOWN이며 reason이 '검증되지 않음'을 알린다`() {
            val status = SlackHealthStatus()
            // 기본값 그대로 사용

            val health = newIndicator(status).health()

            health.status shouldBe Status.DOWN
            (health.details["reason"] as String) shouldBe
                "Slack token validation failed or not yet checked"
        }

        @Test
        fun `isHealthy=false + lastCheckTime 존재시 DOWN이며 lastCheckTime도 details에 남는다`() {
            val status = SlackHealthStatus()
            status.isHealthy.set(false)
            val checkedAt = Instant.parse("2026-04-10T10:00:00Z")
            status.lastCheckTime.set(checkedAt)

            val health = newIndicator(status).health()

            health.status shouldBe Status.DOWN
            health.details["lastCheckTime"] shouldBe checkedAt.toString()
            (health.details["reason"] as String) shouldBe
                "Slack token validation failed or not yet checked"
        }
    }
}
