package com.clipping.mcpserver.service.digest

import java.time.Instant
import java.util.Locale

data class DigestDocumentItem(
    val id: String,
    val title: String,
    val summary: String,
    val keywords: List<String>,
    val importanceScore: Double,
    val whyImportant: String,
    val sourceLink: String,
    val createdAt: Instant?,
    val isFallback: Boolean,
)

data class DigestDocument(
    val categoryName: String,
    val totalCandidates: Int,
    val requestedMaxItems: Int,
    val keywordLimit: Int,
    val items: List<DigestDocumentItem>,
    val topKeywords: List<String>,
) {
    val selectedCount: Int get() = items.size
    val hasFallbackItems: Boolean get() = items.any { it.isFallback }
    val isThinDay: Boolean get() = selectedCount < requestedMaxItems
}

object DigestDocumentBuilder {
    fun build(
        categoryName: String,
        totalCandidates: Int,
        requestedMaxItems: Int,
        keywordLimit: Int,
        items: List<DigestDocumentItem>,
    ): DigestDocument {
        val safeKeywordLimit = keywordLimit.coerceIn(1, 10)
        return DigestDocument(
            categoryName = categoryName,
            totalCandidates = totalCandidates,
            requestedMaxItems = requestedMaxItems,
            keywordLimit = safeKeywordLimit,
            items = items,
            topKeywords = computeTopKeywords(items, safeKeywordLimit),
        )
    }

    fun computeTopKeywords(items: List<DigestDocumentItem>, max: Int): List<String> =
        buildMap<String, Pair<String, Int>> {
            items.asSequence()
                .flatMap { it.keywords.asSequence() }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { raw ->
                    val key = raw.lowercase(Locale.ROOT)
                    val prev = this[key]
                    if (prev == null) {
                        put(key, raw to 1)
                    } else {
                        put(key, prev.first to (prev.second + 1))
                    }
                }
        }
            .values
            .sortedByDescending { it.second }
            .map { it.first }
            .take(max.coerceAtLeast(0))
}
