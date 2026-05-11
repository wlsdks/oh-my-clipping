package com.ohmyclipping.service

import com.ohmyclipping.model.Category
import com.ohmyclipping.model.CategoryPurpose
import com.ohmyclipping.model.CategoryStatus
import com.ohmyclipping.model.EntityRevisionResourceType
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.StaleEditInfo
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.UserClippingRequestStore
import com.ohmyclipping.store.RssSourceStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.SummaryDeliveryStore
import com.ohmyclipping.service.dto.analytics.CategoryStatsBundle
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.support.InputSanitizer
import com.ohmyclipping.support.SlackChannelIdNormalizer
import com.ohmyclipping.support.SlackMentionGuard
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 관리자 카테고리 관리(조회/생성/수정/삭제) 정책을 담당한다.
 */
@Service
class AdminCategoryService(
    private val categoryStore: CategoryStore,
    private val requestStore: UserClippingRequestStore,
    private val sourceStore: RssSourceStore,
    private val summaryDeliveryStore: SummaryDeliveryStore,
    private val auditLogStore: AuditLogStore,
    private val userClippingRequestStore: UserClippingRequestStore,
    private val entityRevisionRecorder: EntityRevisionRecorder,
    private val auditActorResolver: AuditActorResolver
) {

    companion object {
        /** 카테고리 이름 최대 길이 — DB: batch_categories.name VARCHAR(200) */
        const val CATEGORY_NAME_MAX = 200

        /** 카테고리 설명 최대 길이 — DB: batch_categories.description TEXT */
        const val CATEGORY_DESCRIPTION_MAX = 1000

        /** V123(PR1): background/problem_statement 의 서비스 레이어 상한. TEXT 지만 UX 관점에서 제한. */
        const val CATEGORY_FREEFORM_MAX = 2000
    }

    /**
     * 카테고리 목록을 조회한다.
     */
    fun listCategories(): List<Category> =
        // 관리자 화면 기본 목록은 전체 카테고리를 반환한다.
        categoryStore.list()

    /**
     * 검색 조건을 적용하여 카테고리를 페이지네이션 조회한다.
     *
     * @param search 이름/설명 검색어 (선택)
     * @param offset 건너뛸 건수
     * @param limit 조회 건수
     */
    fun findAll(search: String? = null, offset: Int = 0, limit: Int = 30): List<Category> =
        categoryStore.findAll(
            search = search,
            offset = offset.coerceAtLeast(0),
            limit = limit.coerceIn(1, 100)
        )

    /**
     * 검색 조건에 해당하는 카테고리 총 건수를 반환한다.
     */
    fun countAll(search: String? = null): Int =
        categoryStore.countAll(search)

    /**
     * 카테고리 단건을 조회한다.
     */
    fun getCategory(id: String): Category =
        categoryStore.findById(id) ?: throw NotFoundException("Category not found: $id")

    /**
     * 카테고리를 생성한다.
     */
    fun createCategory(
        name: String,
        description: String?,
        slackChannelId: String?,
        maxItems: Int,
        personaId: String?,
        isPublic: Boolean = true,
        purpose: String? = null,
        background: String? = null,
        problemStatement: String? = null
    ): Category {
        // 이름/설명을 InputSanitizer로 저장 경계 검증한 뒤 Slack 멘션을 중립화한다.
        val cleanName = InputSanitizer.sanitizeRequired(name, "주제 이름", CATEGORY_NAME_MAX)
        val normalizedName = SlackMentionGuard.neutralize(cleanName)
        val cleanDescription = InputSanitizer.sanitizeOptional(description, "주제 설명", CATEGORY_DESCRIPTION_MAX)
        ensureValid(maxItems in UserDeliveryScheduleService.ALLOWED_MAX_ITEMS) {
            "maxItems는 ${UserDeliveryScheduleService.ALLOWED_MAX_ITEMS.sorted().joinToString(", ")} 중 하나여야 합니다."
        }
        // 동일 이름 카테고리 중복 생성을 차단한다.
        ensureValid(categoryStore.findByName(normalizedName) == null) { "Category name already exists" }
        // 슬랙 채널 입력은 URL/별칭/접두어를 표준 ID로 정규화한다.
        val normalizedChannelId = normalizeSlackChannelInput(slackChannelId)
        // V123(Phase 3 PR1): purpose 는 enum 값 검증, background/problemStatement 는 길이 및 Slack 멘션 중립화.
        val resolvedPurpose = resolvePurpose(purpose)
        val cleanBackground = InputSanitizer.sanitizeOptional(background, "구독 배경", CATEGORY_FREEFORM_MAX)
            ?.let { SlackMentionGuard.neutralize(it) }
        val cleanProblemStatement = InputSanitizer.sanitizeOptional(problemStatement, "해결하려는 문제", CATEGORY_FREEFORM_MAX)
            ?.let { SlackMentionGuard.neutralize(it) }
        return categoryStore.save(
            Category(
                id = "",
                name = normalizedName,
                description = cleanDescription?.let { SlackMentionGuard.neutralize(it) },
                slackChannelId = normalizedChannelId,
                isPublic = isPublic,
                maxItems = maxItems,
                personaId = normalizeOptionalValue(personaId),
                purpose = resolvedPurpose,
                background = cleanBackground,
                problemStatement = cleanProblemStatement
            )
        )
    }

    /**
     * 카테고리를 수정한다.
     */
    fun updateCategory(
        id: String,
        name: String?,
        description: String?,
        slackChannelId: String?,
        isActive: Boolean?,
        isPublic: Boolean?,
        maxItems: Int?,
        personaId: String?,
        expectedUpdatedAt: Instant?,
        actorUsername: String? = null,
        purpose: String? = null,
        background: String? = null,
        problemStatement: String? = null
    ): Category {
        // 기존 카테고리를 기준으로 부분 업데이트를 계산한다.
        val existing = getCategory(id)
        if (maxItems != null) {
            ensureValid(maxItems in UserDeliveryScheduleService.ALLOWED_MAX_ITEMS) {
            "maxItems는 ${UserDeliveryScheduleService.ALLOWED_MAX_ITEMS.sorted().joinToString(", ")} 중 하나여야 합니다."
        }
        }
        // 빈 문자열 이름 입력은 기존 이름 유지로 정규화한다. Slack 멘션은 중립화한다.
        val resolvedName = name?.let { InputSanitizer.sanitizeOptional(it, "주제 이름", CATEGORY_NAME_MAX) }
            ?.let { SlackMentionGuard.neutralize(it) }
            ?: existing.name
        if (resolvedName != existing.name) {
            // 이름 변경 시에는 중복 이름 충돌을 다시 검증한다.
            val sameNameCategory = categoryStore.findByName(resolvedName)
            ensureValid(sameNameCategory == null || sameNameCategory.id == existing.id) {
                "Category name already exists"
            }
        }
        // 설명 입력이 있으면 저장 전에 길이 상한을 검증한다.
        val resolvedDescription = if (description != null) {
            InputSanitizer.sanitizeOptional(description, "주제 설명", CATEGORY_DESCRIPTION_MAX)
                ?.let { SlackMentionGuard.neutralize(it) }
        } else {
            existing.description
        }
        // 슬랙 채널 입력이 있을 때만 정규화를 거친 뒤 기존 값과 병합한다.
        val resolvedSlackChannelId = if (slackChannelId == null) {
            existing.slackChannelId
        } else {
            normalizeSlackChannelInput(slackChannelId)
        }
        // isActive 변경 시 status도 함께 동기화한다 (status가 진실의 원천).
        val resolvedIsActive = isActive ?: existing.isActive
        val resolvedStatus = when {
            isActive == true -> CategoryStatus.ACTIVE
            isActive == false -> CategoryStatus.PAUSED
            else -> existing.status
        }
        // V123(Phase 3 PR1): metadata 필드 처리.
        // - purpose: null 이면 기존값 유지, 빈 문자열이면 초기화(null), 그 외엔 enum 검증.
        // - background/problemStatement: null 이면 기존값 유지, 그 외엔 길이/Slack 멘션 정제 후 저장.
        val resolvedPurpose = when {
            purpose == null -> existing.purpose
            purpose.isBlank() -> null
            else -> resolvePurpose(purpose)
        }
        val resolvedBackground = if (background == null) {
            existing.background
        } else {
            InputSanitizer.sanitizeOptional(background, "구독 배경", CATEGORY_FREEFORM_MAX)
                ?.let { SlackMentionGuard.neutralize(it) }
        }
        val resolvedProblemStatement = if (problemStatement == null) {
            existing.problemStatement
        } else {
            InputSanitizer.sanitizeOptional(problemStatement, "해결하려는 문제", CATEGORY_FREEFORM_MAX)
                ?.let { SlackMentionGuard.neutralize(it) }
        }
        val updateCandidate = existing.copy(
            name = resolvedName,
            description = resolvedDescription,
            slackChannelId = resolvedSlackChannelId,
            isActive = resolvedIsActive,
            isPublic = isPublic ?: existing.isPublic,
            maxItems = maxItems ?: existing.maxItems,
            personaId = mergeOptionalValue(personaId, existing.personaId),
            status = resolvedStatus,
            purpose = resolvedPurpose,
            background = resolvedBackground,
            problemStatement = resolvedProblemStatement
        )
        // 편집 충돌 메시지에 실을 변경 필드 목록 (사용자가 이번 저장 요청에서 명시한 필드).
        val changedFields = buildList {
            if (name != null && resolvedName != existing.name) add("name")
            if (description != null && resolvedDescription != existing.description) add("description")
            if (slackChannelId != null && resolvedSlackChannelId != existing.slackChannelId) add("slackChannelId")
            if (isActive != null && resolvedIsActive != existing.isActive) add("isActive")
            if (isPublic != null && isPublic != existing.isPublic) add("isPublic")
            if (maxItems != null && maxItems != existing.maxItems) add("maxItems")
            if (personaId != null) add("personaId")
            if (purpose != null && resolvedPurpose != existing.purpose) add("purpose")
            if (background != null && resolvedBackground != existing.background) add("background")
            if (problemStatement != null && resolvedProblemStatement != existing.problemStatement) add("problemStatement")
        }
        // expectedUpdatedAt이 있으면 낙관적 잠금으로 동시 수정 충돌을 감지한다.
        val saved = if (expectedUpdatedAt == null) {
            categoryStore.update(updateCandidate)
        } else {
            categoryStore.updateWithExpectedUpdatedAt(updateCandidate, expectedUpdatedAt)
                ?: run {
                    val latest = categoryStore.findById(id) ?: existing
                    throw ConflictException(
                        message = "카테고리가 다른 관리자에 의해 변경되었습니다. " +
                            "새로고침 후 다시 저장해주세요.",
                        staleEditInfo = StaleEditInfo(
                            latestUpdatedAt = latest.updatedAt,
                            latestEditorName = "관리자",
                            changedFieldNames = changedFields
                        )
                    )
                }
        }
        // 저장 성공 시 통합 revision 이력에 append. 변경 필드가 없으면 스킵해 무의미한 이력을 막는다.
        if (changedFields.isNotEmpty()) {
            entityRevisionRecorder.record(
                resourceType = EntityRevisionResourceType.CATEGORY,
                resourceId = saved.id,
                editorId = actorUsername ?: "system",
                editorDisplayName = null,
                changedFields = changedFields,
                entity = saved
            )
        }
        return saved
    }

    /**
     * 카테고리를 특정 revision snapshot 값으로 복원한다.
     * 낙관적 잠금은 [expectedUpdatedAt]으로 강제하고, 복원 결과도 새 revision으로 append한다.
     */
    fun restoreFromSnapshot(
        id: String,
        snapshot: Category,
        expectedUpdatedAt: Instant,
        actorUsername: String
    ): Category {
        val existing = getCategory(id)
        // 이름 중복 방지 규칙은 복원 경로에서도 유지한다.
        if (snapshot.name != existing.name) {
            val sameName = categoryStore.findByName(snapshot.name)
            ensureValid(sameName == null || sameName.id == existing.id) { "Category name already exists" }
        }
        val changedFields = diffCategoryFields(existing, snapshot)
        if (changedFields.isEmpty()) return existing

        val candidate = existing.copy(
            name = snapshot.name,
            description = snapshot.description,
            slackChannelId = snapshot.slackChannelId,
            isActive = snapshot.isActive,
            isPublic = snapshot.isPublic,
            maxItems = snapshot.maxItems,
            personaId = snapshot.personaId,
            status = snapshot.status
        )
        val saved = categoryStore.updateWithExpectedUpdatedAt(candidate, expectedUpdatedAt)
            ?: run {
                val latest = categoryStore.findById(id) ?: existing
                throw ConflictException(
                    message = "카테고리가 다른 관리자에 의해 변경되었습니다. " +
                        "새로고침 후 다시 저장해주세요.",
                    staleEditInfo = StaleEditInfo(
                        latestUpdatedAt = latest.updatedAt,
                        latestEditorName = "관리자",
                        changedFieldNames = changedFields
                    )
                )
            }
        entityRevisionRecorder.record(
            resourceType = EntityRevisionResourceType.CATEGORY,
            resourceId = saved.id,
            editorId = actorUsername,
            editorDisplayName = null,
            changedFields = changedFields,
            entity = saved
        )
        return saved
    }

    /** 복원 시 변경 필드만 추려 이력에 남긴다. */
    private fun diffCategoryFields(current: Category, snapshot: Category): List<String> {
        val changes = mutableListOf<String>()
        if (current.name != snapshot.name) changes += "name"
        if (current.description != snapshot.description) changes += "description"
        if (current.slackChannelId != snapshot.slackChannelId) changes += "slackChannelId"
        if (current.isActive != snapshot.isActive) changes += "isActive"
        if (current.isPublic != snapshot.isPublic) changes += "isPublic"
        if (current.maxItems != snapshot.maxItems) changes += "maxItems"
        if (current.personaId != snapshot.personaId) changes += "personaId"
        if (current.status != snapshot.status) changes += "status"
        return changes
    }

    /**
     * 카테고리를 일시정지한다.
     * 이미 PAUSED 상태이면 멱등으로 현재 상태를 반환한다.
     */
    fun pauseCategory(id: String): Category {
        val category = getCategory(id)
        // 이미 일시정지 상태이면 store 호출 없이 현재 카테고리를 반환한다.
        if (category.status == CategoryStatus.PAUSED) return category
        categoryStore.pause(id)
        return getCategory(id)
    }

    /**
     * 카테고리 일시정지를 해제한다.
     * 이미 ACTIVE 상태이면 멱등으로 현재 상태를 반환한다.
     */
    fun resumeCategory(id: String): Category {
        val category = getCategory(id)
        // 이미 활성 상태이면 store 호출 없이 현재 카테고리를 반환한다.
        if (category.status == CategoryStatus.ACTIVE) return category
        categoryStore.resume(id)
        return getCategory(id)
    }

    /**
     * 카테고리를 삭제한다.
     * 활성 구독이 있으면 삭제를 거부한다.
     */
    @Transactional
    fun deleteCategory(id: String, deletedByUsername: String? = null) {
        val category = getCategory(id)
        // 활성 구독이 있는 카테고리는 삭제할 수 없다
        val activeSubscriptions = userClippingRequestStore.countApprovedGroupByCategoryId()[id] ?: 0
        ensureValid(activeSubscriptions == 0) {
            "활성 구독이 ${activeSubscriptions}건 있는 카테고리는 삭제할 수 없습니다. 구독을 먼저 해제해 주세요."
        }
        try {
            categoryStore.delete(id)
        } catch (_: DataIntegrityViolationException) {
            throw ConflictException(
                "연결된 소스가 있어 삭제할 수 없어요. 소스를 먼저 삭제하거나 다른 주제로 이동해 주세요."
            )
        }
        // 감사 로그
        val actor = auditActorResolver.resolve(deletedByUsername)
        auditLogStore.log(
            actorId = actor.id, actorName = actor.name,
            action = "DELETE", targetType = "CATEGORY",
            targetId = id, targetName = category.name
        )
    }

    /**
     * 카테고리에 연결된 소스 개수를 조회한다.
     */
    fun countSources(categoryId: String): Int =
        // 대시보드 지표 계산에 사용되는 연결 소스 수를 반환한다.
        categoryStore.countSources(categoryId)

    /** 전체 카테고리의 모니터링 통계를 일괄 조회한다. */
    fun getCategoryStats(categoryIds: List<String>): CategoryStatsBundle {
        // 승인된 구독 수를 카테고리별로 집계한다.
        val subscriberCounts = requestStore.countApprovedGroupByCategoryId()
        // 각 카테고리의 오류 상태 소스 수를 개별 조회한다.
        val errorSourceCounts = categoryIds.associateWith { sourceStore.countErrorByCategoryId(it) }
        // 각 카테고리의 마지막 발송 시간을 조회한다.
        val lastDeliveryAts = categoryIds.mapNotNull { id ->
            summaryDeliveryStore.findLatestSentByCategoryId(id)?.let { id to it.createdAt }
        }.toMap()
        return CategoryStatsBundle(subscriberCounts, errorSourceCounts, lastDeliveryAts)
    }

    private fun normalizeOptionalValue(value: String?): String? =
        value?.trim()?.ifBlank { null }

    /**
     * 입력 문자열을 [CategoryPurpose] enum 으로 해석한다.
     * null/blank 는 null 로 취급하고 허용 밖 값은 [InvalidInputException] 으로 거부한다.
     */
    private fun resolvePurpose(raw: String?): CategoryPurpose? {
        val trimmed = raw?.trim()?.ifBlank { null } ?: return null
        return runCatching { CategoryPurpose.valueOf(trimmed.uppercase()) }
            .getOrElse {
                throw com.ohmyclipping.error.InvalidInputException(
                    "purpose 는 ${CategoryPurpose.entries.joinToString(", ") { it.name }} 중 하나여야 합니다."
                )
            }
    }

    private fun mergeOptionalValue(updatedValue: String?, existingValue: String?): String? =
        if (updatedValue == null) existingValue else normalizeOptionalValue(updatedValue)

    /**
     * Slack 채널 입력을 표준 ID로 정규화한다.
     * - 입력이 비어 있으면 null을 저장한다 (DM 기본값으로 해석).
     * - 채널 링크/`#name`/`ID:Cxxx` 등 다양한 형태를 표준 ID로 변환한다.
     * - 형식이 전혀 매칭되지 않아도 [InputSanitizer]로 trim된 값 그대로 저장을 허용한다.
     *   (채널 이름 그대로 저장되는 레거시 데이터를 보호하기 위함)
     */
    private fun normalizeSlackChannelInput(raw: String?): String? {
        val trimmed = normalizeOptionalValue(raw) ?: return null
        // URL/채널 링크/접두어 형태는 표준 ID로 변환한다.
        val standardized = SlackChannelIdNormalizer.normalize(trimmed)
        return standardized ?: trimmed
    }
}
