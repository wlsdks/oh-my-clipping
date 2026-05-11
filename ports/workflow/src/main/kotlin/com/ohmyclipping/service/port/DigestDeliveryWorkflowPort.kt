package com.ohmyclipping.service.port

/**
 * Prepared digest 생성/발송/후처리 복구를 제공하는 앱 경계 포트.
 *
 * SlackDigestWorker 는 스케줄, 대상 예약, retry orchestration 만 담당하고
 * 실제 digest 생성/전송/finalization 세부 구현은 이 포트 뒤 adapter 에 둔다.
 */
interface DigestDeliveryWorkflowPort {
    fun prepareDigest(
        categoryId: String,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?,
    ): PreparedDigestResult

    fun sendPreparedDigest(
        categoryId: String,
        preparedDigest: PreparedDigestResult,
        slackChannelId: String,
        categoryNameOverride: String? = null,
    ): PreparedDigestResult

    fun finalizePreparedDigest(categoryId: String, preparedDigest: PreparedDigestResult): Int
}

/**
 * Prepared digest workflow 전용 DTO.
 *
 * PipelineDigestResult 와 필드 형태는 유사하지만, prepared digest retry/finalization 경계의 계약을
 * pipeline 실행 결과 DTO와 독립시키기 위해 별도로 유지한다.
 */
data class PreparedDigestResult(
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
    val items: List<PreparedDigestItemResult>,
    val fallbackUsed: Boolean = false,
)

data class PreparedDigestItemResult(
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
