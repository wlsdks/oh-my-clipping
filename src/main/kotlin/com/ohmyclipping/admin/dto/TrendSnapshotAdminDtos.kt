package com.ohmyclipping.admin.dto

data class RunTrendSnapshotRequest(
    val periodType: String,
    val categoryId: String? = null,
    val regionType: String? = "ALL",
    val templateType: String? = "DETAILED",
    val generatedBy: String? = null
)

data class PublishTrendSnapshotRequest(
    val publishedBy: String? = null
)

data class GenerateVisualCardRequest(
    val cardType: String,
    val generatedBy: String? = null
)

data class ReviewVisualCardRequest(
    val approved: Boolean,
    val reviewNote: String? = null,
    val reviewedBy: String? = null,
    val publish: Boolean? = null
)

data class TrendSnapshotResponse(
    val id: String,
    val periodType: String,
    val snapshotFrom: String,
    val snapshotTo: String,
    val categoryId: String?,
    val categoryName: String,
    val regionType: String,
    val title: String,
    val summary: String,
    val keySignals: List<String>,
    val actionItems: List<String>,
    val sourceCount: Int,
    val itemCount: Int,
    val status: String,
    val templateType: String,
    val generatedBy: String?,
    val publishedAt: String?,
    val createdAt: String,
    val updatedAt: String
)

data class TrendVisualCardResponse(
    val id: String,
    val snapshotId: String,
    val cardType: String,
    val title: String,
    val summary: String,
    val panels: List<String>,
    val reviewStatus: String,
    val reviewNote: String?,
    val generatedBy: String?,
    val reviewedBy: String?,
    val reviewedAt: String?,
    val published: Boolean,
    val createdAt: String,
    val updatedAt: String
)

data class ReportReleaseResponse(
    val summaryId: String,
    val categoryId: String,
    val title: String,
    val sourceLink: String,
    val importanceScore: Float,
    val releaseType: String,
    val detectionReason: String,
    val createdAt: String
)

data class QualitySummaryResponse(
    val from: String,
    val to: String,
    val days: Int,
    val itemsCollected: Int,
    val itemsSummarized: Int,
    val itemsSent: Int,
    val reviewPendingCount: Int,
    val reviewPendingRate: Double,
    val excludeRate: Double,
    val feedbackTotal: Int,
    val feedbackPositiveRate: Double,
    val sendSuccessRate: Double,
    val recommendations: List<String>
)
