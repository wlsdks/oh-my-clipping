package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.DeliveryLogResponse
import com.ohmyclipping.admin.dto.DeliveryLogsPageResponse
import com.ohmyclipping.admin.dto.DeliverySummaryResponse
import com.ohmyclipping.admin.util.AdminTimeRange
import com.ohmyclipping.service.DeliveryAdminService
import com.ohmyclipping.service.dto.RetryDeliveryResponse
import com.ohmyclipping.support.PaginationUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

/**
 * 발송 이력 관리 API를 제공하는 관리자 컨트롤러.
 * 발송 요약 통계, 이력 목록 조회, 실패 건 재발송 기능을 담당한다.
 */
@RestController
@RequestMapping("/api/admin/delivery")
class DeliveryAdminController(
    private val deliveryAdminService: DeliveryAdminService
) {

    /**
     * 특정 날짜의 발송 요약 통계를 반환한다.
     *
     * @param date 조회 대상 날짜 (기본: 오늘, yyyy-MM-dd)
     */
    @GetMapping("/summary")
    fun summary(
        @RequestParam(required = false) date: String?
    ): DeliverySummaryResponse {
        // 날짜 파라미터를 안전하게 파싱한다.
        val parsedDate = date?.let { parseLocalDate(it) } ?: LocalDate.now(ZoneId.of("Asia/Seoul"))
        val summary = deliveryAdminService.summary(parsedDate)
        return DeliverySummaryResponse.from(summary)
    }

    /**
     * 발송 이력을 필터 조건으로 페이지네이션 조회한다.
     *
     * @param categoryId 카테고리 ID 필터 (선택)
     * @param status 상태 필터 (선택)
     * @param from 시작 날짜 필터 (선택, yyyy-MM-dd)
     * @param to 종료 날짜 필터 (선택, yyyy-MM-dd)
     * @param within 최근 기간 필터 (선택, "1d" 또는 "7d"). from/to보다 우선 적용된다.
     * @param page 페이지 번호 (기본 0)
     * @param size 페이지 크기 (기본 30)
     */
    @GetMapping("/logs")
    fun listLogs(
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) within: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int
    ): DeliveryLogsPageResponse {
        val safeSize = size.coerceIn(1, 200)
        // 음수 페이지가 DB OFFSET 음수로 전달되지 않도록 첫 페이지로 보정한다.
        val safePage = page.coerceAtLeast(0)
        val offset = PaginationUtils.safeOffset(safePage, safeSize)

        // within 파라미터를 KST 달력 기준 Instant 하한으로 변환한다. 잘못된 값이면 즉시 예외를 던진다.
        val since = AdminTimeRange.parseWithin(within)

        // 날짜 파라미터를 안전하게 파싱한다.
        val fromDate = from?.let { parseLocalDate(it) }
        val toDate = to?.let { parseLocalDate(it) }

        // 필터 조건으로 발송 이력을 조회한다.
        val logs = deliveryAdminService.findLogs(
            categoryId = categoryId,
            status = status,
            from = fromDate,
            to = toDate,
            since = since,
            offset = offset,
            limit = safeSize
        )

        // 총 건수를 조회한다.
        val totalCount = deliveryAdminService.countLogs(
            categoryId = categoryId,
            status = status,
            from = fromDate,
            to = toDate,
            since = since
        )

        return DeliveryLogsPageResponse(
            content = logs.map { DeliveryLogResponse.from(it) },
            totalCount = totalCount,
            page = safePage,
            size = safeSize
        )
    }

    /**
     * 실패 상태의 발송 건을 재발송 요청한다.
     *
     * @param logId 재발송 대상 발송 로그 ID
     */
    @PostMapping("/{logId}/retry")
    fun retryDelivery(
        @PathVariable logId: String
    ): RetryDeliveryResponse {
        deliveryAdminService.retryDelivery(logId)
        return RetryDeliveryResponse(success = true, logId = logId)
    }

    /** yyyy-MM-dd 문자열을 LocalDate로 안전하게 파싱한다. */
    private fun parseLocalDate(value: String): LocalDate? {
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }
}
