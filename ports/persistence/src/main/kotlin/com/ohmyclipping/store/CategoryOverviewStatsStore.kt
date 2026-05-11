package com.ohmyclipping.store

import java.time.Instant
import java.time.LocalDate

interface CategoryOverviewStatsStore {

    /**
     * 카테고리 개요 통계를 단일 쿼리로 조회한다.
     * 소스 수, 구독자 수, 최근 7일 기사 수, 평균 중요도, 마지막 갱신 일시를 반환한다.
     */
    fun fetchCategoryOverviewStats(
        categoryId: String,
        since7Days: LocalDate,
    ): CategoryOverviewRow?

    /**
     * 카테고리 개요 쿼리의 원시 결과 행.
     * 서비스 레이어에서 DTO로 변환한다.
     */
    data class CategoryOverviewRow(
        val sourceCount: Int,
        val subscriberCount: Int,
        val recentItemCount7Days: Int,
        val avgImportance7Days: Double,
        val lastUpdatedAt: Instant?,
    )
}

