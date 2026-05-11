package com.ohmyclipping.store

import java.time.Instant

/**
 * 운영 감사 로그를 기록하고 조회하는 저장소.
 * 관리자 승인/반려 등 주요 운영 행위를 추적한다.
 */
interface AuditLogStore {

    /**
     * 감사 로그를 기록한다.
     *
     * @param actorId `admin_users.id` UUID. 시스템 작업이거나 행위자가 admin_users 에
     *                 존재하지 않으면 NULL 로 저장한다 (FK ON DELETE SET NULL).
     * @param actorName 행위자 표시명 (username 또는 displayName).
     * @param action 행위 종류 (APPROVE, REJECT 등)
     * @param targetType 대상 유형 (SUBSCRIPTION 등)
     * @param targetId 대상 ID (nullable)
     * @param targetName 대상 이름 (nullable, 표시용)
     * @param detail 추가 상세 정보 (nullable)
     */
    fun log(
        actorId: String?,
        actorName: String,
        action: String,
        targetType: String,
        targetId: String? = null,
        targetName: String? = null,
        detail: String? = null
    )

    /**
     * 최근 감사 로그를 조회한다.
     *
     * @param limit 조회 건수 (기본 50)
     */
    fun findRecent(limit: Int = 50): List<AuditLogEntry>

    /**
     * 필터 조건에 따라 감사 로그를 페이지네이션으로 조회한다.
     * 모든 필터는 선택적이며, 결과는 created_at DESC 순으로 정렬된다.
     *
     * @param actorId 행위자 ID 필터 (선택)
     * @param action 행위 종류 필터 (선택)
     * @param targetType 대상 유형 필터 (선택)
     * @param from 시작 시각 필터 (선택, inclusive)
     * @param to 종료 시각 필터 (선택, exclusive)
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
    ): List<AuditLogEntry>

    /**
     * 필터 조건에 따라 감사 로그의 총 건수를 반환한다.
     *
     * @param actorId 행위자 ID 필터 (선택)
     * @param action 행위 종류 필터 (선택)
     * @param targetType 대상 유형 필터 (선택)
     * @param from 시작 시각 필터 (선택, inclusive)
     * @param to 종료 시각 필터 (선택, exclusive)
     */
    fun countAll(
        actorId: String? = null,
        action: String? = null,
        targetType: String? = null,
        from: Instant? = null,
        to: Instant? = null
    ): Int

    /**
     * 감사 로그에 기록된 고유 행위 종류 목록을 반환한다.
     * 필터 드롭다운 구성에 사용된다.
     */
    fun getDistinctActions(): List<String>

    /**
     * 감사 로그에 기록된 고유 대상 유형 목록을 반환한다.
     * 필터 드롭다운 구성에 사용된다.
     */
    fun getDistinctTargetTypes(): List<String>

    /**
     * 지정 일수보다 오래된 감사 로그를 삭제한다.
     *
     * @return 삭제된 건수
     */
    fun deleteOlderThan(days: Int): Int

    data class AuditLogEntry(
        val id: Long,
        // V117 이후 actorId 는 admin_users 탈퇴/외부 토큰 경로에서 NULL 이 될 수 있다.
        val actorId: String?,
        val actorName: String,
        val action: String,
        val targetType: String,
        val targetId: String?,
        val targetName: String?,
        val detail: String?,
        val createdAt: Instant
    )
}
