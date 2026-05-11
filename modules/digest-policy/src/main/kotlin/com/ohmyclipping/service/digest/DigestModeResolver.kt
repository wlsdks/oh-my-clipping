package com.ohmyclipping.service.digest

import kotlin.math.ceil

/**
 * 키워드/조직 개수로 digest mode 를 결정한다.
 * 1×N / N×1 / 1×1 은 모두 CROSSFILTER (하나의 교집합 쿼리로 충분).
 * (0,0) 은 서비스 경계에서 차단되어야 한다 — 도달하면 IllegalState.
 */
fun resolveDigestMode(keywordCount: Int, orgCount: Int): DigestMode = when {
    keywordCount == 0 && orgCount == 0 ->
        throw IllegalStateException("digest requires at least one keyword or org")
    orgCount == 0 -> DigestMode.TOPIC_ONLY
    keywordCount == 0 -> DigestMode.ACCOUNT_ONLY
    keywordCount == 1 || orgCount == 1 -> DigestMode.CROSSFILTER
    else -> DigestMode.DUAL_SECTION
}

/**
 * DUAL_SECTION 예산 분배. (topicBudget, accountBudget) 튜플 반환.
 * 1/3/5 는 하드매핑, 기타는 ceil/floor fallback.
 */
fun splitBudget(budget: Int): Pair<Int, Int> = when (budget) {
    5 -> 3 to 2
    3 -> 2 to 1
    1 -> 1 to 0
    else -> ceil(budget / 2.0).toInt() to (budget / 2)
}
