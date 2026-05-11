package com.clipping.mcpserver.service.dto.clipping

data class CategoryListResult(
    val categories: List<CategoryInfo>
)

data class CategoryInfo(
    val id: String,
    val name: String,
    val description: String?,
    val slackChannelId: String?,
    val isActive: Boolean,
    val sourceCount: Int,
    /**
     * 사용자에게 노출 가능한 공개 카테고리 여부.
     * MCP user_list_categories 는 이 값이 true 인 항목만 반환한다.
     */
    val isPublic: Boolean = false,
)

data class SourceListResult(
    val sources: List<SourceInfo>
)

data class SourceInfo(
    val id: String,
    val name: String,
    val url: String,
    val emoji: String?,
    val isActive: Boolean,
    val sourceRegion: String,
    val categoryId: String,
    val categoryName: String?
)

data class CollectResult(
    val totalCollected: Int,
    val newItems: Int,
    val duplicateSkipped: Int,
    val categories: List<CollectCategoryResult>
)

data class CollectCategoryResult(
    val categoryId: String,
    val categoryName: String,
    val collected: Int,
    val newItems: Int
)

data class SummarizeResult(
    val totalSummarized: Int,
    val categories: List<SummarizeCategoryResult>
)

data class SummarizeCategoryResult(
    val categoryId: String,
    val categoryName: String,
    val summarized: Int
)

data class SummaryListResult(
    val summaries: List<SummaryInfo>,
    val totalCount: Int
)

data class SummaryInfo(
    val id: String,
    val originalTitle: String,
    val translatedTitle: String?,
    val summary: String,
    val keywords: List<String>,
    val importanceScore: Float,
    val sourceLink: String,
    val isSentToSlack: Boolean,
    val categoryId: String,
    val createdAt: String
)

/** 요약 단건 상세 조회 결과. 키워드, 감성, 원본 프리뷰 등 확장 정보를 포함한다. */
data class SummaryDetailResult(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val originalTitle: String,
    val translatedTitle: String?,
    val summary: String,
    val insights: String?,
    val keywords: List<String>,
    val importanceScore: Float,
    val sourceLink: String,
    val sentiment: String?,
    val eventType: String?,
    val isSentToSlack: Boolean,
    val contentPreview: String?,
    val createdAt: String,
)

data class HotFeedbackItem(
    val summaryId: String,
    val title: String,
    val sourceLink: String,
    val likeCount: Int,
    val neutralCount: Int,
    val dislikeCount: Int,
    val totalCount: Int,
    val score: Double
)

data class HotFeedbackResult(
    val from: String,
    val to: String,
    val totalCandidates: Int,
    val items: List<HotFeedbackItem>
)

data class DailySummaryResult(
    val id: String,
    val title: String,
    val totalItems: Int,
    val summaryDate: String,
    val topicKeywords: List<String>,
    val categoryId: String
)

data class AddUrlResult(
    val added: Boolean,
    val duplicate: Boolean,
    val itemId: String?,
    val categoryId: String,
    val sourceLink: String
)

data class OriginalContentResult(
    val found: Boolean,
    val sourceLink: String,
    val title: String,
    val markdown: String,
    val rssItemId: String,
    val archivedAt: String
)

data class RetentionPolicyResult(
    val categoryId: String,
    val keepDays: Int,
    val isEnabled: Boolean,
    val source: String
)

data class PurgeResult(
    val dryRun: Boolean,
    val categoryId: String?,
    val keepDays: Int,
    val cutoffDate: String,
    val deletedSummaries: Int,
    val deletedItems: Int,
    val deletedOriginals: Int,
    val deletedDailySummaries: Int,
    val deletedStats: Int
)

data class ExportRecord(
    val summaryId: String,
    val title: String,
    val summary: String,
    val keywords: List<String>,
    val importanceScore: Float,
    val sourceLink: String,
    val createdAt: String,
    val originalMarkdown: String?
)

data class ExportResult(
    val categoryId: String,
    val exportedAt: String,
    val daysBack: Int?,
    val includeOriginal: Boolean,
    val count: Int,
    val records: List<ExportRecord>
)

data class DigestItemResult(
    val summaryId: String,
    val title: String,
    val summary: String,
    val keywords: List<String>,
    val importanceScore: Float,
    val whyImportant: String,
    val sourceLink: String,
    val createdAt: String,
    val isFallback: Boolean = false,
)

data class DigestResult(
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
    val items: List<DigestItemResult>,
    /** Slack payload 에러로 Block Kit 대신 text-only fallback 으로 전송됐는지 여부. */
    val fallbackUsed: Boolean = false
)

enum class PipelineOrchestrationMode {
    DETERMINISTIC,
    RALPH
}

enum class PipelineStepStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}

data class PipelineStepTrace(
    val step: String,
    val status: PipelineStepStatus,
    val startedAt: String,
    val endedAt: String,
    val detail: String? = null
)

enum class RalphLoopStopReason {
    STOP_PHRASE_DETECTED,
    NO_PROGRESS,
    MAX_ITERATIONS_REACHED,
    LOOP_DISABLED
}

data class PipelineRunResult(
    val collect: CollectResult,
    val summarize: SummarizeResult,
    val digest: DigestResult,
    val orchestrationMode: PipelineOrchestrationMode = PipelineOrchestrationMode.DETERMINISTIC,
    val fallbackApplied: Boolean = false,
    val orchestrationWarnings: List<String> = emptyList(),
    val stepTraces: List<PipelineStepTrace> = emptyList(),
    val loopEnabled: Boolean = false,
    val loopIterationCount: Int = 1,
    val loopStopReason: RalphLoopStopReason = RalphLoopStopReason.LOOP_DISABLED,
    val loopStopPhrase: String? = null
)

data class AiSummaryResponse(
    val translatedTitle: String?,
    val summary: String,
    val keywords: List<String>,
    val importanceScore: Float = 0f,
    val sentiment: String? = null,
    val eventType: String? = null
)

data class AiDailySummaryResponse(
    val title: String,
    val topicKeywords: List<String> = emptyList()
)
