package com.clipping.mcpserver.admin.dto

data class SourceComplianceResponse(
    val sourceId: String,
    val legalBasis: String,
    val summaryAllowed: Boolean,
    val fulltextAllowed: Boolean,
    val reviewNotes: String?,
    val termsReviewedAt: String?
)

data class UpdateSourceComplianceRequest(
    val legalBasis: String? = null,
    val summaryAllowed: Boolean? = null,
    val fulltextAllowed: Boolean? = null,
    val reviewNotes: String? = null,
    val expectedUpdatedAt: String? = null
)
