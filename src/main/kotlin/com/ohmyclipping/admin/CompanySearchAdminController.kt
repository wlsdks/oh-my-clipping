package com.ohmyclipping.admin

import com.ohmyclipping.service.CompanySearchService
import com.ohmyclipping.service.dto.CompanySearchResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자용 DART 기업명 자동완성 API.
 * 관리자 콘솔에서 기업명 검색에 사용한다.
 */
@RestController
@RequestMapping("/api/admin/companies")
class CompanySearchAdminController(
    private val companySearchService: CompanySearchService
) {
    /**
     * 기업명 검색.
     * 쿼리 문자열이 포함된 기업을 상장사 우선으로 반환한다.
     *
     * @param q 검색어
     * @param limit 최대 결과 수 (기본 10)
     */
    @GetMapping
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): List<CompanySearchResult> {
        return companySearchService.searchWithIsCompetitor(q, limit.coerceIn(1, 50))
    }
}
