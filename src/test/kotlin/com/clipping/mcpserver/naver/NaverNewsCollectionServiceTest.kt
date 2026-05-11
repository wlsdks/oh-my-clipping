package com.clipping.mcpserver.naver

import com.clipping.mcpserver.service.collection.NaverNewsSearchPort
import com.clipping.mcpserver.service.collection.NaverNewsSearchItem
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.model.CategoryStatus
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.service.collection.NaverNewsCollectionService
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RssItemStore
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.dao.DuplicateKeyException
import java.time.Instant

class NaverNewsCollectionServiceTest {

    private val naverNewsSearchPort = mockk<NaverNewsSearchPort>()
    private val itemStore = mockk<RssItemStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val categoryRuleStore = mockk<CategoryRuleStore>()

    private lateinit var service: NaverNewsCollectionService

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        service = NaverNewsCollectionService(
            naverNewsSearchPort = naverNewsSearchPort,
            itemStore = itemStore,
            categoryStore = categoryStore,
            categoryRuleStore = categoryRuleStore
        )
        every { itemStore.findRecentTitles(any(), any(), any()) } returns emptyList()
    }

    private fun aCategory(id: String = "cat-1", name: String = "테스트", isActive: Boolean = true) =
        Category(
            id = id,
            name = name,
            isActive = isActive,
            status = if (isActive) CategoryStatus.ACTIVE else CategoryStatus.PAUSED
        )

    private fun aNaverItem(title: String = "뉴스 제목", link: String = "https://example.com/1") =
        NaverNewsSearchItem(
            title = title,
            link = link,
            description = "뉴스 설명",
            pubDate = "Fri, 03 Apr 2026 08:00:00 +0900",
            publishedAt = Instant.now()
        )

    private fun resetService() {
        clearAllMocks()
        service = NaverNewsCollectionService(
            naverNewsSearchPort = naverNewsSearchPort,
            itemStore = itemStore,
            categoryStore = categoryStore,
            categoryRuleStore = categoryRuleStore
        )
        every { itemStore.findRecentTitles(any(), any(), any()) } returns emptyList()
    }

    @Nested
    inner class `collectForCategory 검증` {

        @Test
        fun `API 미설정이면 0을 반환한다`() {
            every { naverNewsSearchPort.isConfigured() } returns false

            service.collectForCategory("cat-1") shouldBe 0

            verify(exactly = 0) { categoryStore.findById(any()) }
        }

        @Test
        fun `카테고리가 존재하지 않으면 0을 반환한다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns null

            service.collectForCategory("cat-1") shouldBe 0
        }

        @Test
        fun `키워드가 없으면 0을 반환한다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = emptyList()
            )

            service.collectForCategory("cat-1") shouldBe 0
        }

        @Test
        fun `CategoryRule이 null이면 0을 반환한다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null

            service.collectForCategory("cat-1") shouldBe 0
        }

        @Test
        fun `신규 기사를 저장하고 건수를 반환한다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("MegaCorp")
            )
            every { naverNewsSearchPort.searchNews("MegaCorp") } returns listOf(
                aNaverItem(title = "MegaCorp 실적", link = "https://example.com/1"),
                aNaverItem(title = "MegaCorp 신제품", link = "https://example.com/2")
            )
            // 링크 기반 중복 없음 (일괄 확인)
            every { itemStore.findExistingLinks(any(), any()) } returns emptySet()
            // 저장 성공
            val savedSlot = slot<RssItem>()
            every { itemStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            service.collectForCategory("cat-1") shouldBe 2

            verify(exactly = 2) { itemStore.save(any()) }
        }

        @Test
        fun `링크 중복이면 건너뛴다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("AI")
            )
            every { naverNewsSearchPort.searchNews("AI") } returns listOf(
                aNaverItem(title = "AI 뉴스", link = "https://example.com/existing")
            )
            // 이미 존재하는 링크 (일괄 확인)
            every { itemStore.findExistingLinks(any(), any()) } returns setOf("https://example.com/existing")

            service.collectForCategory("cat-1") shouldBe 0

            verify(exactly = 0) { itemStore.save(any()) }
        }

        @Test
        fun `canonical URL 기준으로 링크 중복을 확인하고 저장한다`() {
            val rawLink = "https://example.com/news/1?utm_source=naver&id=42#section"
            val canonicalLink = "https://example.com/news/1?id=42"
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("AI")
            )
            every { naverNewsSearchPort.searchNews("AI") } returns listOf(
                aNaverItem(title = "AI 뉴스", link = rawLink)
            )
            every { itemStore.findExistingLinks(listOf(canonicalLink), "cat-1") } returns emptySet()
            val savedSlot = slot<RssItem>()
            every { itemStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            service.collectForCategory("cat-1") shouldBe 1

            savedSlot.captured.link shouldBe canonicalLink
        }

        @Test
        fun `tracking parameter만 다른 SearchCo 기사는 기존 canonical 링크와 중복이면 저장하지 않는다`() {
            val rawLink = "https://example.com/news/1?utm_campaign=x&id=42&fbclid=abc"
            val canonicalLink = "https://example.com/news/1?id=42"
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("AI")
            )
            every { naverNewsSearchPort.searchNews("AI") } returns listOf(
                aNaverItem(title = "AI 뉴스", link = rawLink)
            )
            every { itemStore.findExistingLinks(listOf(canonicalLink), "cat-1") } returns setOf(canonicalLink)

            service.collectForCategory("cat-1") shouldBe 0

            verify(exactly = 0) { itemStore.save(any()) }
        }

        @Test
        fun `같은 응답 안에서 canonical 링크가 같으면 한 번만 저장한다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("AI")
            )
            every { naverNewsSearchPort.searchNews("AI") } returns listOf(
                aNaverItem(title = "AI 뉴스 원본", link = "https://example.com/news/1?id=42&utm_source=a"),
                aNaverItem(title = "AI 뉴스 추적 링크", link = "https://example.com/news/1?fbclid=b&id=42")
            )
            every { itemStore.findExistingLinks(listOf("https://example.com/news/1?id=42"), "cat-1") } returns emptySet()
            every { itemStore.save(any()) } answers { firstArg() }

            service.collectForCategory("cat-1") shouldBe 1

            verify(exactly = 1) { itemStore.save(any()) }
        }

        @Test
        fun `저장되는 RssItem의 rssSourceId는 null이다(FK 제약 준수)`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("AI뉴스")
            )
            every { naverNewsSearchPort.searchNews("AI뉴스") } returns listOf(
                aNaverItem(title = "AI 동향", link = "https://news.naver.com/article/1")
            )
            every { itemStore.findExistingLinks(any(), any()) } returns emptySet()
            val savedSlot = slot<RssItem>()
            every { itemStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            service.collectForCategory("cat-1")

            // rss_sources FK가 존재하므로 null 유지 — 공정 배분 버킷은 store 레이어에서 source_link 도메인으로 파생한다
            savedSlot.captured.rssSourceId shouldBe null
        }

        @Test
        fun `여러 키워드로 검색하여 합산한다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("MegaCorp", "반도체")
            )
            every { naverNewsSearchPort.searchNews("MegaCorp") } returns listOf(
                aNaverItem(title = "MegaCorp 뉴스", link = "https://example.com/1")
            )
            every { naverNewsSearchPort.searchNews("반도체") } returns listOf(
                aNaverItem(title = "반도체 시장", link = "https://example.com/2")
            )
            every { itemStore.findExistingLinks(any(), any()) } returns emptySet()
            val savedSlot = slot<RssItem>()
            every { itemStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            service.collectForCategory("cat-1") shouldBe 2
        }

        @Test
        fun `제목이 유사한 기사는 건너뛴다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("테스트")
            )
            // 거의 동일한 제목의 기사 2개 (토큰 10개 중 9개 공유 → Jaccard 0.82)
            every { naverNewsSearchPort.searchNews("테스트") } returns listOf(
                aNaverItem(title = "MegaCorp 2026년 1분기 영업이익 전년 대비 30퍼센트 증가 실적 발표", link = "https://example.com/1"),
                aNaverItem(title = "MegaCorp 2026년 1분기 영업이익 전년 대비 30퍼센트 증가 실적 속보", link = "https://example.com/2")
            )
            every { itemStore.findExistingLinks(any(), any()) } returns emptySet()
            val savedSlot = slot<RssItem>()
            every { itemStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            // 첫 번째만 저장, 두 번째는 유사 제목으로 건너뜀
            service.collectForCategory("cat-1") shouldBe 1
        }

        @Test
        fun `최근 DB 제목과 유사한 SearchCo 기사는 저장하지 않는다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("반도체")
            )
            every { itemStore.findRecentTitles("cat-1", any(), 500) } returns listOf(
                "MegaCorp 2026년 1분기 영업이익 전년 대비 30퍼센트 증가 실적 발표"
            )
            every { naverNewsSearchPort.searchNews("반도체") } returns listOf(
                aNaverItem(
                    title = "MegaCorp 2026년 1분기 영업이익 전년 대비 30퍼센트 증가 실적 속보",
                    link = "https://example.com/searchco-duplicate"
                )
            )
            every { itemStore.findExistingLinks(any(), any()) } returns emptySet()

            service.collectForCategory("cat-1") shouldBe 0

            verify(exactly = 0) { itemStore.save(any()) }
        }

        @Test
        fun `저장 시점 중복 키 경합은 신규 건수에 포함하지 않고 다음 기사는 계속 저장한다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("AI")
            )
            every { naverNewsSearchPort.searchNews("AI") } returns listOf(
                aNaverItem(title = "AI 중복", link = "https://example.com/race"),
                aNaverItem(title = "AI 신규", link = "https://example.com/new")
            )
            every { itemStore.findExistingLinks(any(), any()) } returns emptySet()
            every { itemStore.save(match { it.link == "https://example.com/race" }) } throws
                DuplicateKeyException("uq_rss_items_link_category")
            every { itemStore.save(match { it.link == "https://example.com/new" }) } answers { firstArg() }

            service.collectForCategory("cat-1") shouldBe 1

            verify(exactly = 2) { itemStore.save(any()) }
        }

        @Test
        fun `한 키워드 검색 실패는 같은 카테고리의 다음 키워드 수집을 막지 않는다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("실패키워드", "정상키워드")
            )
            every { naverNewsSearchPort.searchNews("실패키워드") } throws RuntimeException("naver timeout")
            every { naverNewsSearchPort.searchNews("정상키워드") } returns listOf(
                aNaverItem(title = "정상 수집 기사", link = "https://example.com/ok")
            )
            every { itemStore.findExistingLinks(listOf("https://example.com/ok"), "cat-1") } returns emptySet()
            every { itemStore.save(any()) } answers { firstArg() }

            service.collectForCategory("cat-1") shouldBe 1

            verify(exactly = 1) { itemStore.save(any()) }
        }

        @Test
        fun `공백과 중복 키워드는 SearchCo API 호출 전에 제거한다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf(" AI ", "", "AI", "ai", "  ")
            )
            every { naverNewsSearchPort.searchNews("AI") } returns listOf(
                aNaverItem(title = "AI 정규화 기사", link = "https://example.com/ai")
            )
            every { itemStore.findExistingLinks(listOf("https://example.com/ai"), "cat-1") } returns emptySet()
            every { itemStore.save(any()) } answers { firstArg() }

            service.collectForCategory("cat-1") shouldBe 1

            verify(exactly = 1) { naverNewsSearchPort.searchNews("AI") }
            verify(exactly = 0) { naverNewsSearchPort.searchNews("") }
            verify(exactly = 0) { naverNewsSearchPort.searchNews(" AI ") }
            verify(exactly = 1) { itemStore.save(any()) }
        }

        @Test
        fun `검색 결과가 비어 있으면 중복 링크 조회와 저장을 하지 않는다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findById("cat-1") } returns aCategory()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("결과없음")
            )
            every { naverNewsSearchPort.searchNews("결과없음") } returns emptyList()

            service.collectForCategory("cat-1") shouldBe 0

            verify(exactly = 0) { itemStore.findExistingLinks(any(), any()) }
            verify(exactly = 0) { itemStore.save(any()) }
        }

        @TestFactory
        fun `키워드 정규화 bad case matrix`(): List<DynamicTest> {
            data class KeywordCase(
                val name: String,
                val input: List<String>,
                val expectedQueries: List<String>
            )

            val cases = listOf(
                KeywordCase("blank only", listOf("", " ", "\t", "\n"), emptyList()),
                KeywordCase("trim single keyword", listOf(" AI "), listOf("AI")),
                KeywordCase("dedup case insensitive", listOf("AI", "ai", "Ai", "aI"), listOf("AI")),
                KeywordCase("dedup after trim", listOf(" AI", "AI ", "  ai  "), listOf("AI")),
                KeywordCase("preserve first casing", listOf(" ai ", "AI"), listOf("ai")),
                KeywordCase("korean trim", listOf(" MegaCorp ", "MegaCorp"), listOf("MegaCorp")),
                KeywordCase("korean distinct", listOf("MegaCorp", "반도체"), listOf("MegaCorp", "반도체")),
                KeywordCase("mixed blanks and distinct", listOf("", "AI", " ", "반도체", "\n"), listOf("AI", "반도체")),
                KeywordCase("tab newline trim", listOf("\tAI\n", "\n반도체\t"), listOf("AI", "반도체")),
                KeywordCase("symbols are preserved", listOf("GPT-5", "gpt-5", "GPT 5"), listOf("GPT-5", "GPT 5")),
                KeywordCase("numbers are preserved", listOf("2026", " 2026 ", "2027"), listOf("2026", "2027")),
                KeywordCase("company spacing distinct", listOf("OpenAI", "Open AI"), listOf("OpenAI", "Open AI")),
                KeywordCase("korean spacing distinct", listOf("MegaCorp", "MegaCorp 전자"), listOf("MegaCorp", "MegaCorp 전자")),
                KeywordCase("leading internal space after trim is kept as identity", listOf("AI 뉴스", "AI  뉴스"), listOf("AI 뉴스", "AI  뉴스")),
                KeywordCase("first duplicate wins across many", listOf("Alpha", "beta", "ALPHA", "BETA", "gamma"), listOf("Alpha", "beta", "gamma")),
                KeywordCase("emoji keyword preserved", listOf("AI🚀", "ai🚀"), listOf("AI🚀")),
                KeywordCase("slash keyword preserved", listOf("AI/ML", "ai/ml", "ML"), listOf("AI/ML", "ML")),
                KeywordCase("colon keyword preserved", listOf("시장:반도체", "시장:반도체 "), listOf("시장:반도체")),
                KeywordCase("fullwidth case distinct", listOf("ＡＩ", "AI"), listOf("ＡＩ", "AI")),
                KeywordCase("large duplicate set", List(100) { if (it % 2 == 0) " AI " else "ai" }, listOf("AI"))
            )

            return cases.map { case ->
                DynamicTest.dynamicTest(case.name) {
                    resetService()
                    every { naverNewsSearchPort.isConfigured() } returns true
                    every { categoryStore.findById("cat-1") } returns aCategory()
                    every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                        categoryId = "cat-1",
                        includeKeywords = case.input
                    )
                    for (query in case.expectedQueries) {
                        every { naverNewsSearchPort.searchNews(query) } returns emptyList()
                    }

                    service.collectForCategory("cat-1") shouldBe 0

                    for (query in case.expectedQueries) {
                        verify(exactly = 1) { naverNewsSearchPort.searchNews(query) }
                    }
                    verify(exactly = 0) { itemStore.findExistingLinks(any(), any()) }
                    verify(exactly = 0) { itemStore.save(any()) }
                }
            }
        }
    }

    @Nested
    inner class `collectForAllActiveCategories 검증` {

        @Test
        fun `API 미설정이면 빈 맵 반환`() {
            every { naverNewsSearchPort.isConfigured() } returns false

            service.collectForAllActiveCategories() shouldBe emptyMap()
        }

        @Test
        fun `활성 카테고리만 수집한다`() {
            every { naverNewsSearchPort.isConfigured() } returns true
            every { categoryStore.findOperational() } returns listOf(
                aCategory(id = "cat-1", name = "활성", isActive = true)
            )
            every { categoryStore.findById("cat-1") } returns aCategory(id = "cat-1")
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                includeKeywords = listOf("테스트")
            )
            every { naverNewsSearchPort.searchNews("테스트") } returns listOf(
                aNaverItem(link = "https://example.com/new")
            )
            every { itemStore.findExistingLinks(any(), any()) } returns emptySet()
            val savedSlot = slot<RssItem>()
            every { itemStore.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service.collectForAllActiveCategories()

            result.size shouldBe 1
            result["cat-1"] shouldBe 1
            // 비활성 카테고리는 호출되지 않음
            verify(exactly = 1) { categoryStore.findOperational() }
            verify(exactly = 0) { categoryStore.findById("cat-2") }
        }
    }
}
