package com.clipping.mcpserver.service

import com.clipping.mcpserver.dart.DartCompany
import com.clipping.mcpserver.service.dto.CompanySearchResult
import com.clipping.mcpserver.dart.DartCorpCodeClient
import com.clipping.mcpserver.store.CompetitorWatchlistStore
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * DART 기업 코드를 메모리에 캐싱하고 기업명 검색을 제공하는 서비스.
 * 애플리케이션 시작 시 DART API 데이터를 로드하고, API 키가 없으면
 * 번들된 시드 데이터(주요 상장사)로 폴백한다.
 */
@Service
class CompanySearchService(
    private val dartCorpCodeClient: DartCorpCodeClient,
    private val competitorWatchlistStore: CompetitorWatchlistStore
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 기업 코드 → 기업 정보 캐시 */
    private val cache = ConcurrentHashMap<String, DartCompany>()

    /** 검색용 정렬된 리스트 (상장사 우선) */
    @Volatile
    private var sortedCompanies: List<DartCompany> = emptyList()

    /**
     * 애플리케이션 시작 시 DART 데이터를 로드한다.
     * API 키가 없으면 시드 데이터로 폴백한다.
     */
    @PostConstruct
    fun init() {
        refreshCache()
    }

    /**
     * 주기적으로 DART 데이터를 갱신한다.
     * cron 표현식은 DartProperties.refreshCron으로 설정한다.
     */
    @Scheduled(cron = "\${clipping-mcp-server.dart.refresh-cron:0 0 3 * * MON}")
    fun refreshCache() {
        try {
            val companies = dartCorpCodeClient.fetchAllCompanies()
            if (companies.isEmpty()) {
                // DART API 실패 시 시드 데이터로 폴백
                loadSeedDataIfEmpty()
                return
            }

            applyCache(companies)
            log.info("DART 기업 캐시 갱신 완료: 총 {}건 (상장사 {}건)",
                sortedCompanies.size,
                sortedCompanies.count { it.isListed })
        } catch (e: Exception) {
            log.error("DART 기업 캐시 갱신 실패: {}", e.message, e)
            // 캐시가 비어있으면 시드 데이터라도 로드
            loadSeedDataIfEmpty()
        }
    }

    /**
     * 캐시가 비어있을 때 classpath의 시드 CSV에서 주요 상장사를 로드한다.
     */
    private fun loadSeedDataIfEmpty() {
        if (cache.isNotEmpty()) return
        try {
            val resource = ClassPathResource("data/seed-companies.csv")
            if (!resource.exists()) {
                log.warn("시드 기업 데이터 파일이 없습니다.")
                return
            }
            val companies = mutableListOf<DartCompany>()
            BufferedReader(InputStreamReader(resource.inputStream, Charsets.UTF_8)).use { reader ->
                // 헤더 스킵
                reader.readLine()
                reader.forEachLine { line ->
                    val parts = line.split(",", limit = 3)
                    if (parts.size == 3) {
                        companies.add(DartCompany(parts[0].trim(), parts[1].trim(), parts[2].trim()))
                    }
                }
            }
            applyCache(companies)
            log.info("시드 데이터에서 기업 캐시 로드 완료: {}건", companies.size)
        } catch (e: Exception) {
            log.error("시드 기업 데이터 로드 실패: {}", e.message, e)
        }
    }

    /** 캐시와 정렬 리스트를 갱신한다. */
    private fun applyCache(companies: List<DartCompany>) {
        cache.clear()
        companies.forEach { cache[it.corpCode] = it }
        sortedCompanies = companies.sortedWith(
            compareByDescending<DartCompany> { it.isListed }
                .thenBy { it.corpName }
        )
    }

    /**
     * 기업명에 쿼리 문자열이 포함된 기업을 검색한다.
     * 상장사가 우선 정렬되며, 최대 limit개까지 반환한다.
     *
     * @param query 검색어 (빈 문자열이면 빈 리스트 반환)
     * @param limit 최대 결과 수 (1~50 범위로 보정)
     * @return 매칭된 기업 리스트
     */
    fun search(query: String, limit: Int = 10): List<DartCompany> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        // 내부 호출자가 범위 밖 값을 넘겨도 take()가 실패하지 않도록 보정한다.
        val safeLimit = limit.coerceIn(1, 50)

        return sortedCompanies
            .filter { it.corpName.contains(trimmed, ignoreCase = true) }
            .take(safeLimit)
    }

    /**
     * 기업명에 쿼리 문자열이 포함된 기업을 검색하고, 경쟁사 여부를 함께 반환한다.
     * 경쟁사 워치리스트를 1회만 로드하여 O(N+M) 성능을 보장한다.
     *
     * @param query 검색어 (빈 문자열이면 빈 리스트 반환)
     * @param limit 최대 결과 수 (1~50 범위로 보정)
     * @return 경쟁사 여부(isCompetitor)가 포함된 검색 결과 리스트
     */
    fun searchWithIsCompetitor(query: String, limit: Int = 10): List<CompanySearchResult> {
        val raw = search(query, limit)
        if (raw.isEmpty()) return emptyList()

        // 경쟁사 이름 집합을 1회 로드 — N개 결과 × M개 경쟁사 대신 N+M 비교
        val competitorNames = competitorWatchlistStore.findAll()
            .flatMap { listOf(it.name.lowercase()) + it.aliases.map { a -> a.lowercase() } }
            .toSet()

        return raw.map { company ->
            CompanySearchResult(
                corpCode = company.corpCode,
                corpName = company.corpName,
                stockCode = company.stockCode,
                isCompetitor = company.corpName.lowercase() in competitorNames
            )
        }
    }

    /** 현재 캐시된 기업 수 */
    fun cacheSize(): Int = cache.size
}
