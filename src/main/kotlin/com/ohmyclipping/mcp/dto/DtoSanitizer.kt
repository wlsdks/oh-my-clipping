package com.ohmyclipping.mcp.dto

import org.springframework.stereotype.Component

/**
 * MCP 응답 DTO용 프롬프트 인젝션 방어 산타이저.
 *
 * 기사 본문·제목 등 사용자 콘텐츠에 포함될 수 있는
 * LLM 프롬프트 인젝션 패턴을 탐지하여 안전한 프리픽스를 붙인다.
 * 제어 문자(null~US, DEL)도 함께 제거한다.
 */
@Component
class DtoSanitizer {

    private val injectionPatterns = listOf(
        // SYSTEM/USER/ASSISTANT 롤 위장
        Regex(
            "(?i)^\\s*([*\\-#]{0,3}\\s*)?SYSTEM\\s*[:=]",
            RegexOption.MULTILINE,
        ),
        Regex(
            "(?i)^\\s*([*\\-#]{0,3}\\s*)?USER\\s*[:=]",
            RegexOption.MULTILINE,
        ),
        Regex(
            "(?i)^\\s*([*\\-#]{0,3}\\s*)?ASSISTANT\\s*[:=]",
            RegexOption.MULTILINE,
        ),
        // ChatML 구분자
        Regex("<\\|im_start\\|>"),
        Regex("<\\|im_end\\|>"),
        // Llama-style INST 태그
        Regex("(?i)\\[?\\s*INST\\s*]?"),
        // 지시 무시/재정의 시도
        Regex("(?i)ignore\\s+(all\\s+)?(previous|above)\\s+instructions"),
        Regex("(?i)new\\s+instructions\\s*:"),
    )

    /** 제어 문자 패턴 (탭/줄바꿈 제외) */
    private val controlChars = Regex("[\\x00-\\x08\\x0B-\\x1F\\x7F]")

    /**
     * 텍스트에서 제어 문자를 제거하고 프롬프트 인젝션 패턴에 경고 프리픽스를 삽입한다.
     *
     * @param text 원본 텍스트 (null이면 null 반환)
     * @return 산타이즈된 텍스트 또는 null
     */
    fun sanitize(text: String?): String? {
        if (text.isNullOrEmpty()) return text

        // 제어 문자 제거
        var result = text.replace(controlChars, "")

        // 인젝션 패턴에 경고 프리픽스 삽입
        injectionPatterns.forEach { pattern ->
            result = result.replace(pattern) { match ->
                "\u26A0\uFE0F[user-content] ${match.value}"
            }
        }
        return result
    }
}
