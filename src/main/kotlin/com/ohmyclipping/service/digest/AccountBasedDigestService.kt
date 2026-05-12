package com.ohmyclipping.service.digest

import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.service.CategoryDigestStateService
import com.ohmyclipping.service.FeatureFlagsService
import com.ohmyclipping.service.RuntimeSettingService
import com.ohmyclipping.service.dto.clipping.DigestResult
import com.ohmyclipping.service.port.SlackDeliveryPort
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.DigestDiffLogStore
import com.ohmyclipping.store.SlackChannelDailySendCountStore
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.ZonedDateTime

private val KST: ZoneId = ZoneId.of("Asia/Seoul")
private val log = KotlinLogging.logger {}

/**
 * Account-based digest 전송 전략.
 *
 * `FeatureFlagsService.isAccountBasedDigestEnabled` 가 켜진 카테고리는 dry-run preview 결과를
 * 그대로 Slack 으로 발송한다 (요약 선정/렌더링이 사전 계산된 경로). [DigestService] 와 분리되어
 * legacy(요약 선정 + 렌더링) 경로와 책임이 섞이지 않는다.
 *
 * 반환 규약:
 * - `null` 반환 → 호출자 ([DigestService]) 가 legacy 경로로 폴백한다.
 * - `DigestResult` 반환 (sent/shadow/preview/quota-exhausted 모두 포함) → account-based 경로가 응답을 확정했다.
 *
 * 마감(`finalizeDelivery`) 도 이 클래스 안에서 수행한다 — 호출자가 결과를 받은 뒤 다시
 * `finalizePreparedDigest` 를 부르지 않아도 되도록 self-contained 하게 처리한다.
 */
