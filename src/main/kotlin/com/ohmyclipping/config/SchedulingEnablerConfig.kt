package com.ohmyclipping.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 스케줄링 인프라 활성화.
 * clipping.scheduler.enabled=false 시 모든 @Scheduled 메서드가 비활성화된다.
 * matchIfMissing=true 로 기존 동작(항상 켜짐)을 보존한다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = ["clipping.scheduler.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class SchedulingEnablerConfig
