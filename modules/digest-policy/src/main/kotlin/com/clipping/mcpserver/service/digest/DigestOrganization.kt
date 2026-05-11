package com.clipping.mcpserver.service.digest

/**
 * Digest engine 이 조직 매칭/라벨 계산에 필요한 최소 조직 표현.
 * 앱 도메인 모델과 분리해 엔진 모듈을 독립 컴파일 가능하게 유지한다.
 */
data class DigestOrganization(
    val name: String,
    val aliases: List<String> = emptyList(),
)
