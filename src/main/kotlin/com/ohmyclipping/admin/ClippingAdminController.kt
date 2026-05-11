package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.ClippingSettingResponse
import com.ohmyclipping.admin.dto.RunDigestRequest
import com.ohmyclipping.admin.dto.RunPipelineByCategoryRequest
import com.ohmyclipping.admin.dto.RunPipelineRequest
import com.ohmyclipping.admin.dto.UpdateClippingSettingRequest
import com.ohmyclipping.service.AdminClippingService
import com.ohmyclipping.service.dto.ClippingSetting
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/clipping")
class ClippingAdminController(
    private val adminClippingService: AdminClippingService
) {
    @GetMapping("/settings")
    fun listSettings(): List<ClippingSettingResponse> =
        adminClippingService.listSettings().map { it.toResponse() }

    @GetMapping("/{categoryId}/settings")
    fun getSettings(@PathVariable categoryId: String): ClippingSettingResponse =
        adminClippingService.getSettings(categoryId).toResponse()

    @PutMapping("/{categoryId}/settings")
    fun updateSettings(
        @PathVariable categoryId: String,
        @RequestBody request: UpdateClippingSettingRequest
    ): ClippingSettingResponse =
        adminClippingService.updateSettings(
            categoryId = categoryId,
            isActive = request.isActive,
            slackChannelId = request.slackChannelId,
            maxItems = request.maxItems,
            retentionKeepDays = request.retentionKeepDays,
            retentionEnabled = request.retentionEnabled,
            expectedCategoryUpdatedAt = parseExpectedUpdatedAt(
                request.expectedCategoryUpdatedAt,
                "expectedCategoryUpdatedAt"
            )
        ).toResponse()

    @PostMapping("/{categoryId}/digest")
    fun runDigest(
        @PathVariable categoryId: String,
        @RequestBody(required = false) request: RunDigestRequest?
    ) = adminClippingService.runDigest(
        categoryId = categoryId,
        maxItems = request?.maxItems,
        unsentOnly = request?.unsentOnly,
        sendToSlack = request?.sendToSlack,
        slackChannelId = request?.slackChannelId
    )

    @PostMapping("/{categoryId}/pipeline")
    fun runPipeline(
        @PathVariable categoryId: String,
        @RequestBody(required = false) request: RunPipelineRequest?
    ) = adminClippingService.runPipeline(
        categoryId = categoryId,
        hoursBack = request?.hoursBack,
        maxItems = request?.maxItems,
        unsentOnly = request?.unsentOnly ?: true,
        sendToSlack = request?.sendToSlack ?: false,
        slackChannelId = request?.slackChannelId,
        ralphLoopEnabledOverride = request?.ralphLoopEnabled,
        ralphLoopMaxIterationsOverride = request?.ralphLoopMaxIterations,
        ralphLoopStopPhraseOverride = request?.ralphLoopStopPhrase
    )

    @PostMapping("/pipeline")
    fun runPipelineByCategory(
        @RequestBody request: RunPipelineByCategoryRequest
    ) = adminClippingService.runPipeline(
        categoryId = request.categoryId,
        hoursBack = request.hoursBack,
        maxItems = request.maxItems,
        unsentOnly = request.unsentOnly ?: true,
        sendToSlack = request.sendToSlack ?: false,
        slackChannelId = request.slackChannelId,
        ralphLoopEnabledOverride = request.ralphLoopEnabled,
        ralphLoopMaxIterationsOverride = request.ralphLoopMaxIterations,
        ralphLoopStopPhraseOverride = request.ralphLoopStopPhrase
    )

    private fun ClippingSetting.toResponse() =
        ClippingSettingResponse(
            categoryId = categoryId,
            categoryName = categoryName,
            categoryUpdatedAt = categoryUpdatedAt.toString(),
            isActive = isActive,
            slackChannelId = slackChannelId,
            maxItems = maxItems,
            retentionKeepDays = retentionKeepDays,
            retentionEnabled = retentionEnabled,
            retentionSource = retentionSource
        )
}
