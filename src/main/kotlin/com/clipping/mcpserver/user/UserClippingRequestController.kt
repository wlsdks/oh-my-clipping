package com.clipping.mcpserver.user

import com.clipping.mcpserver.admin.dto.CreateUserClippingRequest
import com.clipping.mcpserver.admin.dto.CreateUserRssSourceRequests
import com.clipping.mcpserver.admin.dto.RegisterWizardOwnershipRequest
import com.clipping.mcpserver.admin.dto.UserClippingRequestResponse
import com.clipping.mcpserver.admin.UserClippingRequestResponseMapper
import com.clipping.mcpserver.service.UserClippingRequestService
import com.clipping.mcpserver.service.dto.UserAdditionalRssSourcesSubmission
import com.clipping.mcpserver.service.dto.UserClippingRequestSubmission
import com.clipping.mcpserver.service.dto.UserRssSourceSubmission
import com.clipping.mcpserver.user.dto.RenameRequestBody
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 전용 클리핑 요청 API.
 */
@RestController
@RequestMapping("/api/user/requests")
class UserClippingRequestController(
    private val userClippingRequestService: UserClippingRequestService,
    private val responseMapper: UserClippingRequestResponseMapper
) {

    @GetMapping
    fun list(authentication: Authentication): List<UserClippingRequestResponse> =
        // 로그인 사용자 기준으로 본인 요청만 조회한다.
        userClippingRequestService.listOwnRequests(authentication.name).map(responseMapper::toResponse)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @RequestBody request: CreateUserClippingRequest
    ): UserClippingRequestResponse =
        // HTTP 요청 DTO를 서비스 전용 submission DTO로 변환한다.
        userClippingRequestService.submitRequest(
            requesterUsername = authentication.name,
            submission = UserClippingRequestSubmission(
                requestName = request.requestName,
                sourceName = request.sourceName,
                sourceUrl = request.sourceUrl,
                slackChannelId = request.slackChannelId,
                personaName = request.personaName,
                personaPrompt = request.personaPrompt,
                summaryStyle = request.summaryStyle,
                targetAudience = request.targetAudience,
                selectedPresetId = request.selectedPresetId,
                requestNote = request.requestNote
            )
        ).let(responseMapper::toResponse)

    /**
     * 사용자 본인의 PENDING 요청을 철회한다.
     * 상태 변경 액션이므로 POST를 사용한다.
     */
    @PostMapping("/{id}/withdraw")
    fun withdraw(
        authentication: Authentication,
        @PathVariable id: String
    ): UserClippingRequestResponse =
        // 사용자 본인의 PENDING 요청만 철회 가능하다.
        userClippingRequestService.withdrawRequest(id, authentication.name).let(responseMapper::toResponse)

    @PostMapping("/{id}/unsubscribe")
    fun unsubscribe(
        authentication: Authentication,
        @PathVariable id: String
    ): UserClippingRequestResponse =
        // 승인된 구독을 해제하고 WITHDRAWN 상태로 전환한다.
        userClippingRequestService.unsubscribeRequest(id, authentication.name).let(responseMapper::toResponse)

    @DeleteMapping("/{id}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRequest(
        authentication: Authentication,
        @PathVariable id: String
    ) {
        // 반려 또는 철회된 요청만 삭제 가능하다.
        userClippingRequestService.deleteRequest(id, authentication.name)
    }

    /**
     * 승인된 구독의 표시 이름을 변경한다.
     */
    @PatchMapping("/{id}/name")
    fun rename(
        authentication: Authentication,
        @PathVariable id: String,
        @RequestBody body: RenameRequestBody
    ): UserClippingRequestResponse =
        // 본인의 승인된 구독 이름만 변경할 수 있다.
        userClippingRequestService.renameRequest(id, authentication.name, body.name)
            .let(responseMapper::toResponse)

    /**
     * 위자드에서 직접 생성한 리소스의 소유권을 자동 APPROVED 상태로 등록한다.
     */
    @PostMapping("/wizard-ownership")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerWizardOwnership(
        authentication: Authentication,
        @RequestBody request: RegisterWizardOwnershipRequest
    ): UserClippingRequestResponse =
        userClippingRequestService.registerWizardOwnership(
            requesterUsername = authentication.name,
            requestName = request.requestName,
            sourceName = request.sourceName,
            sourceUrl = request.sourceUrl,
            slackChannelId = request.slackChannelId,
            personaName = request.personaName,
            personaPrompt = request.personaPrompt,
            summaryStyle = request.summaryStyle,
            targetAudience = request.targetAudience,
            selectedPresetId = request.selectedPresetId,
            categoryId = request.categoryId,
            personaId = request.personaId,
            sourceId = request.sourceId
        ).let(responseMapper::toResponse)

    @PostMapping("/rss-sources")
    @ResponseStatus(HttpStatus.CREATED)
    fun createAdditionalRssSources(
        authentication: Authentication,
        @RequestBody request: CreateUserRssSourceRequests
    ): List<UserClippingRequestResponse> =
        // 추가 RSS 목록을 서비스 전용 구조로 매핑해 일괄 등록한다.
        userClippingRequestService.submitAdditionalRssSources(
            requesterUsername = authentication.name,
            submission = UserAdditionalRssSourcesSubmission(
                baseRequestId = request.baseRequestId,
                sources = request.sources.map { source ->
                    UserRssSourceSubmission(
                        sourceName = source.sourceName,
                        sourceUrl = source.sourceUrl
                    )
                },
                requestNote = request.requestNote
            )
        ).map(responseMapper::toResponse)
}
