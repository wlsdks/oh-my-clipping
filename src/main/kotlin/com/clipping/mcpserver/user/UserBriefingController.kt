package com.clipping.mcpserver.user

import com.clipping.mcpserver.service.BriefingService
import com.clipping.mcpserver.service.dto.BriefingListResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자용 오늘의 브리핑 조회 API.
 * 관리자 전용이 아닌 일반 사용자(ROLE_USER)가 접근할 수 있다.
 */
@RestController
@RequestMapping("/api/user/briefing")
class UserBriefingController(
    private val briefingService: BriefingService
) {

    /**
     * 오늘의 브리핑을 조회한다.
     * categoryId가 주어지면 해당 카테고리만 반환한다.
     */
    @GetMapping("/today")
    fun getTodayBriefings(
        @RequestParam(required = false) categoryId: String?,
        authentication: Authentication
    ): BriefingListResponse = briefingService.getTodayBriefings(categoryId)
}
