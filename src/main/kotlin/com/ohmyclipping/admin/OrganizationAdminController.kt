package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.CreateOrganizationRequest
import com.ohmyclipping.admin.dto.OrganizationListResponse
import com.ohmyclipping.admin.dto.OrganizationResponse
import com.ohmyclipping.admin.dto.SetCategoryOrganizationsRequest
import com.ohmyclipping.admin.dto.UpdateOrganizationRequest
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.Organization
import com.ohmyclipping.model.OrganizationType
import com.ohmyclipping.service.OrganizationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Organization + Category 링크 관리 API.
 *
 * 경로:
 * - `/api/admin/organizations` — CRUD
 * - `/api/admin/categories/{categoryId}/organizations` — 링크 조회/교체
 */
@RestController
@RequestMapping("/api/admin")
class OrganizationAdminController(
    private val organizationService: OrganizationService,
) {

    /** 조직 목록. `?type=COMPETITOR` 필터 지원. usageCount(연결 카테고리 수) 를 배치 집계해 포함한다. */
    @GetMapping("/organizations")
    fun list(@RequestParam(required = false) type: String?): OrganizationListResponse {
        val filter = type?.takeIf { it.isNotBlank() }?.let { parseTypeFilter(it) }
        val rows = organizationService.findAllWithUsageCounts(filter)
        return OrganizationListResponse(
            content = rows.map { (org, count) -> org.toResponse(count) },
            totalCount = rows.size,
        )
    }

    /** 단건 조회. */
    @GetMapping("/organizations/{id}")
    fun get(@PathVariable id: String): OrganizationResponse =
        organizationService.getById(id).toResponse()

    /** 신규 생성. */
    @PostMapping("/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateOrganizationRequest): OrganizationResponse =
        organizationService.create(
            name = request.name,
            type = request.type,
            domain = request.domain,
            description = request.description,
        ).toResponse()

    /** 부분 수정. */
    @PatchMapping("/organizations/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateOrganizationRequest,
    ): OrganizationResponse =
        organizationService.update(
            id = id,
            name = request.name,
            type = request.type,
            domain = request.domain,
            description = request.description,
            aliases = request.aliases,
        ).toResponse()

    /** 삭제. */
    @DeleteMapping("/organizations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) {
        organizationService.delete(id)
    }

    /** 카테고리에 연결된 조직 목록. */
    @GetMapping("/categories/{categoryId}/organizations")
    fun listByCategory(@PathVariable categoryId: String): OrganizationListResponse {
        val organizations = organizationService.findByCategoryId(categoryId)
        return OrganizationListResponse(
            content = organizations.map { it.toResponse() },
            totalCount = organizations.size,
        )
    }

    /** 카테고리 ↔ 조직 링크를 완전 교체한다. */
    @PutMapping("/categories/{categoryId}/organizations")
    fun setCategoryOrganizations(
        @PathVariable categoryId: String,
        @RequestBody request: SetCategoryOrganizationsRequest,
    ): OrganizationListResponse {
        organizationService.setCategoryOrganizations(categoryId, request.organizationIds)
        val organizations = organizationService.findByCategoryId(categoryId)
        return OrganizationListResponse(
            content = organizations.map { it.toResponse() },
            totalCount = organizations.size,
        )
    }

    /** type 쿼리 파라미터 파싱 — 허용 밖이면 400. */
    private fun parseTypeFilter(raw: String): OrganizationType =
        runCatching { OrganizationType.valueOf(raw.trim().uppercase()) }.getOrElse {
            throw InvalidInputException(
                "Invalid organization type filter: '$raw'. Allowed: COMPETITOR, CUSTOMER, PARTNER, OTHER"
            )
        }

    private fun Organization.toResponse(usageCount: Int = 0) = OrganizationResponse(
        id = id,
        tenantId = tenantId,
        name = name,
        type = type.name,
        domain = domain,
        description = description,
        stockCode = stockCode,
        aliases = aliases,
        origin = origin,
        usageCount = usageCount,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}
