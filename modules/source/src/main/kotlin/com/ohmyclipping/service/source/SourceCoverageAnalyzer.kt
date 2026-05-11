package com.ohmyclipping.service.source

import com.ohmyclipping.service.dto.CoverageGapDto
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.RssSourceStore
import org.springframework.stereotype.Service

/**
 * 카테고리별 소스 커버리지 갭을 분석한다.
 * 소스 수 부족, 지역 편중 등을 감지하여 관리자에게 알린다.
 */
@Service
class SourceCoverageAnalyzer(
    private val rssSourceStore: RssSourceStore,
    private val categoryStore: CategoryStore
) {
    companion object {
        /** 카테고리당 최소 소스 수 — 이보다 적으면 HIGH 경고 */
        private const val MIN_SOURCES_PER_CATEGORY = 2

        /** 지역 편중 분석을 적용하는 최소 소스 수 */
        private const val REGION_ANALYSIS_THRESHOLD = 3
    }

    /**
     * 전체 카테고리를 순회하며 커버리지 갭을 분석한다.
     *
     * @return 발견된 갭 목록 (severity 내림차순 정렬)
     */
    fun analyze(): List<CoverageGapDto> {
        val categories = categoryStore.list()
        val gaps = mutableListOf<CoverageGapDto>()

        for (category in categories) {
            // 카테고리에 속한 활성(승인) 소스만 대상으로 한다
            val sources = rssSourceStore.listByCategoryId(category.id)
                .filter { it.crawlApproved && it.isActive }

            // LOW_SOURCE_COUNT: 활성 소스가 최소 기준 미만
            if (sources.size < MIN_SOURCES_PER_CATEGORY) {
                gaps.add(
                    CoverageGapDto(
                        categoryId = category.id,
                        categoryName = category.name,
                        type = "LOW_SOURCE_COUNT",
                        detail = "활성 소스가 ${sources.size}개뿐이에요. 최소 ${MIN_SOURCES_PER_CATEGORY}개 이상이 권장돼요.",
                        severity = "HIGH"
                    )
                )
            }

            // REGION_IMBALANCE: 3개 이상 소스가 모두 국내 또는 모두 해외
            if (sources.size >= REGION_ANALYSIS_THRESHOLD) {
                val domesticCount = sources.count {
                    it.sourceRegion.name == "DOMESTIC"
                }
                val globalCount = sources.size - domesticCount

                if (domesticCount == sources.size) {
                    gaps.add(
                        CoverageGapDto(
                            categoryId = category.id,
                            categoryName = category.name,
                            type = "REGION_IMBALANCE",
                            detail = "모든 소스가 국내 소스예요. 해외 소스를 추가하면 시야가 넓어져요.",
                            severity = "MEDIUM"
                        )
                    )
                } else if (globalCount == sources.size) {
                    gaps.add(
                        CoverageGapDto(
                            categoryId = category.id,
                            categoryName = category.name,
                            type = "REGION_IMBALANCE",
                            detail = "모든 소스가 해외 소스예요. 국내 소스를 추가하면 균형이 맞아요.",
                            severity = "MEDIUM"
                        )
                    )
                }
            }
        }

        // HIGH → MEDIUM → LOW 순 정렬
        return gaps.sortedBy { severityOrder(it.severity) }
    }

    private fun severityOrder(severity: String): Int = when (severity) {
        "HIGH" -> 0
        "MEDIUM" -> 1
        else -> 2
    }
}
