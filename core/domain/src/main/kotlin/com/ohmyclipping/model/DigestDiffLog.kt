package com.ohmyclipping.model

import java.time.Instant
import java.time.LocalDate

/**
 * `digest_diff_log` 테이블의 단일 row 도메인 표현.
 *
 * shadow mode 기간 동안 legacy digest 와 account-based digest 의 결과를 나란히 기록한 로그.
 * store 계층과 service/admin 계층이 공유하는 중립 모델.
 */
data class DigestDiffLog(
    val id: String,
    val categoryId: String,
    val digestDate: LocalDate,
    val legacySummary: String?,
    val newSummary: String?,
    val newMode: String?,
    val sectionsCount: Int,
    val articlesCount: Int,
    val crossMatchCount: Int,
    val createdAt: Instant,
)
