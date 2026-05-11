package com.clipping.mcpserver.service.dto

/** AI Q&A 질문 요청 DTO. 질문 텍스트와 조회 기간, 카테고리 필터를 담는다. */
data class AiQaRequest(
    val question: String,
    val days: Int = 7,
    val categoryId: Long? = null,
)

/** AI Q&A 응답 DTO. 답변 본문과 관련 기사 목록을 포함한다. */
data class AiQaResponse(
    val question: String,
    val answer: String,
    val relatedArticles: List<AiQaRelatedArticle>,
    val contextArticleCount: Int,
)

/** AI Q&A 관련 기사 항목. 기사 요약 ID, 제목, 원문 링크, 관련성 사유를 담는다. */
data class AiQaRelatedArticle(
    val summaryId: String,
    val title: String,
    val sourceLink: String,
    val relevanceReason: String,
)
