package com.ohmyclipping.service.port

import java.net.URI

interface CollectionRuntimeSettingsPort {
    fun currentCollectionSettings(): CollectionRuntimeSettings
}

data class CollectionRuntimeSettings(
    val maxContentLength: Int,
)

interface CollectionArticleExtractorPort {
    fun extract(url: String): CollectionExtractedArticle?
}

data class CollectionExtractedArticle(
    val title: String,
    val content: String,
    val language: String,
)

interface CollectionUrlSafetyPort {
    fun validatePublicHttpUrl(rawUrl: String): URI
}

interface CollectionStatsPort {
    fun recordCollection(categoryId: String, itemsCollected: Int, itemsDuplicates: Int = 0)
}

interface CollectionMetricsPort {
    fun recordExtraction(context: String, success: Boolean, durationMs: Long)

    fun recordCollectionSource(
        sourceId: String,
        categoryId: String,
        success: Boolean,
        durationMs: Long,
        collected: Int,
        newItems: Int,
        duplicates: Int,
    )
}

interface CollectionBackgroundErrorNotifierPort {
    fun notifyCollectionError(context: String, exception: Exception, extra: String? = null)
}
