package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.SignupAvailabilityResponse
import com.ohmyclipping.admin.dto.UsernameAvailabilityResponse
import com.ohmyclipping.admin.dto.UserSignupRequest
import com.ohmyclipping.admin.dto.UserSignupResponse
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.service.AdminAuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 인증 화면에서 사용하는 공개 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/public/user/auth")
class UserAuthPublicController(
    private val adminAuthService: AdminAuthService
) {

    @GetMapping("/signup-availability")
    fun signupAvailability(): SignupAvailabilityResponse {
        val availability = adminAuthService.signupAvailability(AccountRole.USER)
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

    /**
     * JSON API 회원가입 — SPA에서 호출.
     * form redirect 방식 대신 JSON 요청/응답으로 처리한다.
     *
     * V129 이후 [UserSignupRequest.departmentId] 는 필수, [UserSignupRequest.teamId] 는 선택.
     * 서비스 레이어가 FK 해석 + legacy 이름 캐시 동기화를 수행한다.
     */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@RequestBody request: UserSignupRequest): UserSignupResponse {
        val user = adminAuthService.registerUser(
            username = request.email,
            displayName = request.displayName,
            departmentId = request.departmentId,
            teamId = request.teamId,
            rawPassword = request.password,
            slackDmChannelId = null
        )
        return UserSignupResponse(
            id = user.id,
            username = user.username,
            message = "가입 신청이 완료됐어요. 관리자 승인 후 로그인할 수 있어요."
        )
    }

    private fun signupReasonText(reason: String): String = when (reason) {
        "user_signup_enabled" -> "계정이 없으면 아래에서 가입할 수 있어요. 가입 후 관리자 승인이 필요해요."
        else -> "회원가입이 닫혀있어요. 관리자에게 계정을 요청하세요."
    }
}
