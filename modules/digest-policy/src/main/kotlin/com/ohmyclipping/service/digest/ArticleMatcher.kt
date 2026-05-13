package com.ohmyclipping.service.digest

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Hangul + 영숫자 경계 lookaround. JVM `\b` 는 ASCII 전용이라 "MegaCorp" 에서 "MegaCorp" 을 오매칭 방지하지 못함.
 * 접두/접미 모두 Hangul/A-Z/a-z/0-9 가 아니어야 match.
 * 크기 제한 캐시 — digest run 간 공유되어 동일 keyword/alias 재컴파일을 줄이되, 임의 term 증가로
 * 엔진 메모리가 계속 커지지 않게 한다.
 */
private const val MAX_BOUNDARY_REGEX_CACHE_SIZE = 512
private val BOUNDARY_CACHE = ConcurrentHashMap<String, Regex>()
private val boundaryCachePruneLock = Any()

private fun boundaryRegex(term: String): Regex {
    val normalized = term.trim()
    pruneBoundaryCacheIfNeeded()
    return BOUNDARY_CACHE.computeIfAbsent(normalized.lowercase(Locale.ROOT)) {
        Regex(
            "(?<![\\p{IsHangul}A-Za-z0-9])${Regex.escape(normalized)}(?![\\p{IsHangul}A-Za-z0-9])",
            RegexOption.IGNORE_CASE
        )
    }
}

private fun pruneBoundaryCacheIfNeeded() {
    if (BOUNDARY_CACHE.size < MAX_BOUNDARY_REGEX_CACHE_SIZE) return
    synchronized(boundaryCachePruneLock) {
        if (BOUNDARY_CACHE.size >= MAX_BOUNDARY_REGEX_CACHE_SIZE) {
            BOUNDARY_CACHE.clear()
        }
    }
}

fun matchesKeyword(text: String, keyword: String): Boolean =
    keyword.trim().takeIf { it.isNotBlank() }
        ?.let { boundaryRegex(it).containsMatchIn(text) }
        ?: false

fun matchesOrganization(text: String, org: DigestOrganization): Boolean {
    return sequenceOf(org.name)
        .plus(org.aliases.asSequence())
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .any { term -> boundaryRegex(term).containsMatchIn(text) }
}

internal fun articleMatcherBoundaryCacheSizeForTest(): Int = BOUNDARY_CACHE.size

internal fun clearArticleMatcherBoundaryCacheForTest() {
    BOUNDARY_CACHE.clear()
}
