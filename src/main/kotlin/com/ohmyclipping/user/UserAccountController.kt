package com.ohmyclipping.user

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.service.UserAccountApprovalService
import com.ohmyclipping.service.UserDataExportService
import com.ohmyclipping.user.dto.ChangePasswordRequest
import com.ohmyclipping.user.dto.SelfWithdrawRequest
import com.ohmyclipping.user.dto.UpdateProfileRequest
import com.ohmyclipping.user.dto.UpdateProfileResponse
import com.ohmyclipping.user.dto.UpdateSlackRequest
import com.ohmyclipping.user.dto.UpdateSlackResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 사용자 본인 계정 관리 API.
 * 개인정보보호법 제35조(개인정보의 열람), 제36조(개인정보의 정정·삭제) 대응 엔드포인트.
 */
@RestController
@RequestMapping("/api/user/account")
class UserAccountController(
    private val userAccountApprovalService: UserAccountApprovalService,
    private val userDataExportService: UserDataExportService
) {

    /**
     * 사용자 본인이 비밀번호를 변경한다.
     * - 현재 비밀번호 검증 후 새 비밀번호 복잡도 검증 (최소 8자 + 영문 1 + 숫자 1).
     * - must_change_password 플래그도 함께 클리어된다.
     */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        authentication: Authentication,
        @RequestBody request: ChangePasswordRequest
    ) {
        userAccountApprovalService.changePassword(
            username = authentication.name,
            currentPassword = request.currentPassword,
            newPassword = request.newPassword
        )
    }

    /** 사용자 본인 계정 탈퇴. 비밀번호 확인 필수. */
    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun selfWithdraw(
        authentication: Authentication,
        @RequestBody request: SelfWithdrawRequest
    ) {
        userAccountApprovalService.selfWithdraw(
            username = authentication.name,
            rawPassword = request.password
        )
    }

    /** 사용자 본인의 Slack 멤버 ID를 설정/변경한다. */
    @PatchMapping("/slack")
    fun updateSlack(
        authentication: Authentication,
        @RequestBody request: UpdateSlackRequest
    ): UpdateSlackResponse {
        userAccountApprovalService.updateSlackMemberId(
            username = authentication.name,
            slackMemberId = request.slackMemberId
        )
        return UpdateSlackResponse(slackMemberId = request.slackMemberId.trim().uppercase())
    }

    /**
     * V129: 사용자 본인 프로필(부서/팀 FK)을 수정한다.
     * 서비스 레이어가 team.departmentId == departmentId 일관성을 검증하고
     * legacy `department` / `team` 이름 캐시도 함께 동기화한다.
     * null 필드는 변경 없음, 빈 문자열은 초기화(null) 로 해석한다.
     */
    @PatchMapping("/profile")
    fun updateProfile(
        authentication: Authentication,
        @RequestBody request: UpdateProfileRequest
    ): UpdateProfileResponse {
        val updated = userAccountApprovalService.updateSelfProfile(
            username = authentication.name,
            departmentId = request.departmentId,
            teamId = request.teamId
        )
        return UpdateProfileResponse(
            departmentId = updated.departmentId,
            // legacy 이름 캐시가 이미 JOIN 값으로 저장돼 있으므로 별도 조회 없이 그대로 노출.
            departmentName = updated.department,
            teamId = updated.teamId,
            teamName = updated.team
        )
    }

    /**
     * 본인 개인정보 export. 개인정보보호법 제35조(개인정보의 열람) 대응.
     *
     * - 민감 필드(password_hash, totp_secret 등)는 포함되지 않는다.
     * - 일일 [UserDataExportService] 제한에 따라 초과 시 429 를 반환한다.
     * - 모든 요청은 audit_log 테이블에 `PERSONAL_DATA_EXPORT` 액션으로 기록된다.
     *
     * @param format `json` (기본) 또는 `csv`
     */
    @GetMapping("/data-export")
    fun exportPersonalData(
        authentication: Authentication,
        @RequestParam(defaultValue = UserDataExportService.FORMAT_JSON) format: String
    ): ResponseEntity<ByteArray> {
        val username = authentication.name
        // 허용 포맷 검증: 대소문자 무시로 정규화한다.
        val normalized = format.trim().lowercase()
        if (normalized != UserDataExportService.FORMAT_JSON && normalized != UserDataExportService.FORMAT_CSV) {
            throw InvalidInputException("지원하지 않는 포맷입니다. json 또는 csv 를 선택해 주세요.")
        }
        // 포맷에 따라 content-type/본문을 분기한다.
        val (body, mediaType, extension) = if (normalized == UserDataExportService.FORMAT_CSV) {
            Triple(
                userDataExportService.exportWithRateLimit(username, normalized),
                MediaType.parseMediaType("text/csv; charset=utf-8"),
                "csv"
            )
        } else {
            Triple(userDataExportService.exportWithRateLimit(username, normalized), MediaType.APPLICATION_JSON, "json")
        }

        val today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val filename = "personal_data_$today.$extension"

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(mediaType)
            .body(body)
    }
}
