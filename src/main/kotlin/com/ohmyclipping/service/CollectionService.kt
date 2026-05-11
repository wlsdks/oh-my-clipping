package com.ohmyclipping.service

import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.model.*
import com.ohmyclipping.service.dto.clipping.*
import com.ohmyclipping.service.collection.ManualUrlCollectionService
import com.ohmyclipping.service.collection.RssSourceCollectionService
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssItemStore
import com.ohmyclipping.store.RssSourceStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * RSS 피드 자동 수집 흐름을 조정하는 서비스.
 * 개별 URL 추가 공개 계약은 유지하되 실제 처리는 ManualUrlCollectionService에 위임한다.
 */
@Service
class CollectionService(
    private val categoryStore: CategoryStore,
    private val sourceStore: RssSourceStore,
    private val itemStore: RssItemStore,
    private val rssSourceCollectionService: RssSourceCollectionService,
    private val manualUrlCollectionService: ManualUrlCollectionService,
    private val statsService: StatsService,
    private val runtimeSettingService: RuntimeSettingService
) {

    /**
     * 지정된 카테고리(또는 전체 활성 카테고리)의 RSS 소스에서 아이템을 수집한다.
     * 중복 링크/유사 제목은 자동으로 건너뛴다.
     *
     * @param categoryId 특정 카테고리만 수집할 경우 ID, null이면 전체 활성 카테고리
     * @param hoursBack 수집 시간 범위(시간), null이면 런타임 기본값 사용
     * @return 수집 결과 (총 수집 건수, 신규 건수, 중복 건수, 카테고리별 결과)
     */
    fun collect(categoryId: String?, hoursBack: Int?): CollectResult {
        val runtime = runtimeSettingService.current()
        val hours = hoursBack ?: runtime.defaultHoursBack
        ensureValid(hours > 0) { "hoursBack must be greater than 0" }
        val categories = resolveCategories(categoryId)
        ensureValid(categories.isNotEmpty()) { "No categories found" }

        var totalCollected = 0
        var totalNew = 0
        var totalDuplicates = 0
        val categoryResults = mutableListOf<CollectCategoryResult>()

        for (category in categories) {
            val sources = sourceStore.listApproved(category.id)
            var catCollected = 0
            var catNew = 0
            var catDuplicates = 0
            // DB에서 최근 7일간 제목을 조회하여 유사 제목 중복을 DB 수준에서도 감지한다
            val recentDbTitles = itemStore.findRecentTitles(
                categoryId = category.id,
                after = Instant.now().minus(Duration.ofDays(7)),
                limit = 500
            )
            val existingTitles = recentDbTitles.toMutableList()
            val seenLinks = mutableSetOf<String>()

            for (source in sources) {
                val sourceResult = rssSourceCollectionService.collectSource(
                    category = category,
                    source = source,
                    hours = hours,
                    existingTitles = existingTitles,
                    seenLinks = seenLinks
                )

                catCollected += sourceResult.collected
                catNew += sourceResult.newItems
                catDuplicates += sourceResult.duplicates
                totalDuplicates += sourceResult.duplicates
            }

            totalCollected += catCollected
            totalNew += catNew
            categoryResults.add(CollectCategoryResult(category.id, category.name, catCollected, catNew))
            if (catCollected > 0) {
                statsService.recordCollection(category.id, catCollected, catDuplicates)
            }
            log.info { "Collected $catNew new items for category '${category.name}'" }
        }

        return CollectResult(totalCollected, totalNew, totalDuplicates, categoryResults)
    }

    /**
     * 개별 URL을 수동으로 추가한다.
     * URL 안전성 검증, 허용 도메인 확인, robots.txt 정책 확인을 거친 뒤
     * 본문을 추출하여 아이템으로 저장한다.
     *
     * @param categoryId 추가 대상 카테고리 ID
     * @param rawUrl 추가할 URL
     * @return 추가 결과 (성공/중복 여부, 아이템 ID 등)
     */
    fun addUrl(categoryId: String, rawUrl: String): AddUrlResult {
        return manualUrlCollectionService.addUrl(categoryId, rawUrl)
    }

    // -- private helpers --

    private fun resolveCategories(categoryId: String?): List<Category> =
        if (categoryId != null) {
            listOfNotNull(categoryStore.findById(categoryId))
        } else {
            categoryStore.findOperational()
        }
}
