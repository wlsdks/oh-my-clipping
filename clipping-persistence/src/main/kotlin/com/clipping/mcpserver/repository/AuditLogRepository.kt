package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.AuditLogEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/**
 * 운영 감사 로그 JPA 리포지토리.
 * audit_log 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 * 동적 필터 조합 쿼리는 Store 구현에서 처리한다.
 */
interface AuditLogRepository : JpaRepository<AuditLogEntity, Long> {

    /** 최근 감사 로그를 생성일시 내림차순으로 조회한다. */
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<AuditLogEntity>

    /** 행위자 ID로 감사 로그를 조회한다. */
    fun findByActorId(actorId: String): List<AuditLogEntity>

    /** 행위 종류로 감사 로그를 조회한다. */
    fun findByAction(action: String): List<AuditLogEntity>

    /** 대상 유형으로 감사 로그를 조회한다. */
    fun findByTargetType(targetType: String): List<AuditLogEntity>

    /** 고유 행위 종류 목록을 반환한다. */
    @Query("SELECT DISTINCT a.action FROM AuditLogEntity a ORDER BY a.action")
    fun findDistinctActions(): List<String>

    /** 고유 대상 유형 목록을 반환한다. */
    @Query("SELECT DISTINCT a.targetType FROM AuditLogEntity a ORDER BY a.targetType")
    fun findDistinctTargetTypes(): List<String>

    /** 지정 시각 이전의 감사 로그를 삭제한다. */
    @Modifying
    @Query("DELETE FROM AuditLogEntity a WHERE a.createdAt < :cutoff")
    fun deleteByCreatedAtBefore(cutoff: Instant): Int
}
