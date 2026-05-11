package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.CreateDepartmentRequest
import com.ohmyclipping.admin.dto.CreateTeamRequest
import com.ohmyclipping.admin.dto.DepartmentAdminResponse
import com.ohmyclipping.admin.dto.DepartmentTreeAdminNode
import com.ohmyclipping.admin.dto.DepartmentTreeAdminResponse
import com.ohmyclipping.admin.dto.SetActiveRequest
import com.ohmyclipping.admin.dto.TeamAdminResponse
import com.ohmyclipping.admin.dto.UpdateDepartmentRequest
import com.ohmyclipping.admin.dto.UpdateTeamRequest
import com.ohmyclipping.model.Department
import com.ohmyclipping.model.Team
import com.ohmyclipping.service.DepartmentTreeService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 전용 부서/팀 CRUD API.
 *
 * 경로:
 * - `/api/admin/departments/tree` — 비활성 포함 전체 트리
 * - `/api/admin/departments` — 부서 생성
 * - `/api/admin/departments/{id}` — 부서 수정
 * - `/api/admin/departments/{id}/active` — 부서 활성/비활성 토글
 * - `/api/admin/departments/{id}/teams` — 팀 생성
 * - `/api/admin/teams/{id}` — 팀 수정 (부서 이동 포함)
 * - `/api/admin/teams/{id}/active` — 팀 활성/비활성 토글
 *
 * 감사 로그는 서비스 레이어 ([DepartmentTreeService]) 가 principal 을 인자로 받아 직접 기록한다.
 * 이는 admin 레이어가 store 에 직접 의존하지 못하도록 하는 레이어 규칙([com.ohmyclipping
 * .architecture.LayerDependencyRulesTest]) 을 만족한다.
 */
@RestController
@RequestMapping("/api/admin")
class DepartmentAdminController(
    private val departmentTreeService: DepartmentTreeService
) {

    /** 비활성 포함 전체 부서+팀 트리. Admin UI 용. */
    @GetMapping("/departments/tree")
    fun getAdminTree(): DepartmentTreeAdminResponse {
        val nodes = departmentTreeService.getAdminTree().map { tree ->
            DepartmentTreeAdminNode(
                department = tree.department.toAdminResponse(),
                teams = tree.teams.map { it.toAdminResponse() }
            )
        }
        return DepartmentTreeAdminResponse(content = nodes, totalCount = nodes.size)
    }

    /** 부서 신규 생성. */
    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    fun createDepartment(
        authentication: Authentication,
        @RequestBody request: CreateDepartmentRequest
    ): DepartmentAdminResponse = departmentTreeService.createDepartment(
        name = request.name,
        displayOrder = request.displayOrder ?: 0,
        actorPrincipal = authentication.name
    ).toAdminResponse()

    /** 부서 수정. null 필드는 변경 없음. */
    @PutMapping("/departments/{id}")
    fun updateDepartment(
        authentication: Authentication,
        @PathVariable id: String,
        @RequestBody request: UpdateDepartmentRequest
    ): DepartmentAdminResponse = departmentTreeService.updateDepartment(
        id = id,
        name = request.name,
        displayOrder = request.displayOrder,
        actorPrincipal = authentication.name
    ).toAdminResponse()

    /** 부서 활성/비활성 토글 (soft-delete 포함). */
    @PatchMapping("/departments/{id}/active")
    fun setDepartmentActive(
        authentication: Authentication,
        @PathVariable id: String,
        @RequestBody request: SetActiveRequest
    ): DepartmentAdminResponse = departmentTreeService.updateDepartment(
        id = id,
        isActive = request.isActive,
        actorPrincipal = authentication.name
    ).toAdminResponse()

    /** 팀 신규 생성. 부서 id 를 경로로 받는다. */
    @PostMapping("/departments/{departmentId}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTeam(
        authentication: Authentication,
        @PathVariable departmentId: String,
        @RequestBody request: CreateTeamRequest
    ): TeamAdminResponse = departmentTreeService.createTeam(
        departmentId = departmentId,
        name = request.name,
        displayOrder = request.displayOrder ?: 0,
        actorPrincipal = authentication.name
    ).toAdminResponse()

    /** 팀 수정 — departmentId 를 바꾸면 부서 간 이동으로 처리된다. */
    @PutMapping("/teams/{id}")
    fun updateTeam(
        authentication: Authentication,
        @PathVariable id: String,
        @RequestBody request: UpdateTeamRequest
    ): TeamAdminResponse = departmentTreeService.updateTeam(
        id = id,
        name = request.name,
        displayOrder = request.displayOrder,
        newDepartmentId = request.departmentId,
        actorPrincipal = authentication.name
    ).toAdminResponse()

    /** 팀 활성/비활성 토글. */
    @PatchMapping("/teams/{id}/active")
    fun setTeamActive(
        authentication: Authentication,
        @PathVariable id: String,
        @RequestBody request: SetActiveRequest
    ): TeamAdminResponse = departmentTreeService.updateTeam(
        id = id,
        isActive = request.isActive,
        actorPrincipal = authentication.name
    ).toAdminResponse()

    /**
     * 부서 물리 삭제. 활성·하위 팀·참조 사용자 있으면 409.
     * 204 No Content 로 응답한다 — 반환 body 없음.
     */
    @DeleteMapping("/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDepartment(
        authentication: Authentication,
        @PathVariable id: String
    ) {
        departmentTreeService.deleteDepartment(id = id, actorPrincipal = authentication.name)
    }

    /**
     * 팀 물리 삭제. 활성·참조 사용자 있으면 409.
     */
    @DeleteMapping("/teams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTeam(
        authentication: Authentication,
        @PathVariable id: String
    ) {
        departmentTreeService.deleteTeam(id = id, actorPrincipal = authentication.name)
    }

    private fun Department.toAdminResponse(): DepartmentAdminResponse = DepartmentAdminResponse(
        id = id,
        name = name,
        displayOrder = displayOrder,
        isActive = isActive,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )

    private fun Team.toAdminResponse(): TeamAdminResponse = TeamAdminResponse(
        id = id,
        departmentId = departmentId,
        name = name,
        displayOrder = displayOrder,
        isActive = isActive,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}
