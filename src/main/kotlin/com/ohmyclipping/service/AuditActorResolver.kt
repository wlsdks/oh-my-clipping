package com.ohmyclipping.service

import com.ohmyclipping.store.AdminUserStore
import org.springframework.stereotype.Component

/**
 * `audit_log` 기록 시 사용할 actor 식별자를 해석한다.
 *
 * 컨트롤러/서비스는 Spring Security 의 `authentication.name` (= username 문자열) 만
 * 쉽게 얻는다. 하지만 `audit_log.actor_id` 는 V117 이후 `admin_users.id` (UUID) 를
 * 참조하는 FK 컬럼이므로, 여기서 한 번에 변환해 저장 측은 항상 올바른 값을 받는다.
 *
 * 해석 규칙:
 *   1. principal 이 blank/null → `(null, "system")`.
 *   2. admin_users 에 해당 username 이 있으면 → `(id, displayName ?: username)`.
 *   3. 없으면 → `(null, username)`. FK 는 SET NULL 이라 DB 제약을 만족한다.
 *
 * FK 재추가(V120)와 함께 이 Resolver 가 모든 `auditLogStore.log(...)` 의 진입점을
 * 통일한다. 호출자는 raw `authentication.name` 을 넘기지 않는다.
 */
@Component
class AuditActorResolver(
    private val adminUserStore: AdminUserStore
) {

    fun resolve(principal: String?): ResolvedActor {
        if (principal.isNullOrBlank()) {
            return ResolvedActor(id = null, name = "system")
        }
        val user = adminUserStore.findByUsername(principal)
        val displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: principal
        return ResolvedActor(id = user?.id, name = displayName)
    }
}

/** `AuditActorResolver` 의 해석 결과. */
data class ResolvedActor(
    /** `admin_users.id` UUID 또는 null (시스템/외부 행위). */
    val id: String?,
    /** 표시용 이름 (displayName 우선, 없으면 username). */
    val name: String
)
