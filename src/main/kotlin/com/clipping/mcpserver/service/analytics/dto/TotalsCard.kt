package com.clipping.mcpserver.service.analytics.dto

/**
 * Analytics 페이지 상단 4 개 카드용 집계.
 *
 * customStyleRatio 는 customConversionRate 로부터 개명 — 전체 스타일 중 유저
 * 커스텀이 차지하는 비율을 의미하도록 라벨을 정정했다.
 *
 * weekOverWeekDelta 는 Slice 2 시계열 도입 전까지 null 이며, Slice 1 화면은
 * null 일 때 "—" 로 표시한다.
 */
data class TotalsCard(
    val totalStyles: Int,
    val presetCount: Int,
    val customCount: Int,
    val activeSubscriptions: Int,
    /** 활성 구독 유저 중 프리셋을 쓰는 유저 비율 (0.0 ~ 1.0). */
    val presetUsageRate: Double,
    /** 전체 스타일 중 유저 커스텀이 차지하는 비율 (0.0 ~ 1.0). */
    val customStyleRatio: Double,
    /** 전주 대비 활성 구독 변화량. Slice 2 부터 채워진다. */
    val weekOverWeekDelta: Int?
)
