package com.clipping.mcpserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 승인/검토 SLA 에스컬레이션 스케줄러 설정.
 *
 * 파일럿 오픈 이후 운영자가 PENDING 상태 항목을 놓치지 않도록,
 * 임계 일수를 초과해도 처리되지 않은 신청/요청을 운영 채널에 에스컬레이션한다.
 * 정책 스케줄러(User/Clipping/Source)가 본 설정을 공유한다.
 *
 * @property enabled 전체 SLA 에스컬레이션 on/off 스위치. false 이면 세 스케줄러 모두 아무 일도 하지 않는다.
 * @property userApprovalStaleDays 가입 승인 PENDING 상태가 몇 일을 넘으면 알림 대상이 되는지.
 * @property clippingRequestStaleDays 사용자 구독 신청 PENDING 상태가 몇 일을 넘으면 알림 대상이 되는지.
 * @property sourceRequestStaleDays RSS 소스 검증 PENDING 상태가 몇 일을 넘으면 알림 대상이 되는지.
 */
@ConfigurationProperties(prefix = "clipping.sla-escalation")
data class SlaEscalationProperties(
    val enabled: Boolean = true,
    val userApprovalStaleDays: Int = 3,
    val clippingRequestStaleDays: Int = 7,
    val sourceRequestStaleDays: Int = 5,
)
