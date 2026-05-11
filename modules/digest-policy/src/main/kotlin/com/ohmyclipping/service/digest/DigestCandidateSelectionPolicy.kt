package com.ohmyclipping.service.digest

import java.time.Instant
import java.util.Locale

data class DigestCandidate(
    val id: String,
    val title: String,
    val summary: String,
    val keywords: List<String>,
    val importanceScore: Double,
    val combinedScore: Double,
    val sourceId: String?,
    val sourceLink: String,
    val createdAt: Instant,
    val isFallback: Boolean = false,
)

data class DigestCandidateSelectionPolicy(
    val lambda: Double = DEFAULT_LAMBDA,
    val minRawScore: Double = DEFAULT_MIN_RAW_SCORE,
) {
    init {
        if (!lambda.isFinite() || lambda < 0.0) {
            throw EngineInvalidInputException("lambda must be a finite non-negative number")
        }
        if (!minRawScore.isFinite()) {
            throw EngineInvalidInputException("minRawScore must be finite")
        }
    }

    fun select(
        candidates: List<DigestCandidate>,
        maxItems: Int,
        minImportanceScore: Double,
    ): List<DigestCandidate> {
        if (maxItems <= 0 || candidates.isEmpty()) return emptyList()

        val filtered = candidates.filter { candidate ->
            candidate.importanceScore >= minImportanceScore ||
                candidate.combinedScore >= minImportanceScore
        }
        val qualityPool = if (filtered.isNotEmpty()) filtered else candidates
        return selectWithSoftPenalty(
            candidates = dedupeCandidates(qualityPool),
            maxItems = maxItems,
        )
    }

    fun selectWithSoftPenalty(
        candidates: List<DigestCandidate>,
        maxItems: Int,
    ): List<DigestCandidate> {
        if (maxItems <= 0 || candidates.isEmpty()) return emptyList()

        val pool = candidates.toMutableList()
        val pickedPerSource = mutableMapOf<String, Int>()
        val selected = mutableListOf<DigestCandidate>()

        fun comparator(picked: Map<String, Int>): Comparator<DigestCandidate> =
            compareByDescending<DigestCandidate> { candidate ->
                val source = candidate.sourceId ?: NULL_SOURCE_KEY
                candidate.combinedScore - lambda * (picked[source] ?: 0)
            }
                .thenByDescending { it.importanceScore }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }

        while (selected.size < maxItems && pool.isNotEmpty()) {
            val best = pool.minWithOrNull(comparator(pickedPerSource)) ?: break
            if (best.combinedScore < minRawScore) break

            selected += best
            pool.remove(best)
            val source = best.sourceId ?: NULL_SOURCE_KEY
            pickedPerSource[source] = (pickedPerSource[source] ?: 0) + 1
        }

        return selected.sortedWith(
            compareByDescending<DigestCandidate> { it.importanceScore }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )
    }

    fun dedupeCandidates(candidates: List<DigestCandidate>): List<DigestCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val selected = mutableListOf<DigestCandidate>()
        candidates.forEach { candidate ->
            val isDuplicate = selected.any { existing ->
                isLikelyDuplicate(existing, candidate)
            }
            if (!isDuplicate) selected += candidate
        }
        return selected
    }

    private fun isLikelyDuplicate(a: DigestCandidate, b: DigestCandidate): Boolean {
        val titleSimilarity = jaccardSimilarity(a.title.trim(), b.title.trim())
        if (titleSimilarity >= TITLE_DUPLICATE_THRESHOLD) return true

        val semanticSimilarity = semanticSimilarity(a, b)
        return semanticSimilarity >= SEMANTIC_DUPLICATE_THRESHOLD &&
            titleSimilarity >= TITLE_WEAK_DUPLICATE_THRESHOLD
    }

    private fun semanticSimilarity(a: DigestCandidate, b: DigestCandidate): Double {
        val tokensA = semanticTokens(a)
        val tokensB = semanticTokens(b)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).size.toDouble()
        val union = tokensA.union(tokensB).size.toDouble()
        if (union <= 0.0) return 0.0
        return intersection / union
    }

    private fun semanticTokens(candidate: DigestCandidate): Set<String> =
        buildString {
            append(candidate.summary)
            append(' ')
            append(candidate.keywords.joinToString(" "))
        }
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filterNot { STOPWORDS.contains(it) }
            .toSet()

    private fun jaccardSimilarity(text1: String, text2: String): Double {
        val words1 = tokenize(text1)
        val words2 = tokenize(text2)
        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return intersection.toDouble() / union.toDouble()
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .toSet()

    companion object {
        const val DEFAULT_LAMBDA = 0.15
        const val DEFAULT_MIN_RAW_SCORE = 0.3
        const val NULL_SOURCE_KEY = "__manual__"

        private const val TITLE_DUPLICATE_THRESHOLD = 0.65
        private const val TITLE_WEAK_DUPLICATE_THRESHOLD = 0.35
        private const val SEMANTIC_DUPLICATE_THRESHOLD = 0.90

        private val STOPWORDS = setOf(
            "summary", "translated", "title", "digest", "item", "slack", "news", "update",
            "요약", "번역", "제목", "뉴스", "업데이트"
        )
    }
}
