package com.clipping.mcpserver.service.dto

/**
 * 사용자에게 미전달된 다이제스트 뷰.
 * 발송 실패/포기 상태의 다이제스트와 포함된 기사 목록을 담는다.
 */
data class UndeliveredDigestView(
    val deliveryLogId: String,
    val categoryName: String,
    val deliveryDate: String,
    val deliveryTimeLabel: String,
    val status: String,
    val retryCount: Int,
    val maxRetries: Int,
    val articleCount: Int,
    val articles: List<UndeliveredArticleView>
)

/**
 * 미전달 다이제스트에 포함된 기사 뷰.
 */
data class UndeliveredArticleView(
    val summaryId: String,
    val title: String,
    val summary: String,
    val sourceLink: String
)
