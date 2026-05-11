package com.ohmyclipping.admin

import com.ohmyclipping.service.dto.user.BriefingListResponse
import com.ohmyclipping.service.BriefingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 오늘의 브리핑 조회 API를 제공하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/admin/briefing")
class BriefingAdminController(
    private val briefingService: BriefingService
) {

    /**
     * 오늘의 브리핑을 조회한다.
     * categoryId가 주어지면 해당 카테고리만 반환한다.
     */
    @GetMapping("/today")
    fun getTodayBriefings(
        @RequestParam(required = false) categoryId: String?
    ): BriefingListResponse = briefingService.getTodayBriefings(categoryId)
}
