package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.AdminUserEntity
import com.clipping.mcpserver.model.AccountApprovalStatus
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.repository.AdminUserRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.time.Instant

/**
 * 관리자 사용자 목록 제한 조회 테스트.
 */
class JpaAdminUserStoreListByRoleTest {

    private val repository = mockk<AdminUserRepository>()
    private val store = JpaAdminUserStore(repository)

    @Test
    fun `승인 상태가 있으면 Pageable 조건 조회로 필요한 계정만 가져온다`() {
        val pageable = PageRequest.of(0, 50)
        every {
            repository.findByRoleAndApprovalStatusOrderByCreatedAtDesc("USER", "APPROVED", pageable)
        } returns listOf(userEntity("user-1", "APPROVED"))

        val result = store.listByRole(AccountRole.USER, AccountApprovalStatus.APPROVED, 50)

        result.map { it.id } shouldBe listOf("user-1")
        verify(exactly = 1) {
            repository.findByRoleAndApprovalStatusOrderByCreatedAtDesc("USER", "APPROVED", pageable)
        }
        verify(exactly = 0) { repository.findByRoleAndApprovalStatus("USER", "APPROVED") }
    }

    @Test
    fun `승인 상태가 없으면 Pageable 역할 조회로 필요한 계정만 가져온다`() {
        val pageable = PageRequest.of(0, 20)
        every {
            repository.findByRoleOrderByCreatedAtDesc("USER", pageable)
        } returns listOf(userEntity("user-2", "PENDING"))

        val result = store.listByRole(AccountRole.USER, approvalStatus = null, limit = 20)

        result.map { it.id } shouldBe listOf("user-2")
        verify(exactly = 1) { repository.findByRoleOrderByCreatedAtDesc("USER", pageable) }
        verify(exactly = 0) { repository.findByRole("USER") }
    }

    private fun userEntity(id: String, approvalStatus: String): AdminUserEntity {
        val now = Instant.parse("2026-04-26T00:00:00Z")
        return AdminUserEntity(
            id = id,
            username = "$id@example.com",
            passwordHash = "hash",
            role = "USER",
            approvalStatus = approvalStatus,
            createdAt = now,
            updatedAt = now,
        )
    }
}
