package com.ohmyclipping.user

import com.ohmyclipping.service.UserClippingRequestService
import com.ohmyclipping.service.dto.SubmitWithEntriesRequest
import com.ohmyclipping.service.dto.SubmitWithEntriesResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 위자드의 통합 제출 엔드포인트. entries 배열을 한 번에 받아 검증 / 저장 / 응답한다.
 *
 * 상태별 HTTP 응답 코드:
 *   - submitted → 201 Created  (모든 entry 저장 성공)
 *   - partial   → 200 OK       (일부만 저장, errors 배열 동봉)
 *   - rejected  → 200 OK       (모두 실패, DB 미저장)
 */
@RestController
@RequestMapping("/api/user/requests")
class UserRequestsWithEntriesController(
    private val service: UserClippingRequestService
) {

    /**
     * entries 배열을 검증하고 카테고리 요청으로 저장한다.
     * 모두 성공하면 201, 그 외 상태(partial / rejected)면 200을 반환한다.
     */
    @PostMapping("/with-entries")
    fun submit(
        @RequestBody @Valid request: SubmitWithEntriesRequest,
        auth: Authentication
    ): ResponseEntity<SubmitWithEntriesResponse> {
        // 서비스 레이어에서 entry 검증 및 저장을 위임한다.
        val res = service.submitRequestWithEntries(request, username = auth.name)
        val statusCode = when (res.status) {
            "submitted" -> HttpStatus.CREATED
            else -> HttpStatus.OK
        }
        return ResponseEntity.status(statusCode).body(res)
    }
}
