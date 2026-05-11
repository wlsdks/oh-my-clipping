package com.clipping.mcpserver.service.pipeline

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.service.dto.clipping.PipelineStepStatus
import com.clipping.mcpserver.service.dto.clipping.PipelineStepTrace
import com.clipping.mcpserver.service.dto.clipping.PipelineOrchestrationMode
import com.clipping.mcpserver.service.dto.clipping.PipelineRunResult
import com.clipping.mcpserver.service.dto.clipping.RalphLoopStopReason
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.model.ReviewItemDecision
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.service.AdminReviewQueueService
import com.clipping.mcpserver.service.RuntimeSettingService
import com.clipping.mcpserver.service.port.ClippingPipelinePort
import com.clipping.mcpserver.service.port.PipelineCollectCategoryResult
import com.clipping.mcpserver.service.port.PipelineCollectResult
import com.clipping.mcpserver.service.port.PipelineDigestResult
import com.clipping.mcpserver.service.port.PipelineSummarizeCategoryResult
import com.clipping.mcpserver.service.port.PipelineSummarizeResult
import com.clipping.mcpserver.store.ReviewItemDecisionStore
import com.clipping.mcpserver.store.SummaryDeliveryStore
import com.clipping.mcpserver.util.TitleSimilarity
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Locale

