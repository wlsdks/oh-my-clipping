package com.clipping.mcpserver.config

import com.clipping.mcpserver.service.LocalDevSupportService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationArguments
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * DEV_BOOTSTRAP guard 회귀 테스트.
 *
 * 2026-04-22 incident: DEV_BOOTSTRAP=true로 실 사용자 데이터가 있는 DB를 기동해
 * `clipping_user_requests`, `user_delivery_schedules` 등의 row가 DELETE로 소실.
 * 이 테스트는 guard가 해당 시나리오를 hard-abort로 차단하는지 검증한다.
 */
class LocalDevBootstrapConfigTest {

    private fun makeConfig(enabled: Boolean): LocalDevBootstrapConfig {
        val config = LocalDevBootstrapConfig()
        val field = LocalDevBootstrapConfig::class.java.getDeclaredField("devBootstrapEnabled")
        field.isAccessible = true
        field.setBoolean(config, enabled)
        return config
    }

    @Nested
    inner class `bootstrap guard` {

        @Test
        fun `실 사용자가 존재하면 IllegalStateException을 던지고 bootstrap을 호출하지 않는다`() {
            val support = mockk<LocalDevSupportService>(relaxed = true)
            val jdbc = mockk<JdbcTemplate> {
                every { queryForObject(any<String>(), Int::class.java) } returns 3
            }
            val runner = makeConfig(enabled = true).localDevBootstrapRunner(support, jdbc)

            val thrown = shouldThrow<IllegalStateException> {
                runner.run(mockk<ApplicationArguments>(relaxed = true))
            }
            thrown.message!! shouldContain "DEV_BOOTSTRAP aborted"
            thrown.message!! shouldContain "3 real user"

            verify(exactly = 0) { support.bootstrap() }
        }

        @Test
        fun `seed 계정만 존재하면 bootstrap을 정상 실행한다`() {
            val support = mockk<LocalDevSupportService>(relaxed = true)
            val jdbc = mockk<JdbcTemplate> {
                every { queryForObject(any<String>(), Int::class.java) } returns 0
            }
            val runner = makeConfig(enabled = true).localDevBootstrapRunner(support, jdbc)

            runner.run(mockk<ApplicationArguments>(relaxed = true))

            verify(exactly = 1) { support.bootstrap() }
        }

        @Test
        fun `DEV_BOOTSTRAP이 false이면 guard 조회 없이 bootstrap도 skip한다`() {
            val support = mockk<LocalDevSupportService>(relaxed = true)
            val jdbc = mockk<JdbcTemplate>(relaxed = true)
            val runner = makeConfig(enabled = false).localDevBootstrapRunner(support, jdbc)

            runner.run(mockk<ApplicationArguments>(relaxed = true))

            verify(exactly = 0) { support.bootstrap() }
            verify(exactly = 0) { jdbc.queryForObject(any<String>(), any<Class<*>>()) }
        }

        @Test
        fun `admin_users 테이블이 아직 없으면 guard는 0으로 처리하고 bootstrap을 진행한다`() {
            val support = mockk<LocalDevSupportService>(relaxed = true)
            val jdbc = mockk<JdbcTemplate> {
                every { queryForObject(any<String>(), Int::class.java) } throws
                    BadSqlGrammarException("preflight", "SELECT ...", java.sql.SQLException("table missing"))
            }
            val runner = makeConfig(enabled = true).localDevBootstrapRunner(support, jdbc)

            // 첫 Flyway migration 이전 상황 — guard가 예외를 삼키고 bootstrap을 진행해야 한다.
            runner.run(mockk<ApplicationArguments>(relaxed = true))

            verify(exactly = 1) { support.bootstrap() }
        }
    }
}
