package com.clipping.mcpserver.model

import java.time.Instant

/**
 * 페르소나 버전 요약 -- 버전 히스토리 목록용.
 * store 계층과 service 계층 모두 참조할 수 있도록 model 패키지에 위치한다.
 */
data class PersonaVersionSummary(
    val version: Int,
    val changeSummary: String?,
    val createdAt: Instant
)

/**
 * 페르소나 버전 상세 -- 특정 버전의 전체 스냅샷.
 * store 계층과 service 계층 모두 참조할 수 있도록 model 패키지에 위치한다.
 */
data class PersonaVersionDetail(
    val version: Int,
    val name: String,
    val description: String?,
    val systemPrompt: String,
    val summaryStyle: String?,
    val targetAudience: String?,
    val maxItems: Int,
    val language: String,
    val previewTitle: String?,
    val previewSource: String?,
    val previewBody: String?,
    val changeSummary: String?,
    val createdAt: Instant
)
