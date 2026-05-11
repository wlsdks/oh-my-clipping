package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.CategoryPageResponse
import com.ohmyclipping.admin.dto.CategoryResponse
import com.ohmyclipping.admin.dto.CreateCategoryRequest
import com.ohmyclipping.admin.dto.UpdateCategoryRequest
import com.ohmyclipping.model.Category
import com.ohmyclipping.service.AdminCategoryService
import com.ohmyclipping.service.dto.CategoryStatsBundle
import com.ohmyclipping.support.IdempotencyKeyService
import com.ohmyclipping.support.PaginationUtils
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * 카테고리 관리 API를 제공하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/admin/categories")
class CategoryAdminController(
    private val adminCategoryService: AdminCategoryService,
    private val idempotencyKeyService: IdempotencyKeyService
) {

    /**
     * 카테고리 목록을 페이지네이션으로 조회합니다.
     *
     * @param search 이름/설명 검색어 (선택)
     * @param page 페이지 번호 (기본 0)
     * @param size 페이지 크기 (기본 30)
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int
    ): CategoryPageResponse {
        val safeSize = size.coerceIn(1, 200)
        // 음수 페이지가 DB OFFSET 음수로 전달되지 않도록 첫 페이지로 보정한다.
        val safePage = page.coerceAtLeast(0)
        val offset = PaginationUtils.safeOffset(safePage, safeSize)

        // 검색 조건으로 카테고리를 페이지네이션 조회한다.
        val categories = adminCategoryService.findAll(search = q, offset = offset, limit = safeSize)

        // 총 건수를 조회한다.
        val totalCount = adminCategoryService.countAll(search = q)

        // 반환된 페이지 내 카테고리에 대해서만 통계를 조회한다.
        val stats = adminCategoryService.getCategoryStats(categories.map { it.id })

        return CategoryPageResponse(
            content = categories.map { it.toResponse(stats) },
            totalCount = totalCount,
            page = safePage,
            size = safeSize
        )
    }

    /**
     * 카테고리 단건을 조회합니다.
     */
    @GetMapping("/{id}")
    fun get(@PathVariable id: String): CategoryResponse =
        adminCategoryService.getCategory(id).toSingleResponse()

    /**
     * 카테고리를 신규 생성합니다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateCategoryRequest): CategoryResponse =
        adminCategoryService.createCategory(
            name = request.name,
            description = request.description,
            slackChannelId = request.slackChannelId,
            maxItems = request.maxItems,
            personaId = request.personaId,
            isPublic = request.isPublic,
            purpose = request.purpose,
            background = request.background,
            problemStatement = request.problemStatement
        ).toSingleResponse()

    /**
     * 카테고리 정보를 수정합니다.
     *
     * `Idempotency-Key` 헤더가 제공되면 같은 키의 재전송은 DB 를 다시 건드리지 않고 첫 응답을 그대로 재사용한다.
     */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody request: UpdateCategoryRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        authentication: Authentication
    ): CategoryResponse =
        idempotencyKeyService.executeIfKeyPresent(
            actor = authentication.name,
            key = idempotencyKey,
            resultClass = CategoryResponse::class.java
        ) {
            adminCategoryService.updateCategory(
                id = id,
                name = request.name,
                description = request.description,
                slackChannelId = request.slackChannelId,
                isActive = request.isActive,
                isPublic = request.isPublic,
                maxItems = request.maxItems,
                personaId = request.personaId,
                expectedUpdatedAt = parseExpectedUpdatedAt(request.expectedUpdatedAt, "expectedUpdatedAt"),
                actorUsername = authentication.name,
                purpose = request.purpose,
                background = request.background,
                problemStatement = request.problemStatement
            ).toSingleResponse()
        }

    /**
     * 카테고리를 삭제합니다.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String, authentication: org.springframework.security.core.Authentication) {
        adminCategoryService.deleteCategory(id, deletedByUsername = authentication.name)
    }

    /**
     * 카테고리를 일시정지합니다.
     */
    @PutMapping("/{id}/pause")
    fun pause(@PathVariable id: String): CategoryResponse =
        adminCategoryService.pauseCategory(id).toSingleResponse()

    /**
     * 카테고리 일시정지를 해제합니다.
     */
    @PutMapping("/{id}/resume")
    fun resume(@PathVariable id: String): CategoryResponse =
        adminCategoryService.resumeCategory(id).toSingleResponse()

    /** 목록 조회용: 일괄 stats 활용 */
    private fun Category.toResponse(stats: CategoryStatsBundle) = CategoryResponse(
        id = id, name = name, description = description,
        slackChannelId = slackChannelId, isActive = isActive, isPublic = isPublic,
        maxItems = maxItems, personaId = personaId,
        sourceCount = adminCategoryService.countSources(id),
        subscriberCount = stats.subscriberCounts[id] ?: 0,
        lastDeliveryAt = stats.lastDeliveryAts[id]?.toString(),
        errorSourceCount = stats.errorSourceCounts[id] ?: 0,
        status = status.name, pausedAt = pausedAt?.toString(),
        createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
        purpose = purpose?.name,
        background = background,
        problemStatement = problemStatement
    )

    /** 단건 조회용 (create/update/getById/pause/resume): 모니터링 필드 생략 */
    private fun Category.toSingleResponse() = CategoryResponse(
        id = id, name = name, description = description,
        slackChannelId = slackChannelId, isActive = isActive, isPublic = isPublic,
        maxItems = maxItems, personaId = personaId,
        sourceCount = adminCategoryService.countSources(id),
        subscriberCount = 0,
        lastDeliveryAt = null,
        errorSourceCount = 0,
        status = status.name, pausedAt = pausedAt?.toString(),
        createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
        purpose = purpose?.name,
        background = background,
        problemStatement = problemStatement
    )
}
