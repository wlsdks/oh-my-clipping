package com.ohmyclipping.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

private val log = KotlinLogging.logger {}

/**
 * @Async 메서드 실행에 사용할 스레드 풀 설정.
 * 기본 SimpleAsyncTaskExecutor(무한 스레드 생성)를 방지하고
 * 제한된 풀에서 실행하여 OOM을 방지한다.
 */
@Configuration
class AsyncConfig : AsyncConfigurer {

    @Bean("asyncExecutor")
    fun asyncExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 15
        executor.queueCapacity = 50
        executor.setThreadNamePrefix("async-")
        executor.setRejectedExecutionHandler { _, _ ->
            log.warn { "Async task rejected — thread pool and queue are full" }
            throw RejectedExecutionException("Async task rejected because thread pool and queue are full")
        }
        executor.initialize()
        return executor
    }

    override fun getAsyncExecutor(): Executor = asyncExecutor()
}
