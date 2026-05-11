package com.ohmyclipping.admin.dto

/**
 * 키워드 수동 분류 요청 DTO.
 */
data class ClassifyRequest(
    val keyword: String,
    val category: String,
)
