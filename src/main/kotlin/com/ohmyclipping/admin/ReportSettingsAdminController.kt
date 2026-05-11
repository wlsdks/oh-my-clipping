package com.ohmyclipping.admin

import com.ohmyclipping.service.ReportSettingsService
import com.ohmyclipping.service.dto.ReportSettingsResponse
import com.ohmyclipping.service.dto.ReportSettingsUpdateRequest
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 자동 리포트 설정 관리 API.
 * 주간/월간 자동 리포트의 스케줄과 포함 항목을 설정한다.
 */
@RestController
@RequestMapping("/api/admin/report-settings")
class ReportSettingsAdminController(
    private val reportSettingsService: ReportSettingsService
) {

    @GetMapping
    fun getSettings(): ReportSettingsResponse =
        reportSettingsService.getSettings()

    @PutMapping
    fun updateSettings(
        @RequestBody request: ReportSettingsUpdateRequest,
        authentication: Authentication
    ): ReportSettingsResponse =
        reportSettingsService.updateSettings(request, actorUsername = authentication.name)
}
