package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.Department
import com.clipping.mcpserver.model.DepartmentTree
import com.clipping.mcpserver.model.Team
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.DepartmentStore
import com.clipping.mcpserver.store.TeamStore
import com.clipping.mcpserver.util.DepartmentNormalizer
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 부서/팀 조직도를 CRUD 관리하고 트리 뷰를 계산하는 서비스.
 *
 * - 활성 트리 조회 [getActiveTree] 는 Caffeine (5분) 캐시를 사용한다.
 *   캐시 이름은 `department-tree` 이며 모든 변경 메서드가 `allEntries=true` 로 evict 한다.
 * - 이름 중복은 [DepartmentNormalizer.normalize] 결과 기준으로 UNIQUE 를 검증한다.
 *   DB UNIQUE 제약과 서비스 검증을 이중으로 두어 동시 저장 race 를 `ConflictException` 으로 정규화한다.
 * - 팀 부서 이동 [updateTeam] 은 이동 대상 부서에서 UNIQUE(department_id, name_normalized) 를 재검증한다.
 */
@Service
class DepartmentTreeService(
    private val departmentStore: DepartmentStore,
    private val teamStore: TeamStore,
    private val auditLogStore: AuditLogStore,
    private val auditActorResolver: AuditActorResolver,
    private val adminUserStore: AdminUserStore
) {

    companion object {
        /** DB VARCHAR(100) 과 일치 — 원본 표시명 최대 길이. */
        const val NAME_MAX: Int = 100

        /** [getActiveTree] 캐시 이름. `CacheConfig` 와 동일 문자열을 사용한다. */
        const val CACHE_NAME: String = "department-tree"
    }

    /**
     * 활성 부서 + 하위 활성 팀 트리. 공개/관리자 공용 조회에 사용한다.
     *
     * 캐시 TTL 5분, 단일 키 (단 하나의 트리만 캐시). 모든 변경 메서드가 `@CacheEvict` 로 즉시 무효화한다.
     */
    @Cacheable(CACHE_NAME)
    fun getActiveTree(): List<DepartmentTree> {
        val departments = departmentStore.findAllActive()
        if (departments.isEmpty()) return emptyList()
        // 부서당 개별 쿼리를 피하기 위해 활성 팀을 한 번에 읽고 부서별로 그룹화한다.
        val teamsByDepartment = teamStore.findAllActive().groupBy { it.departmentId }
        return departments.map { dept ->
            DepartmentTree(
                department = dept,
                teams = teamsByDepartment[dept.id].orEmpty()
            )
        }
    }

    /**
     * Admin 관리 UI 용 전체 트리. 비활성 항목도 포함한다.
     * 캐시하지 않는다 (관리자 수가 적고, Admin UI 에서 실시간성이 더 중요).
     */
    fun getAdminTree(): List<DepartmentTree> {
        val departments = departmentStore.findAll()
        if (departments.isEmpty()) return emptyList()
        val teamsByDepartment = teamStore.findAll().groupBy { it.departmentId }
        return departments.map { dept ->
            DepartmentTree(
                department = dept,
                teams = teamsByDepartment[dept.id].orEmpty()
            )
        }
    }

    /** 단건 조회. 없으면 [NotFoundException]. */
    fun getDepartment(id: String): Department =
        departmentStore.findById(id) ?: throw NotFoundException("부서를 찾을 수 없습니다: $id")

    /** 단건 조회. 없으면 [NotFoundException]. */
    fun getTeam(id: String): Team =
        teamStore.findById(id) ?: throw NotFoundException("팀을 찾을 수 없습니다: $id")

    /**
     * 신규 부서 생성. 이름 공백/길이 검증 후 정규화 결과 UNIQUE 를 사전 체크한다.
     *
     * @throws InvalidInputException 이름이 비었거나 100자 초과
     * @throws ConflictException 같은 정규화 이름의 부서가 이미 존재
     */
    @CacheEvict(CACHE_NAME, allEntries = true)
    @Transactional
    fun createDepartment(name: String, displayOrder: Int = 0, actorPrincipal: String? = null): Department {
        // 이름 정규화 및 사전 검증 → ConflictException 으로 UNIQUE 충돌을 변환한다.
        val (trimmed, normalized) = validateName(name)
        departmentStore.findByNameNormalized(normalized)?.let {
            throw ConflictException("같은 이름의 부서가 이미 존재합니다: ${it.name}")
        }
        val saved = runCatching {
            departmentStore.save(
                Department(
                    id = "",
                    name = trimmed,
                    nameNormalized = normalized,
                    displayOrder = displayOrder,
                    isActive = true
                )
            )
        }.getOrElse { e ->
            // 동시성 race 로 DB UNIQUE 위반이 발생하면 명확한 409 로 변환.
            if (e is DataIntegrityViolationException) {
                throw ConflictException("같은 이름의 부서가 이미 존재합니다: $trimmed", cause = e)
            }
            throw e
        }
        writeDepartmentAudit(actorPrincipal, "DEPARTMENT_CREATED", saved)
        return saved
    }

    /**
     * 부서 수정. [name], [displayOrder], [isActive] 를 개별 갱신한다.
     * 이름 변경 시 정규화 기준 UNIQUE 재검증.
     */
    @CacheEvict(CACHE_NAME, allEntries = true)
    @Transactional
    fun updateDepartment(
        id: String,
        name: String? = null,
        displayOrder: Int? = null,
        isActive: Boolean? = null,
        actorPrincipal: String? = null
    ): Department {
        val current = departmentStore.findById(id)
            ?: throw NotFoundException("부서를 찾을 수 없습니다: $id")

        val (nextName, nextNormalized) = if (name != null) {
            val (trimmed, normalized) = validateName(name)
            // 동일 id 는 자기 자신이라 허용, 다른 id 가 같은 정규화 이름이면 ConflictException
            val duplicate = departmentStore.findByNameNormalized(normalized)
            if (duplicate != null && duplicate.id != id) {
                throw ConflictException("같은 이름의 부서가 이미 존재합니다: $trimmed")
            }
            trimmed to normalized
        } else current.name to current.nameNormalized

        val updated = runCatching {
            departmentStore.update(
                current.copy(
                    name = nextName,
                    nameNormalized = nextNormalized,
                    displayOrder = displayOrder ?: current.displayOrder,
                    isActive = isActive ?: current.isActive
                )
            )
        }.getOrElse { e ->
            if (e is DataIntegrityViolationException) {
                throw ConflictException("같은 이름의 부서가 이미 존재합니다: $nextName", cause = e)
            }
            throw e
        }
        // 활성 상태 토글을 구분해 감사 로그 action 을 더 의미 있게 남긴다.
        val action = when {
            isActive == true && !current.isActive -> "DEPARTMENT_ACTIVATED"
            isActive == false && current.isActive -> "DEPARTMENT_DEACTIVATED"
            else -> "DEPARTMENT_UPDATED"
        }
        writeDepartmentAudit(actorPrincipal, action, updated)
        return updated
    }

    /**
     * 부서를 soft-delete 한다 (is_active=false). hard-delete 는 [deleteDepartment] 참조.
     */
    @CacheEvict(CACHE_NAME, allEntries = true)
    @Transactional
    fun softDeleteDepartment(id: String, actorPrincipal: String? = null): Department =
        updateDepartment(id = id, isActive = false, actorPrincipal = actorPrincipal)

    /**
     * 부서를 물리 삭제한다. 복구 불가 동작이므로 이중 가드를 둔다:
     *   1) 부서가 현재 비활성(is_active=false) 상태여야 한다 — 먼저 비활성 토글 후 삭제.
     *   2) 하위 팀이 하나라도 존재하면 차단 — 팀을 먼저 정리해야 한다.
     *   3) 부서 FK 로 참조 중인 사용자가 있으면 차단 — 사용자 소속을 먼저 바꾼다.
     */
    @CacheEvict(CACHE_NAME, allEntries = true)
    @Transactional
    fun deleteDepartment(id: String, actorPrincipal: String? = null) {
        val department = departmentStore.findById(id)
            ?: throw NotFoundException("부서를 찾을 수 없습니다: $id")
        // 1) 비활성 상태 요구 — 의도적 double opt-in.
        if (department.isActive) {
            throw ConflictException("활성 부서는 삭제할 수 없습니다. 먼저 비활성화해 주세요.")
        }
        // 2) 하위 팀 잔존 여부 확인 (활성/비활성 모두 포함).
        val childTeams = teamStore.findAllByDepartmentId(id).size
        if (childTeams > 0) {
            throw ConflictException("하위 팀 ${childTeams}개가 남아 있어 부서를 삭제할 수 없습니다. 팀을 먼저 삭제하거나 이동해 주세요.")
        }
        // 3) 사용자 참조 여부.
        val referencingUsers = adminUserStore.countByDepartmentId(id)
        if (referencingUsers > 0) {
            throw ConflictException("${referencingUsers}명의 사용자가 이 부서에 속해 있어 삭제할 수 없습니다.")
        }
        // 감사 로그는 삭제 직전에 남겨 이력을 보존한다.
        writeDepartmentAudit(actorPrincipal, "DEPARTMENT_DELETED", department)
        departmentStore.deleteById(id)
    }

    /**
     * 신규 팀 생성. 상위 부서 존재/활성 여부를 검증하고 UNIQUE(department_id, name_normalized) 를 사전 체크한다.
     */
    @CacheEvict(CACHE_NAME, allEntries = true)
    @Transactional
    fun createTeam(
        departmentId: String,
        name: String,
        displayOrder: Int = 0,
        actorPrincipal: String? = null
    ): Team {
        val department = departmentStore.findById(departmentId)
            ?: throw NotFoundException("부서를 찾을 수 없습니다: $departmentId")
        // 비활성 부서 하위에는 신규 팀을 생성할 수 없다 — 활성화 후 다시 시도.
        ensureValid(department.isActive) { "비활성 부서에는 팀을 추가할 수 없습니다." }

        val (trimmed, normalized) = validateName(name)
        teamStore.findByDepartmentIdAndNameNormalized(departmentId, normalized)?.let {
            throw ConflictException("같은 이름의 팀이 이미 존재합니다: ${it.name}")
        }

        val saved = runCatching {
            teamStore.save(
                Team(
                    id = "",
                    departmentId = departmentId,
                    name = trimmed,
                    nameNormalized = normalized,
                    displayOrder = displayOrder,
                    isActive = true
                )
            )
        }.getOrElse { e ->
            if (e is DataIntegrityViolationException) {
                throw ConflictException("같은 이름의 팀이 이미 존재합니다: $trimmed", cause = e)
            }
            throw e
        }
        writeTeamAudit(actorPrincipal, "TEAM_CREATED", saved)
        return saved
    }

    /**
     * 팀 수정. [newDepartmentId] 가 주어지면 부서 간 이동으로 간주하고 이동 후 UNIQUE 를 재검증한다.
     *
     * @throws NotFoundException 팀 또는 이동 대상 부서가 없을 때
     * @throws ConflictException 이동/수정 결과가 UNIQUE(dept, name_normalized) 를 위반할 때
     */
    @CacheEvict(CACHE_NAME, allEntries = true)
    @Transactional
    fun updateTeam(
        id: String,
        name: String? = null,
        displayOrder: Int? = null,
        isActive: Boolean? = null,
        newDepartmentId: String? = null,
        actorPrincipal: String? = null
    ): Team {
        val current = teamStore.findById(id)
            ?: throw NotFoundException("팀을 찾을 수 없습니다: $id")

        // 이동 대상 부서 검증 — null 이면 현재 부서를 유지한다.
        val targetDepartmentId = if (newDepartmentId != null && newDepartmentId != current.departmentId) {
            val targetDept = departmentStore.findById(newDepartmentId)
                ?: throw NotFoundException("부서를 찾을 수 없습니다: $newDepartmentId")
            ensureValid(targetDept.isActive) { "비활성 부서로 팀을 이동할 수 없습니다." }
            newDepartmentId
        } else current.departmentId

        val (nextName, nextNormalized) = if (name != null) {
            validateName(name)
        } else current.name to current.nameNormalized

        // 이름/부서 중 하나라도 바뀌면 UNIQUE(dept, normalized) 를 자기 자신 제외하고 재검증한다.
        val needsUniqueCheck = targetDepartmentId != current.departmentId || nextNormalized != current.nameNormalized
        if (needsUniqueCheck) {
            val duplicate = teamStore.findByDepartmentIdAndNameNormalized(targetDepartmentId, nextNormalized)
            if (duplicate != null && duplicate.id != id) {
                throw ConflictException("같은 이름의 팀이 이미 존재합니다: $nextName")
            }
        }

        val updated = runCatching {
            teamStore.update(
                current.copy(
                    departmentId = targetDepartmentId,
                    name = nextName,
                    nameNormalized = nextNormalized,
                    displayOrder = displayOrder ?: current.displayOrder,
                    isActive = isActive ?: current.isActive
                )
            )
        }.getOrElse { e ->
            if (e is DataIntegrityViolationException) {
                throw ConflictException("같은 이름의 팀이 이미 존재합니다: $nextName", cause = e)
            }
            throw e
        }
        val action = when {
            isActive == true && !current.isActive -> "TEAM_ACTIVATED"
            isActive == false && current.isActive -> "TEAM_DEACTIVATED"
            else -> "TEAM_UPDATED"
        }
        writeTeamAudit(actorPrincipal, action, updated)
        return updated
    }

    /** 팀 soft-delete. */
    @CacheEvict(CACHE_NAME, allEntries = true)
    @Transactional
    fun softDeleteTeam(id: String, actorPrincipal: String? = null): Team =
        updateTeam(id = id, isActive = false, actorPrincipal = actorPrincipal)

    /**
     * 팀을 물리 삭제한다. 복구 불가이므로 이중 가드:
     *   1) 팀이 현재 비활성 상태여야 한다.
     *   2) 팀 FK 로 참조 중인 사용자가 있으면 차단.
     */
    @CacheEvict(CACHE_NAME, allEntries = true)
    @Transactional
    fun deleteTeam(id: String, actorPrincipal: String? = null) {
        val team = teamStore.findById(id)
            ?: throw NotFoundException("팀을 찾을 수 없습니다: $id")
        if (team.isActive) {
            throw ConflictException("활성 팀은 삭제할 수 없습니다. 먼저 비활성화해 주세요.")
        }
        val referencingUsers = adminUserStore.countByTeamId(id)
        if (referencingUsers > 0) {
            throw ConflictException("${referencingUsers}명의 사용자가 이 팀에 속해 있어 삭제할 수 없습니다.")
        }
        writeTeamAudit(actorPrincipal, "TEAM_DELETED", team)
        teamStore.deleteById(id)
    }

    /**
     * 사용자 department_id / team_id FK 일관성 검증.
     *
     * [teamId] 가 있으면 해당 팀의 `departmentId` 가 [departmentId] 와 같아야 한다.
     * [departmentId] 가 null 이면 [teamId] 도 반드시 null 이어야 한다.
     *
     * @return `(resolvedDepartment, resolvedTeam?)` — 이름 캐시 동기화에 그대로 사용.
     * @throws NotFoundException id 가 DB 에 존재하지 않을 때
     * @throws ConflictException team 의 departmentId 가 요청한 departmentId 와 다를 때
     * @throws InvalidInputException departmentId 없이 teamId 만 준 경우
     */
    fun resolveUserAssignment(departmentId: String?, teamId: String?): Pair<Department?, Team?> {
        if (departmentId.isNullOrBlank()) {
            // departmentId 없이 teamId 만 오는 요청은 스키마 위반으로 간주한다.
            ensureValid(teamId.isNullOrBlank()) { "부서 없이 팀만 지정할 수 없습니다." }
            return null to null
        }
        val department = departmentStore.findById(departmentId)
            ?: throw NotFoundException("부서를 찾을 수 없습니다: $departmentId")
        val team = if (!teamId.isNullOrBlank()) {
            val resolved = teamStore.findById(teamId)
                ?: throw NotFoundException("팀을 찾을 수 없습니다: $teamId")
            // 팀의 상위 부서와 요청 부서가 일치하는지 반드시 확인한다.
            if (resolved.departmentId != departmentId) {
                throw ConflictException("선택한 팀이 부서에 속하지 않습니다.")
            }
            resolved
        } else null
        return department to team
    }

    /** 이름 정규화 + 공통 검증. 반환은 (trimmed, normalized) 쌍. */
    private fun validateName(raw: String): Pair<String, String> {
        val trimmed = raw.trim()
        ensureValid(trimmed.isNotBlank()) { "이름을 입력하세요." }
        ensureValid(trimmed.length <= NAME_MAX) { "이름은 최대 ${NAME_MAX}자까지 입력할 수 있습니다." }
        val normalized = DepartmentNormalizer.normalize(trimmed)
            ?: throw InvalidInputException("이름을 입력하세요.")
        return trimmed to normalized
    }

    /** 부서 변경 감사 로그를 기록한다. [actorPrincipal] 이 null 이면 기록을 건너뛴다. */
    private fun writeDepartmentAudit(actorPrincipal: String?, action: String, target: Department) {
        if (actorPrincipal == null) return
        val actor = auditActorResolver.resolve(actorPrincipal)
        auditLogStore.log(
            actorId = actor.id,
            actorName = actor.name,
            action = action,
            targetType = "DEPARTMENT",
            targetId = target.id,
            targetName = target.name,
            detail = "displayOrder=${target.displayOrder}, isActive=${target.isActive}"
        )
    }

    /** 팀 변경 감사 로그를 기록한다. [actorPrincipal] 이 null 이면 기록을 건너뛴다. */
    private fun writeTeamAudit(actorPrincipal: String?, action: String, target: Team) {
        if (actorPrincipal == null) return
        val actor = auditActorResolver.resolve(actorPrincipal)
        auditLogStore.log(
            actorId = actor.id,
            actorName = actor.name,
            action = action,
            targetType = "TEAM",
            targetId = target.id,
            targetName = target.name,
            detail = "departmentId=${target.departmentId}, displayOrder=${target.displayOrder}, isActive=${target.isActive}"
        )
    }
}
