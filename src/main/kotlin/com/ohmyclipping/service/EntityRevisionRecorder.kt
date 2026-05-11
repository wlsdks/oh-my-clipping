package com.ohmyclipping.service

import com.ohmyclipping.model.EntityRevision
import com.ohmyclipping.model.EntityRevisionResourceType
import com.ohmyclipping.store.EntityRevisionStore
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component

/**
 * 엔티티 변경 이력 기록/복원 공용 헬퍼.
 *
 * 4개 도메인 서비스(AdminPersonaService, AdminCategoryService, AdminCategoryRuleService, AdminSourceService)가
 * update 성공 후 이 컴포넌트를 통해 snapshot을 append한다. restore 시에는 snapshot 문자열을 다시
 * 도메인 객체로 역직렬화한다.
 *
 * 직렬화는 Jackson + JavaTimeModule을 사용하고, ISO-8601 문자열로 Instant를 저장해
 * DB에 저장된 JSON을 사람이 읽고 검증할 수 있게 한다.
 */
@Component
class EntityRevisionRecorder(
    private val entityRevisionStore: EntityRevisionStore
) {

    companion object {
        /** actor username이 UUID 꼴이거나 빈 값이면 표시 이름을 익명화한다. */
        private val UUID_PATTERN =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }

    /**
     * Jackson ObjectMapper 인스턴스.
     * Instant는 ISO-8601 문자열로, Kotlin data class는 jacksonObjectMapper()로 바인딩된다.
     */
    val mapper = jacksonObjectMapper()
        .apply { findAndRegisterModules() }
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    /**
     * 도메인 객체를 JSON으로 직렬화해 revision을 기록한다.
     *
     * @param resourceType 리소스 타입 enum
     * @param resourceId 대상 엔티티 ID
     * @param editorId actor username (낙관적 잠금 로그 원본)
     * @param editorDisplayName 익명화된 표시 이름. 호출자가 미제공 시 [anonymizeEditorName]로 보정.
     * @param changedFields 이번 저장에서 바뀐 필드 이름 목록
     * @param entity snapshot을 찍을 도메인 객체 (data class)
     */
    @Suppress("LongParameterList")
    fun record(
        resourceType: EntityRevisionResourceType,
        resourceId: String,
        editorId: String,
        editorDisplayName: String?,
        changedFields: List<String>,
        entity: Any
    ): EntityRevision {
        // 저장 경계에서 JSON 직렬화 실패가 발생하면 update 자체가 롤백되지 않도록 호출자 트랜잭션 내부에서 처리한다.
        val snapshot = mapper.writeValueAsString(entity)
        return entityRevisionStore.append(
            resourceType = resourceType.wire,
            resourceId = resourceId,
            editorId = editorId,
            editorDisplayName = editorDisplayName ?: anonymizeEditorName(editorId),
            changedFields = changedFields,
            snapshot = snapshot
        )
    }

    /**
     * snapshot JSON 문자열을 도메인 객체로 역직렬화한다.
     *
     * @param snapshot JSON 문자열
     * @param targetClass 도메인 타입(Class 참조)
     */
    fun <T : Any> deserialize(snapshot: String, targetClass: Class<T>): T =
        mapper.readValue(snapshot, targetClass)

    /**
     * (resourceType, resourceId)에 해당하는 revision을 최신순으로 조회한다.
     *
     * 인바운드 어댑터가 store를 직접 import하지 않도록 service 레이어에서 read 경로도 감싼다.
     *
     * @param resourceType 리소스 타입 enum
     * @param resourceId 대상 엔티티 ID
     * @param limit 최대 반환 건수. store 구현체에서 1..100 범위로 보정한다.
     */
    fun listRecent(
        resourceType: EntityRevisionResourceType,
        resourceId: String,
        limit: Int
    ): List<EntityRevision> =
        entityRevisionStore.listRecent(resourceType.wire, resourceId, limit)

    /**
     * revision UUID로 단건 조회한다. 매칭되는 레코드가 없으면 null.
     *
     * 어드민 컨트롤러의 restore 경로에서 snapshot을 불러올 때 사용한다.
     */
    fun findById(revisionId: String): EntityRevision? =
        entityRevisionStore.findById(revisionId)

    /**
     * actor username을 사용자 친화적 표시 이름으로 익명화한다.
     * UUID, 빈 문자열, "system" 같은 비식별 값은 "관리자"로 대체한다.
     */
    fun anonymizeEditorName(raw: String?): String {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank()) return "관리자"
        if (UUID_PATTERN.matches(normalized)) return "관리자"
        if (normalized.equals("system", ignoreCase = true)) return "시스템"
        return normalized
    }
}
