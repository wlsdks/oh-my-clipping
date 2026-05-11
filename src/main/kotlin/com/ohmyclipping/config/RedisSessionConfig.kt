package com.ohmyclipping.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession

/**
 * Redis 기반 세션 저장소 설정.
 * SESSION_STORE_TYPE=redis일 때만 활성화된다.
 * 서버 재시작/배포 시 300+ 유저의 세션이 유지된다.
 */
@Configuration
@ConditionalOnProperty(name = ["spring.session.store-type"], havingValue = "redis")
@EnableRedisWebSession(maxInactiveIntervalInSeconds = 1800, redisNamespace = "clipping:session")
class RedisSessionConfig
