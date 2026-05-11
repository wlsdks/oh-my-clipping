package com.clipping.mcpserver.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * Caffeine 인메모리 캐시 설정.
 * 캐시별로 TTL과 최대 크기를 분리하여 용도에 맞게 운영한다.
 */
@Configuration
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val manager = SimpleCacheManager()
        manager.setCaches(listOf(
            // 기존 채널 목록: 5분, 50개
            CaffeineCache("slack-channels",
                cacheBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(50).build()),
            // 채널 멤버 목록: 변경 빈도가 낮으므로 10분, 채널 수 기준 30개
            CaffeineCache("channel-members",
                cacheBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(30).build()),
            // 차단 채널 목록: 변경 시 @CacheEvict로 즉시 무효화, TTL은 안전망
            CaffeineCache("blocked-channels",
                cacheBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(5).build()),
            // 페르소나 분석 라이브 스냅샷: 5분, 단일 키 (singleton 응답)
            // 주간 배치 (Slice 2 이후) 종료 시 명시적 evict, 그 외엔 TTL 안전망
            CaffeineCache("persona-live",
                cacheBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1).build()),
            // 페르소나 주간 트렌드: 30분, weeks 파라미터별 캐시 (최대 10개 변형)
            // 트렌드 데이터는 주 배치 후 변경되므로 30분 TTL은 충분한 안전망
            CaffeineCache("persona-trends",
                cacheBuilder().expireAfterWrite(30, TimeUnit.MINUTES).maximumSize(10).build()),
            // 페르소나 위험/성장 신호: 5분, lookbackWeeks 별 캐시 (최대 12개 변형)
            // 주간 배치 종료 시 명시적 evict, 그 외엔 TTL 안전망
            CaffeineCache("persona-signals",
                cacheBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(12).build()),
            // 부서/팀 활성 트리: 5분, 단일 키 (싱글턴 응답).
            // DepartmentTreeService 가 변경 메서드마다 @CacheEvict(allEntries=true) 로 즉시 무효화.
            CaffeineCache("department-tree",
                cacheBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(1).build())
        ))
        return manager
    }

    private fun cacheBuilder(): Caffeine<Any, Any> =
        Caffeine.newBuilder().recordStats()
}
