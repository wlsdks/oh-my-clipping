package com.ohmyclipping.admin

import com.ohmyclipping.service.SchedulerStatusService
import com.ohmyclipping.service.dto.admin.SchedulerStatusResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 스케줄러 실시간 상태 조회 API.
 * 시스템 상태 페이지의 상세 패널에서 1분 단위로 폴링하여 사용한다.
 */
@RestController
@RequestMapping("/api/admin/schedulers")
class SchedulerStatusAdminController(
    private val schedulerStatusService: SchedulerStatusService
) {

    /**
     * 등록된 모든 스케줄러의 실행 이력/다음 실행 예정/마지막 에러를 반환한다.
     */
    @GetMapping("/status")
    fun getStatus(): List<SchedulerStatusResponse> = schedulerStatusService.list()
}
