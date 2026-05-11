package com.clipping.mcpserver.user

import com.clipping.mcpserver.service.UserEventService
import com.clipping.mcpserver.service.dto.UserEventBatchRequest
import com.clipping.mcpserver.service.dto.UserEventBatchResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 행동 이벤트 수집 API.
 * 프론트엔드 EventTracker SDK가 전송하는 이벤트를 일괄 수신하여 저장한다.
 */
@RestController
@RequestMapping("/api/user/events")
class UserEventController(
    private val userEventService: UserEventService
) {

    /**
     * 사용자 행동 이벤트를 일괄 수신한다.
     * 유효한 이벤트만 저장하고, 수락/거부 건수를 응답한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun trackEvents(
        authentication: Authentication,
        @RequestBody request: UserEventBatchRequest
    ): UserEventBatchResponse {
        return userEventService.saveBatchForUsername(authentication.name, request.events)
    }
}
