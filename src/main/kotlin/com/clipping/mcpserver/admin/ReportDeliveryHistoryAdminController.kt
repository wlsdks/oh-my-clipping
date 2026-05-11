package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.ReportDeliveryHistoryService
import com.clipping.mcpserver.service.dto.ReportDeliveryHistoryItem
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 자동 리포트 발송 이력 조회 API.
 * 관리자 UI의 실행 이력 드로어에서 최근 N건을 조회할 때 사용한다.
 */
@RestController
@RequestMapping("/api/admin/reports")
class ReportDeliveryHistoryAdminController(
    private val reportDeliveryHistoryService: ReportDeliveryHistoryService
) {

    /**
     * 리포트 발송 이력을 최신순으로 반환한다.
     *
     * @param reportType "WEEKLY" | "MONTHLY" | null(전체)
     * @param limit 최대 조회 건수 (기본 50, 최대 200)
     */
    @GetMapping("/history")
    fun getHistory(
        @RequestParam(required = false) reportType: String?,
        @RequestParam(required = false, defaultValue = "50") limit: Int
    ): List<ReportDeliveryHistoryItem> {
        return reportDeliveryHistoryService.listHistory(reportType, limit)
    }
}
