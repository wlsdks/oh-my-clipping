package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.RunPipelineRequest
import com.ohmyclipping.service.UserSetupResourceService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 self-serve 카테고리의 파이프라인 수동 실행 API를 제공한다.
 */
@RestController
@RequestMapping("/api/user/setup/clipping")
class UserSetupClippingController(
    private val userSetupResourceService: UserSetupResourceService
) {

    /**
     * 로그인 사용자가 소유한 카테고리만 파이프라인을 실행한다.
     */
    @PostMapping("/{categoryId}/pipeline")
    fun runPipeline(
        authentication: Authentication,
        @PathVariable categoryId: String,
        @RequestBody(required = false) request: RunPipelineRequest?
    ) = userSetupResourceService.runOwnPipeline(
        requesterUsername = authentication.name,
        categoryId = categoryId,
        hoursBack = request?.hoursBack,
        maxItems = request?.maxItems,
        unsentOnly = request?.unsentOnly ?: true,
        sendToSlack = request?.sendToSlack ?: false,
        slackChannelId = request?.slackChannelId,
        ralphLoopEnabled = request?.ralphLoopEnabled,
        ralphLoopMaxIterations = request?.ralphLoopMaxIterations,
        ralphLoopStopPhrase = request?.ralphLoopStopPhrase
    )
}
