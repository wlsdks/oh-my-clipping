package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.AsyncJobEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/**
 * 비동기 작업 큐 JPA 리포지토리.
 * clipping_jobs 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 * claim/recover 등 동시성 제어가 필요한 쿼리는 Store 구현에서 처리한다.
 */
interface AsyncJobRepository : JpaRepository<AsyncJobEntity, String> {

    /** 상태별 작업 목록을 조회한다. */
    fun findByStatus(status: String): List<AsyncJobEntity>

    /** 상태별 작업 수를 반환한다. */
    fun countByStatus(status: String): Long

    /** PENDING 상태이며 nextRunAt이 현재 이전인 작업을 조회한다. */
    @Query(
        """SELECT j FROM AsyncJobEntity j
           WHERE j.status = 'PENDING' AND j.nextRunAt <= :now
           ORDER BY j.nextRunAt ASC"""
    )
    fun findDueJobs(now: Instant): List<AsyncJobEntity>

    /** PENDING 상태에서 가장 이른 nextRunAt을 반환한다. */
    @Query("SELECT MIN(j.nextRunAt) FROM AsyncJobEntity j WHERE j.status = 'PENDING'")
    fun findOldestPendingNextRunAt(): Instant?

    /** 완료(SUCCEEDED/FAILED) 상태이며 cutoff 이전에 생성된 작업을 삭제한다. */
    @Modifying
    @Query(
        """DELETE FROM AsyncJobEntity j
           WHERE j.status IN ('SUCCEEDED', 'FAILED') AND j.createdAt < :cutoff"""
    )
    fun deleteCompletedOlderThan(cutoff: Instant): Int

    /** 최근 작업을 생성일 역순으로 페이지네이션 조회한다. */
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<AsyncJobEntity>
}
