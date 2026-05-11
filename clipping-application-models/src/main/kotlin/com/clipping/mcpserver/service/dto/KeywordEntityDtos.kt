package com.clipping.mcpserver.service.dto

/**
 * 키워드 엔티티 분류 항목.
 * 키워드, 분류 카테고리, 등장 횟수를 담는다.
 */
data class KeywordEntityItem(
    val keyword: String,
    val category: String,
    val count: Int,
)

/**
 * 키워드 엔티티 분류 응답.
 * 분류된 키워드 항목 목록을 담는다.
 */
data class KeywordEntityResponse(
    val items: List<KeywordEntityItem>,
)
