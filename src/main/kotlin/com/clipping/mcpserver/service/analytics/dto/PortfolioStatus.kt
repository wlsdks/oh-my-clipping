package com.clipping.mcpserver.service.analytics.dto

/**
 * 프리셋 포트폴리오 상태 분류.
 *
 * - HEALTHY    : 활성 구독이 충분하고 참여율 양호
 * - WATCHING   : 관찰 필요 (감소 추세 또는 낮은 참여율)
 * - DECLINING  : 전주 대비 급락 (Slice 2 이후 시계열 비교로 채워짐)
 * - UNUSED     : 4 주 이상 저조 (Slice 2 이후 채워짐)
 *
 * Slice 1 단계에서는 단순 카운트 기준으로만 분류한다 (5+ HEALTHY,
 * 0 UNUSED, 그 외 WATCHING). 시계열 기반 DECLINING 은 Slice 2 에서 활성화된다.
 */
enum class PortfolioStatus {
    HEALTHY,
    WATCHING,
    DECLINING,
    UNUSED
}