@Service
class AccountBasedDigestService(
    private val categoryStore: CategoryStore,
    private val digestPreviewService: DigestPreviewService,
    private val featureFlagsService: FeatureFlagsService,
    private val digestDiffLogStore: DigestDiffLogStore,
    private val slackChannelDailySendCountStore: SlackChannelDailySendCountStore,
    private val runtimeSettingService: RuntimeSettingService,
    private val slackMessageSender: SlackDeliveryPort,
    private val categoryDigestStateService: CategoryDigestStateService,
    private val digestDeliveryFinalizationService: DigestDeliveryFinalizationService,
) {

    /**
     * 카테고리에 대한 account-based 다이제스트를 생성/전송한다.
     *
     * @return `null` 이면 호출자는 legacy 경로로 폴백한다. 그 외에는 결과를 그대로 응답한다.
     */
    fun generate(
        categoryId: String,
        sendToSlack: Boolean?,
        slackChannelId: String?,
    ): DigestResult? {
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        // dry-run 으로 account-based 렌더링 결과를 먼저 확인한다
        val preview = digestPreviewService.dryRunForCategory(categoryId)
        if (preview.mode == "EMPTY") {
            log.info { "[account-based] $categoryId — EMPTY dry-run result; falling through to legacy" }
            return null
        }

        val isShadow = featureFlagsService.isShadowModeEnabled(categoryId)
        val channelId = slackChannelId ?: category.slackChannelId
        if (channelId.isNullOrBlank()) {
            log.warn { "[account-based] $categoryId — no slack channel configured; skipping" }
            return null
        }

        // Block Kit JSON 문자열을 List<Map> 으로 파싱한다
        val blocks: List<Map<String, Any?>> = try {
            jacksonObjectMapper().readValue(
                preview.blocks,
                object : TypeReference<List<Map<String, Any?>>>() {}
            )
        } catch (e: Exception) {
            log.warn(e) { "[account-based] $categoryId — blocks JSON parse failed; falling back to legacy" }
            return null
        }

        if (isShadow) {
            // shadow 모드: Slack 전송 없이 diff row 를 기록한다
            digestDiffLogStore.insertIfAbsent(
                categoryId = categoryId,
                digestDate = ZonedDateTime.now(KST).toLocalDate(),
                legacySummary = null,
                newSummary = preview.blocks,
                newMode = preview.mode,
                sectionsCount = preview.sectionState.size,
                articlesCount = preview.sectionState.sumOf { it.articlesCount },
                crossMatchCount = preview.sectionState.sumOf { it.badgedCount },
            )
            log.info { "[account-based] $categoryId — shadow mode; Slack skipped, diff row recorded" }
            return emptyAccountResult(category.id, category.name, channelId, preview.blocks)
        }

        // sendToSlack=false 이면 preview-only 반환 (no Slack send)
        if (sendToSlack == false) {
            log.info { "[account-based] $categoryId — sendToSlack=false; preview-only" }
            return emptyAccountResult(category.id, category.name, channelId, preview.blocks)
        }

        // 일별 발송 쿼터를 예약한다 — legacy 경로와 동일한 KST 자정 경계 기준
        val sendDate = ZonedDateTime.now(KST).toLocalDate()
        val runtime = runtimeSettingService.current()
        val reservation = slackChannelDailySendCountStore.reserveSlot(
            channelId = channelId,
            sendDate = sendDate,
            dailyLimit = runtime.slackDailyChannelMessageLimit
        )
        if (!reservation.allowed) {
            log.info { "[account-based] $categoryId — daily Slack quota exhausted for $channelId; skipping" }
            return emptyAccountResult(category.id, category.name, channelId, preview.blocks)
        }

        // 실제 Slack 전송
        val fallbackText = "${category.name} account-based digest"
        val sendResult = try {
            slackMessageSender.sendMessage(
                channelId = channelId,
                text = fallbackText,
                blocks = blocks,
                botToken = runtime.slackBotToken,
            )
        } catch (e: Exception) {
            // 전송 예외 시 쿼터를 반환하고 예외를 다시 던진다
            slackChannelDailySendCountStore.releaseSlot(channelId, sendDate)
            throw e
        }

        // Slack 전송이 실패(ok=false)면 쿼터를 반환한다
        if (!sendResult.ok) {
            slackChannelDailySendCountStore.releaseSlot(channelId, sendDate)
        }

        // DUAL_SECTION 인 경우 실제 전송 성공 시에만 legend 노출 카운트를 증가시킨다
        if (preview.mode == "DUAL_SECTION" && sendResult.ok) {
            categoryDigestStateService.incrementLegendDisplayCount(categoryId)
        }

        val result = DigestResult(
            categoryId = category.id,
            categoryName = category.name,
            unsentOnly = true,
            totalCandidates = 0,
            selectedCount = 0,
            postedToSlack = sendResult.ok,
            slackChannelId = channelId,
            slackMessageTs = sendResult.ts.ifEmpty { null },
            markedSentCount = 0,
            digestText = preview.blocks,
            items = emptyList(),
            fallbackUsed = sendResult.fallbackUsed,
        )

        // 후처리 (sent 마킹/통계 등) — account-based 경로는 items 가 없으므로 idempotent
        // legacy 경로의 finalizePreparedDigest(categoryId, result) 와 동일한 효과:
        // sentSummaryIds 가 빈 리스트이므로 finalizeDelivery 가 no-op 으로 통계만 0/0 으로 기록한다.
        digestDeliveryFinalizationService.finalizeDelivery(
            summaryIds = emptyList(),
            categoryId = categoryId,
            sendAttempts = 0,
            sendSuccesses = 0,
        )

        return result
    }

    private fun emptyAccountResult(
        categoryId: String,
        categoryName: String,
        channelId: String,
        digestText: String,
    ): DigestResult =
        DigestResult(
            categoryId = categoryId,
            categoryName = categoryName,
            unsentOnly = true,
            totalCandidates = 0,
            selectedCount = 0,
            postedToSlack = false,
            slackChannelId = channelId,
            slackMessageTs = null,
            markedSentCount = 0,
            digestText = digestText,
            items = emptyList(),
        )
}
