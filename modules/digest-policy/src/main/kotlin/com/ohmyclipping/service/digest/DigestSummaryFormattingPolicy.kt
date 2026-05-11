package com.ohmyclipping.service.digest

data class DigestSummaryPart(
    val title: String,
    val content: String,
)

object DigestSummaryFormattingPolicy {
    private val crOrCrLf = Regex("\\r\\n?")
    private val unicodeLineSeparators = Regex("[\\u2028\\u2029]")
    private val zeroWidthChars = Regex("[\\u200B\\uFEFF]")
    private val fullWidthSpace = Regex("\\u3000")
    private val sectionEmoji = "(📌|💡|👉|📰|🎓|🔍|📊|📈|🔮|🎯|✅|▸)"
    private val sectionEmojiNonCapturing = "(?:📌|💡|👉|📰|🎓|🔍|📊|📈|🔮|🎯|✅|▸)"
    private val sectionLabelPattern = Regex(
        "(?im)^\\s*($sectionEmoji\\s*)?(?:\\*+\\s*)?" +
            "(?:요약\\s*\\d*|핵심\\s*내용|맥락|실무\\s*시사점|" +
            "핵심\\s*사실|배경\\s*및\\s*맥락|영향\\s*분석|전망|배경|" +
            "변화\\s*포인트|교육\\s*시사점|적용\\s*아이디어|한\\s*줄\\s*요약|" +
            "왜\\s*중요한가|핵심\\s*팩트|비즈니스\\s*임팩트|의사결정\\s*포인트)" +
            "\\s*[:：\\-]\\s*"
    )

    fun sanitizeSummaryForDisplay(text: String): String {
        val normalized = text
            .replace(crOrCrLf, "\n")
            .replace(Regex("$sectionEmoji(?:\\s*\\1)+"), "$1")
            .replace(Regex("$sectionEmoji[ \\t]*$sectionEmojiNonCapturing"), "$1")
            .replace(Regex("(?<=\\S)[ \\t]*(?=$sectionEmojiNonCapturing)"), "\n\n")

        return normalized
            .trim()
            .split("\n")
            .map { line ->
                line.trim()
                    .replace(sectionLabelPattern, "$1")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun stripLeadingDecoration(text: String): String =
        text.replace(Regex("^[^\\p{L}\\p{N}\\(\\[\\\"'＂＇]+"), "").trim()

    fun buildSummaryParts(summary: String, maxChars: Int): List<DigestSummaryPart> {
        val paragraphs = buildDigestParagraphs(summary, maxChars)
        if (paragraphs.isEmpty()) return emptyList()
        val labels = listOf("📌", "🔍", "💡")

        return paragraphs
            .take(3)
            .mapIndexed { index, paragraph ->
                val sanitized = sanitizeSummaryForDisplay(paragraph).trim()
                val stripped = sanitized.split("\n")
                    .joinToString("\n") { stripLeadingDecoration(it) }
                    .let { stripLeadingDecoration(it) }
                DigestSummaryPart(
                    title = labels[index.coerceIn(0, labels.lastIndex)],
                    content = stripped
                )
            }
            .filter { it.content.isNotBlank() }
    }

    fun buildDigestParagraphs(summary: String, maxChars: Int): List<String> {
        val normalized = normalizeText(summary).trim()
        if (normalized.isBlank()) return emptyList()

        val safeLimit = maxChars.coerceAtLeast(120)
        val sourceParagraphs = normalized
            .split(Regex("\\n\\s*\\n+"))
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotBlank() }

        val clippedParagraphs = mutableListOf<String>()
        var remaining = safeLimit
        for (paragraph in sourceParagraphs) {
            if (remaining <= 0) break
            val clipped = clampByWordBoundary(paragraph, remaining)
            if (clipped.isBlank()) continue
            clippedParagraphs.add(clipped)
            remaining -= clipped.length
            if (clipped.length < paragraph.length) break
            if (remaining > 2) {
                remaining -= 2
            }
        }

        if (clippedParagraphs.isEmpty()) return emptyList()
        if (clippedParagraphs.size >= 3) {
            return listOf(
                clippedParagraphs[0],
                clippedParagraphs[1],
                clippedParagraphs.drop(2).joinToString(" ")
            )
        }
        if (clippedParagraphs.size == 2) {
            return clippedParagraphs
        }

        val sentences = clippedParagraphs.first()
            .split(Regex("(?<=[.!?])\\s+|(?<=다\\.)\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (sentences.isEmpty()) return clippedParagraphs
        if (sentences.size == 1) return splitLongSentence(sentences.first())
        if (sentences.size == 2) return sentences
        if (sentences.size == 3) return sentences

        val first = sentences.take(2).joinToString(" ")
        val second = sentences.drop(2).take(2).joinToString(" ")
        val third = sentences.drop(4).joinToString(" ")

        return listOf(first, second, third).filter { it.isNotBlank() }
    }

    private fun normalizeText(text: String): String = text
        .replace(crOrCrLf, "\n")
        .replace(unicodeLineSeparators, "\n")
        .replace(zeroWidthChars, "")
        .replace(fullWidthSpace, " ")

    private fun clampByWordBoundary(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val sliced = text.take(maxChars).trimEnd()
        val lastSpace = sliced.lastIndexOf(' ')
        return if (lastSpace > maxChars / 2) sliced.take(lastSpace).trimEnd() else sliced
    }

    private fun splitLongSentence(text: String): List<String> {
        if (text.length < 260) return listOf(text)
        val pivot = text.length / 2
        val right = text.indexOf(' ', pivot).takeIf { it > 0 }
        val left = text.lastIndexOf(' ', pivot).takeIf { it > 0 }
        val splitAt = when {
            right == null && left == null -> return listOf(text)
            right == null -> left
            left == null -> right
            kotlin.math.abs(right - pivot) < kotlin.math.abs(left - pivot) -> right
            else -> left
        } ?: return listOf(text)

        val firstPart = text.substring(0, splitAt).trim()
        val secondPart = text.substring(splitAt + 1).trim()
        return listOf(firstPart, secondPart).filter { it.isNotBlank() }
    }
}
