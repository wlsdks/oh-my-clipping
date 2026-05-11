package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.CategoryResponse
import com.ohmyclipping.admin.dto.CreateCategoryRequest
import com.ohmyclipping.model.Category
import com.ohmyclipping.service.AdminCategoryService
import com.ohmyclipping.service.UserSetupResourceService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 사용자 self-serve 카테고리 생성 API를 제공한다.
 */
@RestController
@RequestMapping("/api/user/setup/categories")
class UserSetupCategoryController(
    private val userSetupResourceService: UserSetupResourceService,
    private val adminCategoryService: AdminCategoryService
) {

    /**
     * 로그인 사용자의 setup 전용 카테고리를 생성한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        authentication: Authentication,
        @RequestBody request: CreateCategoryRequest
    ): CategoryResponse =
        userSetupResourceService.createOwnCategory(
            requesterUsername = authentication.name,
            name = request.name,
            description = request.description,
            slackChannelId = request.slackChannelId,
            maxItems = request.maxItems,
            personaId = request.personaId
        ).toResponse()

    private fun Category.toResponse() = CategoryResponse(
        id = id,
        name = name,
        description = description,
        slackChannelId = slackChannelId,
        isActive = isActive,
        isPublic = isPublic,
        maxItems = maxItems,
        personaId = personaId,
        sourceCount = adminCategoryService.countSources(id),
        subscriberCount = 0,
        lastDeliveryAt = null,
        errorSourceCount = 0,
        status = status.name,
        pausedAt = pausedAt?.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}
