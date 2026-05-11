package com.ohmyclipping.support

/**
 * Slack 렌더링 입력 텍스트를 정규화하는 헬퍼.
 *
 * LLM/RSS/사용자 입력에 섞여 들어오는 특수 유니코드 문자를 일관된 형태로 치환한다.
 * - CRLF/CR → LF
 * - U+2028(Line Separator), U+2029(Paragraph Separator) → LF
 * - U+200B(zero-width space), U+FEFF(BOM) → 제거
 * - U+3000(전각 공백) → 반각 공백
 *
 * `buildDigestParagraphs` 같은 paragraph 분해 로직 진입 직전에 호출해
 * `\n` 보존 로직이 원하는 대로 동작하도록 한다.
 */
object SlackTextNormalizer {

    private val CR_OR_CRLF = Regex("\\r\\n?")
    private val UNICODE_LINE_SEPARATORS = Regex("[\\u2028\\u2029]")
    private val ZERO_WIDTH_CHARS = Regex("[\\u200B\\uFEFF]")
    private val FULLWIDTH_SPACE = Regex("\\u3000")

    /**
     * Slack 출력용으로 텍스트를 정규화한다.
     * 입력이 null/blank여도 안전하게 빈 문자열을 반환한다.
     */
    fun normalizeForSlack(text: String): String = text
        .replace(CR_OR_CRLF, "\n")
        .replace(UNICODE_LINE_SEPARATORS, "\n")
        .replace(ZERO_WIDTH_CHARS, "")
        .replace(FULLWIDTH_SPACE, " ")
}
