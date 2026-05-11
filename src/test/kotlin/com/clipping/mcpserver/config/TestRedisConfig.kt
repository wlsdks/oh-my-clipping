package com.clipping.mcpserver.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.ZSetOperations
import io.mockk.every
import io.mockk.mockk

/**
 * 테스트 환경에서 실제 Redis 연결 없이 RedisRateLimitService가 동작하도록
 * mock StringRedisTemplate을 제공한다.
 */
@Configuration
class TestRedisConfig {

    @Bean
    @Primary
    @ConditionalOnMissingBean(StringRedisTemplate::class)
    fun stringRedisTemplate(): StringRedisTemplate {
        val zSetOps = mockk<ZSetOperations<String, String>>(relaxed = true)
        val valueOps = mockk<ValueOperations<String, String>>(relaxed = true)
        val template = mockk<StringRedisTemplate>(relaxed = true)
        every { template.opsForZSet() } returns zSetOps
        every { template.opsForValue() } returns valueOps
        every { template.hasKey(any()) } returns false
        every { template.expire(any(), any<java.time.Duration>()) } returns true
        every { valueOps.setIfAbsent(any(), any(), any<java.time.Duration>()) } returns true
        return template
    }
}
