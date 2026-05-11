package com.ohmyclipping.service.competitor

import com.ohmyclipping.content.ArticleContentExtractor
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.CompetitorRssFeed
import com.ohmyclipping.model.CompetitorWatchlist
import com.ohmyclipping.model.OriginalContent
import com.ohmyclipping.service.dto.user.ArticleDetailView
import com.ohmyclipping.service.dto.analytics.CompetitorResponse
import com.ohmyclipping.service.dto.analytics.CompetitorRssFeedResponse
import com.ohmyclipping.service.dto.analytics.CompetitorSentimentItem
import com.ohmyclipping.service.dto.analytics.CompetitorSentimentResponse
import com.ohmyclipping.service.dto.analytics.CompetitorTimelineItem
import com.ohmyclipping.service.dto.analytics.CompetitorTimelineResponse
import com.ohmyclipping.service.dto.analytics.KeywordPreviewItem
import com.ohmyclipping.service.dto.analytics.KeywordPreviewResponse
import com.ohmyclipping.service.dto.analytics.RssFeedInput
import com.ohmyclipping.service.dto.analytics.SovPeriod
import com.ohmyclipping.service.dto.analytics.SovResponse
import com.ohmyclipping.service.dto.analytics.SovShareItem
import com.ohmyclipping.service.collection.toRssItem
import com.ohmyclipping.service.port.RssCollectionPort
import com.ohmyclipping.store.BatchSummaryCompetitorStore
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CompetitorRssFeedStore
import com.ohmyclipping.store.CompetitorWatchlistStore
import com.ohmyclipping.store.OriginalContentStore
import com.ohmyclipping.store.BookmarkedArticleStore
import com.ohmyclipping.support.GoogleNewsRssUrlBuilder
import com.ohmyclipping.support.InputSanitizer
import com.ohmyclipping.support.SlackMentionGuard
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 경쟁사 워치리스트 관리 및 분석 서비스.
 * CRUD, 타임라인, Share of Voice, 키워드 프리뷰 기능을 제공한다.
 */
