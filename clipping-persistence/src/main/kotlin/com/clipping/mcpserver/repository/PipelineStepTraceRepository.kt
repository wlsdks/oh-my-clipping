package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.PipelineStepTraceEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 파이프라인 단계 추적 이력 JPA repository.
 * runId 단위 조회는 DB 조건과 정렬을 사용해 전체 trace 로드를 피한다.
 */
interface PipelineStepTraceRepository : JpaRepository<PipelineStepTraceEntity, String> {

    /**
     * 단일 실행의 단계 추적을 시작 시각 오름차순으로 조회한다.
     * 실행 상세 화면과 로그 조회에서 메모리 필터링 없이 필요한 row만 가져온다.
     */
    fun findByRunIdOrderByStartedAtAsc(runId: String): List<PipelineStepTraceEntity>
}
