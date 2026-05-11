package com.clipping.mcpserver.ai

import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.service.dto.clipping.AiSummaryResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

data class SummaryQualityValidationResult(
    val accepted: Boolean,
    val normalized: AiSummaryResponse?,
    /** reject 사유. accepted=true이면 null. */
    val rejectReason: String? = null
)

@Component
class SummaryQualityValidator(
    private val properties: ClippingMcpServerProperties
) {

    private val whitespace = Regex("\\s+")
    private val paragraphSplit = Regex("\\n\\s*\\n+")
    private val sentenceSplit = Regex("(?<=[.!?])\\s+|(?<=다\\.)\\s+")
    private val nonWord = Regex("[^\\p{L}\\p{N}]")
    private val keywordToken = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}\\-+#.]*")
    private val stopwords = setOf(
        "the", "and", "for", "with", "that", "this", "from", "into", "article", "news", "update",
        "요약", "기사", "오늘", "이번", "관련", "통해", "대한", "에서", "그리고", "하는", "했다"
    )
    private val fallbackKeywords = listOf("클리핑", "요약", "핵심")

    companion object {
        private val VALID_SENTIMENTS = setOf("POSITIVE", "NEUTRAL", "NEGATIVE")
        private val VALID_EVENT_TYPES = setOf(
            "PRODUCT_LAUNCH", "PARTNERSHIP", "FUNDING",
            "POLICY", "PERSONNEL", "OTHER"
        )
    }

    fun validate(title: String, response: AiSummaryResponse): SummaryQualityValidationResult {
        val minChars = properties.summaryMinChars.coerceAtLeast(1)
        val maxChars = properties.summaryMaxChars.coerceAtLeast(minChars)
        val minParagraphs = properties.summaryMinParagraphCount.coerceAtLeast(1)
        val minSentences = properties.summaryMinSentenceCount.coerceAtLeast(1)
        val minKeywords = properties.summaryKeywordMinCount.coerceAtLeast(1)
        val maxKeywords = properties.summaryKeywordMaxCount.coerceAtLeast(minKeywords)

        val normalizedSummary = normalizeSummary(response.summary)
        if (normalizedSummary.length < minChars) {
            val reason = "CHARS_TOO_SHORT: ${normalizedSummary.length}자 < 최소 ${minChars}자"
            log.debug { "품질 reject — $reason" }
            return SummaryQualityValidationResult(accepted = false, normalized = null, rejectReason = reason)
        }
        val structuredSummary = ensureParagraphStructure(normalizedSummary, minParagraphs)
        if (countParagraphs(structuredSummary) < minParagraphs) {
            val reason = "PARAGRAPHS_TOO_FEW: ${countParagraphs(structuredSummary)}개 < 최소 ${minParagraphs}개"
            log.debug { "품질 reject — $reason" }
            return SummaryQualityValidationResult(accepted = false, normalized = null, rejectReason = reason)
        }
        if (countSentences(structuredSummary) < minSentences) {
            val reason = "SENTENCES_TOO_FEW: ${countSentences(structuredSummary)}개 < 최소 ${minSentences}개"
            log.debug { "품질 reject — $reason" }
            return SummaryQualityValidationResult(accepted = false, normalized = null, rejectReason = reason)
        }

        val boundedSummary = clampSummaryLength(structuredSummary, maxChars)
        if (countSentences(boundedSummary) < minSentences) {
            val reason = "SENTENCES_AFTER_CLAMP: ${countSentences(boundedSummary)}개 < 최소 ${minSentences}개 (maxChars=${maxChars}로 자른 후)"
            log.debug { "품질 reject — $reason" }
            return SummaryQualityValidationResult(accepted = false, normalized = null, rejectReason = reason)
        }

        val normalizedKeywords = normalizeKeywords(response.keywords, maxKeywords)
        val completedKeywords = fillKeywords(
            existing = normalizedKeywords,
            title = title,
            summary = boundedSummary,
            minKeywords = minKeywords,
            maxKeywords = maxKeywords
        )

        // sentiment/eventType 유효값 검증 — 유효하지 않으면 null로 처리
        val validatedSentiment = response.sentiment
            ?.trim()?.uppercase()
            ?.takeIf { it in VALID_SENTIMENTS }
        val validatedEventType = response.eventType
            ?.trim()?.uppercase()
            ?.takeIf { it in VALID_EVENT_TYPES }

        val normalized = AiSummaryResponse(
            translatedTitle = response.translatedTitle?.trim()?.takeIf { it.isNotBlank() },
            summary = boundedSummary,
            keywords = completedKeywords,
            importanceScore = normalizeScore(response.importanceScore),
            sentiment = validatedSentiment,
            eventType = validatedEventType
        )
        return SummaryQualityValidationResult(accepted = true, normalized = normalized)
    }

    private fun normalizeSummary(summary: String): String {
        val paragraphs = splitParagraphs(
            summary
                .replace(Regex("\\r\\n?"), "\n")
                .trim()
        )
            .map { it.replace(whitespace, " ").trim() }
            .filter { it.isNotBlank() }
            .map { deduplicateSentences(it) }

        return paragraphs.joinToString("\n\n")
    }

    private fun deduplicateSentences(paragraph: String): String {
        val sentences = splitSentences(paragraph)
        if (sentences.size <= 1) return paragraph

        val seen = mutableSetOf<String>()
        val normalized = mutableListOf<String>()
        for (sentence in sentences) {
            val canonical = sentence.lowercase().replace(nonWord, "")
            if (canonical.isBlank()) continue
            if (!seen.add(canonical)) continue
            normalized.add(sentence)
        }
        return if (normalized.isEmpty()) paragraph else normalized.joinToString(" ")
    }

    private fun ensureParagraphStructure(summary: String, minParagraphs: Int): String {
        if (minParagraphs <= 1) return summary

        val paragraphs = splitParagraphs(summary)
        if (paragraphs.size >= minParagraphs) return paragraphs.joinToString("\n\n")
        if (paragraphs.size != 1) return summary

        val sentences = splitSentences(paragraphs.first())
        if (sentences.size < minParagraphs) return summary

        val rebuilt = mutableListOf<String>()
        for (part in 0 until minParagraphs) {
            val start = (part * sentences.size) / minParagraphs
            val end = ((part + 1) * sentences.size) / minParagraphs
            if (end <= start) continue
            rebuilt.add(sentences.subList(start, end).joinToString(" "))
        }
        return if (rebuilt.isEmpty()) summary else rebuilt.joinToString("\n\n")
    }

    private fun clampSummaryLength(summary: String, maxChars: Int): String {
        if (summary.length <= maxChars) return summary

        val limited = mutableListOf<String>()
        var remaining = maxChars
        for (paragraph in splitParagraphs(summary)) {
            if (remaining <= 0) break
            val clipped = clampByWordBoundary(paragraph, remaining)
            if (clipped.isBlank()) continue
            limited.add(clipped)
            remaining -= clipped.length
            if (clipped.length < paragraph.length) break
            if (remaining > 2) remaining -= 2
        }

        val joined = limited.joinToString("\n\n").trim()
        if (joined.isNotBlank()) {
            return joined
        }
        return clampByWordBoundary(summary.replace(whitespace, " ").trim(), maxChars)
    }

    private fun normalizeScore(score: Float): Float {
        if (!score.isFinite()) return 0f
        return score.coerceIn(0f, 1f)
    }

    private fun normalizeKeywords(keywords: List<String>, maxKeywords: Int): MutableList<String> {
        val seen = mutableSetOf<String>()
        val normalized = mutableListOf<String>()
        for (keyword in keywords) {
            val value = keyword.replace(whitespace, " ").trim()
            if (value.isBlank()) continue
            val canonical = value.lowercase()
            if (!seen.add(canonical)) continue
            normalized.add(value)
            if (normalized.size >= maxKeywords) break
        }
        return normalized
    }

    private fun fillKeywords(
        existing: MutableList<String>,
        title: String,
        summary: String,
        minKeywords: Int,
        maxKeywords: Int
    ): List<String> {
        if (existing.size >= minKeywords) return existing.take(maxKeywords)

        val seen = existing.mapTo(mutableSetOf()) { it.lowercase() }
        val extracted = sequenceOf(title, summary)
            .flatMap { text -> keywordToken.findAll(text).asSequence().map { it.value } }
            .map { it.trim() }
            .filter { it.length >= 2 }
            .filterNot { it.all(Char::isDigit) }
            .filterNot { stopwords.contains(it.lowercase()) }
            .filter { seen.add(it.lowercase()) }
            .toList()

        for (token in extracted) {
            if (existing.size >= minKeywords || existing.size >= maxKeywords) break
            existing.add(token)
        }

        for (fallback in fallbackKeywords) {
            if (existing.size >= minKeywords || existing.size >= maxKeywords) break
            if (seen.add(fallback.lowercase())) {
                existing.add(fallback)
            }
        }

        return existing.take(maxKeywords)
    }

    private fun splitParagraphs(summary: String): List<String> =
        summary.split(paragraphSplit)
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun splitSentences(text: String): List<String> =
        text.split(sentenceSplit)
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun countParagraphs(summary: String): Int =
        splitParagraphs(summary).size

    private fun countSentences(summary: String): Int =
        splitParagraphs(summary).sumOf { splitSentences(it).size }

    private fun clampByWordBoundary(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val sliced = text.take(maxChars).trimEnd()
        val lastSpace = sliced.lastIndexOf(' ')
        if (lastSpace > maxChars / 2) {
            return sliced.take(lastSpace).trimEnd()
        }
        return sliced
    }
}
