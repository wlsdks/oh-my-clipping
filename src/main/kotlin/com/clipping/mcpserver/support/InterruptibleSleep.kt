package com.clipping.mcpserver.support

import java.util.concurrent.TimeUnit

/**
 * 짧은 재시도/레이트리밋 대기를 인터럽트 안전하게 수행한다.
 * InterruptedException을 삼키지 않고 인터럽트 플래그를 복구한 뒤 의미 있는 예외로 감싼다.
 */
object InterruptibleSleep {

    /**
     * 지정한 지연(ms)만큼 대기한다.
     */
    fun sleep(delayMs: Long, context: String) {
        if (delayMs <= 0) return
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("대기 중 인터럽트가 발생했습니다: $context", exception)
        }
    }
}
