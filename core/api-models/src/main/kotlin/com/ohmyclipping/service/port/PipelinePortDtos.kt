package com.ohmyclipping.service.port

data class PipelineCollectResult(
    val totalCollected: Int,
    val newItems: Int,
    val duplicateSkipped: Int,
    val categories: List<PipelineCollectCategoryResult>
)

data class PipelineCollectCategoryResult(
    val categoryId: String,
    val categoryName: String,
    val collected: Int,
    val newItems: Int
)

data class PipelineSummarizeResult(
    val totalSummarized: Int,
    val categories: List<PipelineSummarizeCategoryResult>
)

data class PipelineSummarizeCategoryResult(
    val categoryId: String,
    val categoryName: String,
    val summarized: Int
)

data class PipelineDigestResult(
    val categoryId: String,
    val categoryName: String,
    val unsentOnly: Boolean,
    val totalCandidates: Int,
    val selectedCount: Int,
    val postedToSlack: Boolean,
    val slackChannelId: String?,
    val slackMessageTs: String?,
    val markedSentCount: Int,
    val digestText: String,
    val items: List<PipelineDigestItemResult>,
    val fallbackUsed: Boolean = false
)

data class PipelineDigestItemResult(
    val summaryId: String,
    val title: String,
    val summary: String,
    val keywords: List<String>,
    val importanceScore: Float,
    val whyImportant: String,
    val sourceLink: String,
    val createdAt: String,
    val isFallback: Boolean = false
)
