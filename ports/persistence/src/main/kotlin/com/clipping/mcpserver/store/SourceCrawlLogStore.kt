package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.SourceCrawlLog
import java.time.Instant

/**
 * 소스 크롤 로그를 저장/조회/정리하는 저장소 인터페이스.
 * 크롤 이력 대시보드와 가동률(uptime) 계산에 사용한다.
 */
interface SourceCrawlLogStore {

    /** 크롤 로그를 저장한다. */
    fun save(log: SourceCrawlLog)

    /** 특정 소스의 크롤 로그를 cutoff 이후부터 최신 순으로 조회한다. */
    fun findBySourceId(sourceId: String, cutoff: Instant): List<SourceCrawlLog>

    /** 특정 소스의 cutoff 이후 가동률(성공 비율, 0~100)을 반환한다. 로그가 없으면 null. */
    fun getUptimePercent(sourceId: String, cutoff: Instant): Double?

    /** cutoff 이전의 오래된 로그를 삭제한다. 삭제된 건수를 반환한다. */
    fun deleteOlderThan(cutoff: Instant): Int
}
