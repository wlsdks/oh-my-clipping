package com.ohmyclipping.service.digest

import java.util.concurrent.ConcurrentHashMap

/**
 * Hangul + 영숫자 경계 lookaround. JVM `\b` 는 ASCII 전용이라 "MegaCorp" 에서 "MegaCorp" 을 오매칭 방지하지 못함.
 * 접두/접미 모두 Hangul/A-Z/a-z/0-9 가 아니어야 match.
 * LRU-lite 캐시 — digest run 간 공유되어 동일 keyword/alias 재컴파일 방지.
 */
private val BOUNDARY_CACHE = ConcurrentHashMap<String, Regex>()

private fun boundaryRegex(term: String): Regex =
    BOUNDARY_CACHE.computeIfAbsent(term.lowercase()) {
        Regex(
            "(?<![\\p{IsHangul}A-Za-z0-9])${Regex.escape(term)}(?![\\p{IsHangul}A-Za-z0-9])",
            RegexOption.IGNORE_CASE
        )
    }

fun matchesKeyword(text: String, keyword: String): Boolean =
    boundaryRegex(keyword).containsMatchIn(text)

fun matchesOrganization(text: String, org: DigestOrganization): Boolean {
    if (boundaryRegex(org.name).containsMatchIn(text)) return true
    return org.aliases.any { alias -> boundaryRegex(alias).containsMatchIn(text) }
}
