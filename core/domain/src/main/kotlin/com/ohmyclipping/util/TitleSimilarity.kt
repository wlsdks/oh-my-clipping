package com.ohmyclipping.util

object TitleSimilarity {

    private const val DEFAULT_THRESHOLD = 0.8

    /**
     * 한쪽 제목이 다른쪽에 거의 통째로 포함됐을 때(publisher suffix, "속보" 같은 짧은 접두 추가)
     * jaccard 가 threshold 를 못 넘어도 duplicate 로 판정하기 위한 containment 임계값.
     * 짧은 제목 토큰 1~2 개의 우연 중복으로 오탐하지 않도록 [CONTAINMENT_MIN_TOKENS] 토큰 이상
     * 짧은쪽이 필요하다.
     */
    private const val CONTAINMENT_THRESHOLD = 0.9
    private const val CONTAINMENT_MIN_TOKENS = 4

    fun isDuplicate(title1: String, title2: String, threshold: Double = DEFAULT_THRESHOLD): Boolean {
        val tokens1 = tokenize(title1)
        val tokens2 = tokenize(title2)
        if (tokens1.isEmpty() && tokens2.isEmpty()) return true
        if (tokens1.isEmpty() || tokens2.isEmpty()) return false

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        val jaccard = intersection.toDouble() / union.toDouble()
        if (jaccard >= threshold) return true

        // 한쪽이 다른쪽에 거의 통째로 포함되는 경우(예: 같은 본문 + publisher 접미 토큰 추가) 도 duplicate.
        val smallerSize = minOf(tokens1.size, tokens2.size)
        if (smallerSize < CONTAINMENT_MIN_TOKENS) return false
        val containment = intersection.toDouble() / smallerSize.toDouble()
        return containment >= CONTAINMENT_THRESHOLD
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
