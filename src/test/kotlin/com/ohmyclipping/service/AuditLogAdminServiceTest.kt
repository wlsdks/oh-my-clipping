package com.ohmyclipping.service

import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.AuditLogStore.AuditLogEntry
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class AuditLogAdminServiceTest {

    private val auditLogStore = mockk<AuditLogStore>()

    private val service = AuditLogAdminService(
        auditLogStore = auditLogStore
    )

    @Nested
    inner class `findAll 메서드` {

        @Test
        fun `모든 필터 파라미터를 Store에 그대로 위임한다`() {
            val from = Instant.parse("2026-03-01T00:00:00Z")
            val to = Instant.parse("2026-03-15T23:59:59Z")
            val expected = listOf(testAuditLogEntry(id = 1))
            every {
                auditLogStore.findAll("admin-1", "APPROVE", "SUBSCRIPTION", from, to, 5, 10)
            } returns expected

            val result = service.findAll(
                actorId = "admin-1",
                action = "APPROVE",
                targetType = "SUBSCRIPTION",
                from = from,
                to = to,
                offset = 5,
                limit = 10
            )

            result shouldBe expected
            verify(exactly = 1) {
                auditLogStore.findAll("admin-1", "APPROVE", "SUBSCRIPTION", from, to, 5, 10)
            }
        }

        @Test
        fun `필터가 null이면 null을 그대로 전달한다`() {
            every {
                auditLogStore.findAll(null, null, null, null, null, 0, 30)
            } returns emptyList()

            val result = service.findAll()

            result shouldBe emptyList()
            verify(exactly = 1) {
                auditLogStore.findAll(null, null, null, null, null, 0, 30)
            }
        }

        @Test
        fun `offset과 limit 기본값이 0과 30이다`() {
            val from = Instant.parse("2026-03-10T00:00:00Z")
            every {
                auditLogStore.findAll("admin-2", null, null, from, null, 0, 30)
            } returns emptyList()

            val result = service.findAll(actorId = "admin-2", from = from)

            result shouldBe emptyList()
            verify(exactly = 1) {
                auditLogStore.findAll("admin-2", null, null, from, null, 0, 30)
            }
        }
    }

    @Nested
    inner class `countAll 메서드` {

        @Test
        fun `모든 필터 파라미터를 Store에 그대로 위임한다`() {
            val from = Instant.parse("2026-03-01T00:00:00Z")
            val to = Instant.parse("2026-03-15T23:59:59Z")
            every {
                auditLogStore.countAll("admin-1", "REJECT", "SOURCE", from, to)
            } returns 7

            val result = service.countAll(
                actorId = "admin-1",
                action = "REJECT",
                targetType = "SOURCE",
                from = from,
                to = to
            )

            result shouldBe 7
            verify(exactly = 1) {
                auditLogStore.countAll("admin-1", "REJECT", "SOURCE", from, to)
            }
        }

        @Test
        fun `필터가 null이면 null을 그대로 전달한다`() {
            every {
                auditLogStore.countAll(null, null, null, null, null)
            } returns 100

            val result = service.countAll()

            result shouldBe 100
            verify(exactly = 1) {
                auditLogStore.countAll(null, null, null, null, null)
            }
        }
    }

    @Nested
    inner class `getDistinctActions 메서드` {

        @Test
        fun `Store의 결과를 그대로 반환한다`() {
            val expected = listOf("APPROVE", "REJECT", "DELETE")
            every { auditLogStore.getDistinctActions() } returns expected

            val result = service.getDistinctActions()

            result shouldBe expected
            verify(exactly = 1) { auditLogStore.getDistinctActions() }
        }

        @Test
        fun `결과가 비어 있으면 빈 리스트를 반환한다`() {
            every { auditLogStore.getDistinctActions() } returns emptyList()

            val result = service.getDistinctActions()

            result shouldBe emptyList()
            verify(exactly = 1) { auditLogStore.getDistinctActions() }
        }
    }

    @Nested
    inner class `getDistinctTargetTypes 메서드` {

        @Test
        fun `Store의 결과를 그대로 반환한다`() {
            val expected = listOf("SUBSCRIPTION", "SOURCE", "CATEGORY")
            every { auditLogStore.getDistinctTargetTypes() } returns expected

            val result = service.getDistinctTargetTypes()

            result shouldBe expected
            verify(exactly = 1) { auditLogStore.getDistinctTargetTypes() }
        }

        @Test
        fun `결과가 비어 있으면 빈 리스트를 반환한다`() {
            every { auditLogStore.getDistinctTargetTypes() } returns emptyList()

            val result = service.getDistinctTargetTypes()

            result shouldBe emptyList()
            verify(exactly = 1) { auditLogStore.getDistinctTargetTypes() }
        }
    }

    private fun testAuditLogEntry(id: Long): AuditLogEntry =
        AuditLogEntry(
            id = id,
            actorId = "admin-1",
            actorName = "관리자",
            action = "APPROVE",
            targetType = "SUBSCRIPTION",
            targetId = "sub-1",
            targetName = "테스트 구독",
            detail = "승인 처리",
            createdAt = Instant.now()
        )
}
