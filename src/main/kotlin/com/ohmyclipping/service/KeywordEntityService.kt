package com.ohmyclipping.service

import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.service.dto.KeywordEntityItem
import com.ohmyclipping.service.dto.KeywordEntityResponse
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.KeywordEntityStore
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 키워드 엔티티 분류 서비스.
 * 기사 키워드를 엔티티 카테고리(PERSON, ORG, TECH, TOPIC, LOCATION)와 매칭한다.
 */
@Service
class KeywordEntityService(
    private val keywordEntityStore: KeywordEntityStore,
    private val batchSummaryStore: BatchSummaryStore,
) {

    companion object {
        /** 유효한 엔티티 카테고리 목록 */
        val VALID_CATEGORIES = setOf("PERSON", "ORG", "TECH", "TOPIC", "LOCATION")
    }

    /**
     * 기간 내 기사 키워드를 엔티티 분류와 매칭하여 반환한다.
     * 분류 정보가 없는 키워드는 "TOPIC"으로 폴백한다.
     *
     * @param days 조회 기간(일)
     * @param categoryId 카테고리 필터(null이면 전체)
     * @return 분류된 키워드 목록
     */
    fun getClassifiedKeywords(
        days: Int,
        categoryId: Long?
    ): KeywordEntityResponse {
        val to = Instant.now()
        val from = to.minus(days.toLong(), ChronoUnit.DAYS)
        val catIdStr = categoryId?.toString()

        // 기간 내 기사에서 키워드별 등장 횟수 집계
        val keywordCounts = collectKeywordCounts(from, to, catIdStr)

        // 엔티티 분류 조회
        val allKeywords = keywordCounts.keys.toList()
        val entityMap = loadEntityMap(allKeywords)

        // 분류 결과 조합 — 분류 없으면 TOPIC으로 폴백
        val items = keywordCounts.entries
            .sortedByDescending { it.value }
            .map { (keyword, count) ->
                KeywordEntityItem(
                    keyword = keyword,
                    category = entityMap[keyword] ?: "TOPIC",
                    count = count,
                )
            }

        return KeywordEntityResponse(items = items)
    }

    /**
     * 키워드 엔티티 분류를 수동 등록/수정한다.
     *
     * @param keyword 분류할 키워드
     * @param category 엔티티 카테고리 (PERSON, ORG, TECH, TOPIC, LOCATION)
     * @return 분류 결과
     * @throws IllegalArgumentException 유효하지 않은 카테고리인 경우
     */
    fun classifyKeyword(keyword: String, category: String): KeywordEntityItem {
        // 카테고리 유효성 검증
        ensureValid(category in VALID_CATEGORIES) {
            "유효하지 않은 카테고리: $category"
        }
        val entity = keywordEntityStore.upsert(keyword, category)
        return KeywordEntityItem(
            keyword = entity.keyword,
            category = entity.category,
            count = 0
        )
    }

    /**
     * 기간 내 기사에서 키워드별 등장 횟수를 집계한다.
     */
    private fun collectKeywordCounts(
        from: Instant,
        to: Instant,
        categoryId: String?
    ): Map<String, Int> {
        val articles = batchSummaryStore.findByDateRange(from, to, categoryId)
        val counts = mutableMapOf<String, Int>()
        for (article in articles) {
            for (kw in article.keywords) {
                counts[kw] = (counts[kw] ?: 0) + 1
            }
        }
        return counts
    }

    /**
     * 키워드 목록에 대한 엔티티 분류 맵을 조회한다.
     */
    private fun loadEntityMap(keywords: List<String>): Map<String, String> {
        if (keywords.isEmpty()) return emptyMap()
        return keywordEntityStore.findByKeywords(keywords)
            .associate { it.keyword to it.category }
    }
}
