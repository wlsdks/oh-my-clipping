package com.ohmyclipping.user

import com.ohmyclipping.model.Department
import com.ohmyclipping.model.DepartmentTree
import com.ohmyclipping.model.Team
import com.ohmyclipping.service.DepartmentTreeService
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [DepartmentPublicController] — 공개 엔드포인트가 활성 항목만 노출하고
 * DTO 가 최소 필드(id/name) 로 구성되는지 검증한다.
 */
class DepartmentPublicControllerTest {

    private val service = mockk<DepartmentTreeService>()
    private val controller = DepartmentPublicController(service)

    @Nested
    inner class `GET tree` {
        @Test
        fun `서비스의 getActiveTree 결과만 사용한다`() {
            every { service.getActiveTree() } returns listOf(
                DepartmentTree(
                    department = Department(id = "d1", name = "영업팀", nameNormalized = "영업팀"),
                    teams = listOf(
                        Team(id = "t1", departmentId = "d1", name = "퍼포먼스", nameNormalized = "퍼포먼스")
                    )
                )
            )

            val response = controller.getActiveTree()

            response.departments.size shouldBe 1
            response.departments[0].id shouldBe "d1"
            response.departments[0].name shouldBe "영업팀"
            response.departments[0].teams.map { it.id } shouldContainExactly listOf("t1")
            // 비활성 필터링은 서비스 책임이라 공개 컨트롤러는 getActiveTree() 만 호출해야 한다.
            verify(exactly = 1) { service.getActiveTree() }
            verify(exactly = 0) { service.getAdminTree() }
        }

        @Test
        fun `응답 DTO 는 display_order 나 is_active 같은 내부 필드를 노출하지 않는다`() {
            every { service.getActiveTree() } returns listOf(
                DepartmentTree(
                    department = Department(
                        id = "d1",
                        name = "영업팀",
                        nameNormalized = "영업팀",
                        displayOrder = 9,
                        isActive = true
                    ),
                    teams = listOf(
                        Team(
                            id = "t1",
                            departmentId = "d1",
                            name = "퍼포먼스",
                            nameNormalized = "퍼포먼스",
                            displayOrder = 3,
                            isActive = true
                        )
                    )
                )
            )

            val response = controller.getActiveTree()

            // PublicDepartmentResponse 는 id/name/teams 만 가지고 있으므로 reflection 없이 필드 접근으로 검증.
            val dept = response.departments.single()
            dept.id shouldBe "d1"
            dept.name shouldBe "영업팀"
            dept.teams.single().id shouldBe "t1"
            dept.teams.single().name shouldBe "퍼포먼스"
            // 컴파일러가 display_order 나 is_active 필드 접근을 허용하지 않으면 스키마가 확정됐다는 뜻이다.
        }

        @Test
        fun `빈 트리도 정상 응답한다`() {
            every { service.getActiveTree() } returns emptyList()

            val response = controller.getActiveTree()

            response.departments shouldBe emptyList()
        }
    }
}
