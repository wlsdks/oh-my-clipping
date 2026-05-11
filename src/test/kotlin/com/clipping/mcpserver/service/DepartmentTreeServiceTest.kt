package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.Department
import com.clipping.mcpserver.model.Team
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.DepartmentStore
import com.clipping.mcpserver.store.TeamStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [DepartmentTreeService] 단위 테스트.
 *
 * CRUD, 정규화 기반 중복 차단, 팀 부서 이동 재검증, soft-delete, FK 일관성 해석을 커버한다.
 */
class DepartmentTreeServiceTest {

    private val departmentStore = mockk<DepartmentStore>(relaxed = true)
    private val teamStore = mockk<TeamStore>(relaxed = true)
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val auditActorResolver = mockk<AuditActorResolver>(relaxed = true)
    private val adminUserStore = mockk<AdminUserStore>(relaxed = true)
    private val service = DepartmentTreeService(departmentStore, teamStore, auditLogStore, auditActorResolver, adminUserStore)

    private fun dept(
        id: String,
        name: String = "부서$id",
        normalized: String = name,
        isActive: Boolean = true
    ) = Department(id = id, name = name, nameNormalized = normalized, isActive = isActive)

    private fun team(
        id: String,
        departmentId: String,
        name: String = "팀$id",
        normalized: String = name,
        isActive: Boolean = true
    ) = Team(id = id, departmentId = departmentId, name = name, nameNormalized = normalized, isActive = isActive)

    @Nested
    inner class `부서 CRUD` {
        @Test
        fun `createDepartment 는 이름을 정규화해 저장한다`() {
            every { departmentStore.findByNameNormalized("영업팀") } returns null
            val slotDept = slot<Department>()
            every { departmentStore.save(capture(slotDept)) } answers { slotDept.captured.copy(id = "dept-1") }

            val created = service.createDepartment("  영업팀  ")

            // trim 만 반영한 표시명 + lowercase 정규화가 적용된 name_normalized 를 검증한다.
            slotDept.captured.name shouldBe "영업팀"
            slotDept.captured.nameNormalized shouldBe "영업팀"
            created.id shouldBe "dept-1"
        }

        @Test
        fun `createDepartment 는 공백만 있는 이름을 거부한다`() {
            shouldThrow<InvalidInputException> {
                service.createDepartment("   ")
            }
        }

        @Test
        fun `createDepartment 는 정규화 결과가 같은 부서가 있으면 ConflictException`() {
            every { departmentStore.findByNameNormalized("영업팀") } returns dept("dept-existing")

            shouldThrow<ConflictException> {
                service.createDepartment("영업 팀")
            }
        }

        @Test
        fun `updateDepartment 는 자기 자신과의 이름 충돌을 허용한다`() {
            val current = dept(id = "dept-1", name = "영업팀", normalized = "영업팀")
            every { departmentStore.findById("dept-1") } returns current
            every { departmentStore.findByNameNormalized("영업팀") } returns current
            every { departmentStore.update(any()) } answers { firstArg() }

            // 동일한 이름으로 업데이트해도 자기 자신이므로 통과한다.
            val result = service.updateDepartment(id = "dept-1", name = "영업팀", displayOrder = 5)
            result.displayOrder shouldBe 5
        }

        @Test
        fun `updateDepartment 는 타 부서와 이름이 겹치면 ConflictException`() {
            every { departmentStore.findById("dept-1") } returns dept("dept-1", "영업팀")
            every { departmentStore.findByNameNormalized("마케팅") } returns dept("dept-2", "마케팅")

            shouldThrow<ConflictException> {
                service.updateDepartment(id = "dept-1", name = "마케팅")
            }
        }

        @Test
        fun `softDeleteDepartment 는 isActive false 로 업데이트한다`() {
            every { departmentStore.findById("dept-1") } returns dept("dept-1")
            val slotDept = slot<Department>()
            every { departmentStore.update(capture(slotDept)) } answers { slotDept.captured }

            service.softDeleteDepartment("dept-1")

            slotDept.captured.isActive shouldBe false
        }
    }

