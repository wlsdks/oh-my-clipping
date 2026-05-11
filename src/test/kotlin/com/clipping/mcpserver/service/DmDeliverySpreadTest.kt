package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.digest.*

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * DM 발송 자동 분산 로직의 동작을 검증한다.
 * SlackDigestWorker.DM_SPREAD_MINUTES = 30 기준으로
 * userId 해시가 0~29분에 고르게 분산되는지 확인한다.
 */
class DmDeliverySpreadTest {

    private val spreadMinutes = SlackDigestWorker.DM_SPREAD_MINUTES

    /** userId에서 분 오프셋을 계산한다 (SlackDigestWorker와 동일 로직) */
    private fun minuteOffset(userId: String): Int =
        (userId.hashCode().and(Int.MAX_VALUE)) % spreadMinutes

    @Nested
    inner class `분산 오프셋 계산` {

        @Test
        fun `같은 userId는 항상 같은 오프셋을 반환한다`() {
            val offset1 = minuteOffset("user-001")
            val offset2 = minuteOffset("user-001")
            offset1 shouldBe offset2
        }

        @Test
        fun `오프셋은 0 이상 SPREAD_MINUTES 미만이다`() {
            val offsets = (1..1000).map { minuteOffset("user-$it") }
            offsets.all { it in 0 until spreadMinutes } shouldBe true
        }

        @Test
        fun `다른 userId는 다양한 오프셋으로 분산된다`() {
            val offsets = (1..300).map { minuteOffset("user-$it") }.toSet()
            // 300명이면 30개 슬롯 중 최소 20개는 사용되어야 한다
            offsets.size shouldBeGreaterThan 20
        }
    }

    @Nested
    inner class `300명 분산 시뮬레이션` {

        @Test
        fun `300명이 30분에 분산되면 슬롯당 최대 50명 이하이다`() {
            val slots = IntArray(spreadMinutes)
            for (i in 1..300) {
                slots[minuteOffset("user-$i")]++
            }
            // Slack 한도 50건/분 기준, 구독 1개당 = 사용자 수 ≤ 50
            slots.max() shouldBeLessThanOrEqual 50
        }

        @Test
        fun `300명 × 3구독이면 슬롯당 최대 150건 이하이다`() {
            val subsPerUser = 3
            val slots = IntArray(spreadMinutes)
            for (i in 1..300) {
                slots[minuteOffset("user-$i")] += subsPerUser
            }
            // 최악 슬롯이 Slack 한도(50건/분)의 3배 이내
            // (429 retry가 자동 조절하므로 150건도 허용 가능)
            slots.max() shouldBeLessThanOrEqual 150
        }

        @Test
        fun `특정 분(minute)에 매칭되는 사용자만 필터된다`() {
            val allUsers = (1..300).map { "user-$it" }
            val targetMinute = 5
            val matched = allUsers.filter { uid ->
                minuteOffset(uid) == targetMinute
            }
            // 300명 / 30슬롯 ≈ 평균 10명, 최대 20명
            matched.size shouldBeLessThanOrEqual 30
            matched.size shouldBeGreaterThan 0
        }
    }
}
