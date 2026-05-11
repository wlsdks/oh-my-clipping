package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.RestoreRevisionRequest
import com.ohmyclipping.admin.dto.RevisionSummaryResponse
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.CategoryRule
import com.ohmyclipping.model.EntityRevision
import com.ohmyclipping.model.EntityRevisionResourceType
import com.ohmyclipping.model.Persona
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.service.AdminCategoryRuleService
import com.ohmyclipping.service.AdminCategoryService
import com.ohmyclipping.service.AdminPersonaService
import com.ohmyclipping.service.AdminSourceService
import com.ohmyclipping.service.EntityRevisionRecorder
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 엔티티 변경 이력 + 복원 공용 컨트롤러.
 *
 * 4개 도메인(persona, category, category_rule, rss_source) 각각에 대해
 *   - GET /api/admin/{resource}/{id}/history?limit=20
 *   - POST /api/admin/{resource}/{id}/restore (body: revisionId, expectedUpdatedAt)
 * 를 제공한다. resource path는 enum [EntityRevisionResourceType]의 wire string과 일치한다.
 */
@RestController
@RequestMapping("/api/admin")
class AdminHistoryController(
    private val entityRevisionRecorder: EntityRevisionRecorder,
    private val adminPersonaService: AdminPersonaService,
    private val adminCategoryService: AdminCategoryService,
    private val adminCategoryRuleService: AdminCategoryRuleService,
    private val adminSourceService: AdminSourceService
) {

    companion object {
        /** 기본 limit 값. 프론트 UI가 펼침 시 한 번에 표시하는 건수와 동일. */
        private const val DEFAULT_LIMIT = 20

        /** API 응답 안정성을 위한 하드 상한. */
        private const val MAX_LIMIT = 100
    }

    // ── Persona ────────────────────────────────────────────────────────────

    /** 페르소나 변경 이력을 최신순으로 반환한다. */
    @GetMapping("/personas/{id}/history")
    fun getPersonaHistory(
        @PathVariable id: String,
        @RequestParam(defaultValue = "20") limit: Int
    ): List<RevisionSummaryResponse> =
        listHistory(EntityRevisionResourceType.PERSONA, id, limit)

    /** 페르소나를 특정 revision snapshot 값으로 되돌린다. */
    @PostMapping("/personas/{id}/restore")
    fun restorePersona(
        @PathVariable id: String,
        @RequestBody request: RestoreRevisionRequest,
        authentication: Authentication
    ): Persona {
        val revision = loadRevision(EntityRevisionResourceType.PERSONA, id, request.revisionId)
        val snapshot = entityRevisionRecorder.deserialize(revision.snapshot, Persona::class.java)
        val expected = parseExpectedUpdatedAt(request.expectedUpdatedAt, "expectedUpdatedAt")
            ?: throw InvalidInputException("expectedUpdatedAt이 필요합니다.")
        return adminPersonaService.restoreFromSnapshot(
            id = id,
            snapshot = snapshot,
            expectedUpdatedAt = expected,
            actorUsername = authentication.name
        )
    }

    // ── Category ───────────────────────────────────────────────────────────

    /** 카테고리 변경 이력을 최신순으로 반환한다. */
    @GetMapping("/categories/{id}/history")
    fun getCategoryHistory(
        @PathVariable id: String,
        @RequestParam(defaultValue = "20") limit: Int
    ): List<RevisionSummaryResponse> =
        listHistory(EntityRevisionResourceType.CATEGORY, id, limit)

    /** 카테고리를 특정 revision snapshot 값으로 되돌린다. */
    @PostMapping("/categories/{id}/restore")
    fun restoreCategory(
        @PathVariable id: String,
        @RequestBody request: RestoreRevisionRequest,
        authentication: Authentication
    ): Category {
        val revision = loadRevision(EntityRevisionResourceType.CATEGORY, id, request.revisionId)
        val snapshot = entityRevisionRecorder.deserialize(revision.snapshot, Category::class.java)
        val expected = parseExpectedUpdatedAt(request.expectedUpdatedAt, "expectedUpdatedAt")
            ?: throw InvalidInputException("expectedUpdatedAt이 필요합니다.")
        return adminCategoryService.restoreFromSnapshot(
            id = id,
            snapshot = snapshot,
            expectedUpdatedAt = expected,
            actorUsername = authentication.name
        )
    }

    // ── CategoryRule ───────────────────────────────────────────────────────

    /** 카테고리 규칙 변경 이력을 최신순으로 반환한다. */
    @GetMapping("/category-rules/{categoryId}/history")
    fun getCategoryRuleHistory(
        @PathVariable categoryId: String,
        @RequestParam(defaultValue = "20") limit: Int
    ): List<RevisionSummaryResponse> =
        listHistory(EntityRevisionResourceType.CATEGORY_RULE, categoryId, limit)

    /** 카테고리 규칙을 특정 revision snapshot 값으로 되돌린다. */
    @PostMapping("/category-rules/{categoryId}/restore")
    fun restoreCategoryRule(
        @PathVariable categoryId: String,
        @RequestBody request: RestoreRevisionRequest,
        authentication: Authentication
    ): CategoryRule {
        val revision = loadRevision(EntityRevisionResourceType.CATEGORY_RULE, categoryId, request.revisionId)
        val snapshot = entityRevisionRecorder.deserialize(revision.snapshot, CategoryRule::class.java)
        val expected = parseExpectedUpdatedAt(request.expectedUpdatedAt, "expectedUpdatedAt")
            ?: throw InvalidInputException("expectedUpdatedAt이 필요합니다.")
        return adminCategoryRuleService.restoreFromSnapshot(
            categoryId = categoryId,
            snapshot = snapshot,
            expectedUpdatedAt = expected,
            actorUsername = authentication.name
        )
    }

    // ── RssSource ──────────────────────────────────────────────────────────

    /** RSS 소스 변경 이력을 최신순으로 반환한다. */
    @GetMapping("/sources/{id}/history")
    fun getSourceHistory(
        @PathVariable id: String,
        @RequestParam(defaultValue = "20") limit: Int
    ): List<RevisionSummaryResponse> =
        listHistory(EntityRevisionResourceType.RSS_SOURCE, id, limit)

    /** RSS 소스를 특정 revision snapshot 값으로 되돌린다. */
    @PostMapping("/sources/{id}/restore")
    fun restoreSource(
        @PathVariable id: String,
        @RequestBody request: RestoreRevisionRequest,
        authentication: Authentication
    ): RssSource {
        val revision = loadRevision(EntityRevisionResourceType.RSS_SOURCE, id, request.revisionId)
        val snapshot = entityRevisionRecorder.deserialize(revision.snapshot, RssSource::class.java)
        val expected = parseExpectedUpdatedAt(request.expectedUpdatedAt, "expectedUpdatedAt")
            ?: throw InvalidInputException("expectedUpdatedAt이 필요합니다.")
        return adminSourceService.restoreFromSnapshot(
            id = id,
            snapshot = snapshot,
            expectedUpdatedAt = expected,
            actorUsername = authentication.name
        )
    }

    // ── 공용 헬퍼 ─────────────────────────────────────────────────────────

    /** limit 상한 보정 후 service에 위임. */
    private fun listHistory(
        resourceType: EntityRevisionResourceType,
        resourceId: String,
        limit: Int
    ): List<RevisionSummaryResponse> {
        val safeLimit = limit.coerceIn(1, MAX_LIMIT).takeIf { it > 0 } ?: DEFAULT_LIMIT
        return entityRevisionRecorder.listRecent(resourceType, resourceId, safeLimit).map { it.toResponse() }
    }

    /**
     * revisionId로 revision을 조회하고, 대상 리소스와 일치하는지 확인한다.
     * 불일치 시 [NotFoundException]을 던져 다른 리소스의 revision을 섞어 복원하는 공격을 막는다.
     */
    private fun loadRevision(
        resourceType: EntityRevisionResourceType,
        resourceId: String,
        revisionId: String
    ): EntityRevision {
        val revision = entityRevisionRecorder.findById(revisionId)
            ?: throw NotFoundException("Revision not found: $revisionId")
        if (revision.resourceType != resourceType.wire || revision.resourceId != resourceId) {
            // 타입/리소스 불일치는 의도적 오조립 가능성이 있으므로 NotFoundException으로 일관 처리.
            throw NotFoundException("Revision not found for resource: $revisionId")
        }
        return revision
    }

    private fun EntityRevision.toResponse() = RevisionSummaryResponse(
        revisionId = id,
        revisionNumber = revisionNumber,
        editorId = editorId,
        editorName = editorDisplayName ?: entityRevisionRecorder.anonymizeEditorName(editorId),
        changedFields = changedFields,
        createdAt = createdAt.toString()
    )
}
