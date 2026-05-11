package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.CreateDepartmentRequest
import com.ohmyclipping.admin.dto.CreateTeamRequest
import com.ohmyclipping.admin.dto.SetActiveRequest
import com.ohmyclipping.admin.dto.UpdateDepartmentRequest
import com.ohmyclipping.admin.dto.UpdateTeamRequest
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.model.Department
import com.ohmyclipping.model.DepartmentTree
import com.ohmyclipping.model.Team
import com.ohmyclipping.service.DepartmentTreeService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication

/**
 * [DepartmentAdminController] 의 HTTP 입출력 매핑과 감사 경로 위임을 검증한다.
 *
 * 실제 audit log 기록은 [DepartmentTreeService] 가 수행하므로 여기서는
 * principal 이 올바르게 서비스로 전달되는지만 확인한다.
 */
class DepartmentAdminControllerTest {

    private val service = mockk<DepartmentTreeService>()
    private val controller = DepartmentAdminController(service)

    private val auth = mockk<Authentication>().apply { every { name } returns "admin" }

    private fun dept(id: String = "d1", isActive: Boolean = true): Department =
        Department(id = id, name = "영업팀", nameNormalized = "영업팀", isActive = isActive)

    private fun team(id: String = "t1", departmentId: String = "d1", isActive: Boolean = true): Team =
        Team(id = id, departmentId = departmentId, name = "퍼포먼스", nameNormalized = "퍼포먼스", isActive = isActive)

    @Nested
    inner class `GET tree` {
        @Test
        fun `비활성 포함 전체 트리를 응답으로 노출한다`() {
            every { service.getAdminTree() } returns listOf(DepartmentTree(dept(), listOf(team())))

            val response = controller.getAdminTree()

            response.totalCount shouldBe 1
            response.content[0].department.id shouldBe "d1"
            response.content[0].teams.single().id shouldBe "t1"
        }
    }

    @Nested
    inner class `부서 mutation 위임` {
        @Test
        fun `createDepartment 는 principal 을 서비스로 전달한다`() {
            every {
                service.createDepartment(name = "영업팀", displayOrder = 0, actorPrincipal = "admin")
            } returns dept()

            val response = controller.createDepartment(auth, CreateDepartmentRequest(name = "영업팀"))

            response.id shouldBe "d1"
            verify(exactly = 1) {
                service.createDepartment(name = "영업팀", displayOrder = 0, actorPrincipal = "admin")
            }
        }

        @Test
        fun `updateDepartment 는 변경 필드와 principal 을 서비스로 전달한다`() {
            every {
                service.updateDepartment(
                    id = "d1",
                    name = "마케팅",
                    displayOrder = 7,
                    actorPrincipal = "admin"
                )
            } returns dept().copy(name = "마케팅", displayOrder = 7)

            val response = controller.updateDepartment(
                auth,
                "d1",
                UpdateDepartmentRequest(name = "마케팅", displayOrder = 7)
            )

            response.name shouldBe "마케팅"
            response.displayOrder shouldBe 7
        }

        @Test
        fun `PATCH active false 는 isActive 만 전달해 DEACTIVATED 감사 로그를 서비스에 위임한다`() {
            every {
                service.updateDepartment(id = "d1", isActive = false, actorPrincipal = "admin")
            } returns dept(isActive = false)

            controller.setDepartmentActive(auth, "d1", SetActiveRequest(isActive = false))

            verify(exactly = 1) {
                service.updateDepartment(id = "d1", isActive = false, actorPrincipal = "admin")
            }
        }

        @Test
        fun `서비스가 ConflictException 을 던지면 그대로 전파된다`() {
            every {
                service.createDepartment(name = "영업팀", displayOrder = 0, actorPrincipal = "admin")
            } throws ConflictException("같은 이름의 부서가 이미 존재합니다: 영업팀")

            shouldThrow<ConflictException> {
                controller.createDepartment(auth, CreateDepartmentRequest(name = "영업팀"))
            }
        }
    }

    @Nested
    inner class `팀 mutation 위임` {
        @Test
        fun `createTeam 은 경로 부서 id 를 서비스에 전달한다`() {
            every {
                service.createTeam(
                    departmentId = "d1",
                    name = "퍼포먼스",
                    displayOrder = 0,
                    actorPrincipal = "admin"
                )
            } returns team()

            val response = controller.createTeam(auth, "d1", CreateTeamRequest(name = "퍼포먼스"))

            response.departmentId shouldBe "d1"
        }

        @Test
        fun `updateTeam 은 부서 이동 파라미터를 newDepartmentId 로 전달한다`() {
            every {
                service.updateTeam(
                    id = "t1",
                    name = null,
                    displayOrder = null,
                    newDepartmentId = "d2",
                    actorPrincipal = "admin"
                )
            } returns team(departmentId = "d2")

            val response = controller.updateTeam(auth, "t1", UpdateTeamRequest(departmentId = "d2"))

            response.departmentId shouldBe "d2"
        }
    }
}
