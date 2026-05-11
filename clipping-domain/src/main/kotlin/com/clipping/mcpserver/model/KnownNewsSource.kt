package com.clipping.mcpserver.model

import java.time.Instant

/**
 * 주요 뉴스사이트 매핑 정보.
 * 소스 추가 시 사이트명/도메인으로 자동 탐색에 활용한다.
 */
data class KnownNewsSource(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val domain: String,
    val rssUrl: String,
    val region: String,
    val createdAt: Instant
)
