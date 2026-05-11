package com.ohmyclipping.mcp.dto

import com.ohmyclipping.service.dto.clipping.SummaryInfo

/**
 * MCP 응답용 요약 뷰 모델.
 * 내부 메타데이터 태그를 제거하고 클라이언트에 안전하게 노출한다.
 */
data class SummaryView(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val title: String,
    val summary: String,
    val sourceLink: String,
    val sourceName: String,
    val publishedAt: String?,
    val importanceScore: Double?,
    val createdAt: String,
) {
    companion object {
        private val INTERNAL_TAG =
            Regex("\\[(?:baseRequestId|설정 변경|위자드|시스템)[^]]*]")

        /** SummaryInfo 단독 변환 — 카테고리명/소스명이 없는 레거시 호출용. */
        fun from(info: SummaryInfo) = from(
            info = info,
            categoryName = "",
            sourceName = "",
            publishedAt = null,
        )

        /**
         * SummaryInfo + 외부 조회 결과를 결합하여 뷰를 생성한다.
         *
         * @param info 요약 정보
         * @param categoryName 카테고리 표시명
         * @param sourceName RSS 소스 표시명
         * @param publishedAt ISO-8601 발행일시 (null 허용)
         */
        fun from(
            info: SummaryInfo,
            categoryName: String,
            sourceName: String,
            publishedAt: String?,
        ) = SummaryView(
            id = info.id,
            categoryId = info.categoryId,
            categoryName = categoryName,
            title = info.translatedTitle ?: info.originalTitle,
            summary = info.summary.replace(INTERNAL_TAG, "").trim(),
            sourceLink = info.sourceLink,
            sourceName = sourceName,
            publishedAt = publishedAt,
            importanceScore = info.importanceScore.toDouble(),
            createdAt = info.createdAt,
        )
    }
}
