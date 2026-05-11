package com.clipping.mcpserver.service.dto

/** 기업 검색 결과 DTO. */
data class CompanySearchResult(
    val corpCode: String,
    val corpName: String,
    val stockCode: String,
    /** 경쟁사 워치리스트에 등록된 기업 여부 — 위자드에서 disabled 처리에 사용. */
    val isCompetitor: Boolean = false
)
