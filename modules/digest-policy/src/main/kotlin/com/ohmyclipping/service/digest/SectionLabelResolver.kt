package com.ohmyclipping.service.digest

private const val MAX_VISIBLE = 3
private const val SEPARATOR = "·"

/** 조직 목록을 중간점 구분 요약 문자열로 변환. 3개 초과 시 "외 N개" 형식. */
fun summarizeOrgs(orgs: List<DigestOrganization>): String = summarize(orgs.map { it.name })

/** 키워드 목록을 중간점 구분 요약 문자열로 변환. 3개 초과 시 "외 N개" 형식. */
fun summarizeKeywords(keywords: List<String>): String = summarize(keywords)

private fun summarize(items: List<String>): String {
    if (items.isEmpty()) return ""
    val visible = items.take(MAX_VISIBLE).joinToString(SEPARATOR)
    val remaining = items.size - MAX_VISIBLE
    return if (remaining > 0) "$visible 외 ${remaining}개" else visible
}

/**
 * DigestMode 와 필터 조건을 받아 Slack 섹션 헤더 텍스트를 생성한다.
 *
 * @param mode 다이제스트 구성 모드
 * @param keywords 토픽 키워드 목록
 * @param orgs 관련 조직 목록
 * @param dualSection DUAL_SECTION 모드에서 "topic" 또는 "account" 지정, 그 외는 null.
 *                    DUAL_SECTION 모드에서 null 전달 시 EngineInvalidInputException.
 */
fun resolveSectionLabel(
    mode: DigestMode,
    keywords: List<String>,
    orgs: List<DigestOrganization>,
    dualSection: String?
): String = when (mode) {
    DigestMode.CROSSFILTER -> when {
        keywords.size == 1 && orgs.size == 1 ->
            "📰 ${orgs[0].name}의 ${keywords[0]}"
        keywords.size == 1 ->
            "📰 ${keywords[0]} × ${summarizeOrgs(orgs)}"
        orgs.size == 1 ->
            "📰 ${orgs[0].name} × ${summarizeKeywords(keywords)}"
        else ->
            "📰 ${summarizeKeywords(keywords)} × ${summarizeOrgs(orgs)}"
    }
    DigestMode.TOPIC_ONLY -> "📰 ${summarizeKeywords(keywords)}"
    DigestMode.ACCOUNT_ONLY -> "🏢 내 기업 동향"
    DigestMode.DUAL_SECTION -> when (dualSection) {
        "topic" -> "📰 주제 동향"
        "account" -> "🏢 내 기업"
        else -> throw EngineInvalidInputException("DUAL_SECTION requires dualSection = topic|account")
    }
}
