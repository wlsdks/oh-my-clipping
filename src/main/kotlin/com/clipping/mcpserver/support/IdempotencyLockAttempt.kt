package com.clipping.mcpserver.support

/**
 * Redis 멱등성 락 획득 시도 결과.
 */
internal enum class LockAttempt {
    ACQUIRED,
    HELD_BY_OTHER,
    UNAVAILABLE
}
