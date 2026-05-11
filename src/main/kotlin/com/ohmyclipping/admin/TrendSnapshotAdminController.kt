package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.GenerateVisualCardRequest
import com.ohmyclipping.admin.dto.PublishTrendSnapshotRequest
import com.ohmyclipping.admin.dto.RunTrendSnapshotRequest
import com.ohmyclipping.admin.dto.TrendSnapshotResponse
import com.ohmyclipping.admin.dto.TrendVisualCardResponse
import com.ohmyclipping.service.AdminTrendSnapshotService
import com.ohmyclipping.service.TrendSnapshotResult
import com.ohmyclipping.service.TrendVisualCardResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/trend-snapshots")
class TrendSnapshotAdminController(
    private val trendSnapshotService: AdminTrendSnapshotService
) {

    @PostMapping("/run")
    fun run(
        @RequestBody request: RunTrendSnapshotRequest
    ): TrendSnapshotResponse =
        trendSnapshotService.runSnapshot(
            periodTypeRaw = request.periodType,
            categoryId = request.categoryId,
            regionTypeRaw = request.regionType,
            templateType = request.templateType ?: "DETAILED",
            generatedBy = request.generatedBy
        ).toResponse()

    @GetMapping
    fun list(
        @RequestParam(required = false) periodType: String?,
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) regionType: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int
    ): List<TrendSnapshotResponse> =
        trendSnapshotService.listSnapshots(periodType, categoryId, regionType, status, limit).map { it.toResponse() }

    @PostMapping("/{snapshotId}/publish")
    fun publish(
        @PathVariable snapshotId: String,
        @RequestBody(required = false) request: PublishTrendSnapshotRequest?
    ): TrendSnapshotResponse =
        trendSnapshotService.publishSnapshot(snapshotId, request?.publishedBy).toResponse()

    @PostMapping("/{snapshotId}/generate-visual")
    fun generateVisual(
        @PathVariable snapshotId: String,
        @RequestBody request: GenerateVisualCardRequest
    ): TrendVisualCardResponse =
        trendSnapshotService.generateVisual(
            snapshotId = snapshotId,
            cardTypeRaw = request.cardType,
            generatedBy = request.generatedBy
        ).toResponse()

    @GetMapping("/{snapshotId}/visuals")
    fun listSnapshotVisuals(
        @PathVariable snapshotId: String,
        @RequestParam(required = false, defaultValue = "100") limit: Int
    ): List<TrendVisualCardResponse> =
        trendSnapshotService.listVisualCards(snapshotId = snapshotId, reviewStatusRaw = null, limit = limit)
            .map { it.toResponse() }

    private fun TrendSnapshotResult.toResponse() = TrendSnapshotResponse(
        id = id,
        periodType = periodType.name,
        snapshotFrom = snapshotFrom.toString(),
        snapshotTo = snapshotTo.toString(),
        categoryId = categoryId,
        categoryName = categoryName,
        regionType = regionType.name,
        title = title,
        summary = summary,
        keySignals = keySignals,
        actionItems = actionItems,
        sourceCount = sourceCount,
        itemCount = itemCount,
        status = status.name,
        templateType = templateType,
        generatedBy = generatedBy,
        publishedAt = publishedAt?.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )

    private fun TrendVisualCardResult.toResponse() = TrendVisualCardResponse(
        id = id,
        snapshotId = snapshotId,
        cardType = cardType.name,
        title = title,
        summary = summary,
        panels = panels,
        reviewStatus = reviewStatus.name,
        reviewNote = reviewNote,
        generatedBy = generatedBy,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt?.toString(),
        published = published,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )

}
