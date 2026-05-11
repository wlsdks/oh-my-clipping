package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.RuntimeSettingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 서비스 점검 상태를 인증 없이 제공하는 공개 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/public")
class MaintenancePublicController(
    private val runtimeSettingService: RuntimeSettingService
) {
    /**
     * 현재 점검 모드 상태와 안내 메시지를 반환한다.
     */
    @GetMapping("/maintenance")
    fun getMaintenanceStatus(): RuntimeSettingService.MaintenanceStatus =
        runtimeSettingService.maintenanceStatus()
}
