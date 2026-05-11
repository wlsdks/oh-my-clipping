package com.ohmyclipping.service.digest

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.service.dto.clipping.DigestItemResult
import com.ohmyclipping.model.ReviewDecisionStatus
import com.ohmyclipping.model.ReviewItemDecision
import com.ohmyclipping.model.SummaryFeedbackHotSummary
import com.ohmyclipping.service.AdminReviewQueueService
import com.ohmyclipping.service.RuntimeSettingService
import com.ohmyclipping.service.digest.DigestCandidate
import com.ohmyclipping.service.digest.DigestCandidateSelectionPolicy
import com.ohmyclipping.service.port.LlmSummarizationPort
import com.ohmyclipping.store.DigestCandidateStore
import com.ohmyclipping.store.SummaryFeedbackStore
import org.springframework.core.env.Environment
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 다이제스트 후보 기사를 선정·필터링하는 서비스.
 *
 * 담당 책임:
 * - 룩백 윈도우 안의 후보 요약을 DB 에서 조회하고 미발송 플래그를 적용한다.
 * - 관리자 검토 결정(EXCLUDE/REVIEW)을 후보에서 제거한다.
 * - 최근 피드백 점수를 가져와 랭킹에 반영한다.
 * - 유사 기사(제목/의미 유사도) 를 중복 제거한다.
 * - 소프트 페널티 Greedy 선택으로 소스 다양성을 유도하며 maxItems 를 충족시킨다.
 * - Slack 출력용으로 제목/요약 번역 및 이모지 정규화를 적용해 `DigestItemResult` 를 만든다.
 *
 * Slack 전송 자체는 담당하지 않는다 — 그 책임은 `DigestService` 에 있다.
 *
 * 이 클래스는 Spring Bean 이 아니다 — `DigestService` 가 자신이 받은 의존성으로 직접 인스턴스화한다.
 * 선정 서비스가 별도 Bean 이 되면 기존 DigestService 단위 테스트의 생성자 시그니처가 깨진다.
 */
