package com.ohmyclipping.service.collection

import com.ohmyclipping.model.Language
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.store.CategoryRuleStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssItemStore
import com.ohmyclipping.support.UrlCanonicalizer
import com.ohmyclipping.util.TitleSimilarity
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * SearchCo 뉴스 검색 API를 통해 카테고리별 키워드 기반 뉴스를 수집한다.
 * 기존 RSS 파이프라인과 동일한 저장소(RssItemStore)를 사용하여
 * 후속 요약/다이제스트 파이프라인에 자연스럽게 합류한다.
 */
@Service
class NaverNewsCollectionService(
    private val naverNewsSearchPort: NaverNewsSearchPort,
    private val itemStore: RssItemStore,
    private val categoryStore: CategoryStore,
    private val categoryRuleStore: CategoryRuleStore
) {

    /**
     * 특정 카테고리의 includeKeywords로 SearchCo 뉴스를 검색하고 신규 기사를 저장한다.
     *
     * @param categoryId 수집 대상 카테고리 ID
     * @return 신규 저장된 기사 수
     */
    fun collectForCategory(categoryId: String): Int {
        if (!naverNewsSearchPort.isConfigured()) {
            log.debug { "Naver News API not configured, skipping collection" }
            return 0
        }

        val category = categoryStore.findById(categoryId)
        if (category == null) {
            log.warn { "Category not found: $categoryId" }
            return 0
        }

        // 카테고리의 includeKeywords를 검색 키워드로 사용한다
        val rule = categoryRuleStore.findByCategoryId(categoryId)
        val keywords = normalizeKeywords(rule?.includeKeywords.orEmpty())
        if (keywords.isEmpty()) {
            log.debug { "No keywords for category '${category.name}', skipping Naver collection" }
            return 0
        }

        var totalNew = 0
        // 중복 감지를 위해 최근 DB 제목과 이번 실행에서 저장한 제목을 함께 비교한다.
        val existingTitles = itemStore.findRecentTitles(
            categoryId = categoryId,
            after = Instant.now().minus(Duration.ofDays(7)),
            limit = 500
        ).toMutableList()
        val seenLinks = mutableSetOf<String>()

        for (keyword in keywords) {
            val items = try {
                naverNewsSearchPort.searchNews(keyword)
            } catch (e: RuntimeException) {
                log.warn(e) { "Naver collection skipped failed keyword '$keyword' for category '${category.name}'" }
                continue
            }
            if (items.isEmpty()) continue

            val canonicalLinksByRaw = items.associate { item ->
                item.link to UrlCanonicalizer.canonicalizeToString(item.link)
            }
            val canonicalLinks = canonicalLinksByRaw.values.distinct()

            // 수집된 기사 링크를 일괄 조회해 중복 감지 쿼리를 N+1에서 1회로 줄인다.
            val existingLinks = itemStore.findExistingLinks(canonicalLinks, categoryId)

            for (naverItem in items) {
                val canonicalLink = canonicalLinksByRaw.getValue(naverItem.link)
                // 링크 기반 중복 체크
                if (canonicalLink in existingLinks) continue
                if (!seenLinks.add(canonicalLink)) continue

                // 제목 유사도 기반 중복 체크
                if (existingTitles.any { TitleSimilarity.isDuplicate(it, naverItem.title) }) {
                    log.debug { "Similar Naver title skipped: ${naverItem.title}" }
                    continue
                }

                val rssItem = toRssItem(naverItem, categoryId, canonicalLink)
                try {
                    itemStore.save(rssItem)
                    existingTitles.add(rssItem.title)
                    totalNew++
                } catch (_: DuplicateKeyException) {
                    log.debug { "Duplicate Naver link skipped: ${naverItem.link}" }
                }
            }
        }

        if (totalNew > 0) {
            log.info { "Naver collection: $totalNew new items for category '${category.name}' (keywords=${keywords.size})" }
        }
        return totalNew
    }

    /**
     * 모든 활성 카테고리에 대해 SearchCo 뉴스를 수집한다.
     *
     * @return 카테고리별 신규 수집 건수 맵
     */
    fun collectForAllActiveCategories(): Map<String, Int> {
        if (!naverNewsSearchPort.isConfigured()) return emptyMap()

        val categories = categoryStore.findOperational()
        val results = mutableMapOf<String, Int>()

        for (category in categories) {
            runCatching {
                val newCount = collectForCategory(category.id)
                if (newCount > 0) results[category.id] = newCount
            }.onFailure { e ->
                log.warn { "Naver collection failed for category '${category.name}': ${e.message}" }
            }
        }

        return results
    }

    /** NaverNewsSearchItem을 RssItem으로 변환한다. SearchCo 뉴스는 항상 KOREAN으로 판정한다. */
    private fun toRssItem(item: NaverNewsSearchItem, categoryId: String, canonicalLink: String): RssItem {
        return RssItem(
            id = UUID.randomUUID().toString(),
            title = item.title,
            content = item.description,
            link = canonicalLink,
            publishedAt = item.publishedAt ?: Instant.now(),
            language = Language.KOREAN,
            categoryId = categoryId,
            rssSourceId = null
        )
    }

    private fun normalizeKeywords(keywords: List<String>): List<String> =
        keywords
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .toList()
}
