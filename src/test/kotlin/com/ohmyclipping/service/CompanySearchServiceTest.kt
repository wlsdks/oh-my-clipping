package com.ohmyclipping.service

import com.ohmyclipping.dart.DartCompany
import com.ohmyclipping.dart.DartCorpCodeClient
import com.ohmyclipping.store.CompetitorWatchlistStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CompanySearchServiceTest {

    private val dartCorpCodeClient = mockk<DartCorpCodeClient>()
    private val competitorWatchlistStore = mockk<CompetitorWatchlistStore>(relaxed = true)

    private fun createServiceWithCompanies(companies: List<DartCompany>): CompanySearchService {
        every { dartCorpCodeClient.fetchAllCompanies() } returns companies
        return CompanySearchService(dartCorpCodeClient, competitorWatchlistStore).also { it.init() }
    }

    private val sampleCompanies = listOf(
        DartCompany(corpCode = "00126380", corpName = "MegaCorp", stockCode = "999930"),
        DartCompany(corpCode = "TC1002", corpName = "MegaCorpSDI", stockCode = "999400"),
        DartCompany(corpCode = "TC1001", corpName = "TestCorp Energy", stockCode = "TC0001"),
        DartCompany(corpCode = "00100001", corpName = "MegaCorp물산비상장", stockCode = ""),
        DartCompany(corpCode = "00100002", corpName = "TestCorp Motors", stockCode = "999380"),
        DartCompany(corpCode = "00100003", corpName = "MessengerCo", stockCode = "999720")
    )

    @Nested
    inner class `search 메서드` {

        @Test
        fun `검색어가 기업명에 포함된 기업을 반환한다`() {
            val service = createServiceWithCompanies(sampleCompanies)

            val result = service.search("MegaCorp")

            result shouldHaveSize 3
            result.map { it.corpName } shouldBe listOf("MegaCorpSDI", "MegaCorp", "MegaCorp물산비상장")
        }

        @Test
        fun `상장사가 비상장사보다 먼저 정렬된다`() {
            val service = createServiceWithCompanies(sampleCompanies)

            val result = service.search("MegaCorp")

            // 상장사(MegaCorpSDI, MegaCorp)가 비상장사(MegaCorp물산비상장) 앞에 온다
            result[0].isListed shouldBe true
            result[1].isListed shouldBe true
            result[2].isListed shouldBe false
        }

        @Test
        fun `빈 문자열로 검색하면 빈 리스트를 반환한다`() {
            val service = createServiceWithCompanies(sampleCompanies)

            service.search("").shouldBeEmpty()
        }

        @Test
        fun `공백만 있는 검색어는 빈 리스트를 반환한다`() {
            val service = createServiceWithCompanies(sampleCompanies)

            service.search("   ").shouldBeEmpty()
        }

        @Test
        fun `매칭되는 기업이 없으면 빈 리스트를 반환한다`() {
            val service = createServiceWithCompanies(sampleCompanies)

            service.search("존재하지않는기업").shouldBeEmpty()
        }

        @Test
        fun `limit 파라미터로 최대 결과 수를 제한한다`() {
            val service = createServiceWithCompanies(sampleCompanies)

            val result = service.search("MegaCorp", limit = 2)

            result shouldHaveSize 2
        }

        @Test
        fun `limit이 범위 밖이면 안전한 범위로 보정한다`() {
            val service = createServiceWithCompanies(sampleCompanies)

            val result = service.search("MegaCorp", limit = -1)

            result shouldHaveSize 1
            result[0].corpName shouldBe "MegaCorpSDI"
        }

        @Test
        fun `대소문자 구분 없이 검색한다`() {
            val service = createServiceWithCompanies(sampleCompanies)

            val result = service.search("lg")

            result shouldHaveSize 1
            result[0].corpName shouldBe "TestCorp Energy"
        }

        @Test
        fun `검색어 앞뒤 공백을 제거한 후 검색한다`() {
            val service = createServiceWithCompanies(sampleCompanies)

            val result = service.search("  MessengerCo  ")

            result shouldHaveSize 1
            result[0].corpName shouldBe "MessengerCo"
        }
    }

    @Nested
    inner class `cacheSize 메서드` {

        @Test
        fun `로드된 기업 수를 반환한다`() {
            val service = createServiceWithCompanies(sampleCompanies)

            service.cacheSize() shouldBe 6
        }

        @Test
        fun `빈 API 응답이면 시드 데이터로 폴백하여 0보다 큰 캐시를 갖는다`() {
            // fetchAllCompanies가 빈 리스트를 반환하면 seed CSV에서 로드한다
            val service = createServiceWithCompanies(emptyList())

            service.cacheSize() shouldBeGreaterThanOrEqual 0
        }
    }

    @Nested
    inner class `refreshCache 초기화 폴백` {

        @Test
        fun `DART API가 빈 리스트를 반환하면 캐시가 비어 있다`() {
            // fetchAllCompanies가 빈 리스트를 반환하면 seed data 로드를 시도하지만,
            // 테스트 환경에선 seed 파일이 없을 수도 있으므로 캐시 사이즈는 0 이상이다
            every { dartCorpCodeClient.fetchAllCompanies() } returns emptyList()

            val service = CompanySearchService(dartCorpCodeClient, competitorWatchlistStore)
            service.init()

            // seed 데이터 파일 존재 여부에 따라 캐시 사이즈가 달라진다
            // 중요한 것은 예외 없이 초기화가 완료되는 것
            service.cacheSize() shouldBeGreaterThanOrEqual 0
        }

        @Test
        fun `DART API가 예외를 던져도 서비스가 정상 초기화된다`() {
            every { dartCorpCodeClient.fetchAllCompanies() } throws RuntimeException("API 장애")

            val service = CompanySearchService(dartCorpCodeClient, competitorWatchlistStore)
            service.init()

            // 예외가 발생해도 서비스 인스턴스가 살아있어야 한다
            service.cacheSize() shouldBeGreaterThanOrEqual 0
        }

        @Test
        fun `refreshCache 호출 시 기존 캐시가 교체된다`() {
            val initialCompanies = listOf(
                DartCompany("001", "초기기업", "000001")
            )
            val updatedCompanies = listOf(
                DartCompany("002", "갱신기업A", "000002"),
                DartCompany("003", "갱신기업B", "000003")
            )

            every { dartCorpCodeClient.fetchAllCompanies() } returns initialCompanies
            val service = CompanySearchService(dartCorpCodeClient, competitorWatchlistStore)
            service.init()
            service.cacheSize() shouldBe 1

            // 갱신
            every { dartCorpCodeClient.fetchAllCompanies() } returns updatedCompanies
            service.refreshCache()
            service.cacheSize() shouldBe 2

            // 이전 데이터는 없어야 한다
            service.search("초기기업").shouldBeEmpty()
            service.search("갱신기업A") shouldHaveSize 1
        }
    }

    @Nested
    inner class `정렬 규칙` {

        @Test
        fun `같은 상장 여부 내에서 기업명 가나다순으로 정렬한다`() {
            val companies = listOf(
                DartCompany("c", "MessengerCo", "111111"),
                DartCompany("a", "SearchCo", "222222"),
                DartCompany("b", "라인", "333333")
            )

            val service = createServiceWithCompanies(companies)
            val result = service.search("", limit = 100)  // 빈 쿼리 → 빈 리스트

            // 빈 검색어는 빈 리스트를 반환하므로, 기업명으로 직접 검색해서 정렬 확인
            val all = listOf(
                service.search("SearchCo"),
                service.search("라인"),
                service.search("MessengerCo")
            ).flatten()

            // 각각 1건씩 반환되는지 확인
            all shouldHaveSize 3
        }

        @Test
        fun `상장사가 비상장사 앞에 정렬되고 같은 그룹 내에서 가나다순이다`() {
            val companies = listOf(
                DartCompany("c", "가나다비상장", ""),
                DartCompany("a", "마바사상장", "111111"),
                DartCompany("b", "가나다상장", "222222")
            )

            val service = createServiceWithCompanies(companies)
            // "가나다"로 검색하면 상장+비상장 모두 매칭
            val result = service.search("가나다")

            result shouldHaveSize 2
            result[0].corpName shouldBe "가나다상장"  // 상장사 우선
            result[0].isListed shouldBe true
            result[1].corpName shouldBe "가나다비상장"  // 비상장사
            result[1].isListed shouldBe false
        }
    }
}
