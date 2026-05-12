package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.mcp.McpCallerContext
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.SlackMessageSender
import com.ohmyclipping.service.port.ClippingPipelinePort
import com.ohmyclipping.service.pipeline.toDigestResult
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * Admin 도구 — Slack 채널로 digest 게시.
 *
 * **외부 액션**: 호출 즉시 Slack에 메시지를 게시하고 되돌릴 수 없다.
 * 사용자가 명시적으로 전송을 확인한 경우에만 사용해야 한다.
 *
 * LLM 재시도로 인한 중복 발송을 막기 위해, `(tokenKid, categoryId, KST 날짜,
 * slackChannelId)` 조합을 [AdminSendDigestIdempotencyCache] 로 24시간 잠근다.
 * 선택 파라미터 `confirmationSummary` 에 `"{N}건 to #{채널이름}"` 형태의 확인
 * 문구를 요구해 LLM 이 무의식적으로 발송 도구를 호출하는 사고를 한 번 더 차단한다.
 */
@Component
class AdminSendDigestTool(
    private val clippingPipelinePort: ClippingPipelinePort,
    private val slackMessageSender: SlackMessageSender,
    private val rateLimiter: McpRateLimiter,
    private val idempotencyCache: AdminSendDigestIdempotencyCache,
) {

    companion object {
        /** KST 기준 하루 단위 키를 만들기 위한 존. */
        private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        private const val MIN_MAX_ITEMS = 1
        private const val MAX_MAX_ITEMS = 5

        /** confirmationSummary 포맷 예: "3건 to #tech-news" 또는 "3 to tech-news". */
        private val CONFIRMATION_PATTERN =
            Regex("^\\s*(\\d{1,3})\\s*건?\\s*to\\s*#?([A-Za-z0-9._\\-가-힣]+)\\s*$")
    }

    @Tool(
        description = """
            ⚠️ **외부 액션 — 발송 전용:** Slack 채널에 다이제스트를 즉시 게시하며, 되돌릴 수 없다.
            **역할 분리:** 미리보기는 `admin_pipeline`, 발송은 `admin_send_digest`. 이 도구는 항상 Slack 에 게시한다 (dry-run 옵션 없음).
            **언제 쓰나:** 사용자가 명시적으로 발송을 확정했을 때 ("보내줘", "네 보내줘", "post to Slack").
            **쓰지 말 것:** 사용자가 미리보기 중이거나 확신하지 못할 때 — 먼저 `admin_pipeline` 으로 확인.
            **멱등성:** 같은 (카테고리, 채널, KST 날짜) 조합은 24시간 내 중복 발송이 차단된다 (ConflictException). 날짜가 바뀌면 자동으로 다시 가능.
            **확인:** maxItems 와 slackChannelId 를 쓰는 경우 confirmationSummary 에 '{N}건 to #{채널이름}' 형태로 명시 권장 (선택).
            **파라미터:** categoryId 필수, maxItems/unsentOnly 선택, slackChannelId 선택 (생략 시 카테고리 기본 채널), confirmationSummary 선택.
            **반환:** 게시된 메시지 정보가 담긴 DigestResult.
        """,
    )
    fun admin_send_digest(
        @ToolParam(description = "다이제스트를 만들 카테고리 ID") categoryId: String,
        @ToolParam(description = "포함할 중요 아이템 최대 수", required = false) maxItems: Int?,
        @ToolParam(description = "미발송 요약만 사용할지 여부 (기본 true)", required = false) unsentOnly: Boolean?,
        @ToolParam(description = "채널 override (예: C0123ABC)", required = false) slackChannelId: String?,
        @ToolParam(
            description = "확인 요약 '{N}건 to #{채널이름}' (선택, 제공 시 maxItems/채널과 일치해야 함)",
            required = false,
        ) confirmationSummary: String?,
    ): String = mcpToolCall {
        val normalizedCategoryId = validateCategoryId(categoryId)
        val normalizedMaxItems = validateMaxItems(maxItems)
        val normalizedSlackChannelId = slackChannelId?.trim()?.ifBlank { null }
        val confirmation = confirmationSummary?.trim()?.ifBlank { null }
        val parsedConfirmation = confirmation?.let { parseConfirmationSummary(it) }
        if (parsedConfirmation != null && normalizedMaxItems != null && parsedConfirmation.itemCount != normalizedMaxItems) {
            throw InvalidInputException(
                "확인 요약의 아이템 수(${parsedConfirmation.itemCount})가 maxItems($normalizedMaxItems)와 일치하지 않습니다 — 미리보기(admin_pipeline)와 재확인 필요",
            )
        }

        // 호출 빈도 제한: 최대 2회/시간. Slack 발송은 되돌릴 수 없으므로 엄격히 제한한다.
        rateLimiter.checkOrThrow("admin_send_digest", maxRequests = 2, windowSeconds = 3600)

        // 채널 pre-flight: 명시적으로 채널을 지정한 경우 발송 직전에 존재 여부를 확인해
        // 오타/잘못된 ID로 인한 실패를 감지한다. 존재하지 않으면 NotFoundException 발생.
        // mock/테스트 환경에서 info 가 null 로 나올 수 있으므로 safe-call 로 감싼다.
        var resolvedChannelName: String? = null
        if (normalizedSlackChannelId != null) {
            val info = slackMessageSender.getChannelInfo(botToken = null, channelId = normalizedSlackChannelId)
            @Suppress("UNNECESSARY_SAFE_CALL")
            resolvedChannelName = info?.name
        }

        // confirmationSummary 가 들어온 경우 형식을 검증하고 maxItems/채널이름과 맞춰본다.
        if (parsedConfirmation != null && resolvedChannelName != null &&
            !parsedConfirmation.channelName.equals(resolvedChannelName, ignoreCase = true)
        ) {
            throw InvalidInputException(
                "확인 요약의 채널명(#${parsedConfirmation.channelName})이 실제 채널(#$resolvedChannelName)과 일치하지 않습니다 — 미리보기(admin_pipeline)와 재확인 필요",
            )
        }

        // 멱등성 키: (호출자 토큰 지문, 카테고리, KST 날짜, 채널 override) 조합.
        // slackChannelId 가 null 이면 "default" 로 치환해 "카테고리 기본 채널" 로의 반복 발송도 잠근다.
        val idempotencyKey = buildIdempotencyKey(
            actor = McpCallerContext.tokenKid() ?: "anonymous",
            categoryId = normalizedCategoryId,
            channelId = normalizedSlackChannelId,
        )
        if (!idempotencyCache.tryAcquire(idempotencyKey)) {
            throw ConflictException(
                "이미 같은 카테고리·채널·오늘자 조합으로 발송됨 — 같은 조합은 날짜가 바뀐 뒤 다시 시도하거나 다른 채널을 지정하세요",
            )
        }

        // sendToSlack 파라미터는 PR-06 에서 제거됨 (admin_pipeline 과의 역할 중복 제거).
        // 이 도구는 항상 Slack 에 게시하므로 내부적으로 true 로 고정한다.
        clippingPipelinePort.digest(
            normalizedCategoryId,
            normalizedMaxItems,
            unsentOnly,
            sendToSlack = true,
            normalizedSlackChannelId,
        )
            .toDigestResult()
    }

    private fun validateCategoryId(categoryId: String): String {
        val normalized = categoryId.trim()
        if (normalized.isBlank()) {
            throw InvalidInputException("categoryId must not be blank")
        }
        return normalized
    }

    private fun validateMaxItems(maxItems: Int?): Int? {
        if (maxItems != null && maxItems !in MIN_MAX_ITEMS..MAX_MAX_ITEMS) {
            throw InvalidInputException("maxItems must be between $MIN_MAX_ITEMS and $MAX_MAX_ITEMS")
        }
        return maxItems
    }

    private fun buildIdempotencyKey(actor: String, categoryId: String, channelId: String?): String {
        val today = LocalDate.now(KST_ZONE)
        val channelKey = channelId?.takeIf { it.isNotBlank() } ?: "default"
        return "$actor|$categoryId|$today|$channelKey"
    }

    /**
     * "3건 to #tech-news" 형태의 요약을 파싱해 아이템 수/채널명과 교차 검증한다.
     * 형식이 맞지 않으면 발송 직전에 InvalidInputException 으로 차단한다.
     */
    private fun parseConfirmationSummary(summary: String): ParsedConfirmationSummary {
        val match = CONFIRMATION_PATTERN.matchEntire(summary)
            ?: throw InvalidInputException(
                "confirmationSummary 형식이 올바르지 않습니다 — '{N}건 to #{채널이름}' 형태를 사용하세요",
            )
        return ParsedConfirmationSummary(
            itemCount = match.groupValues[1].toInt(),
            channelName = match.groupValues[2],
        )
    }

    private data class ParsedConfirmationSummary(
        val itemCount: Int,
        val channelName: String,
    )
}
