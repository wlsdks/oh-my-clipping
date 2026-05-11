package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.dto.clipping.HotFeedbackItem
import com.clipping.mcpserver.service.dto.clipping.HotFeedbackResult
import com.clipping.mcpserver.model.SummaryFeedback
import com.clipping.mcpserver.model.SummaryFeedbackHotSummary
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.SummaryFeedbackStore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger {}

private const val ACTION_LIKE = "feedback_like"
private const val ACTION_NEUTRAL = "feedback_neutral"
private const val ACTION_DISLIKE = "feedback_dislike"
private const val FEEDBACK_TYPE_LIKE = "LIKE"
private const val FEEDBACK_TYPE_NEUTRAL = "NEUTRAL"
private const val FEEDBACK_TYPE_DISLIKE = "DISLIKE"

@Service
class SummaryFeedbackService(
    private val summaryFeedbackStore: SummaryFeedbackStore,
    private val batchSummaryStore: BatchSummaryStore,
    private val adminUserStore: AdminUserStore,
    private val objectMapper: ObjectMapper
) {

    /**
     * MCP `user_toggle_feedback` 전용 어댑터.
     *
     * @param userId 내부 사용자 UUID (admin_users.id). orchestrator 가 LLM 발화 맥락에서 주입.
     * @param summaryId 피드백 대상 BatchSummary id.
     * @param reaction LIKE / DISLIKE / NEUTRAL / NONE. NONE 이면 기존 반응을 해제한다.
     * @return 저장된 피드백(있으면) 과 사람이 읽을 메시지 페어. NONE 의 경우 first 는 null.
     */
    fun upsertFromMcp(
        userId: String,
        summaryId: String,
        reaction: String
    ): Pair<SummaryFeedback?, String> {
        // 입력 정규화 및 검증 — 사용자 식별자/요약 존재 여부를 먼저 확인한다.
        if (userId.isBlank()) {
            throw InvalidInputException("userId is required")
        }
        if (summaryId.isBlank()) {
            throw InvalidInputException("summaryId is required")
        }
        validateSummaryExists(summaryId)

        // NONE 반응은 기존 피드백을 삭제해 "반응 해제" 의미로 사용한다.
        val normalized = reaction.trim().uppercase()
        if (normalized == "NONE") {
            val removed = summaryFeedbackStore.deleteBySummaryIdAndUserId(summaryId, userId)
            val message = if (removed) "피드백이 해제되었습니다." else "이미 피드백이 없습니다."
            return null to message
        }

        val feedbackType = when (normalized) {
            FEEDBACK_TYPE_LIKE, FEEDBACK_TYPE_NEUTRAL, FEEDBACK_TYPE_DISLIKE -> normalized
            else -> throw InvalidInputException(
                "Unsupported reaction: $reaction (allowed: LIKE, DISLIKE, NEUTRAL, NONE)"
            )
        }
        val saved = summaryFeedbackStore.upsert(
            SummaryFeedback(
                id = "",
                summaryId = summaryId,
                feedbackType = feedbackType,
                userId = userId
            )
        )
        return saved to feedbackMessage(feedbackType)
    }

    fun recordFromSlackPayload(payload: String): Pair<SummaryFeedback, String> {
        val parsed = parsePayload(payload)
        val summaryId = parsed.summaryId
        if (summaryId.isBlank()) {
            throw InvalidInputException("summaryId is required")
        }
        validateSummaryExists(summaryId)
        val feedbackType = mapActionToType(parsed.actionId)
        // Slack 멤버 ID(U...)를 admin_users UUID로 변환한다.
        // FK 제약(fk_summary_feedback_user)이 admin_users.id를 참조하므로
        // Slack 멤버 ID를 그대로 넣으면 FK 위반이 발생한다.
        val resolvedUserId = resolveSlackMemberIdToUserId(parsed.userId)
        val saved = summaryFeedbackStore.upsert(
            SummaryFeedback(
                id = "",
                summaryId = summaryId,
                feedbackType = feedbackType,
                userId = resolvedUserId
            )
        )
        return saved to feedbackMessage(feedbackType)
    }

    /**
     * Slack 멤버 ID(U...)를 admin_users.id(UUID)로 변환한다.
     * 매칭되는 사용자가 없으면 Slack 멤버 ID를 그대로 반환한다 (FK 없는 환경 대응).
     */
    private fun resolveSlackMemberIdToUserId(slackMemberId: String): String {
        if (!slackMemberId.startsWith("U")) return slackMemberId
        val user = adminUserStore.findBySlackMemberId(slackMemberId)
        if (user == null) {
            log.warn { "Slack 멤버 ID에 매칭되는 사용자 없음: $slackMemberId" }
            return slackMemberId
        }
        return user.id
    }

    fun getWeeklyHotSummary(
        limit: Int,
        days: Int,
        categoryId: String?
    ): HotFeedbackResult {
        val safeLimit = limit.coerceIn(1, 50)
        val safeDays = days.coerceIn(1, 90)
        val to = Instant.now()
        val from = to.minus(safeDays.toLong(), ChronoUnit.DAYS)
        val candidates = summaryFeedbackStore.findWeeklyHot(
            from = from,
            to = to,
            limit = safeLimit,
            categoryId = categoryId
        )
        return HotFeedbackResult(
            from = from.toString(),
            to = to.toString(),
            totalCandidates = candidates.size,
            items = candidates.map { it.toResultItem() }
        )
    }

    /**
     * 피드백 대상 요약이 존재하는지 먼저 확인해 DB 제약조건 예외(500)를 방지한다.
     */
    private fun validateSummaryExists(summaryId: String) {
        if (batchSummaryStore.findById(summaryId) == null) {
            throw InvalidInputException("summaryId not found: $summaryId")
        }
    }

    private fun parsePayload(payload: String): ParsedSlackFeedback {
        val root = runCatching { objectMapper.readTree(payload) }.getOrElse {
            throw InvalidInputException("Invalid payload format")
        }
        val payloadNode = root.path("payload")
        val interactionNode = if (payloadNode.isTextual) {
            runCatching { objectMapper.readTree(payloadNode.asText()) }.getOrElse { null }
        } else {
            root
        } ?: root
        val action = interactionNode.path("actions").firstOrNull()
        if (action == null || action.isMissingNode) {
            throw InvalidInputException("No action found in payload")
        }

        return ParsedSlackFeedback(
            actionId = action.path("action_id").asText(""),
            summaryId = action.path("value").asText("").ifBlank {
                action.path("block_id").asText("").substringAfter("feedback_", "").ifBlank {
                    action.path("block_id").asText("")
                }
            },
            userId = interactionNode.path("user").path("id").asText("anonymous")
        )
    }

    private fun mapActionToType(actionId: String): String {
        val normalizedAction = actionId.substringBefore(":")
        return when (normalizedAction) {
            ACTION_LIKE -> FEEDBACK_TYPE_LIKE
            ACTION_NEUTRAL -> FEEDBACK_TYPE_NEUTRAL
            ACTION_DISLIKE -> FEEDBACK_TYPE_DISLIKE
            else -> throw InvalidInputException("Unsupported actionId: $actionId")
        }
    }

    private fun feedbackMessage(feedbackType: String): String = when (feedbackType) {
        FEEDBACK_TYPE_LIKE -> "좋아요로 반영되었습니다."
        FEEDBACK_TYPE_NEUTRAL -> "보통으로 반영되었습니다."
        FEEDBACK_TYPE_DISLIKE -> "별로로 반영되었습니다."
        else -> "피드백이 반영되었습니다."
    }

    private fun SummaryFeedbackHotSummary.toResultItem() = HotFeedbackItem(
        summaryId = summaryId,
        title = title,
        sourceLink = sourceLink,
        likeCount = likeCount,
        neutralCount = neutralCount,
        dislikeCount = dislikeCount,
        totalCount = totalCount,
        score = score
    )

    private fun JsonNode.firstOrNull(): JsonNode? {
        if (!isArray || size() == 0) return null
        return this[0]
    }

    private data class ParsedSlackFeedback(
        val actionId: String,
        val summaryId: String,
        val userId: String
    )
}
