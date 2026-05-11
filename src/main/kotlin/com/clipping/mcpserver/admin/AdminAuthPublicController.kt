package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.SignupAvailabilityResponse
import com.clipping.mcpserver.admin.dto.UsernameAvailabilityResponse
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.service.AdminAuthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 인증 화면에서 사용하는 공개 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/public/admin/auth")
class AdminAuthPublicController(
    private val adminAuthService: AdminAuthService
) {

    /**
     * 회원가입 가능 여부와 운영자 안내 메시지를 반환합니다.
     */
    @GetMapping("/signup-availability")
    fun signupAvailability(): SignupAvailabilityResponse {
        val availability = adminAuthService.signupAvailability(AccountRole.ADMIN)
        return SignupAvailabilityResponse(
            allowed = availability.allowed,
            reason = availability.reason,
            message = signupReasonText(availability.reason)
        )
    }

    /**
     * 아이디 중복 여부를 확인합니다.
     */
    @GetMapping("/check-username")
    fun checkUsername(@RequestParam username: String): UsernameAvailabilityResponse {
        return UsernameAvailabilityResponse(
            available = adminAuthService.isUsernameAvailable(username)
        )
    }

    private fun signupReasonText(reason: String): String = when (reason) {
        "first_admin_bootstrap" -> "첫 번째 관리자 계정을 만들 수 있어요."
        "admin_signup_enabled" -> "계정이 없으면 아래에서 새로 만들 수 있어요."
        else -> "회원가입이 닫혀있어요. 기존 관리자에게 계정을 요청하세요."
    }
}
