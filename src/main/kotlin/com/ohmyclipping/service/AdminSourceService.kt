package com.ohmyclipping.service

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.service.source.SourceVerificationService
import com.ohmyclipping.model.EntityRevisionResourceType
import com.ohmyclipping.service.dto.CrawlHistoryResponse
import com.ohmyclipping.service.dto.CrawlLogEntry
import com.ohmyclipping.service.dto.DailyArticleCount
import com.ohmyclipping.service.dto.SourceAiCostEntry
import com.ohmyclipping.service.dto.SourceAiCostsResponse
import com.ohmyclipping.service.dto.SourceAnalyticsResponse
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.model.SourceComplianceStatus
import com.ohmyclipping.model.SourceLegalBasis
import com.ohmyclipping.model.SourceRegionType
import com.ohmyclipping.security.UrlSafetyValidator
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.StaleEditInfo
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.service.dto.BulkSourceActionResponse
import com.ohmyclipping.store.RssSourceStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.service.dto.AdminSourcePolicy
import com.ohmyclipping.support.InputSanitizer
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URISyntaxException
import java.time.Instant

/**
 * 관리자 소스 관리(등록/수정/승인/검증) 정책을 담당한다.
 */
@Service
class AdminSourceService(
    private val sourceStore: RssSourceStore,
    private val verificationService: SourceVerificationService,
    private val urlSafetyValidator: UrlSafetyValidator,
    private val categoryStore: CategoryStore,
    private val auditLogStore: com.ohmyclipping.store.AuditLogStore,
    private val crawlLogStore: com.ohmyclipping.store.SourceCrawlLogStore,
    private val llmRunStore: com.ohmyclipping.store.LlmRunStore,
    private val entityRevisionRecorder: EntityRevisionRecorder,
    private val auditActorResolver: AuditActorResolver,
    private val properties: ClippingMcpServerProperties = ClippingMcpServerProperties()
) {
    companion object {
        /** 카테고리당 최대 소스 수 */
        const val MAX_SOURCES_PER_CATEGORY = 10

        /** 소스 이름 최대 길이 — DB: rss_sources.name VARCHAR(200) */
        const val SOURCE_NAME_MAX = 200

        /** 소스 URL 최대 길이 — RFC 권장 2048 기반 */
        const val SOURCE_URL_MAX = 2000

        /** 검토 메모 최대 길이 — DB: rss_sources.review_notes TEXT */
        const val SOURCE_REVIEW_NOTES_MAX = 1000

        /** 저작권 재검토 주기(일) — `expected_review_at = terms_reviewed_at + 180 days` */
        const val COMPLIANCE_REVIEW_PERIOD_DAYS = 180L

    }

    /** 저작권 재검토 예정일을 계산한다. [termsReviewedAt] + [COMPLIANCE_REVIEW_PERIOD_DAYS]. */
    private fun expectedReviewAtFrom(termsReviewedAt: Instant?): Instant? =
        termsReviewedAt?.plusSeconds(COMPLIANCE_REVIEW_PERIOD_DAYS * 86_400)

    /**
     * 소스 목록을 조회한다.
     */
    fun listSources(categoryId: String?): List<RssSource> =
        // 카테고리 필터가 있으면 해당 범위만 조회한다.
        if (categoryId != null) sourceStore.listByCategoryId(categoryId) else sourceStore.list()

    /**
     * 검색/카테고리/저작권 조건을 적용하여 소스를 페이지네이션 조회한다.
     *
     * @param categoryId 카테고리 ID 필터 (선택)
     * @param search 이름/URL 검색어 (선택)
     * @param complianceStatus 저작권 검토 상태 필터 (선택)
     * @param offset 건너뛸 건수
     * @param limit 조회 건수
     */
    fun findAllPaged(
        categoryId: String? = null,
        search: String? = null,
        complianceStatus: SourceComplianceStatus? = null,
        offset: Int = 0,
        limit: Int = 30
    ): List<RssSource> =
        sourceStore.findAll(
            categoryId = categoryId,
            search = search,
            complianceStatus = complianceStatus,
            offset = offset.coerceAtLeast(0),
            limit = limit.coerceIn(1, 100)
        )

    /**
     * 검색/카테고리/저작권 조건에 해당하는 소스 총 건수를 반환한다.
     */
    fun countAll(
        categoryId: String? = null,
        search: String? = null,
        complianceStatus: SourceComplianceStatus? = null
    ): Int =
        sourceStore.countAll(categoryId, search, complianceStatus)

    /**
     * 재검토가 필요한 소스(만료/만료 임박/미검토) 총 건수를 반환한다.
     * 사이드바 뱃지에서 사용한다.
     */
    fun countComplianceAttention(): Int =
        sourceStore.countComplianceAttention(Instant.now())

    /**
     * 소스 단건을 조회한다.
     */
    fun getSource(id: String): RssSource =
        sourceStore.findById(id) ?: throw NotFoundException("Source not found: $id")

    /**
     * 소스를 생성한다.
     */
    fun createSource(
        name: String,
        url: String,
        sourceRegionRaw: String?,
        emoji: String?,
        categoryId: String,
        legalBasisRaw: String?,
        summaryAllowed: Boolean?,
        fulltextAllowed: Boolean?,
        reviewNotes: String?,
        crawlApproved: Boolean = false,
        approvedBy: String? = null,
        responsibilityAcknowledged: Boolean? = null
    ): RssSource {
        // 필수 입력값과 카테고리 존재 여부를 먼저 검증한다.
        val cleanName = InputSanitizer.sanitizeRequired(name, "소스 이름", SOURCE_NAME_MAX)
        ensureValid(url.length <= SOURCE_URL_MAX) { "URL은 ${SOURCE_URL_MAX}자 이내여야 합니다." }
        // 검토 메모 길이 상한을 서비스 경계에서도 다시 방어한다.
        val cleanReviewNotes = InputSanitizer.sanitizeOptional(
            reviewNotes,
            "검토 메모",
            SOURCE_REVIEW_NOTES_MAX
        )
        val normalizedCategoryId = categoryId.trim()
        ensureValid(normalizedCategoryId.isNotBlank()) { "categoryId is required" }
        ensureValid(categoryStore.findById(normalizedCategoryId) != null) {
            "Category not found: $normalizedCategoryId"
        }
        // 카테고리당 소스 수 상한을 검증한다 (최대 10개)
        val existingCount = sourceStore.listByCategoryId(normalizedCategoryId).size
        ensureValid(existingCount < MAX_SOURCES_PER_CATEGORY) {
            "카테고리당 최대 ${MAX_SOURCES_PER_CATEGORY}개의 소스만 등록할 수 있습니다. (현재: ${existingCount}개)"
        }
        // URL은 안전 검증 후 저장 가능한 형태로 정규화한다.
        val safeUrl = urlSafetyValidator.validatePublicHttpUrl(url).toString()
        if (sourceStore.existsByCategoryIdAndUrl(normalizedCategoryId, safeUrl)) {
            throw ConflictException("이미 등록된 소스 URL입니다: $safeUrl")
        }
        // 입력값이 없으면 URL 기반으로 sourceRegion을 추론한다.
        val sourceRegion = parseSourceRegion(sourceRegionRaw) ?: inferSourceRegion(safeUrl)
        // 법적 근거 기본값은 인용 허용(QUOTATION_ONLY)으로 설정한다.
        val legalBasis = parseLegalBasis(legalBasisRaw) ?: SourceLegalBasis.QUOTATION_ONLY
        // 법적 근거와 허용 옵션을 최종 정책으로 정규화한다.
        val policy = normalizePolicy(
            legalBasis = legalBasis,
            summaryAllowed = summaryAllowed ?: true,
            requestedFulltextAllowed = fulltextAllowed ?: false
        )
        // 책임 확인이 명시적으로 true가 아닌 경우 소스 생성을 거부한다.
        if (responsibilityAcknowledged == true) {
            // 책임 확인 완료 — 시각을 기록한다.
        } else if (responsibilityAcknowledged == false) {
            throw InvalidInputException("법적 검토 책임 확인이 필요합니다.")
        }
        val now = Instant.now()
        // 크롤 승인과 함께 생성되는 소스는 사용자가 URL을 이미 확인했으므로 즉시 VERIFIED로 설정한다.
        val initialVerificationStatus = if (crawlApproved) "VERIFIED" else "PENDING"
        return sourceStore.save(
            RssSource(
                id = "",
                name = cleanName,
                url = safeUrl,
                emoji = normalizeOptionalValue(emoji),
                legalBasis = policy.legalBasis,
                summaryAllowed = policy.summaryAllowed,
                fulltextAllowed = policy.fulltextAllowed,
                termsReviewedAt = now,
                expectedReviewAt = expectedReviewAtFrom(now),
                reviewNotes = cleanReviewNotes,
                sourceRegion = sourceRegion,
                categoryId = normalizedCategoryId,
                crawlApproved = crawlApproved,
                approvedBy = if (crawlApproved) approvedBy else null,
                approvedAt = if (crawlApproved) now else null,
                verificationStatus = initialVerificationStatus,
                responsibilityAcknowledgedAt = if (responsibilityAcknowledged == true) now else null
            )
        )
    }

    /**
     * 소스를 수정한다.
     */
    fun updateSource(
        id: String,
        name: String?,
        url: String?,
        sourceRegionRaw: String?,
        emoji: String?,
        isActive: Boolean?,
        categoryId: String?,
        legalBasisRaw: String?,
        summaryAllowed: Boolean?,
        fulltextAllowed: Boolean?,
        reviewNotes: String?,
        expectedUpdatedAt: Instant?,
        actorUsername: String? = null
    ): RssSource {
        // 기존 레코드를 기준으로 부분 업데이트 값을 계산한다.
        val existing = getSource(id)
        // 입력 필드별로 저장 경계 검증을 먼저 적용한다.
        val cleanName = name?.let { InputSanitizer.sanitizeOptional(it, "소스 이름", SOURCE_NAME_MAX) }
        val cleanReviewNotes = reviewNotes?.let {
            InputSanitizer.sanitizeOptional(it, "검토 메모", SOURCE_REVIEW_NOTES_MAX)
        }
        if (url != null) {
            ensureValid(url.length <= SOURCE_URL_MAX) { "URL은 ${SOURCE_URL_MAX}자 이내여야 합니다." }
        }
        val updatedUrl = url?.let { urlSafetyValidator.validatePublicHttpUrl(it).toString() } ?: existing.url
        val updatedSourceRegion = parseSourceRegion(sourceRegionRaw) ?: existing.sourceRegion
        val resolvedCategoryId = resolveCategoryId(categoryId, existing.categoryId)
        val legalBasis = parseLegalBasis(legalBasisRaw) ?: existing.legalBasis
        // 정책 필드는 별도 정규화하여 금지 조합을 사전에 차단한다.
        val policy = normalizePolicy(
            legalBasis = legalBasis,
            summaryAllowed = summaryAllowed ?: existing.summaryAllowed,
            requestedFulltextAllowed = fulltextAllowed ?: existing.fulltextAllowed
        )
        // 정책 변경 여부를 기준으로 termsReviewedAt 갱신 여부를 판단한다.
        val fulltextChanged = policy.fulltextAllowed != existing.fulltextAllowed
        val policyTouched = isPolicyTouched(
            legalBasisRaw = legalBasisRaw,
            summaryAllowed = summaryAllowed,
            fulltextAllowed = fulltextAllowed,
            reviewNotes = reviewNotes,
            fulltextChanged = fulltextChanged
        )
        val newTermsReviewedAt = if (policyTouched) Instant.now() else existing.termsReviewedAt
        // 재활성화(false → true) 감지 시 누적 실패 카운트를 리셋해 다음 크롤 주기부터 깨끗한 상태로 재평가한다.
        val reactivating = !existing.isActive && isActive == true
        val nextCrawlFailCount = if (reactivating) 0 else existing.crawlFailCount
        val updateCandidate = existing.copy(
            name = cleanName ?: existing.name,
            url = updatedUrl,
            emoji = mergeOptionalValue(emoji, existing.emoji),
            isActive = isActive ?: existing.isActive,
            categoryId = resolvedCategoryId,
            sourceRegion = updatedSourceRegion,
            legalBasis = policy.legalBasis,
            summaryAllowed = policy.summaryAllowed,
            fulltextAllowed = policy.fulltextAllowed,
            termsReviewedAt = newTermsReviewedAt,
            expectedReviewAt = if (policyTouched) expectedReviewAtFrom(newTermsReviewedAt) else existing.expectedReviewAt,
            reviewNotes = if (reviewNotes == null) existing.reviewNotes else cleanReviewNotes,
            crawlFailCount = nextCrawlFailCount
        )
        // expectedUpdatedAt이 있으면 낙관적 잠금으로 동시 수정 충돌을 검출한다.
        val saved = saveWithOptimisticLock(
            source = updateCandidate,
            expectedUpdatedAt = expectedUpdatedAt,
            conflictMessage =
                "소스가 다른 관리자에 의해 변경되었습니다. " +
                    "새로고침 후 다시 저장해주세요."
        )
        // 소스 업데이트 이력을 통합 revision 테이블에 append.
        val changedFields = diffUpdateFields(existing, saved)
        if (changedFields.isNotEmpty()) {
            entityRevisionRecorder.record(
                resourceType = EntityRevisionResourceType.RSS_SOURCE,
                resourceId = saved.id,
                editorId = actorUsername ?: "system",
                editorDisplayName = null,
                changedFields = changedFields,
                entity = saved
            )
        }
        return saved
    }

    /** updateSource/restore에서 공통으로 사용하는 변경 필드 diff. */
    private fun diffUpdateFields(current: RssSource, next: RssSource): List<String> {
        val changes = mutableListOf<String>()
        if (current.name != next.name) changes += "name"
        if (current.url != next.url) changes += "url"
        if (current.emoji != next.emoji) changes += "emoji"
        if (current.isActive != next.isActive) changes += "isActive"
        if (current.categoryId != next.categoryId) changes += "categoryId"
        if (current.sourceRegion != next.sourceRegion) changes += "sourceRegion"
        if (current.legalBasis != next.legalBasis) changes += "legalBasis"
        if (current.summaryAllowed != next.summaryAllowed) changes += "summaryAllowed"
        if (current.fulltextAllowed != next.fulltextAllowed) changes += "fulltextAllowed"
        if (current.reviewNotes != next.reviewNotes) changes += "reviewNotes"
        if (current.crawlApproved != next.crawlApproved) changes += "crawlApproved"
        // 재활성화 리셋/복원 등 실패 카운트 변동도 감사 이력에 남겨야 관리자가 원인을 추적할 수 있다.
        if (current.crawlFailCount != next.crawlFailCount) changes += "crawlFailCount"
        return changes
    }

    /**
     * 소스를 특정 revision snapshot 값으로 복원한다.
     */
    fun restoreFromSnapshot(
        id: String,
        snapshot: RssSource,
        expectedUpdatedAt: Instant,
        actorUsername: String
    ): RssSource {
        val existing = getSource(id)
        // 카테고리가 바뀌는 경우 존재 검증을 복원 경로에서도 수행한다.
        if (snapshot.categoryId != existing.categoryId) {
            ensureValid(categoryStore.findById(snapshot.categoryId) != null) {
                "Category not found: ${snapshot.categoryId}"
            }
        }
        val changedFields = diffUpdateFields(existing, snapshot)
        if (changedFields.isEmpty()) return existing

        val candidate = existing.copy(
            name = snapshot.name,
            url = snapshot.url,
            emoji = snapshot.emoji,
            isActive = snapshot.isActive,
            categoryId = snapshot.categoryId,
            sourceRegion = snapshot.sourceRegion,
            legalBasis = snapshot.legalBasis,
            summaryAllowed = snapshot.summaryAllowed,
            fulltextAllowed = snapshot.fulltextAllowed,
            reviewNotes = snapshot.reviewNotes,
            crawlApproved = snapshot.crawlApproved
        )
        val saved = saveWithOptimisticLock(
            source = candidate,
            expectedUpdatedAt = expectedUpdatedAt,
            conflictMessage = "소스가 다른 관리자에 의해 변경되었습니다. 새로고침 후 다시 저장해주세요."
        )
        entityRevisionRecorder.record(
            resourceType = EntityRevisionResourceType.RSS_SOURCE,
            resourceId = saved.id,
            editorId = actorUsername,
            editorDisplayName = null,
            changedFields = changedFields,
            entity = saved
        )
        return saved
    }

    /**
     * 승인/반려와 법적 정책 변경을 하나의 저장으로 처리해 충돌 가능성을 줄입니다.
     */
    @Transactional
    fun approveSource(
        id: String,
        approved: Boolean,
        approvedBy: String?,
        legalBasisRaw: String?,
        summaryAllowed: Boolean?,
        fulltextAllowed: Boolean?,
        reviewNotes: String?,
        expectedUpdatedAt: Instant?
    ): RssSource {
        // 승인 상태 변경도 기존 레코드를 기준으로 정책과 함께 갱신한다.
        val existing = getSource(id)
        val legalBasis = parseLegalBasis(legalBasisRaw) ?: existing.legalBasis
        val policy = normalizePolicy(
            legalBasis = legalBasis,
            summaryAllowed = summaryAllowed ?: existing.summaryAllowed,
            requestedFulltextAllowed = fulltextAllowed ?: existing.fulltextAllowed
        )
        // 정책 관련 입력이 들어온 경우 검토 시각을 현재 시각으로 갱신한다.
        val fulltextChanged = policy.fulltextAllowed != existing.fulltextAllowed
        val policyTouched = isPolicyTouched(
            legalBasisRaw = legalBasisRaw,
            summaryAllowed = summaryAllowed,
            fulltextAllowed = fulltextAllowed,
            reviewNotes = reviewNotes,
            fulltextChanged = fulltextChanged
        )
        // approved=true일 때만 승인 시각과 승인자를 기록한다.
        val approvedAt = if (approved) Instant.now() else null
        val newTermsReviewedAt = if (policyTouched) Instant.now() else existing.termsReviewedAt
        val updateCandidate = existing.copy(
            legalBasis = policy.legalBasis,
            summaryAllowed = policy.summaryAllowed,
            fulltextAllowed = policy.fulltextAllowed,
            termsReviewedAt = newTermsReviewedAt,
            expectedReviewAt = if (policyTouched) expectedReviewAtFrom(newTermsReviewedAt) else existing.expectedReviewAt,
            reviewNotes = if (reviewNotes == null) {
                existing.reviewNotes
            } else {
                InputSanitizer.sanitizeOptional(reviewNotes, "검토 메모", SOURCE_REVIEW_NOTES_MAX) ?: existing.reviewNotes
            },
            crawlApproved = approved,
            approvedBy = if (approved) approvedBy?.trim()?.ifBlank { null } else null,
            approvedAt = approvedAt
        )
        // 승인 플래그와 정책을 한 번의 저장으로 반영해 상태 불일치를 줄인다.
        return saveWithOptimisticLock(
            source = updateCandidate,
            expectedUpdatedAt = expectedUpdatedAt,
            conflictMessage =
                "소스 승인 상태가 다른 관리자에 의해 변경되었습니다. " +
                    "새로고침 후 다시 시도해주세요."
        )
    }

    /**
     * 기대 시각이 주어지면 `updated_at` 일치 조건으로 저장하여 낙관적 잠금을 적용합니다.
     *
     * 실패 시 최신 상태를 다시 읽어 [StaleEditInfo]를 [ConflictException]에 실어 던집니다.
     */
    private fun saveWithOptimisticLock(
        source: RssSource,
        expectedUpdatedAt: Instant?,
        conflictMessage: String
    ): RssSource {
        // 기대 시각이 없으면 기존 update 경로로 저장한다.
        if (expectedUpdatedAt == null) {
            return sourceStore.update(source)
        }
        // 기대 시각 불일치 시 충돌 예외를 던져 마지막 저장자만 반영되게 한다.
        val saved = sourceStore.updateWithExpectedUpdatedAt(source, expectedUpdatedAt)
        if (saved != null) return saved
        val latest = sourceStore.findById(source.id)
        throw ConflictException(
            message = conflictMessage,
            staleEditInfo = StaleEditInfo(
                latestUpdatedAt = latest?.updatedAt ?: expectedUpdatedAt,
                latestEditorName = latest?.approvedBy?.takeIf { it.isNotBlank() } ?: "관리자",
                changedFieldNames = emptyList()
            )
        )
    }

    /**
     * 소스를 삭제한다.
     */
    @Transactional
    fun deleteSource(id: String, deletedByUsername: String? = null) {
        val source = getSource(id)
        if (sourceStore.countArticlesBySourceId(id) > 0) {
            throw ConflictException(
                "연결된 데이터가 있어 삭제할 수 없어요. 관련 데이터를 먼저 정리해 주세요."
            )
        }
        try {
            sourceStore.delete(id)
        } catch (_: DataIntegrityViolationException) {
            throw ConflictException(
                "연결된 데이터가 있어 삭제할 수 없어요. 관련 데이터를 먼저 정리해 주세요."
            )
        }
        val actor = auditActorResolver.resolve(deletedByUsername)
        auditLogStore.log(
            actorId = actor.id, actorName = actor.name,
            action = "DELETE", targetType = "SOURCE",
            targetId = id, targetName = source.name
        )
    }

    /**
     * 특정 소스의 수집 통계(analytics)를 반환한다.
     *
     * @param sourceId 소스 ID
     * @param days 조회 기간 (일 수, 1~90)
     * @throws NotFoundException 소스가 존재하지 않을 때
     */
    fun getSourceAnalytics(sourceId: String, days: Int): SourceAnalyticsResponse {
        // 조회 기간을 1~90일로 제한한다.
        val safeDays = days.coerceIn(1, 90)
        val source = getSource(sourceId)
        val cutoff = java.time.LocalDateTime.now()
            .minusDays(safeDays.toLong())
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
        // 일별 기사 수를 집계한다.
        val dailyCounts = sourceStore.countDailyArticlesBySource(sourceId, cutoff)
        val totalArticles = dailyCounts.sumOf { it.second }
        val avgPerDay = if (safeDays > 0) totalArticles.toDouble() / safeDays else 0.0
        return SourceAnalyticsResponse(
            sourceId = source.id,
            sourceName = source.name,
            days = safeDays,
            totalArticles = totalArticles,
            avgArticlesPerDay = Math.round(avgPerDay * 10.0) / 10.0,
            reliabilityScore = source.reliabilityScore,
            lastSuccessAt = source.lastSuccessAt?.toString(),
            crawlFailCount = source.crawlFailCount,
            dailyArticleCounts = dailyCounts.map { (date, count) ->
                DailyArticleCount(date = date.toString(), count = count)
            }
        )
    }

    /**
     * 지정 기간(days) 동안 소스별 수집 기사 수를 반환한다.
     *
     * @param days 조회 기간 (일 수, 1~90)
     */
    fun getArticleCounts(days: Int): Map<String, Int> {
        // 조회 기간을 1~90일로 제한한다.
        val safeDays = days.coerceIn(1, 90)
        val cutoff = java.time.LocalDateTime.now()
            .minusDays(safeDays.toLong())
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
        return sourceStore.countArticlesBySource(cutoff)
    }

    /**
     * 소스 접근/robots 검증을 수행한다.
     */
    fun verifySource(id: String): String =
        // 검증 결과는 enum name으로 반환해 관리 화면에서 그대로 표시한다.
        verificationService.verify(id).name

    /**
     * 여러 소스의 접근/파싱 유효성 검증을 일괄 수행한다.
     * 개별 소스 검증 실패가 전체를 중단시키지 않으며, 부분 성공 결과를 반환한다.
     *
     * @param ids 검증할 소스 ID 목록 (빈 리스트 허용, 최대 100개)
     */
    fun bulkVerify(ids: List<String>): BulkSourceActionResponse {
        ensureValid(ids.size <= 100) { "한 번에 최대 100개까지 처리할 수 있습니다." }
        val results = mutableMapOf<String, String>()
        var successCount = 0
        var failCount = 0

        // 각 소스를 개별적으로 검증하여 부분 실패를 허용한다.
        for (id in ids) {
            try {
                verifySource(id)
                results[id] = "OK"
                successCount++
            } catch (e: Exception) {
                results[id] = e.message ?: "알 수 없는 오류"
                failCount++
            }
        }
        return BulkSourceActionResponse(
            successCount = successCount,
            failCount = failCount,
            results = results
        )
    }

    /**
     * 여러 소스를 일괄 보관(반려) 처리한다.
     * 개별 소스 처리 실패가 전체를 중단시키지 않으며, 부분 성공 결과를 반환한다.
     *
     * @param ids 보관할 소스 ID 목록 (빈 리스트 허용, 최대 100개)
     */
    fun bulkArchive(ids: List<String>): BulkSourceActionResponse {
        ensureValid(ids.size <= 100) { "한 번에 최대 100개까지 처리할 수 있습니다." }
        val results = mutableMapOf<String, String>()
        var successCount = 0
        var failCount = 0

        // 각 소스를 개별적으로 보관 처리하여 부분 실패를 허용한다.
        for (id in ids) {
            try {
                approveSource(
                    id = id,
                    approved = false,
                    approvedBy = "admin-console",
                    legalBasisRaw = null,
                    summaryAllowed = null,
                    fulltextAllowed = null,
                    reviewNotes = null,
                    expectedUpdatedAt = null
                )
                results[id] = "OK"
                successCount++
            } catch (e: Exception) {
                results[id] = e.message ?: "알 수 없는 오류"
                failCount++
            }
        }
        return BulkSourceActionResponse(
            successCount = successCount,
            failCount = failCount,
            results = results
        )
    }

    private fun parseLegalBasis(raw: String?): SourceLegalBasis? {
        // null 입력은 "변경 없음" 의미로 처리한다.
        if (raw == null) return null
        val normalized = raw.trim().uppercase()
        ensureValid(normalized.isNotBlank()) { "legalBasis must not be blank" }
        return try {
            SourceLegalBasis.valueOf(normalized)
        } catch (_: IllegalArgumentException) {
            throw InvalidInputException("Unsupported legalBasis: $raw")
        }
    }

    private fun parseSourceRegion(raw: String?): SourceRegionType? {
        // null 입력은 "변경 없음" 의미로 처리한다.
        if (raw == null) return null
        val normalized = raw.trim().uppercase()
        ensureValid(normalized.isNotBlank()) { "sourceRegion must not be blank" }
        return try {
            SourceRegionType.valueOf(normalized)
        } catch (_: IllegalArgumentException) {
            throw InvalidInputException("Unsupported sourceRegion: $raw")
        }
    }

    private fun inferSourceRegion(url: String): SourceRegionType {
        // URL 파싱 실패 시 안전하게 UNKNOWN으로 분류한다.
        val host = try {
            java.net.URI(url).host?.lowercase()
        } catch (_: URISyntaxException) {
            null
        } ?: return SourceRegionType.UNKNOWN
        // `.kr` 도메인은 국내, 나머지는 글로벌로 분류한다.
        return if (host.endsWith(".kr")) SourceRegionType.DOMESTIC else SourceRegionType.GLOBAL
    }

    private fun normalizePolicy(
        legalBasis: SourceLegalBasis,
        summaryAllowed: Boolean,
        requestedFulltextAllowed: Boolean
    ): AdminSourcePolicy {
        // 현재 운영 정책상 원문 전문 저장/요약 허용은 금지한다.
        ensureValid(!requestedFulltextAllowed) {
            "fulltextAllowed=true is not allowed by policy"
        }
        // 법적 근거가 금지(PROHIBITED)인 경우 요약/원문 모두 강제로 차단한다.
        if (legalBasis == SourceLegalBasis.PROHIBITED) {
            return AdminSourcePolicy(
                legalBasis = legalBasis,
                summaryAllowed = false,
                fulltextAllowed = false
            )
        }
        return AdminSourcePolicy(
            legalBasis = legalBasis,
            summaryAllowed = summaryAllowed,
            fulltextAllowed = false
        )
    }

    private fun isPolicyTouched(
        legalBasisRaw: String?,
        summaryAllowed: Boolean?,
        fulltextAllowed: Boolean?,
        reviewNotes: String?,
        fulltextChanged: Boolean
    ): Boolean =
        legalBasisRaw != null ||
            summaryAllowed != null ||
            fulltextAllowed != null ||
            reviewNotes != null ||
            fulltextChanged

    /**
     * 특정 소스의 크롤 이력과 가동률 통계를 반환한다.
     *
     * @param sourceId 소스 ID
     * @param days 조회 기간 (일 수, 1~90)
     * @throws NotFoundException 소스가 존재하지 않을 때
     */
    fun getCrawlHistory(sourceId: String, days: Int): CrawlHistoryResponse {
        // 조회 기간을 1~90일로 제한한다.
        val safeDays = days.coerceIn(1, 90)
        getSource(sourceId) // 소스 존재 여부를 확인한다.

        // cutoff를 Java에서 계산하여 파라미터로 전달한다.
        val cutoff = java.time.LocalDateTime.now()
            .minusDays(safeDays.toLong())
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()

        val logs = crawlLogStore.findBySourceId(sourceId, cutoff)
        val uptimePercent = crawlLogStore.getUptimePercent(sourceId, cutoff)

        // 통계 값을 집계한다.
        val successCount = logs.count { it.success }
        val failCount = logs.size - successCount
        val avgResponseTimeMs = logs
            .mapNotNull { it.responseTimeMs }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toInt()

        return CrawlHistoryResponse(
            sourceId = sourceId,
            uptimePercent = uptimePercent,
            avgResponseTimeMs = avgResponseTimeMs,
            totalCrawls = logs.size,
            successCount = successCount,
            failCount = failCount,
            logs = logs.take(50).map { log ->
                CrawlLogEntry(
                    crawledAt = log.crawledAt.toString(),
                    success = log.success,
                    articlesFound = log.articlesFound,
                    responseTimeMs = log.responseTimeMs,
                    errorMessage = log.errorMessage
                )
            }
        )
    }

    /**
     * 소스별 AI 비용 통계를 반환한다.
     *
     * @param days 조회 기간 (일 수, 1~90)
     */
    fun getAiCosts(days: Int): SourceAiCostsResponse {
        val safeDays = days.coerceIn(1, 90)
        // cutoff를 Java에서 계산하여 파라미터로 전달한다.
        val cutoff = java.time.LocalDateTime.now()
            .minusDays(safeDays.toLong())
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()

        val tokensBySource = llmRunStore.sumTokensBySource(cutoff)
        val costs = tokensBySource.mapValues { (_, stats) ->
            val (requestCount, tokensIn, tokensOut) = stats
            // 추정 비용을 계산한다.
            val estimatedUsd =
                (tokensIn * properties.llmInputCostPerMillionUsd + tokensOut * properties.llmOutputCostPerMillionUsd) / 1_000_000.0
            SourceAiCostEntry(
                requestCount = requestCount,
                tokensIn = tokensIn,
                tokensOut = tokensOut,
                estimatedUsd = Math.round(estimatedUsd * 1_000_000.0) / 1_000_000.0
            )
        }
        return SourceAiCostsResponse(costs = costs, days = safeDays)
    }

    private fun resolveCategoryId(updatedCategoryId: String?, existingCategoryId: String): String {
        // null 입력은 기존 카테고리 유지 의미로 처리한다.
        if (updatedCategoryId == null) return existingCategoryId
        val normalized = updatedCategoryId.trim()
        ensureValid(normalized.isNotBlank()) { "categoryId must not be blank" }
        ensureValid(categoryStore.findById(normalized) != null) { "Category not found: $normalized" }
        return normalized
    }

    private fun normalizeOptionalValue(value: String?): String? =
        value?.trim()?.ifBlank { null }

    private fun mergeOptionalValue(updatedValue: String?, existingValue: String?): String? =
        if (updatedValue == null) existingValue else normalizeOptionalValue(updatedValue)
}
