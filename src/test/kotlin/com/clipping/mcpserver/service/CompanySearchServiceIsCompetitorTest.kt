package com.clipping.mcpserver.service

import com.clipping.mcpserver.dart.DartCompany
import com.clipping.mcpserver.service.dto.CompanySearchResult
import com.clipping.mcpserver.dart.DartCorpCodeClient
import com.clipping.mcpserver.model.CompetitorWatchlist
import com.clipping.mcpserver.store.CompetitorWatchlistStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class CompanySearchServiceIsCompetitorTest {

    private val dartCorpCodeClient = mockk<DartCorpCodeClient>()
    private val competitorWatchlistStore = mockk<CompetitorWatchlistStore>(relaxed = true)

    private val sampleCompanies = listOf(
        DartCompany(corpCode = "00126380", corpName = "MegaCorp", stockCode = "999930"),
        DartCompany(corpCode = "TC1002", corpName = "Coursera", stockCode = ""),
        DartCompany(corpCode = "00100001", corpName = "TestCorp Motors", stockCode = "999380")
    )

    private fun createService(): CompanySearchService {
        every { dartCorpCodeClient.fetchAllCompanies() } returns sampleCompanies
        return CompanySearchService(dartCorpCodeClient, competitorWatchlistStore).also { it.init() }
    }

    private fun stubCompetitor(name: String) = CompetitorWatchlist(
        id = "c1",
        name = name,
        aliases = emptyList(),
        excludeKeywords = emptyList(),
        tier = "tier1",
        isActive = true,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Nested
    inner class `searchWithIsCompetitor isCompetitor 플래그` {

        @Test
        fun `경쟁사로 등록된 기업은 isCompetitor=true`() {
            // Coursera가 경쟁사로 등록된 경우
            every { competitorWatchlistStore.findAll() } returns listOf(stubCompetitor("Coursera"))

            val service = createService()
            val results = service.searchWithIsCompetitor("MegaCorp", limit = 10)

            // MegaCorp는 경쟁사가 아님
            results.first { it.corpName == "MegaCorp" }.isCompetitor shouldBe false
        }

        @Test
        fun `경쟁사 없으면 모두 isCompetitor=false`() {
            every { competitorWatchlistStore.findAll() } returns emptyList()

            val service = createService()
            val results = service.searchWithIsCompetitor("MegaCorp", limit = 10)

            results.all { !it.isCompetitor } shouldBe true
        }

        @Test
        fun `경쟁사 이름과 정확히 일치하는 기업은 isCompetitor=true`() {
            every { competitorWatchlistStore.findAll() } returns listOf(stubCompetitor("Coursera"))

            val service = createService()
            val results = service.searchWithIsCompetitor("Coursera", limit = 10)

            results.first { it.corpName == "Coursera" }.isCompetitor shouldBe true
        }

        @Test
        fun `대소문자 무시하고 경쟁사 매칭`() {
            every { competitorWatchlistStore.findAll() } returns listOf(stubCompetitor("coursera"))

            val service = createService()
            val results = service.searchWithIsCompetitor("Coursera", limit = 10)

            results.first { it.corpName == "Coursera" }.isCompetitor shouldBe true
        }

        @Test
        fun `빈 검색어는 빈 리스트 반환`() {
            every { competitorWatchlistStore.findAll() } returns emptyList()

            val service = createService()
            val results = service.searchWithIsCompetitor("", limit = 10)

            results shouldBe emptyList()
        }

        @Test
        fun `경쟁사 리스트를 1회만 로드하여 성능 최적화`() {
            // findAll()은 딱 한 번만 호출되어야 한다 (결과가 여러 건이어도)
            every { competitorWatchlistStore.findAll() } returns listOf(stubCompetitor("MegaCorp"))

            val service = createService()
            // 3개 기업을 포함하는 검색
            val results = service.searchWithIsCompetitor("MegaCorp", limit = 10)

            // findAll은 정확히 1회 호출 (N*M 대신 N+M)
            io.mockk.verify(exactly = 1) { competitorWatchlistStore.findAll() }
            results.first { it.corpName == "MegaCorp" }.isCompetitor shouldBe true
        }
    }

    @Nested
    inner class `기존 search 메서드 하위 호환성` {

        @Test
        fun `기존 search 메서드는 DartCompany 리스트를 그대로 반환한다`() {
            val service = createService()
            val results = service.search("MegaCorp")

            // 기존 반환 타입 유지
            results.first().corpName shouldBe "MegaCorp"
        }
    }
}
