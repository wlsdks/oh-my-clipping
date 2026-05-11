package com.ohmyclipping.store

import com.ohmyclipping.model.AdminUser
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.nulls.shouldBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcAdminUserStoreTest {

    @Autowired
    lateinit var adminUserStore: AdminUserStore

    @Test
    fun `save and findByUsername should normalize username`() {
        val saved = adminUserStore.save(
            AdminUser(
                id = "",
                username = "Admin.User",
                passwordHash = "hash",
                displayName = "Operator"
            )
        )

        saved.username shouldBe "admin.user"

        val found = adminUserStore.findByUsername(" ADMIN.USER ")
        found shouldNotBe null
        found!!.id shouldBe saved.id
        found.displayName shouldBe "Operator"
    }

    @Test
    fun `save 시 team 이 DB 에 저장되고 findById 로 복원된다`() {
        val saved = adminUserStore.save(
            AdminUser(
                id = "",
                username = "team-user-${System.nanoTime()}",
                passwordHash = "hash",
                displayName = "Team Member",
                department = "영업팀",
                team = "solutions-team-a"
            )
        )

        val found = adminUserStore.findById(saved.id)
        found shouldNotBe null
        found!!.team shouldBe "solutions-team-a"
        found.department shouldBe "영업팀"
    }

    @Test
    fun `team 이 null 이면 그대로 null 로 저장, 조회된다`() {
        val saved = adminUserStore.save(
            AdminUser(
                id = "",
                username = "noteam-user-${System.nanoTime()}",
                passwordHash = "hash"
            )
        )

        val found = adminUserStore.findById(saved.id)
        found!!.team.shouldBeNull()
    }

    @Test
    fun `update 로 team 을 교체할 수 있다`() {
        val saved = adminUserStore.save(
            AdminUser(
                id = "",
                username = "update-team-${System.nanoTime()}",
                passwordHash = "hash",
                team = "old-team"
            )
        )

        adminUserStore.update(saved.copy(team = "new-team"))

        val found = adminUserStore.findById(saved.id)
        found!!.team shouldBe "new-team"
    }

    @Test
    fun `updateLastLoginAt should update timestamp`() {
        adminUserStore.save(
            AdminUser(
                id = "",
                username = "ops",
                passwordHash = "hash"
            )
        )

        adminUserStore.updateLastLoginAt("ops")
        val updated = adminUserStore.findByUsername("ops")
        updated shouldNotBe null
        updated!!.lastLoginAt shouldNotBe null
    }
}
