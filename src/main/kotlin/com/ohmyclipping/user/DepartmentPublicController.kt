package com.ohmyclipping.user

import com.ohmyclipping.service.DepartmentTreeService
import com.ohmyclipping.user.dto.PublicDepartmentResponse
import com.ohmyclipping.user.dto.PublicDepartmentTreeResponse
import com.ohmyclipping.user.dto.PublicTeamResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 공개(익명 접근 가능) 부서/팀 트리 API.
 *
 * 경로: `/api/public/departments/tree`.
 * SecurityConfig 의 공개 레인(permitAll prefix) 을 그대로 사용한다.
 * 응답에는 활성 부서 + 하위 활성 팀만 담기며 display_order, is_active, timestamps 등
 * 내부 운영 필드는 노출하지 않는다.
 *
 * Rate limit 주의: Round-3 스펙 §2.5 B 안에 따라 Day 1 은 무제한 상태로 배포한다.
 * 내부 ngrok 환경 한정이라 recon 리스크는 낮지만, 공개 배포 전에 반드시 옵션 A 로 업그레이드할 것
 * (RateLimitFilter 에 `/api/public/departments/tree` 분당 60회 IP 기반 리밋 추가).
 */
@RestController
@RequestMapping("/api/public/departments")
class DepartmentPublicController(
    private val departmentTreeService: DepartmentTreeService
) {

    /** 활성 부서+하위 활성 팀 트리. signup 과 ProfileEditModal 공용. */
    @GetMapping("/tree")
    fun getActiveTree(): PublicDepartmentTreeResponse {
        val trees = departmentTreeService.getActiveTree()
        return PublicDepartmentTreeResponse(
            departments = trees.map { tree ->
                PublicDepartmentResponse(
                    id = tree.department.id,
                    name = tree.department.name,
                    teams = tree.teams.map { PublicTeamResponse(id = it.id, name = it.name) }
                )
            }
        )
    }
}
