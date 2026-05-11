package com.clipping.mcpserver.service.analytics.dto

/** 주간 트렌드 API 응답. weeks 배열과 동일 인덱스로 매핑되는 시리즈 리스트. */
data class WeeklyTrendsResponse(
    val weeks: List<String>,
    val series: List<PersonaTrendSeries>
)

/** 페르소나별 주간 트렌드 시리즈. 각 리스트의 인덱스는 weeks 배열과 대응. */
data class PersonaTrendSeries(
    val personaId: String,
    val personaName: String,
    val isPreset: Boolean,
    val activeSubs: List<Int>,
    val engagedUsers: List<Int>,
    val deliveredCount: List<Int>
)