class DigestSelectionService(
    private val digestCandidateStore: DigestCandidateStore,
    private val adminReviewQueueService: AdminReviewQueueService,
    private val summaryFeedbackStore: SummaryFeedbackStore,
    // 렌더러는 이모지 중복 제거(`sanitizeSummaryForDisplay`) 때문에만 주입 — 번역 결과를 카드로 보내기 전 정규화.
    private val renderer: DigestRenderer,
    private val summarizer: LlmSummarizationPort,
    private val environment: Environment
) {

    companion object {
        private const val LAMBDA_PROPERTY = "clipping.digest.fair_share.lambda"
        private const val MIN_RAW_SCORE_PROPERTY = "clipping.digest.fair_share.min_raw_score"
        private const val LAMBDA_DEFAULT = 0.15
        private const val MIN_RAW_SCORE_DEFAULT = 0.3
        private const val LOOKBACK_HOURS_PROPERTY = "clipping.digest.lookback_hours"
        private const val LOOKBACK_HOURS_DEFAULT = 168L  // 7일 — 시의성 없는 오래된 기사를 다이제스트에서 제외
    }

    /**
     * 다이제스트 후보 요약을 조회하고 리뷰 결정을 반영해 최종 후보 목록과 소스 맵을 반환한다.
     *
     * @return Pair(필터링된 후보 목록, summaryId → rssSourceId 사이드카 맵)
     */
    fun fetchAndFilterCandidates(
        categoryId: String,
        onlyUnsent: Boolean
    ): Pair<List<BatchSummary>, Map<String, String?>> {
        // 런타임 설정 가능한 룩백 윈도우로 후보를 rss_source_id 사이드카와 함께 조회한다
        val lookbackHours = environment.getProperty(
            LOOKBACK_HOURS_PROPERTY,
            LOOKBACK_HOURS_DEFAULT.toString()
        ).toLong()
        val since = Instant.now().minus(lookbackHours, ChronoUnit.HOURS)
        val (fetched, sourceMap) = if (onlyUnsent) {
            digestCandidateStore.findDigestCandidatesWithSource(categoryId, since, limit = 1000)
                .let { (summaries, map) -> summaries.filter { !it.isSentToSlack } to map }
        } else {
            digestCandidateStore.findDigestCandidatesWithSource(categoryId, since, limit = 1000)
        }
        // 관리자 검토 정책에 따라 제외/보류 항목을 걸러낸다
        val decisionsBySummaryId = adminReviewQueueService.ensurePolicyReviewDecisions(fetched)
        val filtered = applyReviewDecisionsToDigestCandidates(fetched, decisionsBySummaryId)
        return filtered to sourceMap
    }

    /**
     * 중요도 점수, 피드백 신호, 소스 다양성을 고려하여 다이제스트에 포함할 항목을 선정한다.
     *
     * @param sourceMap summaryId → rssSourceId 사이드카 맵 (fetchAndFilterCandidates 에서 전달)
     */
    fun selectDigestItems(
        category: Category,
        candidates: List<BatchSummary>,
        sourceMap: Map<String, String?>,
        maxItems: Int?,
        runtime: RuntimeSettingService.RuntimeSettings
    ): List<DigestItemResult> {
        // 최근 피드백 신호를 로드한다
        val feedbackSignals = loadRecentFeedbackSignals(
            categoryId = category.id,
            candidateCount = candidates.size
        )
        // 후보를 엔진 후보 모델로 변환하고 선정 policy 에 위임한다
        val resolvedMaxItems = (
            maxItems
                ?: if (category.maxItems > 0) category.maxItems
                else runtime.digestDefaultMaxItems
            ).coerceIn(1, 7)
        val allRanked = candidates.map { summary ->
            val feedback = feedbackSignals[summary.id]
            RankedCandidate(
                summary = summary,
                rssSourceId = sourceMap[summary.id],
                combinedScore = summary.importanceScore.toDouble() + feedbackBoost(feedback),
                importanceScore = summary.importanceScore.toDouble(),
                createdAt = summary.createdAt,
                id = summary.id
            )
        }
        val selected = selectionPolicy().select(
            candidates = allRanked.map { it.toDigestCandidate() },
            maxItems = resolvedMaxItems,
            minImportanceScore = runtime.digestMinImportanceScore.toDouble()
        )
        val selectedSummaries = selected.mapNotNull { selectedCandidate ->
            allRanked.firstOrNull { it.id == selectedCandidate.id }?.summary
        }
        // 선정 사유를 생성하고 Slack 형식으로 변환한다
        val reasonBySummaryId = selectedSummaries.associate { summary ->
            summary.id to buildSelectionReason(
                summary = summary,
                minImportanceScore = runtime.digestMinImportanceScore,
                feedback = feedbackSignals[summary.id]
            )
        }
        return selectedSummaries
            .map { it.toDigestItem(reasonBySummaryId[it.id].orEmpty()) }
            .map { localizeDigestItemForSlack(it) }
    }

    // -- ranking & dedup --

    // @VisibleForTesting — internal 로 노출해 테스트 모듈에서 직접 생성할 수 있게 한다
    internal data class RankedCandidate(
        val summary: BatchSummary,
        val rssSourceId: String?,
        val combinedScore: Double,
        val importanceScore: Double,
        val createdAt: Instant,
        val id: String,
    )

    private fun selectionPolicy(): DigestCandidateSelectionPolicy =
        DigestCandidateSelectionPolicy(
            lambda = environment.getProperty(LAMBDA_PROPERTY, LAMBDA_DEFAULT.toString()).toDouble(),
            minRawScore = environment.getProperty(MIN_RAW_SCORE_PROPERTY, MIN_RAW_SCORE_DEFAULT.toString()).toDouble()
        )

    private fun RankedCandidate.toDigestCandidate(): DigestCandidate {
        val title = summary.translatedTitle?.takeIf { it.isNotBlank() } ?: summary.originalTitle
        return DigestCandidate(
            id = id,
            title = title,
            summary = summary.summary,
            keywords = summary.keywords,
            importanceScore = importanceScore,
            combinedScore = combinedScore,
            sourceId = rssSourceId,
            sourceLink = summary.sourceLink,
            createdAt = createdAt,
            isFallback = summary.isFallback
        )
    }

    /**
     * 소스 다양성을 유도하는 소프트 페널티 Greedy 선택.
     * 각 선택 단계에서 effectiveScore = combinedScore − λ × (이미 선택된 같은 소스 수) 로 평가하여
     * 한 소스가 독점하지 못하도록 유도하되 점수 격차가 큰 기사는 여전히 선택되게 한다.
     */
    // @VisibleForTesting — internal 로 노출해 테스트 모듈에서 알고리즘을 직접 검증할 수 있게 한다
    internal fun selectWithSoftPenalty(
        candidates: List<RankedCandidate>,
        maxItems: Int,
    ): List<BatchSummary> =
        selectionPolicy()
            .selectWithSoftPenalty(candidates.map { it.toDigestCandidate() }, maxItems)
            .mapNotNull { selected -> candidates.firstOrNull { it.id == selected.id }?.summary }

    /** 최근 피드백 점수를 요약별로 조회해 다이제스트 선별에 반영한다 */
    private fun loadRecentFeedbackSignals(
        categoryId: String,
        candidateCount: Int
    ): Map<String, SummaryFeedbackHotSummary> {
        if (candidateCount <= 0) return emptyMap()
        val to = Instant.now()
        val from = to.minus(30, ChronoUnit.DAYS)
        val rows = summaryFeedbackStore.findWeeklyHot(
            from = from,
            to = to,
            limit = candidateCount.coerceIn(1, 500),
            categoryId = categoryId
        )
        return rows.associateBy { it.summaryId }
    }

    private fun feedbackBoost(feedback: SummaryFeedbackHotSummary?): Double {
        if (feedback == null || feedback.totalCount <= 0) return 0.0
        val likeDislikeDelta =
            (feedback.likeCount - feedback.dislikeCount).toDouble() / feedback.totalCount.toDouble()
        val volume = feedback.totalCount.coerceAtMost(20).toDouble() / 20.0
        return (likeDislikeDelta * 0.12 + volume * 0.03).coerceIn(-0.15, 0.15)
    }

    /** 다이제스트 카드별 선정 근거 문구를 생성한다 */
    private fun buildSelectionReason(
        summary: BatchSummary,
        minImportanceScore: Float,
        feedback: SummaryFeedbackHotSummary?
    ): String {
        val reasons = mutableListOf<String>()
        val scoreLabel = "%.2f".format(summary.importanceScore)
        val thresholdLabel = "%.2f".format(minImportanceScore)
        if (summary.importanceScore >= minImportanceScore) {
            reasons += "중요도 $scoreLabel (기준 $thresholdLabel) 통과"
        } else {
            reasons += "중요도 $scoreLabel (기준 $thresholdLabel) 미달, 상위 후보 보정 선발"
        }
        if (feedback != null && feedback.totalCount > 0) {
            reasons += "피드백 +${feedback.likeCount}/-${feedback.dislikeCount} (총 ${feedback.totalCount}) 반영"
        }
        return reasons.joinToString(" · ").take(220)
    }

    /** 검토함에서 제외/보류로 처리된 항목은 다이제스트 후보에서 제거한다 */
    private fun applyReviewDecisionsToDigestCandidates(
        candidates: List<BatchSummary>,
        decisionsBySummaryId: Map<String, ReviewItemDecision>
    ): List<BatchSummary> {
        if (candidates.isEmpty()) return emptyList()
        return candidates.filter { summary ->
            when (decisionsBySummaryId[summary.id]?.status) {
                ReviewDecisionStatus.EXCLUDE,
                ReviewDecisionStatus.REVIEW -> false
                else -> true
            }
        }
    }

    // -- localization --

    private fun BatchSummary.toDigestItem(whyImportant: String): DigestItemResult {
        val title = translatedTitle?.takeIf { it.isNotBlank() } ?: originalTitle
        return DigestItemResult(
            summaryId = id,
            title = title,
            summary = summary,
            keywords = keywords,
            importanceScore = importanceScore,
            whyImportant = whyImportant,
            sourceLink = sourceLink,
            createdAt = createdAt.toString(),
            isFallback = isFallback
        )
    }

    private fun localizeDigestItemForSlack(item: DigestItemResult): DigestItemResult {
        val localizedTitle = localizeTextForSlack(
            text = item.title,
            context = "제목"
        )
        val localizedSummary = localizeTextForSlack(
            text = item.summary,
            context = "요약"
        )
        val sanitizedSummary = renderer.sanitizeSummaryForDisplay(localizedSummary)
        if (localizedTitle == item.title && sanitizedSummary == item.summary) {
            return item
        }
        return item.copy(
            title = localizedTitle,
            summary = sanitizedSummary
        )
    }

    private fun localizeTextForSlack(text: String, context: String): String {
        val normalized = text.trim()
        if (normalized.isBlank()) return normalized
        if (isLikelyKorean(normalized)) return normalized

        val translated = runCatching {
            summarizer.translateToKorean(normalized, context)
        }.getOrNull()

        return translated?.trim()?.takeIf { it.isNotBlank() } ?: normalized
    }

    private fun isLikelyKorean(text: String): Boolean {
        val letters = text.count { it.isLetter() }
        if (letters < 2) return false
        val koreanLetters = text.count { it in '\uAC00'..'\uD7A3' }
        val englishLetters = text.count {
            it.lowercaseChar() in 'a'..'z' || it.uppercaseChar() in 'A'..'Z'
        }
        val koreanRatio = koreanLetters.toDouble() / letters
        val englishRatio = englishLetters.toDouble() / letters
        return koreanRatio >= 0.55 && englishRatio <= 0.45
    }
}
