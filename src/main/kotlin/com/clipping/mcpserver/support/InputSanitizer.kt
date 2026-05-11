package com.clipping.mcpserver.support

import com.clipping.mcpserver.error.InvalidInputException

/**
 * 유저/관리자 입력 문자열을 저장 경계에서 일관되게 검증/정규화하는 공용 유틸.
 *
 * 이 유틸은 저장 직전 "길이 상한 + 기본 escape" 일관 적용을 담당한다.
 * PR G의 [SlackMentionGuard], PR H의 [SlackTextNormalizer]와 역할이 다음과 같이 분리되어 있다:
 * - [SlackMentionGuard]: 집단 멘션 패턴을 저장 시점에 중립화 (보안)
 * - [SlackTextNormalizer]: Slack 전송 직전 공백/라인분리자 정규화 (발송 직전)
 * - [InputSanitizer]: 저장 경계에서 길이 상한/trim/제어 문자 제거 (이 파일)
 *
 * 필드 라벨은 한국어 에러 메시지에 그대로 삽입되므로 사용자 친화적으로 작성한다.
 */
object InputSanitizer {

    // 제어 문자(U+0000~0008, 000B, 000C, 000E~001F, 007F)를 제거하기 위한 정규식.
    // 탭(\t=U+0009), LF(\n=U+000A), CR(\r=U+000D)은 의도적으로 보존한다.
    private val CONTROL_REGEX = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    // Unicode bidi override/embed/isolate 제어 문자(U+202A~U+202E, U+2066~U+2069).
    // 페르소나 이름/카테고리명/요약 같은 저장 텍스트에 섞이면 Slack 메시지나 관리자 UI에서
    // 표시 방향이 뒤집혀 UI spoofing을 유발하므로 저장 경계에서 제거한다.
    private val BIDI_OVERRIDE_REGEX = Regex("[\\u202A-\\u202E\\u2066-\\u2069]")

    /**
     * 선택 입력 문자열을 정규화한다.
     * - null 입력은 null 그대로 통과한다.
     * - trim 적용 후 제어 문자를 제거한다.
     * - trim 결과가 빈 문자열이면 null로 반환한다 (optional 의미).
     * - 길이가 [maxLength]를 초과하면 [InvalidInputException]을 던진다.
     *
     * @param raw 원본 입력값 (null 허용)
     * @param fieldLabel 에러 메시지에 사용할 한국어 필드 이름 (예: "설명")
     * @param maxLength 허용 최대 길이 (글자 수 기준)
     */
    fun sanitizeOptional(raw: String?, fieldLabel: String, maxLength: Int): String? {
        if (raw == null) return null
        // trim 후 제어 문자 + bidi 오버라이드 문자를 제거해 저장 일관성을 유지한다.
        val cleaned = removeBidiOverride(removeControlChars(raw.trim()))
        if (cleaned.isEmpty()) return null
        // 길이 초과는 저장 전에 즉시 거부해 DB VARCHAR 제약 위반을 방지한다.
        if (cleaned.length > maxLength) {
            throw InvalidInputException("${fieldLabel}은 최대 ${maxLength}자까지 입력할 수 있어요")
        }
        return cleaned
    }

    /**
     * 필수 입력 문자열을 정규화한다.
     * - null 또는 빈 문자열이면 [InvalidInputException]을 던진다 (필수 의미).
     * - trim 후 제어 문자를 제거한다.
     * - [minLength] 미만이거나 [maxLength] 초과면 [InvalidInputException]을 던진다.
     *
     * @param raw 원본 입력값 (null/blank 불가)
     * @param fieldLabel 에러 메시지에 사용할 한국어 필드 이름 (예: "이름")
     * @param maxLength 허용 최대 길이 (글자 수 기준)
     * @param minLength 허용 최소 길이 (기본 1)
     */
    fun sanitizeRequired(
        raw: String?,
        fieldLabel: String,
        maxLength: Int,
        minLength: Int = 1
    ): String {
        // optional 경로를 재사용해 trim/제어 문자 제거/최대 길이 검증을 공유한다.
        val cleaned = sanitizeOptional(raw, fieldLabel, maxLength)
            ?: throw InvalidInputException("${fieldLabel}을 입력해 주세요")
        // 최소 길이 제약은 optional에서는 검사하지 않으므로 여기에서만 체크한다.
        if (cleaned.length < minLength) {
            throw InvalidInputException("${fieldLabel}은 최소 ${minLength}자 이상이어야 해요")
        }
        return cleaned
    }

    private fun removeControlChars(s: String): String = s.replace(CONTROL_REGEX, "")

    private fun removeBidiOverride(s: String): String = s.replace(BIDI_OVERRIDE_REGEX, "")
}