@Service
class RalphPipelineOrchestrator(
    private val clippingPipelinePort: ClippingPipelinePort,
    private val runtimeSettingService: RuntimeSettingService,
    private val batchSummaryStore: SummaryDeliveryStore,
    private val reviewItemDecisionStore: ReviewItemDecisionStore,
    private val adminReviewQueueService: AdminReviewQueueService,
    private val metrics: ClippingMetrics
) {

    private data class CriticOutcome(
        val reviewedLowImportanceCount: Int,
        val reviewedDuplicateCount: Int
    )

    private data class SingleRunOutcome(
        val collect: PipelineCollectResult,
        val summarize: PipelineSummarizeResult,
        val digest: PipelineDigestResult,
        val critic: CriticOutcome,
        val stepTraces: List<PipelineStepTrace>,
        val warnings: List<String>
    )

    /**
     * Ralph 오케스트레이션 모드 실행.
     * Ralph 루프가 활성화되면 stop phrase 또는 무진전/최대반복 조건까지 반복 수행한다.
     */
    fun runPipeline(
        categoryId: String,
        hoursBack: Int?,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?,
        loopEnabledOverride: Boolean? = null,
        loopMaxIterationsOverride: Int? = null,
        loopStopPhraseOverride: String? = null
    ): PipelineRunResult {
        val runtime = runtimeSettingService.current()
        val resolvedHoursBack = hoursBack ?: runtime.defaultHoursBack
        val resolvedMaxItems = maxItems ?: runtime.digestDefaultMaxItems
        val resolvedUnsentOnly = unsentOnly ?: true
        val resolvedSendToSlack = sendToSlack ?: false
        val loopEnabled = loopEnabledOverride ?: runtime.ralphLoopEnabled

        if (!loopEnabled) {
            val single = runSingleIteration(
                categoryId = categoryId,
                hoursBack = resolvedHoursBack,
                maxItems = resolvedMaxItems,
                unsentOnly = resolvedUnsentOnly,
                sendToSlack = resolvedSendToSlack,
                slackChannelId = slackChannelId,
                minImportanceScore = runtime.digestMinImportanceScore,
                iteration = 1
            )
            return PipelineRunResult(
                collect = single.collect.toCollectResult(),
                summarize = single.summarize.toSummarizeResult(),
                digest = single.digest.toDigestResult(),
                orchestrationMode = PipelineOrchestrationMode.RALPH,
                fallbackApplied = false,
                orchestrationWarnings = single.warnings,
                stepTraces = single.stepTraces,
                loopEnabled = false,
                loopIterationCount = 1,
                loopStopReason = RalphLoopStopReason.LOOP_DISABLED,
                loopStopPhrase = null
            ).also { result ->
                metrics.recordRalphLoopResult(
                    stopReason = result.loopStopReason.name,
                    iterationCount = result.loopIterationCount
                )
            }
        }

        val maxIterations = (loopMaxIterationsOverride ?: runtime.ralphLoopMaxIterations).coerceIn(1, 30)
        val stopPhrase = (loopStopPhraseOverride ?: runtime.ralphLoopStopPhrase).trim().ifBlank { "RALPH_STOP" }
        val allOutcomes = mutableListOf<SingleRunOutcome>()
        val allTraces = mutableListOf<PipelineStepTrace>()
        val allWarnings = mutableListOf<String>()
        var noProgressStreak = 0
        var stopReason = RalphLoopStopReason.MAX_ITERATIONS_REACHED

        for (iteration in 1..maxIterations) {
            val outcome = runSingleIteration(
                categoryId = categoryId,
                hoursBack = resolvedHoursBack,
                maxItems = resolvedMaxItems,
                unsentOnly = resolvedUnsentOnly,
                sendToSlack = resolvedSendToSlack,
                slackChannelId = slackChannelId,
                minImportanceScore = runtime.digestMinImportanceScore,
                iteration = iteration
            )
            allOutcomes += outcome
            allTraces += outcome.stepTraces
            allWarnings += outcome.warnings

            val phraseDetected = containsStopPhrase(outcome, stopPhrase)
            if (phraseDetected) {
                stopReason = RalphLoopStopReason.STOP_PHRASE_DETECTED
                break
            }

            val progress = hasMeaningfulProgress(outcome)
            noProgressStreak = if (progress) 0 else noProgressStreak + 1
            if (noProgressStreak >= 2) {
                stopReason = RalphLoopStopReason.NO_PROGRESS
                allWarnings += "Ralph loop stopped due to no progress in 2 consecutive iterations"
                break
            }
            if (iteration == maxIterations) {
                stopReason = RalphLoopStopReason.MAX_ITERATIONS_REACHED
                allWarnings += "Ralph loop reached max iterations=$maxIterations"
            }
        }

        val finalDigest = allOutcomes.lastOrNull()?.digest
            ?: clippingPipelinePort.digest(
                categoryId = categoryId,
                maxItems = resolvedMaxItems,
                unsentOnly = resolvedUnsentOnly,
                sendToSlack = resolvedSendToSlack,
                slackChannelId = slackChannelId
            )

        return PipelineRunResult(
            collect = aggregateCollectResults(allOutcomes.map { it.collect }).toCollectResult(),
            summarize = aggregateSummarizeResults(allOutcomes.map { it.summarize }).toSummarizeResult(),
            digest = finalDigest.toDigestResult(),
            orchestrationMode = PipelineOrchestrationMode.RALPH,
            fallbackApplied = false,
            orchestrationWarnings = allWarnings.distinct(),
            stepTraces = allTraces.toList(),
            loopEnabled = true,
            loopIterationCount = allOutcomes.size.coerceAtLeast(1),
            loopStopReason = stopReason,
            loopStopPhrase = stopPhrase
        ).also { result ->
            metrics.recordRalphLoopResult(
                stopReason = result.loopStopReason.name,
                iterationCount = result.loopIterationCount
            )
        }
    }

    /**
     * Ralph 1회 실행 루프.
     */
    private fun runSingleIteration(
        categoryId: String,
        hoursBack: Int,
        maxItems: Int,
        unsentOnly: Boolean,
        sendToSlack: Boolean,
        slackChannelId: String?,
        minImportanceScore: Float,
        iteration: Int
    ): SingleRunOutcome {
        val traces = mutableListOf<PipelineStepTrace>()

        appendPlanTrace(
            traces = traces,
            hoursBack = hoursBack,
            maxItems = maxItems,
            unsentOnly = unsentOnly,
            sendToSlack = sendToSlack,
            slackChannelId = slackChannelId,
            iteration = iteration
        )

        val collect = traceStep(
            step = "ITERATION_${iteration}_COLLECT",
            traces = traces,
            action = {
                clippingPipelinePort.collect(categoryId, hoursBack)
            },
            detailBuilder = { result ->
                "newItems=${result.newItems}, duplicateSkipped=${result.duplicateSkipped}"
            }
        )

        val summarize = traceStep(
            step = "ITERATION_${iteration}_SUMMARIZE",
            traces = traces,
            action = {
                clippingPipelinePort.summarize(categoryId)
            },
            detailBuilder = { result ->
                "totalSummarized=${result.totalSummarized}"
            }
        )

        val critic = traceStep(
            step = "ITERATION_${iteration}_CRITIC_REVIEW",
            traces = traces,
            action = {
                applyCriticReview(
                    categoryId = categoryId,
                    minImportanceScore = minImportanceScore
                )
            },
            detailBuilder = { outcome ->
                "reviewedLowImportance=${outcome.reviewedLowImportanceCount}, reviewedDuplicate=${outcome.reviewedDuplicateCount}"
            }
        )

        val digest = traceStep(
            step = "ITERATION_${iteration}_DIGEST",
            traces = traces,
            action = {
                clippingPipelinePort.digest(
                    categoryId = categoryId,
                    maxItems = maxItems,
                    unsentOnly = unsentOnly,
                    sendToSlack = sendToSlack,
                    slackChannelId = slackChannelId
                )
            },
            detailBuilder = { result ->
                "selectedCount=${result.selectedCount}, postedToSlack=${result.postedToSlack}"
            }
        )

        val warnings = listOfNotNull(
            if (critic.reviewedLowImportanceCount > 0 || critic.reviewedDuplicateCount > 0) {
                "Ralph critic moved ${critic.reviewedLowImportanceCount + critic.reviewedDuplicateCount} item(s) to review queue in iteration=$iteration"
            } else {
                null
            }
        )

        return SingleRunOutcome(
            collect = collect,
            summarize = summarize,
            digest = digest,
            critic = critic,
            stepTraces = traces.toList(),
            warnings = warnings
        )
    }

    /**
     * Ralph Critic 규칙:
     * 1) 중요도 임계치 미달 항목은 REVIEW로 전환
     * 2) 제목 유사 중복 항목(후순위)은 REVIEW로 전환
     * 단, 이미 운영자가 직접 판단한(reviewedBy 존재) 항목은 덮어쓰지 않는다.
     */
    private fun applyCriticReview(
        categoryId: String,
        minImportanceScore: Float
    ): CriticOutcome {
        val unsent = batchSummaryStore.findUnsent(categoryId)
        if (unsent.isEmpty()) {
            return CriticOutcome(
                reviewedLowImportanceCount = 0,
                reviewedDuplicateCount = 0
            )
        }

        val decisions = reviewItemDecisionStore.findBySummaryIds(unsent.map { it.id })
            .associateBy { it.summaryId }

        val protectedSummaryIds = decisions.values
            .filter { !it.reviewedBy.isNullOrBlank() }
            .map { it.summaryId }
            .toSet()

        val sorted = unsent.sortedWith(
            compareByDescending<BatchSummary> { it.importanceScore }
                .thenByDescending { it.createdAt }
        )

        val primaryTitles = mutableListOf<String>()
        val duplicateReviewTargets = mutableSetOf<String>()

        for (summary in sorted) {
            if (summary.id in protectedSummaryIds) continue
            if (shouldSkipCriticUpdate(summary.id, decisions)) continue
            val normalizedTitle = normalizedTitle(summary)
            val duplicateOf = primaryTitles.firstOrNull { baseline ->
                TitleSimilarity.isDuplicate(baseline, normalizedTitle)
            }
            if (duplicateOf != null) {
                duplicateReviewTargets += summary.id
            } else {
                primaryTitles += normalizedTitle
            }
        }

        var reviewedLowImportanceCount = 0
        var reviewedDuplicateCount = 0

        for (summary in sorted) {
            if (summary.id in protectedSummaryIds) continue
            if (shouldSkipCriticUpdate(summary.id, decisions)) continue

            if (summary.id in duplicateReviewTargets) {
                adminReviewQueueService.markReview(
                    summaryId = summary.id,
                    reason = "Ralph critic: 유사 제목 중복 의심으로 검토 전환",
                    reviewedBy = "ralph-critic"
                )
                reviewedDuplicateCount++
                continue
            }

            if (summary.importanceScore < minImportanceScore) {
                adminReviewQueueService.markReview(
                    summaryId = summary.id,
                    reason = "Ralph critic: 중요도 %.2f가 임계치 %.2f 미만".format(
                        Locale.US,
                        summary.importanceScore,
                        minImportanceScore
                    ),
                    reviewedBy = "ralph-critic"
                )
                reviewedLowImportanceCount++
            }
        }

        return CriticOutcome(
            reviewedLowImportanceCount = reviewedLowImportanceCount,
            reviewedDuplicateCount = reviewedDuplicateCount
        )
    }

    private fun shouldSkipCriticUpdate(
        summaryId: String,
        decisions: Map<String, ReviewItemDecision>
    ): Boolean {
        val current = decisions[summaryId]?.status ?: return false
        return current == ReviewDecisionStatus.REVIEW || current == ReviewDecisionStatus.EXCLUDE
    }

    private fun normalizedTitle(summary: BatchSummary): String =
        (summary.translatedTitle?.takeIf { it.isNotBlank() } ?: summary.originalTitle).trim()

    private fun appendPlanTrace(
        traces: MutableList<PipelineStepTrace>,
        hoursBack: Int,
        maxItems: Int,
        unsentOnly: Boolean,
        sendToSlack: Boolean,
        slackChannelId: String?,
        iteration: Int
    ) {
        val now = Instant.now().toString()
        traces += PipelineStepTrace(
            step = "ITERATION_${iteration}_PLAN",
            status = PipelineStepStatus.SUCCEEDED,
            startedAt = now,
            endedAt = now,
            detail = "collect(hoursBack=$hoursBack) -> summarize -> critic(minScore) -> digest(maxItems=$maxItems, unsentOnly=$unsentOnly, sendToSlack=$sendToSlack, channel=${slackChannelId ?: "default"})"
        )
    }

    /**
     * 각 실행 단계를 시간/상태와 함께 기록한다.
     * 예외가 발생하면 trace에 FAILED를 남기고 상위로 전달한다.
     */
    private fun <T> traceStep(
        step: String,
        traces: MutableList<PipelineStepTrace>,
        action: () -> T,
        detailBuilder: (T) -> String?
    ): T {
        val startedAt = Instant.now()
        return try {
            val result = action()
            traces += PipelineStepTrace(
                step = step,
                status = PipelineStepStatus.SUCCEEDED,
                startedAt = startedAt.toString(),
                endedAt = Instant.now().toString(),
                detail = detailBuilder(result)
            )
            result
        } catch (e: RuntimeException) {
            traces += PipelineStepTrace(
                step = step,
                status = PipelineStepStatus.FAILED,
                startedAt = startedAt.toString(),
                endedAt = Instant.now().toString(),
                detail = e.message?.take(240)
            )
            throw e
        }
    }

    private fun hasMeaningfulProgress(outcome: SingleRunOutcome): Boolean =
        outcome.collect.newItems > 0 ||
            outcome.summarize.totalSummarized > 0 ||
            (outcome.critic.reviewedLowImportanceCount + outcome.critic.reviewedDuplicateCount) > 0

    private fun containsStopPhrase(outcome: SingleRunOutcome, stopPhrase: String): Boolean {
        val normalized = stopPhrase.trim()
        if (normalized.isBlank()) return false
        val keyword = normalized.lowercase(Locale.ROOT)
        // stop phrase는 실행 결과물(digest text)에서만 검사한다.
        // 내부 trace/warning 문자열까지 포함하면 정적 문구(예: collect(...))에 의한 오탐으로
        // 루프가 조기 종료될 수 있다.
        return outcome.digest.digestText.lowercase(Locale.ROOT).contains(keyword)
    }

    private fun aggregateCollectResults(results: List<PipelineCollectResult>): PipelineCollectResult {
        if (results.isEmpty()) {
            return PipelineCollectResult(
                totalCollected = 0,
                newItems = 0,
                duplicateSkipped = 0,
                categories = emptyList()
            )
        }
        val categoryMap = linkedMapOf<String, PipelineCollectCategoryResult>()
        for (result in results) {
            for (category in result.categories) {
                val current = categoryMap[category.categoryId]
                categoryMap[category.categoryId] = if (current == null) {
                    category
                } else {
                    current.copy(
                        collected = current.collected + category.collected,
                        newItems = current.newItems + category.newItems
                    )
                }
            }
        }
        return PipelineCollectResult(
            totalCollected = results.sumOf { it.totalCollected },
            newItems = results.sumOf { it.newItems },
            duplicateSkipped = results.sumOf { it.duplicateSkipped },
            categories = categoryMap.values.toList()
        )
    }

    private fun aggregateSummarizeResults(results: List<PipelineSummarizeResult>): PipelineSummarizeResult {
        if (results.isEmpty()) {
            return PipelineSummarizeResult(totalSummarized = 0, categories = emptyList())
        }
        val categoryMap = linkedMapOf<String, PipelineSummarizeCategoryResult>()
        for (result in results) {
            for (category in result.categories) {
                val current = categoryMap[category.categoryId]
                categoryMap[category.categoryId] = if (current == null) {
                    category
                } else {
                    current.copy(summarized = current.summarized + category.summarized)
                }
            }
        }
        return PipelineSummarizeResult(
            totalSummarized = results.sumOf { it.totalSummarized },
            categories = categoryMap.values.toList()
        )
    }
}
