package com.ohmyclipping.admin

import com.ohmyclipping.admin.dto.ReviewVisualCardRequest
import com.ohmyclipping.admin.dto.TrendVisualCardResponse
import com.ohmyclipping.service.AdminTrendSnapshotService
import com.ohmyclipping.service.TrendVisualCardResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 비주얼 카드 관리 전용 관리자 API.
 */
@RestController
@RequestMapping("/api/admin/visual-cards")
class VisualCardAdminController(
    private val trendSnapshotService: AdminTrendSnapshotService
) {

    @GetMapping
    fun listVisualCards(
        @RequestParam(required = false) reviewStatus: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int
    ): List<TrendVisualCardResponse> =
        trendSnapshotService.listVisualCards(snapshotId = null, reviewStatusRaw = reviewStatus, limit = limit)
            .map { it.toResponse() }

    @PostMapping("/{cardId}/review")
    fun reviewVisualCard(
        @PathVariable cardId: String,
        @RequestBody request: ReviewVisualCardRequest
    ): TrendVisualCardResponse =
        trendSnapshotService.reviewVisualCard(
            cardId = cardId,
            approved = request.approved,
            reviewNote = request.reviewNote,
            reviewedBy = request.reviewedBy,
            publish = request.publish
        ).toResponse()

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
