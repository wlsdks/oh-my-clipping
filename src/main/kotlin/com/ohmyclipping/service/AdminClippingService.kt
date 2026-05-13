package com.ohmyclipping.service

import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.service.dto.clipping.DigestResult
import com.ohmyclipping.service.dto.clipping.PipelineRunResult
import com.ohmyclipping.model.CategoryStatus
import com.ohmyclipping.model.RetentionPolicy
import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.service.dto.admin.ClippingSetting
import com.ohmyclipping.service.pipeline.DeterministicPipelineRunner
import com.ohmyclipping.service.pipeline.RalphPipelineOrchestrator
import com.ohmyclipping.service.pipeline.RalphLoopOverridePolicy
import com.ohmyclipping.service.pipeline.toDigestResult
import com.ohmyclipping.service.port.ClippingPipelinePort
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RetentionPolicyStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val log = KotlinLogging.logger {}

@Service
class AdminClippingService(
    private val categoryStore: CategoryStore,
    private val retentionPolicyStore: RetentionPolicyStore,
    private val clippingPipelinePort: ClippingPipelinePort,
    private val runtimeSettingService: RuntimeSettingService,
    private val ralphPipelineOrchestrator: RalphPipelineOrchestrator,
    private val deterministicPipelineRunner: DeterministicPipelineRunner,
    private val properties: ClippingMcpServerProperties
) {

    @Transactional(readOnly = true)
    fun listSettings(): List<ClippingSetting> =
        categoryStore.list()
            .sortedBy { it.name }
            .map { category ->
                val policy = retentionPolicyStore.findByCategoryId(category.id)
                toSetting(
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryUpdatedAt = category.updatedAt,
                    isActive = category.isActive,
                    slackChannelId = category.slackChannelId,
                    maxItems = category.maxItems,
                    policy = policy
                )
            }

    @Transactional(readOnly = true)
    fun getSettings(categoryId: String): ClippingSetting {
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")
        val policy = retentionPolicyStore.findByCategoryId(category.id)
        return toSetting(
            categoryId = category.id,
            categoryName = category.name,
            categoryUpdatedAt = category.updatedAt,
            isActive = category.isActive,
            slackChannelId = category.slackChannelId,
            maxItems = category.maxItems,
            policy = policy
        )
    }

    @Transactional
    fun updateSettings(
        categoryId: String,
        isActive: Boolean?,
        slackChannelId: String?,
        maxItems: Int?,
        retentionKeepDays: Int?,
        retentionEnabled: Boolean?,
        expectedCategoryUpdatedAt: Instant?
    ): ClippingSetting {
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("Category not found: $categoryId")

        if (maxItems != null) {
            ensureValid(maxItems in UserDeliveryScheduleService.ALLOWED_MAX_ITEMS) {
                "maxItems는 ${UserDeliveryScheduleService.ALLOWED_MAX_ITEMS.sorted().joinToString(", ")} 중 하나여야 합니다."
            }
        }
        if (retentionKeepDays != null) {
            ensureValid(retentionKeepDays > 0) { "retentionKeepDays must be greater than 0" }
        }

        val resolvedIsActive = isActive ?: category.isActive
        val updateCandidate = category.copy(
            isActive = resolvedIsActive,
            status = if (resolvedIsActive) CategoryStatus.ACTIVE else CategoryStatus.PAUSED,
            slackChannelId = if (slackChannelId != null) {
                slackChannelId.trim().ifBlank { null }
            } else {
                category.slackChannelId
            },
            maxItems = maxItems ?: category.maxItems
        )
        val updatedCategory = if (expectedCategoryUpdatedAt == null) {
            categoryStore.update(updateCandidate)
        } else {
            categoryStore.updateWithExpectedUpdatedAt(updateCandidate, expectedCategoryUpdatedAt)
                ?: throw ConflictException("카테고리 설정이 다른 관리자에 의해 변경되었습니다. 새로고침 후 다시 저장해주세요.")
        }

        val existingPolicy = retentionPolicyStore.findByCategoryId(categoryId)
        val updatedPolicy = if (
            retentionKeepDays != null || retentionEnabled != null || existingPolicy != null
        ) {
            retentionPolicyStore.saveOrUpdate(
                RetentionPolicy(
                    id = existingPolicy?.id ?: "",
                    categoryId = categoryId,
                    keepDays = retentionKeepDays ?: existingPolicy?.keepDays ?: properties.retentionDefaultDays,
                    isEnabled = retentionEnabled ?: existingPolicy?.isEnabled ?: true
                )
            )
        } else {
            null
        }

        return toSetting(
            categoryId = updatedCategory.id,
            categoryName = updatedCategory.name,
            categoryUpdatedAt = updatedCategory.updatedAt,
            isActive = updatedCategory.isActive,
            slackChannelId = updatedCategory.slackChannelId,
            maxItems = updatedCategory.maxItems,
            policy = updatedPolicy
        )
    }

    fun runDigest(
        categoryId: String,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?
    ): DigestResult =
        clippingPipelinePort.digest(
            categoryId = categoryId,
            maxItems = maxItems,
            unsentOnly = unsentOnly,
            sendToSlack = sendToSlack,
            slackChannelId = slackChannelId
        ).toDigestResult()

    fun runPipeline(
        categoryId: String,
        hoursBack: Int?,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?,
        ralphLoopEnabledOverride: Boolean? = null,
        ralphLoopMaxIterationsOverride: Int? = null,
        ralphLoopStopPhraseOverride: String? = null
    ): PipelineRunResult {
        RalphLoopOverridePolicy.validateMaxIterations(ralphLoopMaxIterationsOverride)

        val runtime = runtimeSettingService.current()
        if (runtime.ralphOrchestrationEnabled) {
            try {
                return ralphPipelineOrchestrator.runPipeline(
                    categoryId = categoryId,
                    hoursBack = hoursBack,
                    maxItems = maxItems,
                    unsentOnly = unsentOnly,
                    sendToSlack = sendToSlack,
                    slackChannelId = slackChannelId,
                    loopEnabledOverride = ralphLoopEnabledOverride,
                    loopMaxIterationsOverride = ralphLoopMaxIterationsOverride,
                    loopStopPhraseOverride = ralphLoopStopPhraseOverride
                )
            } catch (e: RuntimeException) {
                log.warn(e) { "Ralph orchestration failed. Falling back to deterministic pipeline for category=$categoryId" }
                return deterministicPipelineRunner.run(
                    categoryId = categoryId,
                    hoursBack = hoursBack,
                    maxItems = maxItems,
                    unsentOnly = unsentOnly,
                    sendToSlack = sendToSlack,
                    slackChannelId = slackChannelId,
                    fallbackReason = e.message
                )
            }
        }

        return deterministicPipelineRunner.run(
            categoryId = categoryId,
            hoursBack = hoursBack,
            maxItems = maxItems,
            unsentOnly = unsentOnly,
            sendToSlack = sendToSlack,
            slackChannelId = slackChannelId,
            fallbackReason = null
        )
    }

    private fun toSetting(
        categoryId: String,
        categoryName: String,
        categoryUpdatedAt: Instant,
        isActive: Boolean,
        slackChannelId: String?,
        maxItems: Int,
        policy: RetentionPolicy?
    ): ClippingSetting = ClippingSetting(
        categoryId = categoryId,
        categoryName = categoryName,
        categoryUpdatedAt = categoryUpdatedAt,
        isActive = isActive,
        slackChannelId = slackChannelId,
        maxItems = maxItems,
        retentionKeepDays = policy?.keepDays ?: properties.retentionDefaultDays,
        retentionEnabled = policy?.isEnabled ?: false,
        retentionSource = if (policy == null) "default" else "category_policy"
    )
}