    @Nested
    inner class `팀 CRUD 와 부서 이동` {
        @Test
        fun `createTeam 은 부서 존재와 활성 여부를 먼저 확인한다`() {
            every { departmentStore.findById("dept-1") } returns dept("dept-1", isActive = true)
            every { teamStore.findByDepartmentIdAndNameNormalized("dept-1", "퍼포먼스") } returns null
            val slotTeam = slot<Team>()
            every { teamStore.save(capture(slotTeam)) } answers { slotTeam.captured.copy(id = "team-1") }

            val created = service.createTeam(departmentId = "dept-1", name = "퍼포먼스")

            slotTeam.captured.departmentId shouldBe "dept-1"
            slotTeam.captured.nameNormalized shouldBe "퍼포먼스"
            created.id shouldBe "team-1"
        }

        @Test
        fun `createTeam 은 비활성 부서에는 생성을 거부한다`() {
            every { departmentStore.findById("dept-1") } returns dept("dept-1", isActive = false)

            shouldThrow<InvalidInputException> {
                service.createTeam(departmentId = "dept-1", name = "새팀")
            }
        }

        @Test
        fun `createTeam 은 같은 부서 내 정규화 이름 충돌을 차단한다`() {
            every { departmentStore.findById("dept-1") } returns dept("dept-1")
            every { teamStore.findByDepartmentIdAndNameNormalized("dept-1", "퍼포먼스") } returns
                team("team-existing", "dept-1", "퍼포먼스")

            shouldThrow<ConflictException> {
                service.createTeam(departmentId = "dept-1", name = "퍼포먼스")
            }
        }

        @Test
        fun `updateTeam 은 부서 이동 시 이동 대상 부서에서 UNIQUE 를 재검증한다`() {
            val current = team("team-1", "dept-source", "퍼포먼스", "퍼포먼스")
            every { teamStore.findById("team-1") } returns current
            every { departmentStore.findById("dept-target") } returns dept("dept-target")
            // 이동 대상 부서에 이미 "퍼포먼스" 팀이 있으면 충돌.
            every { teamStore.findByDepartmentIdAndNameNormalized("dept-target", "퍼포먼스") } returns
                team("team-other", "dept-target", "퍼포먼스")

            shouldThrow<ConflictException> {
                service.updateTeam(id = "team-1", newDepartmentId = "dept-target")
            }
        }

        @Test
        fun `updateTeam 은 부서 이동과 이름 변경을 동시에 반영한다`() {
            val current = team("team-1", "dept-source", "퍼포먼스", "퍼포먼스")
            every { teamStore.findById("team-1") } returns current
            every { departmentStore.findById("dept-target") } returns dept("dept-target")
            every { teamStore.findByDepartmentIdAndNameNormalized("dept-target", "성과팀") } returns null
            val slotTeam = slot<Team>()
            every { teamStore.update(capture(slotTeam)) } answers { slotTeam.captured }

            service.updateTeam(id = "team-1", name = "성과팀", newDepartmentId = "dept-target")

            slotTeam.captured.departmentId shouldBe "dept-target"
            slotTeam.captured.name shouldBe "성과팀"
        }

        @Test
        fun `updateTeam 은 존재하지 않는 팀에 대해 NotFoundException`() {
            every { teamStore.findById("missing") } returns null
            shouldThrow<NotFoundException> {
                service.updateTeam(id = "missing", name = "x")
            }
        }
    }

    @Nested
    inner class `FK 일관성 해석` {
        @Test
        fun `resolveUserAssignment 는 team_departmentId 가 일치하면 통과한다`() {
            every { departmentStore.findById("dept-1") } returns dept("dept-1")
            every { teamStore.findById("team-1") } returns team("team-1", "dept-1")

            val (d, t) = service.resolveUserAssignment("dept-1", "team-1")

            d?.id shouldBe "dept-1"
            t?.id shouldBe "team-1"
        }

        @Test
        fun `resolveUserAssignment 는 team 이 다른 부서 소속이면 ConflictException`() {
            every { departmentStore.findById("dept-1") } returns dept("dept-1")
            every { teamStore.findById("team-1") } returns team("team-1", "dept-other")

            shouldThrow<ConflictException> {
                service.resolveUserAssignment("dept-1", "team-1")
            }
        }

        @Test
        fun `resolveUserAssignment 는 부서 없이 팀만 지정하면 InvalidInputException`() {
            shouldThrow<InvalidInputException> {
                service.resolveUserAssignment(departmentId = null, teamId = "team-1")
            }
        }

        @Test
        fun `resolveUserAssignment 는 둘 다 null 이면 null 쌍을 반환한다`() {
            val result = service.resolveUserAssignment(null, null)
            result.first shouldBe null
            result.second shouldBe null
        }
    }

