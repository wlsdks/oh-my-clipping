package com.clipping.mcpserver.service

import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.AuditLogStore.AuditLogEntry
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 관리자용 감사 로그 조회 서비스.
 * AuditLogStore에 대한 조회 위임과 필터 옵션 제공을 담당한다.
 */
@Service
class AuditLogAdminService(
    private val auditLogStore: AuditLogStore
) {

    /**
     * 필터 조건에 따라 감사 로그를 페이지네이션으로 조회한다.
     *
     * @param actorId 행위자 ID 필터 (선택)
     * @param action 행위 종류 필터 (선택)
     * @param targetType 대상 유형 필터 (선택)
     * @param from 시작 시각 필터 (선택)
     * @param to 종료 시각 필터 (선택)
     * @param offset 건너뛸 건수
     * @param limit 조회 건수
     */
    fun findAll(
        actorId: String? = null,
        action: String? = null,
        targetType: String? = null,
        from: Instant? = null,
        to: Instant? = null,
        offset: Int = 0,
        limit: Int = 30
    ): List<AuditLogEntry> {
        return auditLogStore.findAll(actorId, action, targetType, from, to, offset, limit)
    }

    /**
     * 필터 조건에 따라 감사 로그의 총 건수를 반환한다.
     */
    fun countAll(
        actorId: String? = null,
        action: String? = null,
        targetType: String? = null,
        from: Instant? = null,
        to: Instant? = null
    ): Int {
        return auditLogStore.countAll(actorId, action, targetType, from, to)
    }

    /**
     * 감사 로그에 기록된 고유 행위 종류 목록을 반환한다.
     */
    fun getDistinctActions(): List<String> {
        return auditLogStore.getDistinctActions()
    }

    /**
     * 감사 로그에 기록된 고유 대상 유형 목록을 반환한다.
     */
    fun getDistinctTargetTypes(): List<String> {
        return auditLogStore.getDistinctTargetTypes()
    }
}
