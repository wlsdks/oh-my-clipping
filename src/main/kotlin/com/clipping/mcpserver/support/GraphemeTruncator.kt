package com.clipping.mcpserver.support

import java.text.BreakIterator
import java.util.Locale

/**
 * 한글 조합 문자, 이모지 ZWJ sequence, 스킨톤 modifier 등을
 * grapheme cluster 단위로 안전하게 자르는 truncation 유틸.
 *
 * Java `String#substring`은 char(UTF-16 code unit) 기준으로 동작하기 때문에
 * surrogate pair 중간, 한글 자소 조합 중간, 이모지 ZWJ 시퀀스 중간에서 깨진다.
 * BreakIterator의 character instance를 사용해 사용자 지각 문자(user-perceived character) 단위로 자른다.
 */
object GraphemeTruncator {

    private const val DEFAULT_ELLIPSIS = "…"

    /**
     * grapheme cluster 기준으로 최대 [maxGraphemes]개만 남기고 자른 뒤 ellipsis를 붙인다.
     *
     * @param text 원본 텍스트. null-safe 아님 — 호출부에서 null guard 필요.
     * @param maxGraphemes 허용하는 최대 grapheme 수. 0 이하이면 ellipsis만 반환.
     * @param ellipsis 잘림을 표시하는 접미 문자(기본 `…`). 생략 표시가 필요 없으면 빈 문자열 전달.
     * @return 잘린 텍스트. 원본이 이미 허용 범위 이내면 ellipsis 없이 원본 그대로 반환.
     */
    fun truncateByGrapheme(
        text: String,
        maxGraphemes: Int,
        ellipsis: String = DEFAULT_ELLIPSIS
    ): String {
        if (maxGraphemes <= 0) return ellipsis
        if (text.isEmpty()) return text

        // BreakIterator는 사용자 지각 문자 경계를 탐지해 한글 조합/이모지 ZWJ 시퀀스를 안전하게 다룬다.
        // 경계 나열은 `[0, b1, b2, ..., text.length]` 형태이고, 인접 경계 사이 1 grapheme으로 계산한다.
        val iterator = BreakIterator.getCharacterInstance(Locale.KOREAN)
        iterator.setText(text)

        var count = 0
        var truncateAt = text.length
        iterator.first()
        // maxGraphemes번째 경계까지 이동한 위치를 찾는다. 도달 전에 DONE이면 원본이 짧다는 뜻.
        while (true) {
            val next = iterator.next()
            if (next == BreakIterator.DONE) {
                // 전체 원본이 maxGraphemes 이하의 grapheme 으로 구성 → 그대로 반환
                return text
            }
            count++
            if (count >= maxGraphemes) {
                truncateAt = next
                break
            }
        }

        // maxGraphemes 를 만족하는 경계까지 왔을 때 원본이 여기서 끝나면 잘림 없이 반환
        if (truncateAt >= text.length) return text

        // 잘림 발생: ellipsis 부착 후 반환 (빈 ellipsis면 잘린 원문만)
        return text.substring(0, truncateAt) + ellipsis
    }
}
