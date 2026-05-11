package com.ohmyclipping.user

import com.ohmyclipping.service.source.UserSourceValidationService
import com.ohmyclipping.user.dto.ExistingSourceResponse
import com.ohmyclipping.user.dto.ValidateUrlRequest
import com.ohmyclipping.user.dto.ValidateUrlResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 유저 RSS 제출 시 URL 사전 검증 API를 제공한다.
 * 도메인 차단 여부, 기존 소스 정보, RSS/robots.txt 검증 결과를 반환한다.
 */
@RestController
@RequestMapping("/api/user/sources")
class UserSourceValidationController(
    private val validationService: UserSourceValidationService
) {
    /**
     * URL에 대해 5단계 사전 검증을 수행한다.
     * 인증된 사용자만 호출할 수 있다.
     */
    @PostMapping("/validate-url")
    fun validateUrl(
        authentication: Authentication,
        @RequestBody request: ValidateUrlRequest
    ): ValidateUrlResponse {
        val result = validationService.validate(request.url)
        return ValidateUrlResponse(
            rssValid = result.rssValid,
            robotsAllowed = result.robotsAllowed,
            domainBlocked = result.domainBlocked,
            blockReason = result.blockReason,
            existingSource = result.existingSource?.let {
                ExistingSourceResponse(name = it.name, legalBasis = it.legalBasis)
            }
        )
    }
}
