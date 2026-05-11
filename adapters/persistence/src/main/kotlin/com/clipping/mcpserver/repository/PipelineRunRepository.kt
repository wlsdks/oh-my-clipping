package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.PipelineRunEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface PipelineRunRepository : JpaRepository<PipelineRunEntity, String> {
    fun findByCategoryIdOrderByCreatedAtDesc(categoryId: String, pageable: Pageable): List<PipelineRunEntity>
    fun findByCategoryIdAndStatus(categoryId: String, status: String): List<PipelineRunEntity>
    fun findByStatus(status: String): List<PipelineRunEntity>
    fun countByCategoryIdAndStatus(categoryId: String, status: String): Int
    fun countByCategoryId(categoryId: String): Int
    fun countByStatus(status: String): Int
    fun deleteByCreatedAtBefore(cutoff: Instant): Int

    /**
     * 특정 카테고리에서 cutoff 이후에 종료된 가장 최근 FAILED 실행을 반환한다.
     * 쿨다운 창 내 기존 스레드를 탐지하는 데 사용한다.
     */
    @Query("""
        SELECT r FROM PipelineRunEntity r
        WHERE r.categoryId = :categoryId
          AND r.status = 'FAILED'
          AND r.endedAt > :cutoff
        ORDER BY r.endedAt DESC
    """)
    fun findLatestFailedByCategory(
        @Param("categoryId") categoryId: String,
        @Param("cutoff") cutoff: Instant,
        pageable: Pageable,
    ): List<PipelineRunEntity>

    /**
     * 특정 카테고리의 최근 N건 실행(종료 시각 역순)을 반환한다.
     * 복구 감지에서 연속 실패 streak 계산에 사용한다.
     */
    fun findByCategoryIdOrderByEndedAtDesc(categoryId: String, pageable: Pageable): List<PipelineRunEntity>

    /**
     * 파이프라인 실행의 Slack 스레드 정보를 업데이트한다.
     */
    @Modifying
    @Query("UPDATE PipelineRunEntity r SET r.slackThreadTs = :threadTs, r.slackPayloadJson = :payloadJson WHERE r.id = :runId")
    fun updateSlackThread(
        @Param("runId") runId: String,
        @Param("threadTs") threadTs: String,
        @Param("payloadJson") payloadJson: String,
    )
}
