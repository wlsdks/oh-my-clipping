package com.clipping.mcpserver.admin

import com.clipping.mcpserver.admin.dto.SourceComplianceResponse
import com.clipping.mcpserver.admin.dto.UpdateSourceComplianceRequest
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.service.AdminSourceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/source-compliance")
class SourceComplianceAdminController(
    private val adminSourceService: AdminSourceService
) {

    @GetMapping("/{sourceId}")
    fun get(@PathVariable sourceId: String): SourceComplianceResponse =
        adminSourceService.getSource(sourceId).toResponse()

    @PutMapping("/{sourceId}")
    fun update(
        @PathVariable sourceId: String,
        @RequestBody request: UpdateSourceComplianceRequest
    ): SourceComplianceResponse =
        adminSourceService.updateSource(
            id = sourceId,
            name = null,
            url = null,
            sourceRegionRaw = null,
            emoji = null,
            isActive = null,
            categoryId = null,
            legalBasisRaw = request.legalBasis,
            summaryAllowed = request.summaryAllowed,
            fulltextAllowed = request.fulltextAllowed,
            reviewNotes = request.reviewNotes,
            expectedUpdatedAt = parseExpectedUpdatedAt(request.expectedUpdatedAt, "expectedUpdatedAt")
        ).toResponse()

    private fun RssSource.toResponse() = SourceComplianceResponse(
        sourceId = id,
        legalBasis = legalBasis.name,
        summaryAllowed = summaryAllowed,
        fulltextAllowed = fulltextAllowed,
        reviewNotes = reviewNotes,
        termsReviewedAt = termsReviewedAt?.toString()
    )
}