@Service
class CompetitorWatchlistService(
    private val watchlistStore: CompetitorWatchlistStore,
    private val batchSummaryStore: BatchSummaryStore,
    private val batchSummaryCompetitorStore: BatchSummaryCompetitorStore,
    private val competitorRssFeedStore: CompetitorRssFeedStore,
    private val originalContentStore: OriginalContentStore,
    private val articleContentExtractor: ArticleContentExtractor,
    private val rssFeedCollector: RssCollectionPort,
    private val bookmarkedArticleStore: BookmarkedArticleStore,
    private val organizationSynchronizer: CompetitorOrganizationSynchronizer
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val VALID_TIERS = setOf("DIRECT", "ADJACENT", "GLOBAL")
        private const val SIMILARITY_THRESHOLD = 0.8
        private const val KEYWORD_PREVIEW_HOURS_BACK = 72
        private const val KEYWORD_PREVIEW_MAX_ITEMS = 5

        /** 경쟁사 이름 최대 길이 — DB: competitor_watchlist.name VARCHAR(100) */
        const val COMPETITOR_NAME_MAX = 100

        /** 별칭 1건당 최대 길이 */
        const val COMPETITOR_ALIAS_MAX = 60

        /** 제외 키워드 1건당 최대 길이 */
        const val COMPETITOR_EXCLUDE_KEYWORD_MAX = 60

        /** 경쟁사당 허용 별칭 수 */
        const val COMPETITOR_ALIASES_COUNT_MAX = 10

        /** 경쟁사당 허용 제외 키워드 수 */
        const val COMPETITOR_EXCLUDE_KEYWORDS_COUNT_MAX = 20
    }

    /**
     * 전체 경쟁사 목록을 조회한다. RSS 피드와 기사 통계를 포함한다.
     */
    fun list(): List<CompetitorResponse> {
        val competitors = watchlistStore.findAll()
        if (competitors.isEmpty()) return emptyList()

        val competitorIds = competitors.map { it.id }
        // 경쟁사별 기사 통계를 일괄 조회한다.
        val articleCounts = batchSummaryCompetitorStore.countByCompetitorIds(competitorIds)
        val since24h = Instant.now().minus(24, ChronoUnit.HOURS)
        val last24hCounts = batchSummaryCompetitorStore.countByCompetitorIdsSince(
            competitorIds, since24h
        )

        return competitors.map { competitor ->
            val rssFeeds = competitorRssFeedStore.findByCompetitorId(competitor.id)
            competitor.toResponse(
                rssFeeds = rssFeeds.map { it.toResponse() },
                articleCount = articleCounts[competitor.id] ?: 0,
                last24hCount = last24hCounts[competitor.id] ?: 0
            )
        }
    }

    /**
     * 새 경쟁사를 등록한다.
     *
     * @param name 경쟁사 이름 (빈 문자열 불가)
     * @param keywords 검색 키워드 목록
     * @param tier 등급 (DIRECT/ADJACENT/GLOBAL)
     * @param rssFeeds 수동 RSS 피드 목록 (선택)
     */
    fun create(
        name: String,
        aliases: List<String>,
        excludeKeywords: List<String> = emptyList(),
        tier: String,
        rssFeeds: List<RssFeedInput> = emptyList()
    ): CompetitorResponse {
        // 이름 필수 검증 + 저장 경계에서 길이/제어 문자 정규화를 적용한다.
        val cleanName = InputSanitizer.sanitizeRequired(name, "경쟁사 이름", COMPETITOR_NAME_MAX)
        // 등급 유효성 검증
        ensureValid(tier in VALID_TIERS) { "유효하지 않은 등급입니다: $tier (허용: $VALID_TIERS)" }
        // 중복 이름 검증 (대소문자 무시)
        val duplicate = list().find { it.name.equals(cleanName, ignoreCase = true) }
        if (duplicate != null) {
            throw InvalidInputException("이미 등록된 경쟁사입니다: $cleanName")
        }

        // 별칭/제외 키워드는 개별 항목마다 길이를 검증한다 — 전체 개수 상한도 함께 강제한다.
        val cleanAliases = sanitizeList(aliases, "별칭", COMPETITOR_ALIAS_MAX, COMPETITOR_ALIASES_COUNT_MAX)
        val cleanExcludeKeywords = sanitizeList(
            excludeKeywords,
            "제외 키워드",
            COMPETITOR_EXCLUDE_KEYWORD_MAX,
            COMPETITOR_EXCLUDE_KEYWORDS_COUNT_MAX
        )

        // Slack 멘션 패턴을 저장 시점에 중립화한다 — 경쟁사 이름/별칭/제외 키워드가 다이제스트에 삽입되므로.
        val watchlist = CompetitorWatchlist(
            id = "",
            name = SlackMentionGuard.neutralize(cleanName),
            aliases = cleanAliases.map { SlackMentionGuard.neutralize(it) },
            excludeKeywords = cleanExcludeKeywords.map { SlackMentionGuard.neutralize(it) },
            tier = tier
        )
        val saved = watchlistStore.save(watchlist)

        // RSS 피드를 저장한다.
        val savedFeeds = rssFeeds.map { feedInput ->
            competitorRssFeedStore.save(
                CompetitorRssFeed(
                    id = "",
                    competitorId = saved.id,
                    feedUrl = feedInput.url.trim(),
                    label = feedInput.label?.trim()
                )
            )
        }

        // 경쟁사 관리 = 단일 관리점. Organizations 테이블로 mirror 한다.
        organizationSynchronizer.onCompetitorCreated(saved.name)

        return saved.toResponse(
            rssFeeds = savedFeeds.map { it.toResponse() }
        )
    }

    /**
     * 기존 경쟁사 정보를 수정한다.
     * null인 필드는 기존 값을 유지한다.
     * rssFeeds가 제공되면 기존 피드를 삭제하고 새로 등록한다 (교체 전략).
     */
    fun update(
        id: String,
        name: String?,
        aliases: List<String>?,
        excludeKeywords: List<String>? = null,
        tier: String?,
        isActive: Boolean?,
        rssFeeds: List<RssFeedInput>? = null
    ): CompetitorResponse {
        // 대상 경쟁사 존재 여부 확인
        val existing = watchlistStore.findById(id)
            ?: throw NotFoundException("경쟁사를 찾을 수 없습니다: $id")

        if (tier != null) {
            ensureValid(tier in VALID_TIERS) {
                "유효하지 않은 등급입니다: $tier (허용: $VALID_TIERS)"
            }
        }
        // 각 입력 필드를 저장 경계 정규화 후 Slack 멘션을 중립화한다.
        val cleanName = name?.let {
            InputSanitizer.sanitizeOptional(it, "경쟁사 이름", COMPETITOR_NAME_MAX)
        }
        val cleanAliases = aliases?.let {
            sanitizeList(it, "별칭", COMPETITOR_ALIAS_MAX, COMPETITOR_ALIASES_COUNT_MAX)
        }
        val cleanExcludeKeywords = excludeKeywords?.let {
            sanitizeList(it, "제외 키워드", COMPETITOR_EXCLUDE_KEYWORD_MAX, COMPETITOR_EXCLUDE_KEYWORDS_COUNT_MAX)
        }

        // Slack 멘션 패턴을 중립화해 저장한다.
        val updated = existing.copy(
            name = cleanName?.let { SlackMentionGuard.neutralize(it) } ?: existing.name,
            aliases = cleanAliases?.map { SlackMentionGuard.neutralize(it) } ?: existing.aliases,
            excludeKeywords = cleanExcludeKeywords?.map { SlackMentionGuard.neutralize(it) } ?: existing.excludeKeywords,
            tier = tier ?: existing.tier,
            isActive = isActive ?: existing.isActive
        )
        val saved = watchlistStore.update(updated)

        // 이름이 변경됐다면 Organizations 도 같은 이름으로 mirror 한다.
        if (saved.name != existing.name) {
            organizationSynchronizer.onCompetitorRenamed(existing.name, saved.name)
        }

        // RSS 피드 교체: 제공되면 기존 피드를 삭제하고 새로 등록한다.
        if (rssFeeds != null) {
            competitorRssFeedStore.deleteByCompetitorId(id)
            rssFeeds.forEach { feedInput ->
                competitorRssFeedStore.save(
                    CompetitorRssFeed(
                        id = "",
                        competitorId = id,
                        feedUrl = feedInput.url.trim(),
                        label = feedInput.label?.trim()
                    )
                )
            }
        }

        // 최신 RSS 피드와 통계를 조회하여 응답에 포함한다.
        val currentFeeds = competitorRssFeedStore.findByCompetitorId(id)
        val articleCount = batchSummaryCompetitorStore.countByCompetitorId(id)
        val since24h = Instant.now().minus(24, ChronoUnit.HOURS)
        val last24hCount = batchSummaryCompetitorStore.countByCompetitorIdSince(id, since24h)

        return saved.toResponse(
            rssFeeds = currentFeeds.map { it.toResponse() },
            articleCount = articleCount,
            last24hCount = last24hCount
        )
    }

    /**
     * 경쟁사를 삭제한다.
     *
     * @throws NotFoundException 해당 ID의 경쟁사가 없을 경우
     */
    fun delete(id: String) {
        val existing = watchlistStore.findById(id)
            ?: throw NotFoundException("경쟁사를 찾을 수 없습니다: $id")
        // 연결된 RSS 피드도 함께 삭제한다.
        competitorRssFeedStore.deleteByCompetitorId(id)
        watchlistStore.delete(id)

        // Organizations 테이블의 mirror 레코드도 함께 정리한다.
        organizationSynchronizer.onCompetitorDeleted(existing.name)
    }

    /**
     * 경쟁사 관련 기사 타임라인을 조회한다.
     * junction 테이블(batch_summary_competitors)을 통해 경쟁사별 연결된 기사를 조회한다.
     * 유사한 제목(80% 이상 겹침)의 중복 기사는 제거한다.
     *
     * @param days 조회 기간 일수 (1~365 범위로 보정)
     * @param competitorId 특정 경쟁사만 필터 (null이면 전체)
     * @param eventType 이벤트 유형 필터 (null이면 전체)
     * @param sentiment 논조 필터: POSITIVE/NEUTRAL/NEGATIVE (null이면 전체)
     * @param limit 최대 결과 수 (1~100 범위로 보정)
     */
    fun getTimeline(
        days: Int,
        competitorId: String?,
        eventType: String?,
        sentiment: String? = null,
        limit: Int = 30
    ): CompetitorTimelineResponse {
        // 외부 요청값이 음수/과대 입력이어도 SQL LIMIT 과 take()가 깨지지 않도록 보정한다.
        val safeDays = days.coerceIn(1, 365)
        val safeLimit = limit.coerceIn(1, 100)
        // 활성 경쟁사 목록 조회
        val competitors = watchlistStore.findActive()
        if (competitors.isEmpty()) {
            return CompetitorTimelineResponse(items = emptyList())
        }

        // 특정 경쟁사 필터 적용
        val targetCompetitors = if (competitorId != null) {
            competitors.filter { it.id == competitorId }
        } else {
            competitors
        }
        if (targetCompetitors.isEmpty()) {
            return CompetitorTimelineResponse(items = emptyList())
        }

        val zone = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(zone)
        val fromDate = today.minusDays(safeDays.toLong() - 1)
        val fromInstant = fromDate.atStartOfDay(zone).toInstant()
        val toInstant = today.plusDays(1).atStartOfDay(zone).toInstant()

        val competitorIds = targetCompetitors.map { it.id }
        val competitorMap = targetCompetitors.associateBy { it.id }

        // junction 테이블에서 관련 요약 ID를 조회한다.
        val summaryIds = batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
            competitorIds, fromInstant, toInstant, limit = safeLimit * 3
        )
        if (summaryIds.isEmpty()) {
            return CompetitorTimelineResponse(items = emptyList())
        }

        // 요약 기사를 일괄 로드한다.
        val summaries = batchSummaryStore.findByIds(summaryIds)
        val summaryMap = summaries.associateBy { it.id }

        // 모든 요약에 연결된 경쟁사를 일괄 조회한다 (N+1 방지).
        val allLinks = batchSummaryCompetitorStore.findBySummaryIds(summaryIds)
        val linksBySummaryId = allLinks.groupBy { it.summaryId }

        val items = mutableListOf<CompetitorTimelineItem>()
        for (summaryId in summaryIds) {
            val summary = summaryMap[summaryId] ?: continue
            val linkedCompetitors = linksBySummaryId[summaryId] ?: continue
            // 타겟 경쟁사 중 첫 번째 매칭을 대표로 사용한다.
            val matchedCompetitor = linkedCompetitors
                .mapNotNull { competitorMap[it.competitorId] }
                .firstOrNull() ?: continue

            items += CompetitorTimelineItem(
                summaryId = summary.id,
                competitorId = matchedCompetitor.id,
                competitorName = matchedCompetitor.name,
                title = summary.translatedTitle ?: summary.originalTitle,
                summary = summary.summary,
                keywords = summary.keywords,
                sourceLink = summary.sourceLink,
                importanceScore = summary.importanceScore,
                eventType = summary.eventType,
                sentiment = summary.sentiment,
                createdAt = summary.createdAt.toString()
            )
        }

        // eventType 필터 적용
        val eventFiltered = if (eventType != null) {
            items.filter { it.eventType == eventType }
        } else {
            items
        }

        // 논조 필터 적용
        val filtered = if (sentiment != null) {
            eventFiltered.filter { it.sentiment == sentiment }
        } else {
            eventFiltered
        }

        // 유사 제목 중복을 제거한다.
        val deduplicated = removeSimilarTitles(filtered)

        // 최신순 정렬 후 limit 적용
        val sorted = deduplicated
            .sortedByDescending { it.createdAt }
            .take(safeLimit)

        return CompetitorTimelineResponse(items = sorted)
    }

    /**
     * Share of Voice(경쟁사별 언급 점유율)를 계산한다.
     * junction 테이블을 통해 경쟁사별 연결된 기사 수를 카운트한다.
     *
     * @param days 조회 기간 일수 (1~365 범위로 보정)
     * @param offsetDays 기간 전체를 과거로 밀 일수 (0 이상으로 보정, days = 이전 기간)
     */
    fun getShareOfVoice(days: Int, offsetDays: Int = 0): SovResponse {
        // 외부 요청값이 음수/과대 입력이어도 미래 기간을 만들지 않도록 보정한다.
        val safeDays = days.coerceIn(1, 365)
        val safeOffsetDays = offsetDays.coerceAtLeast(0)
        val zone = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(zone)
        // offsetDays만큼 전체 윈도우를 과거로 이동한다.
        val toDate = today.minusDays(safeOffsetDays.toLong())
        val fromDate = toDate.minusDays(safeDays.toLong() - 1)
        val fromInstant = fromDate.atStartOfDay(zone).toInstant()
        val toInstant = toDate.plusDays(1).atStartOfDay(zone).toInstant()

        // 활성 경쟁사 목록 조회
        val competitors = watchlistStore.findActive()
        if (competitors.isEmpty()) {
            return SovResponse(
                period = SovPeriod(from = fromDate.toString(), to = toDate.toString()),
                totalArticles = 0,
                shares = emptyList()
            )
        }

        val competitorIds = competitors.map { it.id }

        // junction 테이블에서 경쟁사별 기사 수를 조회한다.
        val summaryIds = batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
            competitorIds, fromInstant, toInstant, limit = 1000
        )

        // 모든 요약에 연결된 경쟁사를 일괄 조회한다 (N+1 방지).
        val allLinks = batchSummaryCompetitorStore.findBySummaryIds(summaryIds)
        val countMap = mutableMapOf<String, Int>()
        for (link in allLinks) {
            if (link.competitorId in competitorIds) {
                countMap[link.competitorId] =
                    (countMap[link.competitorId] ?: 0) + 1
            }
        }

        val totalArticles = countMap.values.sum()

        // 점유율 계산
        val shares = competitors
            .filter { countMap.containsKey(it.id) }
            .map { competitor ->
                val count = countMap[competitor.id] ?: 0
                SovShareItem(
                    competitorId = competitor.id,
                    name = competitor.name,
                    count = count,
                    share = if (totalArticles > 0) {
                        count.toDouble() / totalArticles
                    } else {
                        0.0
                    }
                )
            }
            .sortedByDescending { it.count }

        return SovResponse(
            period = SovPeriod(
                from = fromDate.toString(), to = toDate.toString()
            ),
            totalArticles = totalArticles,
            shares = shares
        )
    }

    /**
     * 전주 대비 Share of Voice 변화량(delta)을 포함한 SOV를 반환한다.
     * 현재 기간과 직전 동일 기간을 비교하여 각 SovShareItem에 delta를 병합한다.
     *
     * @param days 조회 기간 일수 (기본 7일)
     */
    fun getShareOfVoiceWithDelta(days: Int = 7): SovResponse {
        // 이전 기간 offset도 보정된 조회 기간과 동일하게 맞춘다.
        val safeDays = days.coerceIn(1, 365)
        // 현재 기간과 이전 기간 SOV를 각각 조회한다.
        val current = getShareOfVoice(safeDays, offsetDays = 0)
        val previous = getShareOfVoice(safeDays, offsetDays = safeDays)

        // 이전 기간 결과를 competitorId 기준으로 맵으로 변환한다.
        val prevMap = previous.shares.associateBy { it.competitorId }

        // 현재 항목에 delta 값을 병합한다.
        val mergedShares = current.shares.map { curr ->
            val prev = prevMap[curr.competitorId]
            curr.copy(
                prevCount = prev?.count,
                prevShare = prev?.share,
                shareDelta = if (prev != null) curr.share - prev.share else null
            )
        }

        return current.copy(shares = mergedShares)
    }

    /**
     * 주간 다이제스트용 경쟁사별 TOP 기사 목록을 반환한다.
     * importanceScore 내림차순으로 정렬하여 경쟁사별 최대 topPerCompetitor개를 반환한다.
     *
     * @param days 조회 기간 일수 (기본 7일)
     * @param topPerCompetitor 경쟁사당 최대 기사 수 (기본 5)
     * @return 경쟁사 이름 → 기사 목록 맵
     */
    fun getTopArticlesForWeeklyDigest(
        days: Int = 7,
        topPerCompetitor: Int = 5
    ): Map<String, List<CompetitorTimelineItem>> {
        // 전체 기사를 충분히 큰 limit으로 조회한다.
        val allItems = getTimeline(
            days = days,
            competitorId = null,
            eventType = null,
            sentiment = null,
            limit = 500
        ).items

        // 경쟁사 이름 기준으로 그룹화 후 importanceScore 내림차순 정렬 및 topN 적용한다.
        return allItems
            .groupBy { it.competitorName }
            .mapValues { (_, articles) ->
                articles
                    .sortedByDescending { it.importanceScore }
                    .take(topPerCompetitor)
            }
    }

    /**
     * 경쟁사별 논조(긍정/중립/부정) 집계를 반환한다.
     * junction 테이블을 통해 경쟁사별 기사를 매칭하고 sentiment별로 카운트한다.
     *
     * @param days 조회 기간 일수 (1~365 범위로 보정)
     */
    fun getCompetitorSentiment(days: Int): CompetitorSentimentResponse {
        // 외부 요청값이 음수/과대 입력이어도 미래 기간을 만들지 않도록 보정한다.
        val safeDays = days.coerceIn(1, 365)
        val zone = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(zone)
        val fromDate = today.minusDays(safeDays.toLong() - 1)
        val fromInstant = fromDate.atStartOfDay(zone).toInstant()
        val toInstant = today.plusDays(1).atStartOfDay(zone).toInstant()

        // 활성 경쟁사 목록 조회
        val competitors = watchlistStore.findActive()
        if (competitors.isEmpty()) {
            return CompetitorSentimentResponse(emptyList())
        }

        val competitorIds = competitors.map { it.id }

        // junction 테이블에서 관련 기사를 조회한다.
        val summaryIds = batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
            competitorIds, fromInstant, toInstant, limit = 500
        )
        if (summaryIds.isEmpty()) {
            return CompetitorSentimentResponse(emptyList())
        }

        val summaries = batchSummaryStore.findByIds(summaryIds)
        val summaryMap = summaries.associateBy { it.id }

        // 모든 요약에 연결된 경쟁사를 일괄 조회한다 (N+1 방지).
        val allLinks = batchSummaryCompetitorStore.findBySummaryIds(summaryIds)
        val linksBySummaryId = allLinks.groupBy { it.summaryId }

        // 경쟁사별 sentiment 카운트를 집계한다.
        data class SentimentCount(
            var positive: Int = 0,
            var neutral: Int = 0,
            var negative: Int = 0
        )

        val countMap = mutableMapOf<String, SentimentCount>()
        for (summaryId in summaryIds) {
            val summary = summaryMap[summaryId] ?: continue
            if (summary.sentiment == null) continue
            val linkedCompetitors = linksBySummaryId[summaryId] ?: continue
            for (link in linkedCompetitors) {
                if (link.competitorId !in competitorIds) continue
                val counts = countMap.getOrPut(link.competitorId) {
                    SentimentCount()
                }
                when (summary.sentiment) {
                    "POSITIVE" -> counts.positive++
                    "NEUTRAL" -> counts.neutral++
                    "NEGATIVE" -> counts.negative++
                }
            }
        }

        // 결과 DTO 변환 (기사가 있는 경쟁사만 포함)
        val items = competitors
            .filter { countMap.containsKey(it.id) }
            .map { competitor ->
                val counts = countMap[competitor.id] ?: SentimentCount()
                val total = counts.positive + counts.neutral + counts.negative
                CompetitorSentimentItem(
                    competitorId = competitor.id,
                    competitorName = competitor.name,
                    positive = counts.positive,
                    neutral = counts.neutral,
                    negative = counts.negative,
                    total = total,
                    positiveRate = if (total > 0) {
                        counts.positive.toDouble() / total
                    } else {
                        0.0
                    },
                )
            }

        return CompetitorSentimentResponse(items)
    }

    /**
     * 키워드로 Google News RSS 프리뷰를 반환한다.
     * 최근 72시간 내 기사 중 최대 5개를 반환한다.
     *
     * @param keywords 검색 키워드 목록
     */
    fun previewKeywords(keywords: List<String>): KeywordPreviewResponse {
        val filtered = keywords.map { it.trim() }.filter { it.isNotBlank() }
        if (filtered.isEmpty()) {
            return KeywordPreviewResponse(
                items = emptyList(),
                message = "키워드를 입력해 주세요."
            )
        }

        // Google News RSS URL을 생성한다.
        val url = GoogleNewsRssUrlBuilder.buildUrl(filtered)
            ?: return KeywordPreviewResponse(
                items = emptyList(),
                message = "유효한 RSS URL을 생성할 수 없습니다."
            )

        return try {
            // RSS 피드를 수집하여 프리뷰 항목을 생성한다.
            val rssItems = rssFeedCollector.collectByUrl(url, KEYWORD_PREVIEW_HOURS_BACK)
                .map { it.toRssItem() }
            val previewItems = rssItems.take(KEYWORD_PREVIEW_MAX_ITEMS).map { item ->
                KeywordPreviewItem(
                    title = item.title,
                    link = item.link,
                    publishedAt = item.publishedAt?.toString()
                )
            }
            KeywordPreviewResponse(
                items = previewItems,
                message = if (previewItems.isEmpty()) {
                    "최근 ${KEYWORD_PREVIEW_HOURS_BACK}시간 내 관련 기사가 없습니다."
                } else {
                    "${previewItems.size}건의 관련 기사를 찾았습니다."
                }
            )
        } catch (e: Exception) {
            log.warn("키워드 프리뷰 RSS 수집 실패: keywords={}", filtered, e)
            KeywordPreviewResponse(
                items = emptyList(),
                message = "RSS 피드 수집에 실패했습니다. 잠시 후 다시 시도해 주세요."
            )
        }
    }

    /**
     * 유사한 제목(80% 이상 문자 겹침)의 중복 기사를 제거한다.
     * importanceScore가 높은 기사를 유지한다.
     */
    private fun removeSimilarTitles(
        items: List<CompetitorTimelineItem>
    ): List<CompetitorTimelineItem> {
        if (items.size <= 1) return items

        // 중요도 순으로 정렬하여 높은 것부터 처리한다.
        val sorted = items.sortedByDescending { it.importanceScore }
        val result = mutableListOf<CompetitorTimelineItem>()

        for (item in sorted) {
            val isDuplicate = result.any { existing ->
                charOverlapRatio(existing.title, item.title) > SIMILARITY_THRESHOLD
            }
            if (!isDuplicate) {
                result += item
            }
        }

        return result
    }

    /**
     * 두 문자열의 문자 겹침 비율을 계산한다.
     * 짧은 쪽 기준으로 공통 문자 비율을 반환한다.
     */
    private fun charOverlapRatio(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val charsA = a.lowercase().toList()
        val charsB = b.lowercase().toMutableList()

        var overlap = 0
        for (c in charsA) {
            val idx = charsB.indexOf(c)
            if (idx >= 0) {
                overlap++
                charsB.removeAt(idx)
            }
        }
        val shorter = minOf(a.length, b.length)
        return if (shorter > 0) overlap.toDouble() / shorter else 0.0
    }

    /**
     * 경쟁사 기사 상세 정보를 조회한다.
     * 원문 마크다운이 DB에 없으면 소스 링크에서 실시간 추출을 시도한다.
     * 현재 사용자의 북마크 상태를 함께 반환한다.
     *
     * @param summaryId 기사(BatchSummary) ID
     * @param userId 현재 인증된 사용자 ID (북마크 상태 조회용)
     * @return 기사 상세 뷰 (존재하지 않으면 null)
     */
    fun getCompetitorArticleDetail(summaryId: String, userId: String): ArticleDetailView? {
        // 기사 조회
        val summary = batchSummaryStore.findById(summaryId) ?: return null

        // 원문 마크다운을 조회하거나 실시간 추출한다.
        val originalMarkdown = fetchOrExtractOriginalContent(
            summary.rssItemId, summary.sourceLink
        )

        // 북마크 상태를 확인한다.
        val isBookmarked = bookmarkedArticleStore
            .findBookmarkedSummaryIds(userId, listOf(summaryId))
            .contains(summaryId)

        // junction 테이블에서 연결된 첫 번째 경쟁사 이름을 조회한다.
        val competitorName = batchSummaryCompetitorStore
            .findBySummaryId(summaryId)
            .firstOrNull()
            ?.competitorId
            ?.let { watchlistStore.findById(it)?.name }

        return ArticleDetailView(
            id = summary.id,
            title = summary.translatedTitle ?: summary.originalTitle,
            summary = summary.summary,
            insights = summary.insights,
            originalContent = originalMarkdown,
            keywords = summary.keywords,
            importanceScore = summary.importanceScore,
            sourceLink = summary.sourceLink,
            categoryId = "",
            categoryName = "",
            isBookmarked = isBookmarked,
            createdAt = summary.createdAt.toString(),
            relatedArticles = emptyList(),
            competitorName = competitorName,
            eventType = summary.eventType
        )
    }

    /**
     * 원문 마크다운을 조회하고, DB에 없으면 소스 링크에서 실시간 추출하여 저장한다.
     * 추출 실패 시 null을 반환한다.
     *
     * rssItemId가 null인 경우(V139 retention 후 기사 삭제됨)는 DB 조회를 건너뛰고
     * 소스 링크에서 실시간 추출만 시도한다.
     */
    private fun fetchOrExtractOriginalContent(
        rssItemId: String?,
        sourceLink: String
    ): String? {
        // rssItemId가 있는 경우 DB에서 기존 원문을 먼저 조회한다.
        if (rssItemId != null) {
            val existing = originalContentStore.findByRssItemId(rssItemId)
            if (existing != null) return existing.markdown
        }

        // 소스 링크가 없으면 추출 불가
        if (sourceLink.isBlank()) return null

        return try {
            // 소스 링크에서 원문을 실시간 추출한다.
            val extracted = articleContentExtractor.extract(sourceLink)
                ?: return null
            // rssItemId가 있을 때만 DB에 저장한다 (null이면 FK 연결 불가로 저장 생략).
            if (rssItemId != null) {
                originalContentStore.save(
                    OriginalContent(
                        id = UUID.randomUUID().toString(),
                        rssItemId = rssItemId,
                        sourceLink = sourceLink,
                        title = extracted.title,
                        markdown = extracted.content
                    )
                )
            }
            extracted.content
        } catch (e: Exception) {
            log.warn(
                "경쟁사 기사 원문 실시간 추출 실패: sourceLink={}",
                sourceLink, e
            )
            null
        }
    }

    /**
     * 문자열 리스트를 저장 경계에서 일괄 검증/정규화한다.
     * - 항목 trim 후 빈 문자열 제거
     * - 개별 항목 길이 상한 초과 시 [InvalidInputException]을 던진다 (부분 정규화 금지)
     * - 전체 개수 상한 초과 시 [InvalidInputException]을 던진다
     */
    private fun sanitizeList(
        values: List<String>,
        fieldLabel: String,
        maxLength: Int,
        maxCount: Int
    ): List<String> {
        // 항목을 sanitize하되 빈 문자열은 자연스럽게 필터링한다.
        val cleaned = values.mapNotNull { InputSanitizer.sanitizeOptional(it, fieldLabel, maxLength) }
        if (cleaned.size > maxCount) {
            throw InvalidInputException("${fieldLabel}은 최대 ${maxCount}개까지 등록할 수 있어요")
        }
        return cleaned
    }

    /**
     * CompetitorWatchlist를 CompetitorResponse DTO로 변환한다.
     */
    private fun CompetitorWatchlist.toResponse(
        rssFeeds: List<CompetitorRssFeedResponse> = emptyList(),
        articleCount: Long = 0,
        last24hCount: Long = 0
    ) = CompetitorResponse(
        id = id,
        name = name,
        aliases = aliases,
        excludeKeywords = excludeKeywords,
        tier = tier,
        isActive = isActive,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
        rssFeeds = rssFeeds,
        articleCount = articleCount,
        last24hCount = last24hCount
    )

    /**
     * CompetitorRssFeed를 CompetitorRssFeedResponse로 변환한다.
     */
    private fun CompetitorRssFeed.toResponse() = CompetitorRssFeedResponse(
        id = id,
        feedUrl = feedUrl,
        label = label
    )
}
