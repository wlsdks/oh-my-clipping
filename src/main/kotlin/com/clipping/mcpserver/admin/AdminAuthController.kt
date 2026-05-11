package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.SignupForm
import com.clipping.mcpserver.service.AdminAuthService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import java.net.URI

@Controller
class AdminAuthController(
    private val adminAuthService: AdminAuthService
) {
    /**
     * 관리자 회원가입 요청을 처리합니다.
     *
     * 기존 리다이렉트 기반 인증 플로우를 유지하여
     * 프론트엔드(SPA)와 스프링 시큐리티 세션 인증이 함께 동작하도록 구성합니다.
     */
    @PostMapping("/admin/signup")
    fun signup(
        @ModelAttribute form: SignupForm
    ): ResponseEntity<Void> {
        if (form.password != form.confirmPassword) {
            return redirect("/admin/signup?error=password_mismatch")
        }

        adminAuthService.registerAdmin(
            username = form.username,
            displayName = form.displayName,
            rawPassword = form.password
        )
        return redirect("/admin/login?registered=1")
    }

    @PostMapping("/user/signup")
    fun userSignup(
        @ModelAttribute form: SignupForm
    ): ResponseEntity<Void> {
        if (form.password != form.confirmPassword) {
            return redirect("/user/signup?error=password_mismatch")
        }

        adminAuthService.registerUser(
            username = form.username,
            displayName = form.displayName,
            departmentId = form.departmentId,
            teamId = form.teamId,
            rawPassword = form.password,
            slackDmChannelId = form.slackDmChannelId
        )
        return redirect("/user/login?pending_approval=1")
    }

    private fun redirect(path: String): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(path))
            .build()
}
