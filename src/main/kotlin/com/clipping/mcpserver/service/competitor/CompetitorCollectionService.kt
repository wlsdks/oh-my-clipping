package com.clipping.mcpserver.service.competitor

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.service.collection.toRssItem
import com.clipping.mcpserver.service.port.RssCollectionPort
import com.clipping.mcpserver.store.BatchSummaryCompetitorStore
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CompetitorRssFeedStore
import com.clipping.mcpserver.store.CompetitorWatchlistStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.support.GoogleNewsRssUrlBuilder
import com.clipping.mcpserver.support.InterruptibleSleep
import java.util.UUID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

/**
 * 경쟁사 RSS 수집 오케스트레이터.
 * 활성 경쟁사 목록을 순회하며 Google News 키워드 검색과 수동 RSS 피드를
 * 모두 수집하고, 중복 기사는 junction만 추가한다.
 */
@Service
class CompetitorCollectionService(
    private val watchlistStore: CompetitorWatchlistStore,
    private val rssFeedStore: CompetitorRssFeedStore,
    private val rssFeedCollector: RssCollectionPort,
    private val rssItemStore: RssItemStore,
    private val batchSummaryStore: BatchSummaryStore,
    private val batchSummaryCompetitorStore: BatchSummaryCompetitorStore,
    private val jdbc: JdbcTemplate
) {

    companion object {
        /** 경쟁사 수집 전용 카테고리 ID. 일반 뉴스 카테고리와 구별한다. */
        const val COMPETITOR_CATEGORY_ID = "__competitor__"

        /** RSS 설명(content)에서 BatchSummary.summary에 저장할 최대 문자 수 */
        private const val MAX_DESCRIPTION_LENGTH = 500

        /** 경쟁사 기사의 기본 중요도 점수 (0.0~1.0) */
        private const val DEFAULT_IMPORTANCE_SCORE = 0.5f

        /** 피드당 수집할 최대 기사 수. 무제한 결과를 방지한다. */
        private const val MAX_ARTICLES_PER_FEED = 30

        /** 경쟁사 간 수집 대기 시간(ms). Google News rate limit(429) 방지. */
        private const val RATE_LIMIT_DELAY_MS = 1000L
    }

    /**
     * 모든 활성 경쟁사의 RSS를 수집한다.
     * 경쟁사별로 실패를 격리하여 한 경쟁사의 오류가 다른 경쟁사 수집에 영향을 주지 않는다.
     *
     * @param hoursBack 수집 범위(시간). 기본 24시간.
     * @return 경쟁사별 수집 결과 리스트
     */
    fun collectAll(hoursBack: Int = 24): List<CollectionResult> {
        if (hoursBack <= 0) {
            throw InvalidInputException("hoursBack must be greater than 0")
        }

        // 활성 경쟁사만 대상으로 한다.
        val competitors = watchlistStore.findActive()
        if (competitors.isEmpty()) {
            log.info { "활성 경쟁사가 없어 수집을 건너뛴다." }
            return emptyList()
        }

        // __competitor__ 시스템 카테고리가 없으면 자동 생성한다.
        ensureCompetitorCategoryExists()

        log.info { "경쟁사 수집 시작: ${competitors.size}개 경쟁사" }

        return competitors.mapIndexed { index, competitor ->
            // 경쟁사별로 실패를 격리한다.
            runCatching {
                // 경쟁사 간 rate limit 대기 — Google News 429 방지
                if (index > 0) {
                    InterruptibleSleep.sleep(RATE_LIMIT_DELAY_MS, "competitor collection rate limit")
                }

                val allItems = mutableListOf<RssItem>()

                // Google News 키워드 검색 URL 생성 및 수집
                // 키워드가 없으면 경쟁사 이름을 검색어로 사용한다.
                val searchTerms = listOf(competitor.name) + competitor.aliases
                val googleNewsUrl = GoogleNewsRssUrlBuilder.buildUrl(searchTerms, competitor.excludeKeywords)
                if (googleNewsUrl != null) {
                    val googleItems = rssFeedCollector.collectByUrl(googleNewsUrl, hoursBack)
                        .map { it.toRssItem() }
                        .take(MAX_ARTICLES_PER_FEED)
                    allItems.addAll(googleItems)
                }

                // 수동 RSS 피드 수집
                val manualFeeds = rssFeedStore.findByCompetitorId(competitor.id)
                for (feed in manualFeeds) {
                    runCatching {
                        val feedItems = rssFeedCollector.collectByUrl(feed.feedUrl, hoursBack)
                            .map { it.toRssItem() }
                            .take(MAX_ARTICLES_PER_FEED)
                        allItems.addAll(feedItems)
                    }.onFailure { e ->
                        log.warn(e) { "수동 RSS 피드 수집 실패: ${feed.feedUrl} (경쟁사: ${competitor.name})" }
                    }
                }

                var newCount = 0
                var linkedCount = 0

                for (item in allItems.distinctBy { it.link }) {
                    // 이미 요약된 기사인지 확인한다.
                    val existingSummary = batchSummaryStore.findBySourceLinkAndCategoryId(item.link, COMPETITOR_CATEGORY_ID)
                    if (existingSummary != null) {
                        // 기존 요약에 경쟁사 junction만 추가한다.
                        batchSummaryCompetitorStore.link(existingSummary.id, competitor.id)
                        linkedCount++
                    } else {
                        // RssItem을 DB에 먼저 저장하여 FK 제약조건을 만족시킨다.
                        val savedItem = rssItemStore.save(
                            item.copy(categoryId = COMPETITOR_CATEGORY_ID)
                        )
                        // 저장된 RssItem ID로 BatchSummary를 생성한다.
                        val summary = createBatchSummaryFromRssItem(
                            savedItem, COMPETITOR_CATEGORY_ID
                        )
                        val saved = batchSummaryStore.save(summary)
                        batchSummaryCompetitorStore.link(saved.id, competitor.id)
                        newCount++
                    }
                }

                log.info { "경쟁사 수집 완료: ${competitor.name} — 신규 ${newCount}건, 기존 연결 ${linkedCount}건" }
                CollectionResult(
                    competitorId = competitor.id,
                    competitorName = competitor.name,
                    newItemCount = newCount,
                    linkedItemCount = linkedCount,
                    error = null
                )
            }.getOrElse { e ->
                log.error(e) { "경쟁사 수집 실패: ${competitor.name}" }
                CollectionResult(
                    competitorId = competitor.id,
                    competitorName = competitor.name,
                    newItemCount = 0,
                    linkedItemCount = 0,
                    error = e.message
                )
            }
        }
    }

    /**
     * RSS 피드 아이템으로부터 기본 BatchSummary를 생성한다.
     * AI 요약 없이 RSS 원본 데이터(제목, 설명)로 채운다.
     * 경쟁사 AI 요약 보강은 CompetitorCollectionScheduler 책임으로 분리되어 있다.
     */
    private fun createBatchSummaryFromRssItem(item: RssItem, categoryId: String): BatchSummary {
        val descriptionSnippet = item.content?.take(MAX_DESCRIPTION_LENGTH) ?: ""
        return BatchSummary(
            id = UUID.randomUUID().toString(),
            originalTitle = item.title,
            summary = descriptionSnippet,
            sourceLink = item.link,
            categoryId = categoryId,
            rssItemId = item.id,
            importanceScore = DEFAULT_IMPORTANCE_SCORE,
            isSentToSlack = false
        )
    }

    /**
     * 경쟁사 수집 결과.
     */
    /**
     * __competitor__ 시스템 카테고리가 존재하지 않으면 자동 생성한다.
     * DB를 초기화하거나 신규 환경에서도 경쟁사 수집이 정상 동작하도록 보장한다.
     */
    private fun ensureCompetitorCategoryExists() {
        val exists = runCatching {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM batch_categories WHERE id = ?",
                Int::class.java,
                COMPETITOR_CATEGORY_ID
            )?.let { it > 0 } ?: false
        }.getOrDefault(false)

        if (!exists) {
            log.info { "__competitor__ 시스템 카테고리가 없어 자동 생성합니다." }
            jdbc.update(
                """INSERT INTO batch_categories (id, name, is_active, max_items, created_at, updated_at, status)
                   VALUES (?, '경쟁사 뉴스 (시스템)', true, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ACTIVE')""",
                COMPETITOR_CATEGORY_ID
            )
        }
    }

    data class CollectionResult(
        val competitorId: String,
        val competitorName: String,
        val newItemCount: Int,
        val linkedItemCount: Int,
        val error: String?
    ) {
        /** 수집 성공 여부 */
        val isSuccess: Boolean get() = error == null
    }
}
