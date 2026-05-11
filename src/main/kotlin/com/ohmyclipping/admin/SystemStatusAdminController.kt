package com.ohmyclipping.admin

import com.ohmyclipping.service.SystemStatusService
import com.ohmyclipping.service.dto.admin.SystemStatusResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 시스템 상태 조회 API를 제공하는 관리자 컨트롤러.
 * 서버, 데이터베이스, Slack, 스케줄러 상태를 한 번에 반환한다.
 */
@RestController
@RequestMapping("/api/admin/system")
class SystemStatusAdminController(
    private val systemStatusService: SystemStatusService
) {

    /**
     * 서버/DB/Slack/스케줄러 상태를 조회한다.
     */
    @GetMapping("/status")
    fun getStatus(): SystemStatusResponse {
        return systemStatusService.getStatus()
    }
}
