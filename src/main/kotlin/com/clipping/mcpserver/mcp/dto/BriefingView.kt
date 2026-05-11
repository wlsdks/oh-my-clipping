package com.clipping.mcpserver.mcp.dto

/**
 * 사용자 아침 브리핑 응답 DTO.
 *
 * 구독 카테고리별로 최근 요약을 묶어서 전달한다. 슬랙 한 줄 질문("내 구독 오늘 것만") 한 번에
 * arc-reactor 가 N회 반복 호출 없이 섹션화된 결과를 받을 수 있게 한다.
 */
data class BriefingSection(
    val categoryId: String,
    val categoryName: String,
    val summaries: List<SummaryView>,
)

data class BriefingView(
    val sinceDays: Int,
    val perCategoryLimit: Int,
    val sections: List<BriefingSection>,
    /** 구독이 없을 때 orchestrator 가 사용자에게 안내할 메시지. 없으면 null. */
    val emptyNote: String? = null,
)
