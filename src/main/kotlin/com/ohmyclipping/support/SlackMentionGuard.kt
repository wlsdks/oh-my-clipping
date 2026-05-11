package com.ohmyclipping.support

/**
 * 사용자 입력이 Slack에 도달하기 전에 집단/개별 멘션 패턴을 중립화하는 유틸.
 *
 * Slack은 `<!channel>`, `<!here>`, `<!everyone>`, `<@U...>`, `<#C...>` 같은 mrkdwn
 * 태그를 그대로 해석해 채널 전체에게 알림을 보낸다. 관리자/사용자 입력 필드
 * (페르소나 이름/스타일, 카테고리 설명, 차단 사유, 경쟁사 별칭 등)가 그대로
 * Slack 메시지에 삽입되면 악의적 입력으로 집단 알림을 유발할 수 있다.
 *
 * 본 유틸은 감지된 패턴의 여는 꺾쇠 `<` 바로 뒤에 zero-width space(U+200B)를
 * 삽입해 Slack 파서가 멘션으로 인식하지 못하게 만든다. 원본 텍스트의 가독성은
 * 최대한 보존된다.
 */
object SlackMentionGuard {

    /**
     * Slack mrkdwn에서 멘션으로 해석되는 패턴을 탐지한다.
     *
     * - `<!channel>`, `<!here>`, `<!everyone>`
     * - `<@U…>` (사용자 멘션)
     * - `<#C…>` (채널 링크)
     * - `<!subteam^…>` (user group broadcast) 등 기타 `<![A-Z]…>` 패턴
     */
    private val DANGEROUS = Regex(
        "<[!@#](?:channel|here|everyone|subteam\\^[A-Z0-9]+|[UWCGD][A-Z0-9]+)"
    )

    private const val ZERO_WIDTH_SPACE = "\u200B"

    /** 입력에 Slack 집단/개별 멘션 패턴이 있으면 true. */
    fun containsDangerousMention(input: String): Boolean =
        DANGEROUS.containsMatchIn(input)

    /**
     * 집단/개별 멘션 패턴을 zero-width space로 깨서 무해화한다.
     *
     * 예: `<!channel>` → `<\u200B!channel>`. Slack 파서는 `<\u200B!channel>`을
     * 멘션으로 인식하지 않으므로 알림을 보내지 않는다. 일반 텍스트(`<div>`,
     * `x < 3` 등)는 패턴에 걸리지 않아 원본 그대로 반환된다.
     */
    fun neutralize(input: String): String {
        if (input.isEmpty()) return input
        // 패턴이 없으면 원본을 그대로 돌려줘 메모리 복사를 피한다.
        if (!DANGEROUS.containsMatchIn(input)) return input
        // 각 매치의 첫 문자 `<` 뒤에 zero-width space를 삽입해 파서 감지를 우회한다.
        return DANGEROUS.replace(input) { match ->
            "<" + ZERO_WIDTH_SPACE + match.value.substring(1)
        }
    }

    /**
     * nullable 입력 편의 변형. null을 전달하면 null을 그대로 돌려준다.
     */
    fun neutralizeOrNull(input: String?): String? =
        if (input == null) null else neutralize(input)
}
