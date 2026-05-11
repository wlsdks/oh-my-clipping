package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.MeResponse
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.service.AdminAuthService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 현재 로그인 사용자 정보 조회 API.
 * 다른 모든 컨트롤러와 일관되게 동기 방식으로 응답한다.
 */
@RestController
@RequestMapping("/api")
class MeController(private val adminAuthService: AdminAuthService) {
    @GetMapping("/me")
    fun me(authentication: Authentication): MeResponse {
        // 인증된 사용자명으로 사용자 정보를 조회한다.
        val user = adminAuthService.findByUsername(authentication.name)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다")
        return MeResponse(
            id = user.id,
            username = user.username,
            displayName = user.displayName,
            role = user.role.name,
            approvalStatus = user.approvalStatus.name,
            hasSlackDm = !user.slackMemberId.isNullOrBlank(),
            mustChangePassword = user.mustChangePassword,
            department = user.department,
            team = user.team
        )
    }
}
