package com.clipping.mcpserver.service.analytics

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 위험/성장 판정 임계치. 운영 중 튜닝이 잦을 것으로 예상되어 상수 대신
 * `application.yml` 로 노출한다.
 *
 * 스펙 §4.4 및 §2/§2.5 참고.
 */
@ConfigurationProperties("analytics.risk")
data class AnalyticsRiskProperties(
    /** 이탈 초과 — 최소 이탈 건수. */
    val churnMinCount: Int = 2,
    /** 이탈 초과 — 전주 active_subs 최소치 (구독자 3명 페르소나가 매주 울리는 것 방지). */
    val churnBaselineMin: Int = 8,
    /** 참여 하락 — 퍼센트포인트 기준 (0.10 = 10pp). */
    val engagementDropPp: Int = 10,
    /** 참여 하락/상승 공통 — 전·이번 주 모두 delivered_count 최소치. */
    val engagementMinDeliveries: Int = 30,
    /** 유휴 — 연속 0 발송 주 수. */
    val idleWeeks: Int = 4
)

@ConfigurationProperties("analytics.growth")
data class AnalyticsGrowthProperties(
    /** 구독 급증 — 전주 대비 증가율 (%). */
    val subsSurgePct: Int = 20,
    /** 구독 급증 — 증가 절댓값 최소치. */
    val subsSurgeMin: Int = 3,
    /** 참여 상승 — 퍼센트포인트 기준 (0.10 = 10pp). */
    val engagementRisePp: Int = 10
)
