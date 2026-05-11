package com.ohmyclipping.service

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import javax.sql.DataSource

/**
 * LocalDevSupportService 테스트.
 *
 * 이 서비스는 @Profile("local") 전용 dev utility다.
 * bootstrap()은 JDBC + ResourceDatabasePopulator(SQL 파일 실행)에 의존해
 * 순수 단위 테스트 격리가 어렵다.
 * loginShortcuts() 반환값과 seed 데이터 정합성을 중심으로 검증한다.
 */
class LocalDevSupportServiceTest {

    private val service = LocalDevSupportService(
        jdbc = mockk<JdbcTemplate>(relaxed = true),
        passwordEncoder = mockk<PasswordEncoder>(),
        dataSource = mockk<DataSource>(),
        runtimeSettingService = mockk<RuntimeSettingService>()
    )

    @Nested
    inner class `loginShortcuts` {

        @Test
        fun `3개의 로그인 단축키를 반환한다`() {
            service.loginShortcuts() shouldHaveSize 3
        }

        @Test
        fun `admin 단축키의 필드가 올바르다`() {
            val admin = service.loginShortcuts().find { it.key == "admin" }

            admin shouldNotBe null
            admin!!.label shouldBe "관리자 로그인"
            admin.scope shouldBe "admin"
            admin.username shouldBe "dev.admin@clipping.local"
            admin.password shouldBe "LocalPass123!"
        }

        @Test
        fun `user 단축키의 필드가 올바르다`() {
            val user = service.loginShortcuts().find { it.key == "user" }

            user shouldNotBe null
            user!!.label shouldBe "회원 로그인"
            user.scope shouldBe "user"
            user.username shouldBe "dev.user@clipping.local"
        }

        @Test
        fun `fresh 단축키의 필드가 올바르다`() {
            val fresh = service.loginShortcuts().find { it.key == "fresh" }

            fresh shouldNotBe null
            fresh!!.label shouldBe "신규 가입자 로그인"
            fresh.scope shouldBe "new-user"
            fresh.username shouldBe "dev.user.fresh@clipping.local"
        }

        @Test
        fun `모든 단축키에 동일한 비밀번호가 설정되어 있다`() {
            service.loginShortcuts().forEach { shortcut ->
                shortcut.password shouldBe "LocalPass123!"
            }
        }

        @Test
        fun `단축키 scope 목록은 admin, user, new-user 순서이다`() {
            service.loginShortcuts().map { it.scope } shouldBe listOf("admin", "user", "new-user")
        }

        @Test
        fun `단축키 key 목록은 admin, user, fresh 순서이다`() {
            service.loginShortcuts().map { it.key } shouldBe listOf("admin", "user", "fresh")
        }
    }

    @Nested
    inner class `LocalDevLoginShortcut 데이터 클래스` {

        @Test
        fun `동일 값의 인스턴스는 equals가 true다`() {
            val a = LocalDevLoginShortcut("k", "l", "s", "u", "p", "n")
            val b = LocalDevLoginShortcut("k", "l", "s", "u", "p", "n")
            a shouldBe b
        }

        @Test
        fun `다른 key이면 equals가 false다`() {
            val a = LocalDevLoginShortcut("k1", "l", "s", "u", "p", "n")
            val b = LocalDevLoginShortcut("k2", "l", "s", "u", "p", "n")
            (a == b) shouldBe false
        }
    }

    @Nested
    inner class `LocalDevAccountSeed 데이터 클래스` {

        @Test
        fun `동일 값의 인스턴스는 equals가 true다`() {
            val a = LocalDevAccountSeed("id", "user", "ADMIN", "name", "dept", "APPROVED", "note", null)
            val b = LocalDevAccountSeed("id", "user", "ADMIN", "name", "dept", "APPROVED", "note", null)
            a shouldBe b
        }

        @Test
        fun `department가 null이어도 정상 생성된다`() {
            val seed = LocalDevAccountSeed("id", "user", "USER", "name", null, "PENDING", "note", null)
            seed.department shouldBe null
        }

        @Test
        fun `approvedByUserId가 null이어도 정상 생성된다`() {
            val seed = LocalDevAccountSeed("id", "user", "USER", "name", "dept", "APPROVED", "note", null)
            seed.approvedByUserId shouldBe null
        }
    }
}
