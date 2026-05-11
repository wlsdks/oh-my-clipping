package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.EntityRevision

/**
 * 엔티티 변경 이력 저장소.
 *
 * `entity_revision_history` 테이블은 append-only로 관리되며, 도메인별 update 성공 후
 * 서비스가 [append]를 호출해 revision을 기록한다. Retention은 Phase 2에서 처리한다.
 */
interface EntityRevisionStore {

    /**
     * 신규 revision을 append한다.
     *
     * 호출자는 리소스 업데이트 성공 후 이 메서드를 호출해 편집 이력을 남긴다.
     * revision_number는 `(resourceType, resourceId)` 단위로 1부터 증가한다 —
     * 호출자가 latest revision + 1 을 직접 계산하지 않아도 되도록 구현체가 [nextRevisionNumber]로 채운다.
     *
     * @return 저장된 [EntityRevision]
     */
    @Suppress("LongParameterList")
    fun append(
        resourceType: String,
        resourceId: String,
        editorId: String,
        editorDisplayName: String?,
        changedFields: List<String>,
        snapshot: String
    ): EntityRevision

    /**
     * (resourceType, resourceId)에 해당하는 revision을 최신순으로 조회한다.
     *
     * @param limit 최대 반환 건수. 1 이상 100 이하로 구현체에서 보정.
     */
    fun listRecent(resourceType: String, resourceId: String, limit: Int): List<EntityRevision>

    /** revision UUID로 단건 조회한다. 매칭되는 레코드가 없으면 null. */
    fun findById(revisionId: String): EntityRevision?

    /**
     * (resourceType, resourceId)의 다음 revision_number를 반환한다.
     *
     * 기존 레코드가 없으면 1을 반환한다. 테스트와 외부 호출에서도 사용할 수 있다.
     */
    fun nextRevisionNumber(resourceType: String, resourceId: String): Long
}
