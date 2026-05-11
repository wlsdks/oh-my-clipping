package com.clipping.mcpserver.model

import java.time.Instant

/**
 * 엔티티 변경 이력 도메인 모델.
 *
 * 4개 도메인(Persona, Category, CategoryRule, RssSource) 공통 히스토리 표현.
 * `snapshot`은 업데이트 직후 엔티티의 JSON 직렬화 문자열. restore 시 이 값을 역직렬화해 복원한다.
 *
 * @property id 레코드 UUID
 * @property resourceType 리소스 타입 문자열 ([EntityRevisionResourceType] enum의 wire value)
 * @property resourceId 대상 엔티티 ID
 * @property revisionNumber (resourceType, resourceId) 별로 1부터 증가하는 리비전 번호
 * @property editorId actor username (낙관적 잠금 로그용 원본 값)
 * @property editorDisplayName 익명화된 표시 이름. null이면 프론트에서 "관리자"로 표시.
 * @property changedFields 이번 revision에서 바뀐 필드 이름 목록. 빈 리스트 허용.
 * @property snapshot 엔티티 JSON 직렬화 문자열.
 * @property createdAt 레코드 생성 시각.
 */
data class EntityRevision(
    val id: String,
    val resourceType: String,
    val resourceId: String,
    val revisionNumber: Long,
    val editorId: String,
    val editorDisplayName: String?,
    val changedFields: List<String>,
    val snapshot: String,
    val createdAt: Instant
)

/**
 * 통합 히스토리 API에서 지원하는 리소스 타입.
 *
 * 문자열 값은 DB `resource_type` 컬럼과 API path segment에 동일하게 사용된다.
 */
enum class EntityRevisionResourceType(val wire: String) {
    PERSONA("persona"),
    CATEGORY("category"),
    CATEGORY_RULE("category_rule"),
    RSS_SOURCE("rss_source");

    companion object {
        /** wire string으로 enum을 조회한다. 매칭 실패 시 null. */
        fun fromWire(raw: String): EntityRevisionResourceType? =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) }
    }
}
