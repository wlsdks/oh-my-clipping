package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.CategoryBrowseItem
import com.clipping.mcpserver.admin.dto.SubscribeRequest
import com.clipping.mcpserver.admin.dto.SubscribeResponse
import com.clipping.mcpserver.service.UserCategoryBrowseService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자가 기존 카테고리를 탐색하고 즉시 구독할 수 있는 API를 제공한다.
 */
@RestController
@RequestMapping("/api/user/categories")
class UserCategoryBrowseController(
    private val userCategoryBrowseService: UserCategoryBrowseService
) {

    /**
     * 사용자가 구독 가능한 카테고리 목록을 조회한다.
     */
    @GetMapping("/browse")
    fun browse(authentication: Authentication): List<CategoryBrowseItem> =
        userCategoryBrowseService.browse(authentication.name).map {
            CategoryBrowseItem(
                id = it.id,
                name = it.name,
                description = it.description,
                slackChannelId = it.slackChannelId,
                subscriberCount = it.subscriberCount,
                isSubscribed = it.isSubscribed,
                deliveryHour = it.deliveryHour,
                maxItems = it.maxItems
            )
        }

    /**
     * 기존 카테고리에 즉시 구독한다 (관리자 승인 불필요).
     */
    @PostMapping("/{categoryId}/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    fun subscribe(
        authentication: Authentication,
        @PathVariable categoryId: String,
        @RequestBody body: SubscribeRequest
    ): SubscribeResponse {
        val result = userCategoryBrowseService.subscribe(
            username = authentication.name,
            categoryId = categoryId,
            slackChannelId = body.slackChannelId
        )
        return SubscribeResponse(
            requestId = result.requestId,
            categoryId = result.categoryId,
            status = result.status
        )
    }

}
