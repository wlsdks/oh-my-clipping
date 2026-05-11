package com.ohmyclipping.support

import java.net.URI

/**
 * Slack 메시지 렌더링 출력 시점에 적용되는 이중 방어용 escape 유틸.
 *
 * 입력 저장 시점의 중립화와는 별도로, 최종 텍스트/링크 조립 직전에 다시 한 번 safeguard를 건다.
 * Slack 공식 가이드(https://api.slack.com/reference/surfaces/formatting#escaping)에 따라
 * `&`, `<`, `>` 세 문자만 HTML entity로 escape하고, mrkdwn 포맷 문자는 `\` prefix로 무력화한다.
 *
 * 사용 맥락:
 * - 사용자 입력(기사 제목, 요약, 키워드, 카테고리명)을 Slack section text에 넣기 직전
 * - 링크 label에 사용자 입력이 들어갈 때 link markup 깨짐 방지
 * - sourceLink 등 URL은 scheme 화이트리스트로 검증
 */
object SlackEscapeUtil {

    private val MRKDWN_FORMAT_CHARS = Regex("([*_~`])")
    private val LINK_LABEL_BREAKERS = Regex("[>|]")
    private val SAFE_URL_SCHEMES = setOf("http", "https")

    // Unicode bidirectional override/embed/isolate 제어 문자 6종.
    // U+202A LRE, U+202B RLE, U+202C PDF, U+202D LRO, U+202E RLO,
    // U+2066 LRI, U+2067 RLI, U+2068 FSI, U+2069 PDI.
    // 이들은 이후 문자열의 표시 방향을 임의로 뒤집어 Slack/브라우저에서 UI spoofing을 유발한다.
    private val BIDI_OVERRIDE = Regex("[\\u202A-\\u202E\\u2066-\\u2069]")

    /**
     * Slack API 스펙대로 `&`, `<`, `>` 세 문자만 HTML entity로 이스케이프한다.
     *
     * 순서 주의: `&`를 먼저 치환해야 `&lt;`/`&gt;`에 들어간 `&`가 중복 이스케이프되지 않는다.
     */
    fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /**
     * mrkdwn 포맷 문자 `*_~`가 사용자 입력에서 의도치 않게 발동되지 않도록
     * 각 문자 앞에 backslash를 삽입한다. Slack은 `\` 이스케이프를 mrkdwn에서 인식한다.
     */
    fun escapeMrkdwn(text: String): String =
        text.replace(MRKDWN_FORMAT_CHARS, "\\\\$1")

    /**
     * Unicode bidi 오버라이드/임베드/isolate 제어 문자를 제거한다.
     *
     * 악의적 입력에 U+202E(RLO) 등을 섞으면 이후 문자열 전체의 표시 방향이 반전되어
     * Slack/브라우저에서 전혀 다른 텍스트처럼 보이게 된다. 저장 경계와 렌더 경계 양쪽에서
     * 이 문자들을 제거해 스푸핑을 원천 차단한다.
     *
     * 제거 대상: U+202A~U+202E, U+2066~U+2069 (9개 코드포인트).
     */
    fun neutralizeBidiOverride(text: String): String =
        text.replace(BIDI_OVERRIDE, "")

    /**
     * escapeHtml + escapeMrkdwn을 결합한 일반 텍스트 렌더 기본 함수.
     * 순서: bidi 중성화 → HTML entity → mrkdwn.
     * bidi 제어 문자는 escape 이전에 제거해야 이후 치환 결과가 방향 반전에 영향받지 않는다.
     */
    fun escapeForMrkdwnSection(text: String): String =
        escapeMrkdwn(escapeHtml(neutralizeBidiOverride(text)))

    /**
     * Slack 링크 `<url|label>`의 label 부분에 사용자 입력을 넣을 때
     * `>`, `|`를 whitespace로 치환해 링크 마크업을 깨뜨리지 않게 한다.
     * 치환 전후 trim은 하지 않는다 — 호출부에서 필요하면 별도로 수행.
     */
    fun sanitizeLinkLabel(label: String): String =
        label.replace(LINK_LABEL_BREAKERS, " ")

    /**
     * URL scheme 화이트리스트 검사.
     * http/https만 허용한다. javascript:, data:, ftp: 등은 차단 대상.
     * 파싱 실패/빈 값/scheme 누락은 모두 false로 취급한다.
     */
    fun isSafeUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val scheme = URI(url).scheme
            scheme != null && scheme.lowercase() in SAFE_URL_SCHEMES
        } catch (_: IllegalArgumentException) {
            // URI 생성자는 문법 오류 시 URISyntaxException을 던지지만
            // checked exception이라 Kotlin에서는 IllegalArgumentException으로 감싸질 수 있다.
            false
        } catch (_: java.net.URISyntaxException) {
            false
        }
    }
}
