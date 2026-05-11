package com.clipping.mcpserver.service.analytics.dto

import java.time.Instant

/**
 * 프리셋 포트폴리오 테이블의 한 행.
 *
 * Slice 1 단계에서는 weekOverWeekDelta / engagementRate / lastDeliveredAt 가
 * 부분적으로 null 일 수 있다 (시계열 인프라가 Slice 2 이후 활성화되기 때문).
 * 프론트는 null 을 "—" 로 렌더링한다.
 */
data class PresetPortfolioItem(
    val personaId: String,
    val personaName: String,
    val activeSubs: Int,
    /** 전주 대비 변화량. Slice 2 부터 채워진다. */
    val weekOverWeekDelta: Int?,
    /** 0.0 ~ 1.0 범위의 참여율. Slice 2 부터 채워진다. */
    val engagementRate: Double?,
    val status: PortfolioStatus,
    val lastDeliveredAt: Instant?
)
