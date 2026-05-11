package com.ohmyclipping.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * 스케줄러 스레드 풀 설정.
 *
 * 두 개의 독립된 스레드 풀로 Slack 다이제스트 발송이 다른 스케줄 작업을 차단하지 않도록 분리한다.
 * - **taskScheduler** (30스레드, "sched-"): @Scheduled 기본 풀.
 *   AsyncClipJobWorker, StuckJobRecovery, SourceHealth, EmptyCategory,
 *   SlackTokenValidation, CostAlert, DataCleanup, AutoReport, LoginRateLimit 등이 사용한다.
 * - **digestTaskScheduler** (5스레드, "digest-"): SlackDigestWorker 전용.
 *   Slack API 호출 시 rate-limit 대기로 스레드가 장시간 점유되어도
 *   RSS 수집·AI 요약 등 다른 스케줄 작업에 영향을 주지 않는다.
 */
@Configuration
class SchedulerConfig {

    companion object {
        /** 기본 스케줄러 풀 크기 (다이제스트 제외 일반 작업용). 300유저 스케일 대응을 위해 30으로 확장. */
        const val DEFAULT_POOL_SIZE = 30

        /** 다이제스트 전용 스케줄러 풀 크기 */
        const val DIGEST_POOL_SIZE = 5
    }

    @Bean
    fun taskScheduler(): ThreadPoolTaskScheduler {
        return ThreadPoolTaskScheduler().apply {
            poolSize = DEFAULT_POOL_SIZE
            setThreadNamePrefix("sched-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(30)
        }
    }

    @Bean(name = ["digestTaskScheduler"], initMethod = "afterPropertiesSet", destroyMethod = "shutdown")
    fun digestTaskScheduler(): ThreadPoolTaskScheduler {
        return ThreadPoolTaskScheduler().apply {
            poolSize = DIGEST_POOL_SIZE
            setThreadNamePrefix("digest-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(60)
        }
    }
}
