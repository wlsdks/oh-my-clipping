package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.dto.AutoExcludedItem
import com.clipping.mcpserver.service.dto.AutoExcludedResponse
import com.clipping.mcpserver.service.dto.BulkRevertItem
import com.clipping.mcpserver.service.dto.CategoryAccuracy
import com.clipping.mcpserver.service.dto.CategorySummary
import com.clipping.mcpserver.service.dto.RestoreFromAutoExcludeResult
import com.clipping.mcpserver.service.dto.ReviewPolicyStatusResponse
import com.clipping.mcpserver.service.dto.ReviewStatsResponse
import com.clipping.mcpserver.service.dto.ReviewSummaryResponse
import com.clipping.mcpserver.service.dto.RuleDryRunResult
import com.clipping.mcpserver.service.dto.RuleEvaluationResult
import com.clipping.mcpserver.service.dto.ScoreDistribution
import com.clipping.mcpserver.service.query.ReviewPolicyQueryHelper
import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.model.ReviewItemAudit
import com.clipping.mcpserver.model.ReviewItemDecision
import com.clipping.mcpserver.service.dto.BulkActionFailure
import com.clipping.mcpserver.service.dto.BulkActionResponse
import com.clipping.mcpserver.store.AccuracyRow
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.ReviewItemAuditStore
import com.clipping.mcpserver.store.ReviewItemDecisionStore
import com.clipping.mcpserver.store.SummaryDeliveryStore
import com.clipping.mcpserver.support.PaginationUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val CONTENT_SAFETY_EXCLUDE_THRESHOLD = 0.05

/** dry-run 기본 분석 기간 (일). */
private const val DEFAULT_DRY_RUN_DAYS = 30

/** dry-run 기본 샘플 수. UI 미리보기 행 수와 일치시킨다. */
private const val DEFAULT_DRY_RUN_MAX_SAMPLES = 5

/** dry-run 한 회 분석 상한 — DoS 방지. 최근 N 일 내 summary 가 500 개 이상이어도 앞 500 만 스캔. */
private const val DRY_RUN_MAX_ANALYZED = 500

