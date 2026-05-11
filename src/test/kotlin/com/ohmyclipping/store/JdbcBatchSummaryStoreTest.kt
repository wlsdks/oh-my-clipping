package com.ohmyclipping.store

import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.RssItem
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.junit.jupiter.api.assertThrows

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcBatchSummaryStoreTest {

    @Autowired lateinit var batchSummaryStore: BatchSummaryStore
    @Autowired lateinit var summarySearchStore: SummarySearchStore
    @Autowired lateinit var digestCandidateStore: DigestCandidateStore
    @Autowired lateinit var categoryStore: CategoryStore
    @Autowired lateinit var itemStore: RssItemStore
    @Autowired lateinit var entityManager: EntityManager
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var categoryId: String
    private lateinit var jdbcBatchSummaryStore: JdbcBatchSummaryStore

    @BeforeEach
    fun setup() {
        categoryId = categoryStore.save(
            Category(id = "", name = "BatchSummaryStore-${System.nanoTime()}")
        ).id
        jdbcBatchSummaryStore = JdbcBatchSummaryStore(jdbcTemplate)
    }

    @Test
    fun `findByCategoryId는 100건을 초과해도 전체 요약을 반환한다`() {
        saveSummaries(count = 101, sent = false)

        batchSummaryStore.findByCategoryId(categoryId).size shouldBe 101
    }

    @Test
    fun `findUnsent는 20건을 초과해도 전체 미발송 요약을 반환한다`() {
        saveSummaries(count = 21, sent = false)
        saveSummaries(count = 3, sent = true)

        batchSummaryStore.findUnsent(categoryId).size shouldBe 21
    }

    @Test
    fun `save는 rss item 카테고리와 다른 요약을 거부한다`() {
        val otherCategoryId = categoryStore.save(
            Category(id = "", name = "BatchSummaryStore-Other-${System.nanoTime()}")
        ).id
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "store-item-mismatch",
                content = "store-content-mismatch",
                link = "https://93.184.216.34/batch-summary-mismatch-${System.nanoTime()}",
                categoryId = categoryId
            )
        )

        assertThrows<DataAccessException> {
            batchSummaryStore.save(
                BatchSummary(
                    id = "",
                    originalTitle = "summary-title-mismatch",
                    translatedTitle = "요약 제목 mismatch",
                    summary = "요약 본문 mismatch",
                    keywords = listOf("kw-mismatch"),
                    importanceScore = 0.7f,
                    sourceLink = item.link,
                    isSentToSlack = false,
                    categoryId = otherCategoryId,
                    rssItemId = item.id
                )
            )
        }
    }

    @Test
    fun `save는 같은 기사라도 링크가 다른 요약을 거부한다`() {
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "store-item-link-mismatch",
                content = "store-content-link-mismatch",
                link = "https://93.184.216.34/batch-summary-link-ok-${System.nanoTime()}",
                categoryId = categoryId
            )
        )

        assertThrows<DataAccessException> {
            batchSummaryStore.save(
                BatchSummary(
                    id = "",
                    originalTitle = item.title,
                    translatedTitle = "요약 제목 link mismatch",
                    summary = "요약 본문 link mismatch",
                    keywords = listOf("kw-link-mismatch"),
                    importanceScore = 0.7f,
                    sourceLink = "https://93.184.216.34/batch-summary-link-bad-${System.nanoTime()}",
                    isSentToSlack = false,
                    categoryId = categoryId,
                    rssItemId = item.id
                )
            )
        }
    }

    @Test
    fun `findDigestCandidatesWithSource는 since 이후 기사를 내림차순으로 반환한다`() {
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "digest-candidate-title",
                content = "digest-candidate-content",
                link = "https://93.184.216.34/digest-candidate-${System.nanoTime()}",
                categoryId = categoryId
            )
        )
        val summary = batchSummaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = item.title,
                translatedTitle = "다이제스트 후보",
                summary = "다이제스트 본문",
                keywords = listOf("digest"),
                importanceScore = 0.8f,
                sourceLink = item.link,
                isSentToSlack = false,
                categoryId = categoryId,
                rssItemId = item.id
            )
        )

        // JPA 세션을 플러시해야 JDBC 쿼리에서 저장된 데이터가 보인다.
        entityManager.flush()

        val (summaries, sourceMap) = digestCandidateStore.findDigestCandidatesWithSource(
            categoryId = categoryId,
            since = summary.createdAt.minusSeconds(1),
            limit = 100
        )

        summaries.any { it.id == summary.id } shouldBe true
        sourceMap.containsKey(summary.id) shouldBe true
    }

    @Test
    fun `findDigestCandidatesWithSource는 rss source가 없으면 source link 도메인을 소스 버킷으로 사용한다`() {
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "manual-digest-candidate",
                content = "manual-digest-content",
                link = "https://www.example-news.com/manual-${System.nanoTime()}",
                categoryId = categoryId,
                rssSourceId = null
            )
        )
        val summary = batchSummaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = item.title,
                translatedTitle = "수동 후보",
                summary = "수동 URL 기반 후보",
                keywords = listOf("manual"),
                importanceScore = 0.8f,
                sourceLink = item.link,
                isSentToSlack = false,
                categoryId = categoryId,
                rssItemId = item.id
            )
        )

        entityManager.flush()

        val (summaries, sourceMap) = digestCandidateStore.findDigestCandidatesWithSource(
            categoryId = categoryId,
            since = summary.createdAt.minusSeconds(1),
            limit = 100
        )

        summaries.any { it.id == summary.id } shouldBe true
        sourceMap[summary.id] shouldBe "example-news.com"
    }

    @Test
    fun `findDigestCandidatesWithSource는 since보다 오래된 기사를 제외한다`() {
        val item = itemStore.save(
            RssItem(
                id = "",
                title = "digest-old-title",
                content = "digest-old-content",
                link = "https://93.184.216.34/digest-old-${System.nanoTime()}",
                categoryId = categoryId
            )
        )
        val summary = batchSummaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = item.title,
                translatedTitle = "오래된 후보",
                summary = "오래된 본문",
                keywords = listOf("old"),
                importanceScore = 0.5f,
                sourceLink = item.link,
                isSentToSlack = false,
                categoryId = categoryId,
                rssItemId = item.id
            )
        )

        // JPA 세션을 플러시해야 JDBC 쿼리에서 저장된 데이터가 보인다.
        entityManager.flush()

        // since를 summary 생성 이후로 설정하면 결과에서 제외되어야 한다.
        val (summaries, _) = digestCandidateStore.findDigestCandidatesWithSource(
            categoryId = categoryId,
            since = summary.createdAt.plusSeconds(60),
            limit = 100
        )

        summaries.any { it.id == summary.id } shouldBe false
    }

    @Test
    fun `findDigestCandidatesWithSource는 빈 결과일 때 empty pair를 반환한다`() {
        val futureInstant = java.time.Instant.now().plusSeconds(3600)

        val (summaries, sourceMap) = digestCandidateStore.findDigestCandidatesWithSource(
            categoryId = categoryId,
            since = futureInstant,
            limit = 100
        )

        summaries shouldBe emptyList()
        sourceMap shouldBe emptyMap()
    }

    @Test
    fun `findByDateRange 는 limit 가 지정되면 SQL LIMIT 을 적용해 초과분을 제외한다`() {
        // 같은 카테고리에 10개 저장 → limit=3 로 요청하면 최신 3개만.
        saveSummaries(count = 10, sent = false)
        val since = java.time.Instant.now().minusSeconds(60L * 60)

        val all = batchSummaryStore.findByDateRange(since, java.time.Instant.now(), categoryId)
            .filter { it.categoryId == categoryId }
        val limited = batchSummaryStore.findByDateRange(
            since, java.time.Instant.now(), categoryId, limit = 3,
        )

        all.size shouldBe 10
        limited.size shouldBe 3
        // LIMIT 가 created_at DESC 정렬 뒤에 적용되는지 — 결과는 all 의 앞 3건과 일치해야 한다.
        limited.map { it.id } shouldBe all.take(3).map { it.id }
    }

    @Test
    fun `findByCategoryIdsAndDateRange 는 카테고리별 top N 을 단일 쿼리로 반환한다`() {
        // 두 카테고리 각각 5개 저장. limitPerCategory=2 → 카테고리마다 최신 2개 (총 4개) 만 남아야 한다.
        val otherCategoryId = categoryStore.save(
            Category(id = "", name = "BatchSummaryStore-Other-${System.nanoTime()}"),
        ).id
        saveSummaries(count = 5, sent = false)
        saveSummaries(count = 5, sent = false, categoryIdOverride = otherCategoryId)
        // @Primary JpaBatchSummaryStore 가 native SQL 로 조회하므로 persistence context 를 먼저 flush.
        entityManager.flush()

        val since = java.time.Instant.now().minusSeconds(60L * 60)
        val result = batchSummaryStore.findByCategoryIdsAndDateRange(
            categoryIds = listOf(categoryId, otherCategoryId),
            from = since,
            to = java.time.Instant.now(),
            limitPerCategory = 2,
        )

        // 두 카테고리 × 2 = 총 4개, 카테고리별 exactly 2개.
        result.filter { it.categoryId == categoryId }.size shouldBe 2
        result.filter { it.categoryId == otherCategoryId }.size shouldBe 2
        // N+1 없이 한 번의 호출로 양 카테고리 결과가 합쳐져 들어와야 한다.
        result.size shouldBe 4
    }

    @Test
    fun `findByCategoryIdsAndDateRange 는 빈 categoryIds 입력에 빈 리스트를 반환한다`() {
        val result = batchSummaryStore.findByCategoryIdsAndDateRange(
            categoryIds = emptyList(),
            from = java.time.Instant.now().minusSeconds(60L * 60),
            to = java.time.Instant.now(),
            limitPerCategory = 5,
        )
        result shouldBe emptyList()
    }

    @Test
    fun `findSentArticles와 countSentArticles는 빈 categoryIds이면 발행 기사가 있어도 반환하지 않는다`() {
        saveSummaries(count = 3, sent = true)
        entityManager.flush()

        val articles = batchSummaryStore.findSentArticles(
            categoryIds = emptyList(),
            offset = 0,
            limit = 20
        )
        val count = batchSummaryStore.countSentArticles(categoryIds = emptyList())

        articles shouldBe emptyList()
        count shouldBe 0
    }

    @Test
    fun `findTopArticles keyword 는 제목 본문이 아니라 keywords 컬럼만 검색한다`() {
        val now = java.time.Instant.now()
        val titleOnly = saveSummary(
            originalTitle = "AI title only",
            translatedTitle = "AI 번역 제목",
            summary = "AI 본문",
            keywords = listOf("not-match"),
            importanceScore = 0.99f,
        )
        val keywordMatch = saveSummary(
            originalTitle = "plain title",
            translatedTitle = "평범한 제목",
            summary = "평범한 본문",
            keywords = listOf("AI"),
            importanceScore = 0.5f,
        )
        entityManager.flush()

        val result = batchSummaryStore.findTopArticles(
            from = now.minusSeconds(60L * 60),
            to = now.plusSeconds(60L * 60),
            categoryId = categoryId,
            sentiment = null,
            eventType = null,
            keyword = "ai",
            limit = 10,
        )

        result.map { it.id } shouldBe listOf(keywordMatch.id)
        result.none { it.id == titleOnly.id } shouldBe true
    }

    @Test
    fun `searchInDateRange 는 최신 limit window 밖의 일치 기사도 반환한다`() {
        val matching = saveSummary(
            originalTitle = "Semiconductor capacity signal",
            translatedTitle = "반도체 생산능력 신호",
            summary = "공급망 변화를 설명하는 본문",
            keywords = listOf("not-used"),
            importanceScore = 0.9f,
        )
        repeat(50) { index ->
            saveSummary(
                originalTitle = "Unrelated market update $index",
                translatedTitle = "무관한 시장 업데이트 $index",
                summary = "검색어가 없는 본문 $index",
                keywords = listOf("noise-$index"),
                importanceScore = 0.8f,
            )
        }
        entityManager.flush()

        val primaryResult = summarySearchStore.searchInDateRange(
            categoryId = categoryId,
            query = "semiconductor",
            from = java.time.LocalDate.now().minusDays(1),
            to = java.time.LocalDate.now(),
            limit = 10,
        )
        val jdbcResult = jdbcBatchSummaryStore.searchInDateRange(
            categoryId = categoryId,
            query = "semiconductor",
            from = java.time.LocalDate.now().minusDays(1),
            to = java.time.LocalDate.now(),
            limit = 10,
        )

        primaryResult.map { it.id } shouldBe listOf(matching.id)
        jdbcResult.map { it.id } shouldBe listOf(matching.id)
    }

    @Test
    fun `searchInDateRange LIKE fallback 은 keywords 필드도 검색한다`() {
        val keywordOnly = saveSummary(
            originalTitle = "Plain capacity signal",
            translatedTitle = "평범한 생산능력 신호",
            summary = "검색어가 제목과 본문에는 없는 데이터",
            keywords = listOf("semiconductor-keyword-only"),
            importanceScore = 0.9f,
        )
        saveSummary(
            originalTitle = "Semiconductor title should not match requested token",
            translatedTitle = "반도체 제목",
            summary = "다른 본문",
            keywords = listOf("other-keyword"),
            importanceScore = 0.8f,
        )
        entityManager.flush()

        val primaryResult = summarySearchStore.searchInDateRange(
            categoryId = categoryId,
            query = "semiconductor-keyword-only",
            from = java.time.LocalDate.now().minusDays(1),
            to = java.time.LocalDate.now(),
            limit = 10,
        )
        val jdbcResult = jdbcBatchSummaryStore.searchInDateRange(
            categoryId = categoryId,
            query = "semiconductor-keyword-only",
            from = java.time.LocalDate.now().minusDays(1),
            to = java.time.LocalDate.now(),
            limit = 10,
        )

        primaryResult.map { it.id } shouldBe listOf(keywordOnly.id)
        jdbcResult.map { it.id } shouldBe listOf(keywordOnly.id)
    }

    private fun saveSummaries(count: Int, sent: Boolean, categoryIdOverride: String? = null) {
        val targetCategory = categoryIdOverride ?: categoryId
        repeat(count) { index ->
            val item = itemStore.save(
                RssItem(
                    id = "",
                    title = "store-item-$index",
                    content = "store-content-$index",
                    link = "https://93.184.216.34/batch-summary-${System.nanoTime()}-$index",
                    categoryId = targetCategory
                )
            )
            batchSummaryStore.save(
                BatchSummary(
                    id = "",
                    originalTitle = item.title,
                    translatedTitle = "요약 제목 $index",
                    summary = "요약 본문 $index",
                    keywords = listOf("kw-$index"),
                    importanceScore = 0.9f,
                    sourceLink = item.link,
                    isSentToSlack = sent,
                    categoryId = targetCategory,
                    rssItemId = item.id
                )
            )
        }
    }

    private fun saveSummary(
        originalTitle: String,
        translatedTitle: String,
        summary: String,
        keywords: List<String>,
        importanceScore: Float,
    ): BatchSummary {
        val suffix = System.nanoTime()
        val item = itemStore.save(
            RssItem(
                id = "",
                title = originalTitle,
                content = "store-content-$suffix",
                link = "https://93.184.216.34/batch-summary-top-$suffix",
                categoryId = categoryId
            )
        )
        return batchSummaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = originalTitle,
                translatedTitle = translatedTitle,
                summary = summary,
                keywords = keywords,
                importanceScore = importanceScore,
                sourceLink = item.link,
                isSentToSlack = false,
                categoryId = categoryId,
                rssItemId = item.id,
            )
        )
    }
}
