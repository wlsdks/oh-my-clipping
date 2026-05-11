package com.ohmyclipping.util

object TitleSimilarity {

    private const val DEFAULT_THRESHOLD = 0.8

    fun isDuplicate(title1: String, title2: String, threshold: Double = DEFAULT_THRESHOLD): Boolean {
        return jaccardSimilarity(title1, title2) >= threshold
    }

    fun jaccardSimilarity(text1: String, text2: String): Double {
        val words1 = tokenize(text1)
        val words2 = tokenize(text2)
        if (words1.isEmpty() && words2.isEmpty()) return 1.0
        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return intersection.toDouble() / union.toDouble()
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .toSet()
}
