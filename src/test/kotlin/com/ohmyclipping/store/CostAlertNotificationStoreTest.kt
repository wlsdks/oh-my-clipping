package com.ohmyclipping.store

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CostAlertNotificationStoreTest {

    @Autowired
    lateinit var store: CostAlertNotificationStore

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun clearTable() {
        jdbcTemplate.execute("DELETE FROM cost_alert_notifications")
    }

    @Nested
    inner class `tryRegister — 최초 등록` {

        @Test
        fun `최초 호출은 true를 반환한다`() {
            store.tryRegister("2026-04", "CRITICAL_100") shouldBe true
        }

        @Test
        fun `같은 월+레벨 재호출은 false를 반환한다`() {
            store.tryRegister("2026-04", "CRITICAL_100") shouldBe true
            store.tryRegister("2026-04", "CRITICAL_100") shouldBe false
        }
    }

    @Nested
    inner class `tryRegister — 독립 조합` {

        @Test
        fun `같은 월 다른 레벨은 각각 true를 반환한다`() {
            store.tryRegister("2026-04", "CRITICAL_100") shouldBe true
            store.tryRegister("2026-04", "CRITICAL_90") shouldBe true
        }

        @Test
        fun `다른 월 같은 레벨은 각각 true를 반환한다`() {
            store.tryRegister("2026-03", "CRITICAL_100") shouldBe true
            store.tryRegister("2026-04", "CRITICAL_100") shouldBe true
        }

        @Test
        fun `90퍼센트 발송 이력이 있어도 100퍼센트 도달 시 독립적으로 등록된다`() {
            // 90% 레벨 먼저 등록
            store.tryRegister("2026-04", "CRITICAL_90") shouldBe true
            // 100% 레벨은 별개로 최초 등록
            store.tryRegister("2026-04", "CRITICAL_100") shouldBe true
            // 이후 재시도는 false
            store.tryRegister("2026-04", "CRITICAL_90") shouldBe false
            store.tryRegister("2026-04", "CRITICAL_100") shouldBe false
        }
    }
}
