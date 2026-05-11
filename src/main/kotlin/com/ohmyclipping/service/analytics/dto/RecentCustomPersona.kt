package com.ohmyclipping.service.analytics.dto

import java.time.Instant

/**
 * 최근 생성된 커스텀 페르소나 (어드민 모니터링용 요약).
 * `systemPromptPreview` 는 PII 노출 방지를 위해 호출자에서 절단한 텍스트만 담는다.
 */
data class RecentCustomPersona(
    val id: String,
    val personaName: String,
    val userName: String,
    val systemPromptPreview: String,
    val createdAt: Instant
)
