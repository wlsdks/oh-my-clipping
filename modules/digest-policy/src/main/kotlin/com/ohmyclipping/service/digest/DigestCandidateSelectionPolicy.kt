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
        if (!minRawScore.isFinite() || minRawScore < 0.0) {
            throw EngineInvalidInputException("minRawScore must be a finite non-negative number")
        }
    }

    fun select(
        candidates: List<DigestCandidate>,
        maxItems: Int,
        minImportanceScore: Double,
    ): List<DigestCandidate> {
        if (!minImportanceScore.isFinite() || minImportanceScore < 0.0) {
            throw EngineInvalidInputException("minImportanceScore must be a finite non-negative number")
        }
        validateMaxItems(maxItems)
        if (candidates.isEmpty()) return emptyList()
        validateCandidateScores(candidates)

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
        validateMaxItems(maxItems)
        if (candidates.isEmpty()) return emptyList()
        validateCandidateScores(candidates)

        val pool = candidates.toMutableList()
        val pickedPerSource = mutableMapOf<String, Int>()
        val selected = mutableListOf<DigestCandidate>()

        fun comparator(picked: Map<String, Int>): Comparator<DigestCandidate> =
            compareByDescending<DigestCandidate> { candidate ->
                val source = candidate.normalizedSourceKey()
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
            val source = best.normalizedSourceKey()
            pickedPerSource[source] = (pickedPerSource[source] ?: 0) + 1
        }

        return selected.sortedWith(
            compareByDescending<DigestCandidate> { it.importanceScore }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )
    }

    private fun validateCandidateScores(candidates: List<DigestCandidate>) {
        candidates.forEach { candidate ->
            if (!candidate.importanceScore.isFinite()) {
                throw EngineInvalidInputException("candidate importanceScore must be finite: ${candidate.id}")
            }
            if (!candidate.combinedScore.isFinite()) {
                throw EngineInvalidInputException("candidate combinedScore must be finite: ${candidate.id}")
            }
        }
    }

    private fun validateMaxItems(maxItems: Int) {
        if (maxItems <= 0) {
            throw EngineInvalidInputException("maxItems must be greater than 0")
        }
    }

    fun dedupeCandidates(candidates: List<DigestCandidate>): List<DigestCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val selected = mutableListOf<DigestCandidateFingerprint>()
        candidates.forEach { candidate ->
            val fingerprint = candidate.toFingerprint()
            val isDuplicate = selected.any { existing ->
                isLikelyDuplicate(existing, fingerprint)
            }
            if (!isDuplicate) selected += fingerprint
        }
        return selected.map { it.candidate }
    }

    private fun isLikelyDuplicate(a: DigestCandidateFingerprint, b: DigestCandidateFingerprint): Boolean {
        val titleSimilarity = jaccardSimilarity(a.titleTokens, b.titleTokens)
        if (titleSimilarity >= TITLE_DUPLICATE_THRESHOLD) return true

        val semanticSimilarity = semanticSimilarity(a, b)
        return semanticSimilarity >= SEMANTIC_DUPLICATE_THRESHOLD &&
            titleSimilarity >= TITLE_WEAK_DUPLICATE_THRESHOLD
    }

    private fun semanticSimilarity(a: DigestCandidateFingerprint, b: DigestCandidateFingerprint): Double {
        val tokensA = a.semanticTokens
        val tokensB = b.semanticTokens
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).size.toDouble()
        val union = tokensA.union(tokensB).size.toDouble()
        if (union <= 0.0) return 0.0
        return intersection / union
    }

    private fun DigestCandidate.toFingerprint(): DigestCandidateFingerprint =
        DigestCandidateFingerprint(
            candidate = this,
            titleTokens = tokenize(title.trim()),
            semanticTokens = semanticTokens()
        )

    private fun DigestCandidate.semanticTokens(): Set<String> =
        buildString {
            append(summary)
            append(' ')
            append(keywords.joinToString(" "))
        }
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filterNot { STOPWORDS.contains(it) }
            .toSet()

    private fun DigestCandidate.normalizedSourceKey(): String =
        sourceId?.trim()?.takeIf { it.isNotBlank() } ?: NULL_SOURCE_KEY

    private data class DigestCandidateFingerprint(
        val candidate: DigestCandidate,
        val titleTokens: Set<String>,
        val semanticTokens: Set<String>,
    )

    private fun jaccardSimilarity(words1: Set<String>, words2: Set<String>): Double {
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