    @Nested
    inner class `트리 집계` {
        @Test
        fun `getActiveTree 는 활성 부서와 활성 팀만 포함한다`() {
            val d1 = dept("d1")
            val d2 = dept("d2")
            val t1 = team("t1", "d1")
            val t2 = team("t2", "d1")
            val t3 = team("t3", "d2")
            every { departmentStore.findAllActive() } returns listOf(d1, d2)
            every { teamStore.findAllActive() } returns listOf(t1, t2, t3)

            val tree = service.getActiveTree()

            tree.size shouldBe 2
            tree[0].department.id shouldBe "d1"
            tree[0].teams.map { it.id } shouldBe listOf("t1", "t2")
            tree[1].department.id shouldBe "d2"
            tree[1].teams.map { it.id } shouldBe listOf("t3")
            verify(exactly = 1) { teamStore.findAllActive() }
        }

        @Test
        fun `getAdminTree 는 비활성 포함 전체 트리를 반환한다`() {
            every { departmentStore.findAll() } returns listOf(dept("d1", isActive = false))
            every { teamStore.findAll() } returns listOf(team("t1", "d1", isActive = false))

            val tree = service.getAdminTree()

            tree.size shouldBe 1
            tree[0].department.isActive shouldBe false
            tree[0].teams.single().isActive shouldBe false
        }
    }

    @Nested
    inner class `hard delete 가드` {
        @Test
        fun `부서가 활성 상태면 삭제가 거부된다`() {
            every { departmentStore.findById("d1") } returns dept("d1", isActive = true)

            val ex = shouldThrow<ConflictException> {
                service.deleteDepartment("d1")
            }
            ex.message shouldContain "먼저 비활성화"

            verify(exactly = 0) { departmentStore.deleteById(any()) }
        }

        @Test
        fun `하위 팀이 남아 있으면 부서 삭제가 거부된다`() {
            every { departmentStore.findById("d1") } returns dept("d1", isActive = false)
            every { teamStore.findAllByDepartmentId("d1") } returns listOf(team("t1", "d1"))

            shouldThrow<ConflictException> { service.deleteDepartment("d1") }
            verify(exactly = 0) { departmentStore.deleteById(any()) }
        }

        @Test
        fun `참조 사용자가 있으면 부서 삭제가 거부된다`() {
            every { departmentStore.findById("d1") } returns dept("d1", isActive = false)
            every { teamStore.findAllByDepartmentId("d1") } returns emptyList()
            every { adminUserStore.countByDepartmentId("d1") } returns 2

            shouldThrow<ConflictException> { service.deleteDepartment("d1") }
            verify(exactly = 0) { departmentStore.deleteById(any()) }
        }

        @Test
        fun `모든 가드 통과시 부서가 물리 삭제되고 감사 로그가 기록된다`() {
            every { departmentStore.findById("d1") } returns dept("d1", isActive = false)
            every { teamStore.findAllByDepartmentId("d1") } returns emptyList()
            every { adminUserStore.countByDepartmentId("d1") } returns 0
            every { auditActorResolver.resolve(any()) } returns ResolvedActor(id = "admin-id", name = "admin")

            service.deleteDepartment("d1", actorPrincipal = "admin")

            verify(exactly = 1) { departmentStore.deleteById("d1") }
            verify(exactly = 1) { auditLogStore.log(any(), any(), "DEPARTMENT_DELETED", any(), any(), any(), any()) }
        }

        @Test
        fun `활성 팀은 삭제가 거부된다`() {
            every { teamStore.findById("t1") } returns team("t1", "d1", isActive = true)

            shouldThrow<ConflictException> { service.deleteTeam("t1") }
            verify(exactly = 0) { teamStore.deleteById(any()) }
        }

        @Test
        fun `팀 참조 사용자가 있으면 삭제가 거부된다`() {
            every { teamStore.findById("t1") } returns team("t1", "d1", isActive = false)
            every { adminUserStore.countByTeamId("t1") } returns 1

            shouldThrow<ConflictException> { service.deleteTeam("t1") }
            verify(exactly = 0) { teamStore.deleteById(any()) }
        }

        @Test
        fun `모든 가드 통과시 팀이 물리 삭제되고 감사 로그가 기록된다`() {
            every { teamStore.findById("t1") } returns team("t1", "d1", isActive = false)
            every { adminUserStore.countByTeamId("t1") } returns 0
            every { auditActorResolver.resolve(any()) } returns ResolvedActor(id = "admin-id", name = "admin")

            service.deleteTeam("t1", actorPrincipal = "admin")

            verify(exactly = 1) { teamStore.deleteById("t1") }
            verify(exactly = 1) { auditLogStore.log(any(), any(), "TEAM_DELETED", any(), any(), any(), any()) }
        }
    }
}