@Service
class AdminReviewQueueService(
    private val batchSummaryStore: BatchSummaryStore,
    private val summaryDeliveryStore: SummaryDeliveryStore = batchSummaryStore,
    private val categoryStore: CategoryStore,
    private val categoryRuleService: AdminCategoryRuleService,
    private val reviewItemDecisionStore: ReviewItemDecisionStore,
    private val reviewItemAuditStore: ReviewItemAuditStore,
    private val reviewPolicyQueryHelper: ReviewPolicyQueryHelper,
    private val ruleEvaluator: ReviewPolicyRuleEvaluator
) {

    /**
     * 다이제스트 직전 정책 기반 자동 판정을 적용한다.
     *
     * 입력 제약: summaries의 categoryId는 조회 가능해야 한다(없는 카테고리는 rule lookup에서 예외 발생).
     * 부작용:
     *  - REVIEW/EXCLUDE 제안: 결정/감사 이력을 저장해 자동 발송에서 제외한다.
     *  - INCLUDE 제안 + autoApproveThreshold 활성 + importance ≥ 임계값:
     *    즉시 INCLUDE 결정을 저장해 자동 발송에 포함되도록 한다 (policy-auto 감사).
     *  - INCLUDE 제안 + 위 조건 미충족: 저장하지 않음(기존 동작 유지).
     */
    @Transactional
    fun ensurePolicyReviewDecisions(summaries: List<BatchSummary>): Map<String, ReviewItemDecision> {
        if (summaries.isEmpty()) return emptyMap()

        val now = Instant.now()
        val decisionsBySummaryId = reviewItemDecisionStore.findBySummaryIds(summaries.map { it.id })
            .associateBy { it.summaryId }
            .toMutableMap()
        val rulesByCategory = mutableMapOf<String, CategoryRule>()
        // 룰 엔진에서 필요한 Category 객체 캐시 — categoryRule 조회와 별개로 별도 lookup
        val categoriesById = mutableMapOf<String, Category?>()

        summaries.forEach { summary ->
            if (decisionsBySummaryId.containsKey(summary.id)) return@forEach

            val rule = rulesByCategory.getOrPut(summary.categoryId) {
                categoryRuleService.getCategoryRule(summary.categoryId)
            }

            // 룰 엔진 선평가 — event_type_blacklist / zero_signal 에 걸리면 즉시 자동 EXCLUDE
            val category = categoriesById.getOrPut(summary.categoryId) {
                categoryStore.findById(summary.categoryId)
            }
            if (category != null) {
                val ruleResult = ruleEvaluator.evaluate(summary, category, rule)
                if (ruleResult is RuleEvaluationResult.Exclude) {
                    val persisted = persistAutoExclude(summary, now, ruleResult.reason)
                    decisionsBySummaryId[summary.id] = persisted
                    return@forEach
                }
            }

            val suggestion = suggestStatus(summary, rule)

            // INCLUDE 제안은 autoApproveThreshold 활성 + 점수 충족 시에만 자동 승인한다.
            val persistedStatus = resolvePolicyAutoStatus(suggestion.status, rule, summary.importanceScore)
                ?: return@forEach

            // 자동 승인이면 별도 reason을 붙여 감사 이력에서 구분 가능하게 한다.
            val reason = if (persistedStatus == ReviewDecisionStatus.INCLUDE &&
                suggestion.status == ReviewDecisionStatus.INCLUDE &&
                rule.autoApproveThreshold != null
            ) {
                "자동 승인 (신뢰도 ${"%.2f".format(summary.importanceScore)} ≥ 임계값 " +
                    "${"%.2f".format(rule.autoApproveThreshold)})"
            } else {
                suggestion.reason
            }

            val persisted = reviewItemDecisionStore.upsert(
                ReviewItemDecision(
                    summaryId = summary.id,
                    categoryId = summary.categoryId,
                    status = persistedStatus,
                    suggestedStatus = suggestion.status,
                    reason = reason,
                    reviewedBy = "policy-auto",
                    reviewedAt = now
                )
            )
            reviewItemAuditStore.append(
                ReviewItemAudit(
                    id = "",
                    summaryId = summary.id,
                    categoryId = summary.categoryId,
                    fromStatus = null,
                    toStatus = persistedStatus,
                    reason = reason,
                    reviewedBy = "policy-auto",
                    reviewedAt = now
                )
            )
            decisionsBySummaryId[summary.id] = persisted
        }

        return decisionsBySummaryId
    }

    /**
     * 룰 엔진이 발동했을 때 자동 EXCLUDE 결정과 감사 이력을 함께 적재한다.
     *
     * reason prefix 는 `rule:{name}` 으로 통일해 기존 "제외 키워드 일치" / "중요도 ..." 와 구분한다.
     * reviewedBy 는 기존 정책 자동 판정과 동일한 `policy-auto` — `ReviewItemAudit.reviewedBy` 는
     * FK 가 아닌 표시용 문자열이므로 `AuditActorResolver` 는 불필요.
     */
    private fun persistAutoExclude(
        summary: BatchSummary,
        now: Instant,
        ruleName: String
    ): ReviewItemDecision {
        // 룰 reason 은 기계 판독 가능한 접두어를 붙여 기존 자연어 reason 과 구분한다
        val reason = "rule:$ruleName"
        val persisted = reviewItemDecisionStore.upsert(
            ReviewItemDecision(
                summaryId = summary.id,
                categoryId = summary.categoryId,
                status = ReviewDecisionStatus.EXCLUDE,
                suggestedStatus = ReviewDecisionStatus.EXCLUDE,
                reason = reason,
                reviewedBy = "policy-auto",
                reviewedAt = now
            )
        )
        reviewItemAuditStore.append(
            ReviewItemAudit(
                id = "",
                summaryId = summary.id,
                categoryId = summary.categoryId,
                fromStatus = null,
                toStatus = ReviewDecisionStatus.EXCLUDE,
                reason = reason,
                reviewedBy = "policy-auto",
                reviewedAt = now
            )
        )
        return persisted
    }

    /**
     * 미발송 검토함의 카테고리별 상태 집계를 반환합니다.
     */
    fun getSummary(): ReviewSummaryResponse {
        val counts = reviewItemDecisionStore.countByCategory()
        return ReviewSummaryResponse(
            totalCount = counts.sumOf { it.totalCount },
            reviewCount = counts.sumOf { it.reviewCount },
            includeCount = counts.sumOf { it.includeCount },
            excludeCount = counts.sumOf { it.excludeCount },
            categories = counts.map { c ->
                CategorySummary(
                    categoryId = c.categoryId,
                    categoryName = c.categoryName,
                    totalCount = c.totalCount,
                    reviewCount = c.reviewCount,
                    includeCount = c.includeCount,
                    excludeCount = c.excludeCount,
                    suggestedIncludeCount = c.suggestedIncludeCount
                )
            }
        )
    }

    /**
     * 지정 기간의 AI 정확도 통계를 반환합니다.
     * suggested_status가 NULL이거나 자동 정책(policy-auto)이 생성한 항목은 제외합니다.
     */
    fun getReviewStats(period: String): ReviewStatsResponse {
        val now = Instant.now()
        // 기간 파라미터를 일(day) 수로 변환한다.
        val days = when (period) {
            "30d" -> 30L
            else -> 7L
        }
        val from = now.minus(Duration.ofDays(days))
        val previousFrom = from.minus(Duration.ofDays(days))

        // 현재 기간과 이전 기간의 정확도 데이터를 조회한다.
        val currentRows = reviewItemDecisionStore.getAccuracyStats(from, now)
        val previousRows = reviewItemDecisionStore.getAccuracyStats(previousFrom, from)

        return buildStatsResponse(period, currentRows, previousRows)
    }

    /**
     * 관리자 대시보드용 카테고리별 리뷰 정책 현황을 조회합니다.
     *
     * 활성 카테고리 전체의 정책 임계값, 누적 pending, 7일 처리 지표, 평균 점수,
     * event_type 분포, 마지막 처리 시각을 단일 응답으로 제공합니다. 집계는 query helper
     * 가 수행하며 본 메서드는 응답 래핑과 `generatedAt` 시각 기록만 담당합니다.
     */
    fun getPolicyStatus(): ReviewPolicyStatusResponse =
        ReviewPolicyStatusResponse(
            categories = reviewPolicyQueryHelper.getPolicyStatus(),
            generatedAt = Instant.now(),
        )

    /**
     * importance_score 의 10 버킷 히스토그램 분포를 조회합니다.
     *
     * - [categoryId] 가 null 이면 전체 카테고리 집계. 지정되면 해당 카테고리만 필터.
     * - [days] 는 과거 기간(일). 1 ~ 90 범위를 벗어나면 양 끝으로 clamp 합니다 —
     *   관리자 UI 의 기본 프리셋(7/14/30/90) 과 일치하도록 상한을 90일로 제한합니다.
     */
    fun getScoreDistribution(categoryId: String?, days: Int): ScoreDistribution =
        reviewPolicyQueryHelper.getScoreDistribution(categoryId, days.coerceIn(1, 90))

    /**
     * 룰 dry-run — 저장 없이 제안된 룰로 최근 summaries 를 재평가해 "이 룰이 켜지면 몇 건이
     * 자동 EXCLUDE 될까" 를 미리 보여준다.
     *
     * - 기존 카테고리 rule 을 base 로 삼고, 제안된 [proposedExcludeEventTypes] 만 override 한다.
     *   (PR-3-lite 범위에서 편집 가능한 필드는 `excludeEventTypes` 단일 필드)
     * - 평가 대상은 최근 [days] 일 범위의 summary 이며, DoS 방지 상한 500 건 적용.
     * - 어떤 결정/감사 이력도 DB 에 쓰지 않는다 (read-only 시뮬레이션).
     *
     * @param categoryId 대상 카테고리 id. 존재하지 않으면 [NotFoundException].
     * @param proposedExcludeEventTypes 제안된 event_type 블랙리스트. 빈 리스트면 룰 비활성 시뮬레이션.
     * @param days 분석 기간 (일). 1..90 으로 clamp — 관리자 UI 프리셋과 일치시키기 위함.
     * @param maxSamples 상위 N 개 샘플을 반환할지. 1..50 으로 clamp.
     * @throws NotFoundException 카테고리가 존재하지 않을 때.
     * @throws InvalidInputException days/maxSamples 가 허용 범위를 벗어날 때.
     */
    @Transactional(readOnly = true)
    fun dryRunRule(
        categoryId: String,
        proposedExcludeEventTypes: List<String>,
        days: Int = DEFAULT_DRY_RUN_DAYS,
        maxSamples: Int = DEFAULT_DRY_RUN_MAX_SAMPLES,
    ): RuleDryRunResult {
        // days / maxSamples 는 사용자 입력이므로 도메인 예외로 검증 (INVALID_INPUT 400 유도)
        ensureValid(days in 1..90) { "days must be between 1 and 90" }
        ensureValid(maxSamples in 1..50) { "maxSamples must be between 1 and 50" }

        // 카테고리 존재 여부 선검증 — rule lookup 전에 404 를 먼저 내보낸다
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        // 기존 rule 을 base 로 두고 제안된 blacklist 만 override (나머지 필드는 유지)
        val baseRule = categoryRuleService.getCategoryRule(categoryId)
        val proposedRule = baseRule.copy(excludeEventTypes = proposedExcludeEventTypes)

        // 최근 N 일 범위 summary 조회 — store 기본 시그니처에 맞춰 from/to 모두 명시
        val now = Instant.now()
        val from = now.minus(Duration.ofDays(days.toLong()))
        val summaries = batchSummaryStore.findByDateRange(from, now, categoryId)
            .take(DRY_RUN_MAX_ANALYZED)

        // 각 summary 를 룰 엔진에 태워 EXCLUDE 여부 판정 (저장 없음)
        val excluded = mutableListOf<RuleDryRunResult.DryRunSample>()
        summaries.forEach { summary ->
            val result = ruleEvaluator.evaluate(summary, category, proposedRule)
            if (result is RuleEvaluationResult.Exclude) {
                excluded += RuleDryRunResult.DryRunSample(
                    summaryId = summary.id,
                    title = summary.translatedTitle?.takeIf { it.isNotBlank() } ?: summary.originalTitle,
                    eventType = summary.eventType,
                    score = summary.importanceScore,
                    reason = result.reason,
                )
            }
        }

        return RuleDryRunResult(
            analyzedCount = summaries.size,
            wouldAutoExclude = excluded.size,
            wouldStayUnchanged = summaries.size - excluded.size,
            samples = excluded.take(maxSamples),
        )
    }

    /**
     * 정책 룰이 자동 EXCLUDE 처리한 항목을 최신순으로 조회한다 (감사 뷰).
     *
     * `reviewed_by = 'policy-auto'` 이고 `status = 'EXCLUDE'` 인 레코드만 대상으로 한다.
     * reason prefix 는 `rule:event_type_blacklist` / `rule:zero_signal` 등 룰 엔진이 기록한 값.
     *
     * 입력 제약:
     *  - [days] 는 1..90. 범위를 벗어나면 coerce 로 양 끝으로 잘린다.
     *  - [size] 는 1..100 (페이지네이션 DoS 방지). coerce.
     *  - [page] 는 0 이상. 음수 입력 시 InvalidInputException.
     *
     * @param categoryId null/blank 이면 전체 카테고리.
     * @param reason null/blank 이면 전체. 그 외 `rule:` 등의 prefix 로 LIKE 필터.
     * @param days 최근 N 일 범위 (1..90).
     * @param page 0-based 페이지 번호.
     * @param size 페이지 크기 (1..100).
     */
    @Transactional(readOnly = true)
    fun listAutoExcluded(
        categoryId: String? = null,
        reason: String? = null,
        days: Int = 7,
        page: Int = 0,
        size: Int = 20,
    ): AutoExcludedResponse {
        // page 는 음수 입력 방어 — 도메인 예외로 400 을 유도 (coerceAtLeast 로 조용히 0 으로 바꾸면 버그를 숨김)
        ensureValid(page >= 0) { "page must be >= 0" }

        // 사용자 입력을 안전한 범위로 clamp — UI 의 기본 프리셋과 일치
        val safeDays = days.coerceIn(1, 90)
        val safeSize = size.coerceIn(1, 100)
        val offset = PaginationUtils.safeOffset(page, safeSize)
        val since = Instant.now().minus(Duration.ofDays(safeDays.toLong()))

        // blank 문자열은 필터 미지정과 동치로 취급 — UI 가 초기화 직후 빈 문자열을 넘기는 사례 방어
        val normalizedCategoryId = categoryId?.trim()?.ifBlank { null }
        val normalizedReason = reason?.trim()?.ifBlank { null }

        val rows = reviewItemDecisionStore.findAutoExcluded(
            since = since,
            categoryId = normalizedCategoryId,
            reasonPrefix = normalizedReason,
            limit = safeSize,
            offset = offset,
        )
        val totalCount = reviewItemDecisionStore.countAutoExcluded(
            since = since,
            categoryId = normalizedCategoryId,
            reasonPrefix = normalizedReason,
        )
        val breakdown = reviewItemDecisionStore.breakdownAutoExcludedByReason(
            since = since,
            categoryId = normalizedCategoryId,
            reasonPrefix = normalizedReason,
        )

        return AutoExcludedResponse(
            items = rows.map {
                AutoExcludedItem(
                    summaryId = it.summaryId,
                    title = it.title,
                    originalTitle = it.originalTitle,
                    translatedTitle = it.translatedTitle,
                    categoryId = it.categoryId,
                    categoryName = it.categoryName,
                    score = it.score,
                    reason = it.reason,
                    excludedAt = it.excludedAt,
                    summary = it.summary,
                    sourceUrl = it.sourceUrl,
                    sourceName = it.sourceName,
                    publishedAt = it.publishedAt,
                    eventType = it.eventType,
                    sentiment = it.sentiment,
                )
            },
            totalCount = totalCount,
            reasonBreakdown = breakdown,
        )
    }

    /**
     * 정책 룰이 자동 제외한 기사를 관리자가 REVIEW 상태로 복구한다.
     *
     * 보호 조건:
     *  - 대상 summary 의 review item 이 존재해야 한다 — 없으면 [NotFoundException].
     *  - 현재 상태가 `EXCLUDE` 이고 `reviewed_by = 'policy-auto'` 인 항목만 허용한다.
     *    사람이 직접 EXCLUDE 한 항목이나 이미 다른 상태인 항목은 [ConflictException].
     *
     * 부작용:
     *  - `clipping_review_items` 를 REVIEW 로 upsert 하고 reviewed_by/at 을 복구자 이름/현재시각으로 갱신.
     *  - `clipping_review_item_audits` 에 이력 append — reason = `"manual_restore_from_auto_exclude"`.
     *
     * @param summaryId 복구할 summary id.
     * @param actor 복구자 표시명 (controller 에서 `authentication.name` 전달).
     *   `ReviewItemAudit.reviewedBy` 는 FK 가 아닌 display-only 문자열이라 그대로 저장해도 안전.
     */
    @Transactional
    fun restoreFromAutoExclude(summaryId: String, actor: String): RestoreFromAutoExcludeResult {
        // 조회 대상 확인 — 존재 안 하면 404 로 빠르게 반환
        val current = reviewItemDecisionStore.findBySummaryId(summaryId)
            ?: throw NotFoundException("Review item not found: $summaryId")

        // 정책 자동 제외만 허용 — 사람이 한 제외는 다른 엔드포인트로 되돌려야 함 (bulk-revert 등)
        if (current.status != ReviewDecisionStatus.EXCLUDE || current.reviewedBy != "policy-auto") {
            throw ConflictException(
                "Not a policy-auto EXCLUDE item (status=${current.status}, reviewedBy=${current.reviewedBy})"
            )
        }

        val now = Instant.now()
        val normalizedActor = actor.trim().ifBlank { "admin" }

        reviewItemDecisionStore.upsert(
            ReviewItemDecision(
                summaryId = summaryId,
                categoryId = current.categoryId,
                status = ReviewDecisionStatus.REVIEW,
                // 기존 suggestedStatus 는 유지해 AI 제안 이력이 손실되지 않게 한다
                suggestedStatus = current.suggestedStatus,
                reason = "manual_restore_from_auto_exclude",
                reviewedBy = normalizedActor,
                reviewedAt = now,
            )
        )
        // 감사 이력 append — fromStatus=EXCLUDE, toStatus=REVIEW 로 전이가 명확하게 기록됨
        reviewItemAuditStore.append(
            ReviewItemAudit(
                id = "",
                summaryId = summaryId,
                categoryId = current.categoryId,
                fromStatus = ReviewDecisionStatus.EXCLUDE,
                toStatus = ReviewDecisionStatus.REVIEW,
                reason = "manual_restore_from_auto_exclude",
                reviewedBy = normalizedActor,
                reviewedAt = now,
            )
        )

        return RestoreFromAutoExcludeResult(summaryId = summaryId, newStatus = "REVIEW")
    }

    /**
     * 검토함 항목을 조회합니다.
     *
     * 입력 제약:
     *  - limit은 1..300 범위여야 한다. 초과 시 InvalidInputException.
     *  - perCategory가 null이 아니면 1..limit 범위여야 한다.
     *  - categoryId가 지정되면 해당 카테고리가 존재해야 한다.
     *
     * perCategory가 지정되면 카테고리별 top-N(우선순위 기준)을 먼저 뽑아
     * 합집합을 만든 뒤 priority/importance/createdAt로 최종 정렬해 limit까지 반환한다.
     * 특정 카테고리 필터가 걸린 경우(categoryId != null) perCategory는 무시된다 —
     * 이미 단일 카테고리로 범위가 좁혀졌기 때문이다.
     */
    fun listReviewItems(
        categoryId: String?,
        statusRaw: String?,
        limit: Int,
        perCategory: Int? = null
    ): List<ReviewQueueItem> {
        ensureValid(limit in 1..300) { "limit must be between 1 and 300" }
        if (perCategory != null) {
            ensureValid(perCategory in 1..limit) { "perCategory must be between 1 and limit($limit)" }
        }
        if (categoryId != null) {
            categoryStore.findById(categoryId) ?: throw NotFoundException("Category not found: $categoryId")
        }

        val statusFilter = parseStatus(statusRaw)
        val categoryNames = categoryStore.list().associateBy({ it.id }, { it.name })

        val summaries = summaryDeliveryStore.findUnsent(categoryId)

        if (summaries.isEmpty()) return emptyList()

        val decisionsBySummaryId = reviewItemDecisionStore.findBySummaryIds(summaries.map { it.id })
            .associateBy { it.summaryId }
        val rulesByCategory = mutableMapOf<String, CategoryRule>()

        // 각 summary를 ReviewQueueItem으로 변환하면서 status 필터를 적용한다.
        val items = summaries.mapNotNull { summary ->
            val decision = decisionsBySummaryId[summary.id]
            val rule = rulesByCategory.getOrPut(summary.categoryId) {
                categoryRuleService.getCategoryRule(summary.categoryId)
            }
            val suggestion = suggestStatus(summary, rule)
            val currentStatus = decision?.status ?: suggestion.status

            if (statusFilter != null && currentStatus != statusFilter) {
                return@mapNotNull null
            }
            val priority = computePriority(summary, currentStatus)

            ReviewQueueItem(
                summaryId = summary.id,
                categoryId = summary.categoryId,
                categoryName = categoryNames[summary.categoryId] ?: summary.categoryId,
                title = summary.translatedTitle?.takeIf { it.isNotBlank() } ?: summary.originalTitle,
                summary = summary.summary,
                sourceLink = summary.sourceLink,
                keywords = summary.keywords,
                importanceScore = summary.importanceScore,
                suggestedStatus = suggestion.status,
                currentStatus = currentStatus,
                statusReason = decision?.reason ?: suggestion.reason,
                reviewedBy = decision?.reviewedBy,
                reviewedAt = decision?.reviewedAt,
                priorityScore = priority.score,
                priorityLabel = priority.label,
                createdAt = summary.createdAt
            )
        }

        // 카테고리별 top-N 샘플링: 단일 카테고리 필터가 있으면 의미 없으므로 생략한다.
        val sampled = if (perCategory != null && categoryId == null) {
            applyPerCategorySampling(items, perCategory)
        } else {
            items
        }

        return sampled.sortedWith(
            compareByDescending<ReviewQueueItem> { it.priorityScore }
                .thenByDescending { it.importanceScore }
                .thenByDescending { it.createdAt }
        ).take(limit)
    }

    /**
     * 검토 항목을 포함 상태로 승인합니다.
     */
    @Transactional
    fun approve(summaryId: String, reason: String?, reviewedBy: String?): ReviewItemDecision =
        updateDecision(
            summaryId = summaryId,
            status = ReviewDecisionStatus.INCLUDE,
            reason = reason,
            reviewedBy = reviewedBy
        )

    /**
     * 검토 항목을 제외 상태로 처리합니다.
     */
    @Transactional
    fun exclude(summaryId: String, reason: String?, reviewedBy: String?): ReviewItemDecision =
        updateDecision(
            summaryId = summaryId,
            status = ReviewDecisionStatus.EXCLUDE,
            reason = reason,
            reviewedBy = reviewedBy
        )

    /**
     * 검토 항목을 REVIEW 상태로 되돌립니다.
     */
    @Transactional
    fun markReview(summaryId: String, reason: String?, reviewedBy: String?): ReviewItemDecision =
        updateDecision(
            summaryId = summaryId,
            status = ReviewDecisionStatus.REVIEW,
            reason = reason,
            reviewedBy = reviewedBy
        )

    /**
     * 다수의 검토 항목을 한 번에 포함 상태로 승인합니다.
     * 이미 동일 상태인 항목은 ALREADY_PROCESSED로 실패 처리하고 나머지는 정상 처리합니다.
     */
    @Transactional
    fun bulkApprove(ids: List<String>, reviewNote: String?): BulkActionResponse =
        bulkUpdateStatus(ids, ReviewDecisionStatus.INCLUDE, reviewNote)

    /**
     * 다수의 검토 항목을 한 번에 제외 상태로 처리합니다.
     * 이미 동일 상태인 항목은 ALREADY_PROCESSED로 실패 처리하고 나머지는 정상 처리합니다.
     */
    @Transactional
    fun bulkExclude(ids: List<String>, reviewNote: String?): BulkActionResponse =
        bulkUpdateStatus(ids, ReviewDecisionStatus.EXCLUDE, reviewNote)

    /**
     * 각 항목을 개별 지정된 이전 상태로 되돌립니다.
     * 일괄 REVIEW 복원이 아닌 항목별 previousStatus를 각각 적용합니다.
     */
    @Transactional
    fun bulkRevert(reverts: List<BulkRevertItem>): BulkActionResponse {
        // 벌크 되돌리기 건수 범위를 검증한다 (1~100건). 도메인 예외로 400 응답을 유도한다.
        ensureValid(reverts.size in 1..100) { "벌크 되돌리기는 1~100건까지 가능합니다" }

        val ids = reverts.map { it.id }
        val existingByIds = reviewItemDecisionStore.findBySummaryIds(ids).associateBy { it.summaryId }
        // 결정이 없는 항목의 categoryId를 얻기 위해 batchSummary를 조회한다
        val idsWithoutDecision = ids.filter { existingByIds[it] == null }
        val summaryById = if (idsWithoutDecision.isNotEmpty()) {
            batchSummaryStore.findByIds(idsWithoutDecision).associateBy { it.id }
        } else {
            emptyMap()
        }
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<BulkActionFailure>()
        val audits = mutableListOf<ReviewItemAudit>()
        val now = Instant.now()

        for (revert in reverts) {
            val decision = existingByIds[revert.id]
            // previousStatus를 파싱한다; 잘못된 값이면 실패 처리한다
            val targetStatus = try {
                ReviewDecisionStatus.valueOf(revert.previousStatus)
            } catch (_: IllegalArgumentException) {
                failed.add(
                    BulkActionFailure(
                        id = revert.id,
                        reason = "알 수 없는 상태: ${revert.previousStatus}",
                        code = "INVALID_STATUS"
                    )
                )
                continue
            }

            // 결정과 summary 모두 없으면 FK 위반을 유발하므로 항목 단위로 실패 처리한다.
            val resolvedCategoryId = decision?.categoryId ?: summaryById[revert.id]?.categoryId
            if (resolvedCategoryId == null) {
                failed.add(
                    BulkActionFailure(
                        id = revert.id,
                        reason = "해당 summary를 찾을 수 없어요",
                        code = "NOT_FOUND"
                    )
                )
                continue
            }

            val fromStatus = decision?.status ?: targetStatus
            val categoryId = resolvedCategoryId

            // 결정 상태를 이전 상태(previousStatus)로 upsert한다
            reviewItemDecisionStore.upsert(
                ReviewItemDecision(
                    summaryId = revert.id,
                    categoryId = categoryId,
                    status = targetStatus,
                    suggestedStatus = decision?.suggestedStatus,
                    reason = "벌크 처리 취소",
                    reviewedBy = "admin",
                    reviewedAt = now
                )
            )
            succeeded.add(revert.id)

            // 감사 이력을 배치 적재용 목록에 추가한다
            audits.add(
                ReviewItemAudit(
                    id = UUID.randomUUID().toString(),
                    summaryId = revert.id,
                    categoryId = categoryId,
                    fromStatus = fromStatus,
                    toStatus = targetStatus,
                    reason = "벌크 처리 취소",
                    reviewedBy = "admin",
                    reviewedAt = now,
                    createdAt = now
                )
            )
        }

        // 성공 항목의 감사 이력을 단일 배치로 저장한다
        if (audits.isNotEmpty()) reviewItemAuditStore.batchAppend(audits)
        return BulkActionResponse(succeeded = succeeded, failed = failed)
    }

    /**
     * 검토 항목의 이력을 조회합니다.
     */
    fun listAudits(summaryId: String, limit: Int): List<ReviewQueueAuditItem> {
        ensureValid(limit in 1..200) { "limit must be between 1 and 200" }
        batchSummaryStore.findById(summaryId)
            ?: throw NotFoundException("Batch summary not found: $summaryId")
        return reviewItemAuditStore.listBySummaryId(summaryId, limit).map { audit ->
            ReviewQueueAuditItem(
                id = audit.id,
                summaryId = audit.summaryId,
                categoryId = audit.categoryId,
                fromStatus = audit.fromStatus,
                toStatus = audit.toStatus,
                reason = audit.reason,
                reviewedBy = audit.reviewedBy,
                reviewedAt = audit.reviewedAt,
                createdAt = audit.createdAt
            )
        }
    }

    private fun buildStatsResponse(
        period: String,
        currentRows: List<AccuracyRow>,
        previousRows: List<AccuracyRow>
    ): ReviewStatsResponse {
        val total = currentRows.sumOf { it.count }
        val matched = currentRows.filter { it.suggestedStatus == it.actualStatus }.sumOf { it.count }

        // 전체 정확도 = AI 제안과 실제 결과가 일치한 건 / 전체 검토 건수
        val overallAccuracy = if (total > 0) matched.toDouble() / total else 0.0

        // INCLUDE 정확도 = AI가 INCLUDE 제안 중 최종 INCLUDE 비율
        val totalIncludeSuggested = currentRows.filter { it.suggestedStatus == "INCLUDE" }.sumOf { it.count }
        val matchedInclude = currentRows.filter {
            it.suggestedStatus == "INCLUDE" && it.actualStatus == "INCLUDE"
        }.sumOf { it.count }
        val includeAccuracy = if (totalIncludeSuggested > 0) matchedInclude.toDouble() / totalIncludeSuggested else 0.0

        // EXCLUDE 정확도 = AI가 EXCLUDE 제안 중 최종 EXCLUDE 비율
        val totalExcludeSuggested = currentRows.filter { it.suggestedStatus == "EXCLUDE" }.sumOf { it.count }
        val matchedExclude = currentRows.filter {
            it.suggestedStatus == "EXCLUDE" && it.actualStatus == "EXCLUDE"
        }.sumOf { it.count }
        val excludeAccuracy = if (totalExcludeSuggested > 0) matchedExclude.toDouble() / totalExcludeSuggested else 0.0

        // 이전 기간 정확도 (데이터가 없으면 null)
        val prevTotal = previousRows.sumOf { it.count }
        val prevMatched = previousRows.filter { it.suggestedStatus == it.actualStatus }.sumOf { it.count }
        val previousPeriodAccuracy = if (prevTotal > 0) prevMatched.toDouble() / prevTotal else null

        // 카테고리별 집계
        val categoryBreakdown = currentRows.groupBy { it.categoryId to it.categoryName }
            .map { (key, rows) ->
                val catTotal = rows.sumOf { it.count }
                val catMatched = rows.filter { it.suggestedStatus == it.actualStatus }.sumOf { it.count }
                val catIncTotal = rows.filter { it.suggestedStatus == "INCLUDE" }.sumOf { it.count }
                val catIncMatched = rows.filter {
                    it.suggestedStatus == "INCLUDE" && it.actualStatus == "INCLUDE"
                }.sumOf { it.count }
                val catExcTotal = rows.filter { it.suggestedStatus == "EXCLUDE" }.sumOf { it.count }
                val catExcMatched = rows.filter {
                    it.suggestedStatus == "EXCLUDE" && it.actualStatus == "EXCLUDE"
                }.sumOf { it.count }
                CategoryAccuracy(
                    categoryId = key.first,
                    categoryName = key.second,
                    totalReviewed = catTotal,
                    accuracy = if (catTotal > 0) catMatched.toDouble() / catTotal else 0.0,
                    includeAccuracy = if (catIncTotal > 0) catIncMatched.toDouble() / catIncTotal else 0.0,
                    excludeAccuracy = if (catExcTotal > 0) catExcMatched.toDouble() / catExcTotal else 0.0
                )
            }

        return ReviewStatsResponse(
            period = period,
            totalReviewed = total,
            overallAccuracy = overallAccuracy,
            includeAccuracy = includeAccuracy,
            excludeAccuracy = excludeAccuracy,
            overriddenCount = total - matched,
            previousPeriodAccuracy = previousPeriodAccuracy,
            categoryBreakdown = categoryBreakdown
        )
    }

    private fun bulkUpdateStatus(
        ids: List<String>,
        targetStatus: ReviewDecisionStatus,
        reviewNote: String?
    ): BulkActionResponse {
        // 벌크 처리 건수 범위를 검증한다 (1~100건). 도메인 예외로 400 응답을 유도한다.
        ensureValid(ids.size in 1..100) {
            "벌크 처리는 1~100건까지 가능합니다 (요청: ${ids.size}건)"
        }

        val existingByIds = reviewItemDecisionStore.findBySummaryIds(ids).associateBy { it.summaryId }
        // 결정이 없는 항목의 categoryId를 얻기 위해 batchSummary를 조회한다
        val idsWithoutDecision = ids.filter { existingByIds[it] == null }
        val summaryById = if (idsWithoutDecision.isNotEmpty()) {
            batchSummaryStore.findByIds(idsWithoutDecision).associateBy { it.id }
        } else {
            emptyMap()
        }

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<BulkActionFailure>()
        val audits = mutableListOf<ReviewItemAudit>()
        val now = Instant.now()

        for (id in ids) {
            val decision = existingByIds[id]
            // 이미 대상 상태인 항목은 중복 처리로 간주해 실패 목록에 추가한다
            if (decision != null && decision.status == targetStatus) {
                failed.add(
                    BulkActionFailure(
                        id = id,
                        reason = "이미 ${targetStatus.name} 상태",
                        code = "ALREADY_PROCESSED"
                    )
                )
                continue
            }

            // 결정과 summary 모두 없으면 FK 위반을 유발하므로 항목 단위로 실패 처리한다.
            // (decision=null + summary=null → category_id="" 저장 시 전체 트랜잭션 롤백)
            val resolvedCategoryId = decision?.categoryId ?: summaryById[id]?.categoryId
            if (resolvedCategoryId == null) {
                failed.add(
                    BulkActionFailure(
                        id = id,
                        reason = "해당 summary를 찾을 수 없어요",
                        code = "NOT_FOUND"
                    )
                )
                continue
            }

            val categoryId = resolvedCategoryId
            val fromStatus = decision?.status ?: ReviewDecisionStatus.REVIEW
            // 결정 상태를 대상 상태로 upsert한다
            reviewItemDecisionStore.upsert(
                ReviewItemDecision(
                    summaryId = id,
                    categoryId = categoryId,
                    status = targetStatus,
                    suggestedStatus = decision?.suggestedStatus,
                    reason = reviewNote,
                    reviewedBy = "admin",
                    reviewedAt = now
                )
            )
            succeeded.add(id)

            // 감사 이력을 배치 적재용 목록에 추가한다
            audits.add(
                ReviewItemAudit(
                    id = UUID.randomUUID().toString(),
                    summaryId = id,
                    categoryId = categoryId,
                    fromStatus = fromStatus,
                    toStatus = targetStatus,
                    reason = reviewNote ?: "벌크 ${targetStatus.name}",
                    reviewedBy = "admin",
                    reviewedAt = now,
                    createdAt = now
                )
            )
        }

        // 성공 항목의 감사 이력을 단일 배치로 저장한다
        if (audits.isNotEmpty()) reviewItemAuditStore.batchAppend(audits)
        return BulkActionResponse(succeeded = succeeded, failed = failed)
    }

    private fun updateDecision(
        summaryId: String,
        status: ReviewDecisionStatus,
        reason: String?,
        reviewedBy: String?
    ): ReviewItemDecision {
        val summary = batchSummaryStore.findById(summaryId)
            ?: throw NotFoundException("Batch summary not found: $summaryId")
        val previous = reviewItemDecisionStore.findBySummaryId(summaryId)
        val normalizedReason = reason?.trim()?.ifBlank { null }
        val normalizedReviewer = reviewedBy?.trim()?.ifBlank { null }
        val reviewedAt = Instant.now()

        val persisted = reviewItemDecisionStore.upsert(
            ReviewItemDecision(
                summaryId = summaryId,
                categoryId = summary.categoryId,
                status = status,
                reason = normalizedReason,
                reviewedBy = normalizedReviewer,
                reviewedAt = reviewedAt
            )
        )
        reviewItemAuditStore.append(
            ReviewItemAudit(
                id = "",
                summaryId = summaryId,
                categoryId = summary.categoryId,
                fromStatus = previous?.status,
                toStatus = status,
                reason = normalizedReason,
                reviewedBy = normalizedReviewer,
                reviewedAt = reviewedAt
            )
        )
        return persisted
    }

    /**
     * 정책-자동 저장 대상 status를 결정한다.
     * - REVIEW/EXCLUDE: 그대로 반환 (항상 저장).
     * - INCLUDE + autoApproveThreshold 활성 + importance ≥ 임계값: INCLUDE 자동 승인.
     * - INCLUDE + 임계값 미충족 또는 비활성: null (저장하지 않음, 기존 동작 유지).
     */
    private fun resolvePolicyAutoStatus(
        suggested: ReviewDecisionStatus,
        rule: CategoryRule,
        importance: Float
    ): ReviewDecisionStatus? {
        if (suggested == ReviewDecisionStatus.REVIEW || suggested == ReviewDecisionStatus.EXCLUDE) {
            return suggested
        }
        // INCLUDE 제안 — 자동 승인 임계값이 설정된 경우에만 즉시 INCLUDE 저장
        val threshold = rule.autoApproveThreshold ?: return null
        return if (importance.toDouble() >= threshold) ReviewDecisionStatus.INCLUDE else null
    }

    /**
     * 카테고리별 top-N 샘플링을 적용한다.
     * 각 카테고리 내에서 priorityScore DESC + importance DESC + createdAt DESC 정렬 후
     * 상위 perCategory개씩 뽑아 union한 리스트를 반환한다.
     */
    private fun applyPerCategorySampling(
        items: List<ReviewQueueItem>,
        perCategory: Int
    ): List<ReviewQueueItem> =
        items.groupBy { it.categoryId }
            .values
            .flatMap { bucket ->
                bucket.sortedWith(
                    compareByDescending<ReviewQueueItem> { it.priorityScore }
                        .thenByDescending { it.importanceScore }
                        .thenByDescending { it.createdAt }
                ).take(perCategory)
            }

    private fun suggestStatus(summary: BatchSummary, rule: CategoryRule): SuggestedStatus {
        val haystack = buildString {
            append(summary.originalTitle)
            append('\n')
            append(summary.translatedTitle ?: "")
            append('\n')
            append(summary.summary)
            append('\n')
            append(summary.keywords.joinToString(" "))
        }.lowercase()

        val matchedExclude = rule.excludeKeywords.firstOrNull { haystack.contains(it.lowercase()) }
        if (matchedExclude != null && rule.autoExcludeEnabled) {
            return SuggestedStatus(
                status = ReviewDecisionStatus.EXCLUDE,
                reason = "제외 키워드 일치: $matchedExclude"
            )
        }

        val matchedInclude = rule.includeKeywords.firstOrNull { haystack.contains(it.lowercase()) }
        if (matchedInclude != null) {
            return SuggestedStatus(
                status = ReviewDecisionStatus.INCLUDE,
                reason = "포함 키워드 일치: $matchedInclude"
            )
        }

        val score = summary.importanceScore.toDouble()
        // 콘텐츠 안전성 정책: importance 0은 광고/스팸/혐오/가짜뉴스로 판단된 항목이므로 즉시 제외
        if (score <= CONTENT_SAFETY_EXCLUDE_THRESHOLD) {
            return SuggestedStatus(
                status = ReviewDecisionStatus.EXCLUDE,
                reason = "콘텐츠 안전성 자동 제외 (importance=${"%.2f".format(score)})"
            )
        }
        if (score >= rule.includeThreshold) {
            return SuggestedStatus(
                status = ReviewDecisionStatus.INCLUDE,
                reason = "중요도 ${"%.2f".format(score)}가 포함 임계치 ${"%.2f".format(rule.includeThreshold)} 이상"
            )
        }

        if (score >= rule.reviewThreshold) {
            return SuggestedStatus(
                status = ReviewDecisionStatus.REVIEW,
                reason = "중요도 ${"%.2f".format(score)}가 검토 임계치 ${"%.2f".format(rule.reviewThreshold)} 이상"
            )
        }

        if (rule.uncertainToReview) {
            return SuggestedStatus(
                status = ReviewDecisionStatus.REVIEW,
                reason = "자동 분류 확신 부족 항목은 검토함으로 전환"
            )
        }

        if (rule.autoExcludeEnabled) {
            return SuggestedStatus(
                status = ReviewDecisionStatus.EXCLUDE,
                reason = "검토 임계치 미달 항목 자동 제외"
            )
        }

        return SuggestedStatus(
            status = ReviewDecisionStatus.INCLUDE,
            reason = "기본 정책에 따라 자동 포함"
        )
    }

    private fun computePriority(summary: BatchSummary, currentStatus: ReviewDecisionStatus): Priority {
        var score = when (currentStatus) {
            ReviewDecisionStatus.REVIEW -> 90
            ReviewDecisionStatus.INCLUDE -> 60
            ReviewDecisionStatus.EXCLUDE -> 45
        }

        score += (summary.importanceScore * 40).toInt().coerceIn(0, 40)
        val ageHours = Duration.between(summary.createdAt, Instant.now()).toHours()
        score += when {
            ageHours <= 24 -> 25
            ageHours <= 72 -> 12
            else -> 0
        }

        val label = when {
            score >= 120 -> "높음"
            score >= 85 -> "보통"
            else -> "낮음"
        }
        return Priority(score = score, label = label)
    }

    private fun parseStatus(raw: String?): ReviewDecisionStatus? {
        if (raw == null) return null
        val normalized = raw.trim().uppercase()
        if (normalized.isBlank() || normalized == "ALL") return null
        return try {
            ReviewDecisionStatus.valueOf(normalized)
        } catch (_: IllegalArgumentException) {
            throw InvalidInputException("Unsupported status: $raw")
        }
    }

    private data class SuggestedStatus(
        val status: ReviewDecisionStatus,
        val reason: String
    )

    private data class Priority(
        val score: Int,
        val label: String
    )
}

data class ReviewQueueItem(
    val summaryId: String,
    val categoryId: String,
    val categoryName: String,
    val title: String,
    val summary: String,
    val sourceLink: String,
    val keywords: List<String>,
    val importanceScore: Float,
    val suggestedStatus: ReviewDecisionStatus,
    val currentStatus: ReviewDecisionStatus,
    val statusReason: String,
    val reviewedBy: String?,
    val reviewedAt: Instant?,
    val priorityScore: Int,
    val priorityLabel: String,
    val createdAt: Instant
)

data class ReviewQueueAuditItem(
    val id: String,
    val summaryId: String,
    val categoryId: String,
    val fromStatus: ReviewDecisionStatus?,
    val toStatus: ReviewDecisionStatus,
    val reason: String?,
    val reviewedBy: String?,
    val reviewedAt: Instant?,
    val createdAt: Instant
)
