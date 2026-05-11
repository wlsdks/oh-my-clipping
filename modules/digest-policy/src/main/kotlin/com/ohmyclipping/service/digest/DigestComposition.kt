package com.ohmyclipping.service.digest

/** digest 생성 파이프라인에서 사용하는 article 표현. 매치 판정은 호출자가 미리 수행. */
data class DigestArticle(
    val id: String,
    val title: String,
    val summary: String,
    /** 키워드(토픽) 기준으로 매칭됐는지 여부 */
    val matchesKeyword: Boolean,
    /** 조직(account) 기준으로 매칭됐는지 여부 */
    val matchesOrganization: Boolean
)

/** ⭐ 배지를 포함한 article 래퍼. */
data class BadgedArticle(val article: DigestArticle, val badged: Boolean)

/** 섹션 종류(kind)와 해당 섹션에 포함된 배지 달린 article 목록. */
data class DigestSectionResult(val kind: String, val articles: List<BadgedArticle>)

/**
 * mode + budget 으로 섹션 리스트를 구성한다. 순수 함수.
 *
 * - budget=1 + DUAL_SECTION 은 CROSSFILTER 로 downgrade (account 0 섹션 방지)
 * - TOPIC_ONLY / ACCOUNT_ONLY / CROSSFILTER → 단일 섹션, ⭐ 배지 없음
 * - DUAL_SECTION → splitBudget 으로 예산 분배, topic 섹션의 crossMatch 기사에 ⭐ 배지
 * - DUAL account 섹션은 topic 에 이미 선택된 기사를 제외(dedup). 비-org 기사로 패딩 안함 (라벨 정합성).
 *
 * @param mode 섹션 구성 모드 (resolveDigestMode 결과)
 * @param articles ArticleMatcher 가 matchesKeyword/matchesOrganization 를 채운 article 목록
 * @param budget 다이제스트에 포함할 총 기사 수
 */
fun composeSections(
    mode: DigestMode,
    articles: List<DigestArticle>,
    budget: Int
): List<DigestSectionResult> {
    // budget=1 이면 DUAL 섹션 중 account 에 0건이 되어 무의미 — CROSSFILTER 로 downgrade
    val effectiveMode =
        if (budget == 1 && mode == DigestMode.DUAL_SECTION) DigestMode.CROSSFILTER else mode

    return when (effectiveMode) {
        DigestMode.TOPIC_ONLY -> listOf(
            DigestSectionResult(
                kind = "topic",
                articles = selectTop(articles, budget).map { BadgedArticle(it, badged = false) }
            )
        )
        DigestMode.ACCOUNT_ONLY -> listOf(
            DigestSectionResult(
                kind = "account",
                articles = selectTop(articles, budget).map { BadgedArticle(it, badged = false) }
            )
        )
        DigestMode.CROSSFILTER -> listOf(
            DigestSectionResult(
                kind = "cross",
                articles = selectTop(articles, budget).map { BadgedArticle(it, badged = false) }
            )
        )
        DigestMode.DUAL_SECTION -> {
            val (topicBudget, accountBudget) = splitBudget(budget)
            // topic 섹션: keyword 매칭 후보에서 상위 topicBudget 건 선택
            val topicCandidates = articles.filter { it.matchesKeyword }
            val topicSelected = selectTop(topicCandidates, topicBudget)
            val topicIds = topicSelected.map { it.id }.toSet()
            // account 섹션: ONLY org 매칭 기사 — topic 에 이미 선택된 기사 제외
            // 예산 미달(under-budget)은 정상 동작 — 비-org 기사로 채우면 "🏢 내 기업" 라벨과 불일치
            val accountCandidates = articles.filter {
                it.matchesOrganization && it.id !in topicIds
            }
            val accountSelected = selectTop(accountCandidates, accountBudget)
            listOf(
                DigestSectionResult(
                    kind = "topic",
                    // crossMatch(keyword+org 둘 다 매칭) 기사에만 ⭐ 배지 부여
                    articles = topicSelected.map { BadgedArticle(it, badged = it.matchesOrganization) }
                ),
                DigestSectionResult(
                    kind = "account",
                    articles = accountSelected.map { BadgedArticle(it, badged = false) }
                )
            )
        }
    }
}

/** 내부 helper — caller 가 미리 정렬한 articles 에서 앞에서부터 N개 선택. */
private fun selectTop(articles: List<DigestArticle>, limit: Int): List<DigestArticle> =
    if (limit <= 0) emptyList() else articles.take(limit)
