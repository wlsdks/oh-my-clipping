package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.Organization
import com.clipping.mcpserver.model.OrganizationType
import com.clipping.mcpserver.model.OrganizationOrigins
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.OrganizationStore
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Organization 생성/수정/삭제 + Category 링크 관리를 담당한다.
 *
 * 도메인 규칙:
 * - name 은 필수 + 200자 제한 (DB VARCHAR(200)).
 * - type 은 OrganizationType enum 중 하나.
 * - 동일 tenant 내 name 중복은 ConflictException.
 * - domain/description 은 optional — 빈 문자열은 null 로 정규화.
 */
@Service
class OrganizationService(
    private val store: OrganizationStore,
    private val categoryStore: CategoryStore,
) {

    companion object {
        /** DB VARCHAR(200) 과 일치. */
        const val NAME_MAX = 200

        /** DB VARCHAR(255) 와 일치. */
        const val DOMAIN_MAX = 255

        /** UX 관점 상한. DB 는 TEXT. */
        const val DESCRIPTION_MAX = 2000

        /** 별칭 목록 최대 개수. */
        const val ALIASES_MAX_COUNT = 20

        /** 개별 별칭 최대 길이. */
        const val ALIAS_MAX_LENGTH = 50
    }

    /**
     * 조직 목록을 조회한다. [typeFilter] 가 주어지면 해당 타입만 반환.
     */
    fun findAll(typeFilter: OrganizationType? = null): List<Organization> =
        store.findAll(typeFilter)

    /**
     * 조직 목록과 각 조직에 연결된 카테고리 수를 함께 반환한다.
     *
     * N+1 쿼리 없이 [OrganizationStore.countCategoryLinksByOrganizationIds] 를 배치 호출하여
     * 어드민 목록 페이지의 usageCount 를 채운다.
     *
     * @param typeFilter null 이면 전체 타입 반환, 지정 시 해당 타입만 반환.
     * @return (Organization, usageCount) Pair 목록. 링크가 없는 조직은 usageCount=0.
     */
    fun findAllWithUsageCounts(typeFilter: OrganizationType? = null): List<Pair<Organization, Int>> {
        val orgs = store.findAll(typeFilter)
        if (orgs.isEmpty()) return emptyList()
        // 배치 집계 — 링크 없는 조직은 결과 Map 에 포함되지 않으므로 ?: 0 으로 방어.
        val counts = store.countCategoryLinksByOrganizationIds(orgs.map { it.id })
        return orgs.map { it to (counts[it.id] ?: 0) }
    }

    /**
     * 단건 조회. 없으면 NotFoundException.
     */
    fun getById(id: String): Organization =
        store.findById(id) ?: throw NotFoundException("Organization not found: $id")

    /**
     * 조직을 신규 생성한다.
     *
     * - name / type 유효성 검증
     * - 동일 이름 중복은 ConflictException
     */
    fun create(
        name: String,
        type: String,
        domain: String? = null,
        description: String? = null,
    ): Organization {
        // name 정규화 및 필수 체크
        val normalizedName = name.trim()
        ensureValid(normalizedName.isNotBlank()) { "Organization name is required" }
        ensureValid(normalizedName.length <= NAME_MAX) { "Organization name is too long" }

        val parsedType = parseType(type)
        val normalizedDomain = domain?.trim()?.takeIf { it.isNotEmpty() }
        normalizedDomain?.let {
            ensureValid(it.length <= DOMAIN_MAX) { "Organization domain is too long" }
        }
        val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
        normalizedDescription?.let {
            ensureValid(it.length <= DESCRIPTION_MAX) { "Organization description is too long" }
        }

        // 중복 체크 — DB UNIQUE 도 있지만 안내 메시지를 위해 서비스에서도 확인.
        store.findByName(normalizedName)?.let {
            throw ConflictException("Organization with name '$normalizedName' already exists")
        }

        return try {
            store.save(
                Organization(
                    id = "",
                    name = normalizedName,
                    type = parsedType,
                    domain = normalizedDomain,
                    description = normalizedDescription,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            // 동시성으로 race 가 발생해도 명확한 409 로 변환.
            throw ConflictException("Organization with name '$normalizedName' already exists", cause = e)
        }
    }

    /**
     * 조직을 부분 수정한다. null 필드는 변경 없음. 빈 문자열 domain/description 은 null 로 초기화.
     *
     * @param aliases null = 변경 없음, non-null = 전체 교체 (trim/dedup/빈값 제거 정규화 포함).
     */
    fun update(
        id: String,
        name: String?,
        type: String?,
        domain: String?,
        description: String?,
        aliases: List<String>? = null,
    ): Organization {
        val current = store.findById(id) ?: throw NotFoundException("Organization not found: $id")

        val nextName = if (name != null) {
            val trimmed = name.trim()
            ensureValid(trimmed.isNotBlank()) { "Organization name is required" }
            ensureValid(trimmed.length <= NAME_MAX) { "Organization name is too long" }
            // 다른 id 에서 같은 이름을 쓰고 있으면 충돌.
            val duplicate = store.findByName(trimmed)
            if (duplicate != null && duplicate.id != id) {
                throw ConflictException("Organization with name '$trimmed' already exists")
            }
            trimmed
        } else current.name

        val nextType = if (type != null) parseType(type) else current.type

        // 빈 문자열 → null 로 초기화, null 자체 → 변경 없음.
        val nextDomain = when {
            domain == null -> current.domain
            domain.isBlank() -> null
            else -> {
                val trimmed = domain.trim()
                ensureValid(trimmed.length <= DOMAIN_MAX) { "Organization domain is too long" }
                trimmed
            }
        }

        val nextDescription = when {
            description == null -> current.description
            description.isBlank() -> null
            else -> {
                val trimmed = description.trim()
                ensureValid(trimmed.length <= DESCRIPTION_MAX) { "Organization description is too long" }
                trimmed
            }
        }

        // aliases null → 변경 없음. non-null → trim/dedup/빈값 제거 후 교체.
        val nextAliases = if (aliases != null) {
            val normalized = aliases.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            ensureValid(normalized.size <= ALIASES_MAX_COUNT) { "Organization aliases limit exceeded" }
            ensureValid(normalized.all { it.length <= ALIAS_MAX_LENGTH }) { "Organization alias is too long" }
            normalized
        } else current.aliases

        return try {
            store.update(
                current.copy(
                    name = nextName,
                    type = nextType,
                    domain = nextDomain,
                    description = nextDescription,
                    aliases = nextAliases,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            throw ConflictException("Organization with name '$nextName' already exists", cause = e)
        }
    }

    /**
     * 조직을 삭제한다. 연결된 category_organizations 는 DB CASCADE 로 정리된다.
     */
    fun delete(id: String) {
        // 존재하지 않으면 NotFound — idempotent 삭제가 필요하면 호출자가 예외를 흡수.
        store.findById(id) ?: throw NotFoundException("Organization not found: $id")
        store.delete(id)
    }

    /**
     * 특정 카테고리에 연결된 조직 목록을 조회한다.
     */
    fun findByCategoryId(categoryId: String): List<Organization> {
        categoryStore.findById(categoryId) ?: throw NotFoundException("Category not found: $categoryId")
        return store.findByCategoryId(categoryId)
    }

    /**
     * 카테고리 ↔ 조직 링크를 완전 교체한다.
     *
     * - 존재하지 않는 categoryId / organizationIds 는 NotFound 로 거부.
     * - 부분 정규화 금지 — 유효하지 않은 id 가 하나라도 있으면 전체 거부.
     */
    @Transactional
    fun setCategoryOrganizations(categoryId: String, organizationIds: List<String>) {
        categoryStore.findById(categoryId) ?: throw NotFoundException("Category not found: $categoryId")

        // 빈 리스트는 허용 (모든 링크 해제).
        val unique = organizationIds.distinct().filter { it.isNotBlank() }
        if (unique.isNotEmpty()) {
            // 부분 정규화 금지: 존재하지 않는 id 가 섞여 있으면 전체 거부.
            val missing = unique.filter { store.findById(it) == null }
            if (missing.isNotEmpty()) {
                throw InvalidInputException(
                    "Organization(s) not found: ${missing.joinToString(", ")}"
                )
            }
        }

        store.setCategoryOrganizations(categoryId, unique)
    }

    /**
     * 카테고리에 조직을 연결한다. 이미 연결된 경우 무시(idempotent).
     *
     * 위자드 승인 흐름에서 조직을 하나씩 추가할 때 사용한다.
     * setCategoryOrganizations 와 달리 기존 링크를 삭제하지 않는다.
     */
    @Transactional
    fun linkToCategoryIfAbsent(categoryId: String, orgId: String) {
        // 이미 링크가 존재하면 중복 저장을 생략한다.
        val alreadyLinked = store.findByCategoryId(categoryId).any { it.id == orgId }
        if (alreadyLinked) return
        // 기존 링크는 보존하고 새 조직만 추가한다.
        val currentIds = store.findByCategoryId(categoryId).map { it.id }
        store.setCategoryOrganizations(categoryId, currentIds + orgId)
    }

    /**
     * stockCode + name 으로 조직을 upsert 한다.
     *
     * identity 앵커 우선순위: stockCode 매치 → name 매치 → 신규 INSERT.
     * - 기존 row 의 stockCode 가 null 이고 요청에 있으면 주입한다.
     * - 기존 stockCode 와 요청 stockCode 가 다르면 ConflictException.
     * - 동시성 INSERT race 는 DataIntegrityViolationException 을 catch 후 re-select 로 복구.
     *
     * @param tenantId 테넌트 식별자 (현재 항상 "default")
     * @param name 조직명
     * @param stockCode 한국 주식 종목 코드 (nullable)
     * @param origin 생성 경로 (기본: [OrganizationOrigins.USER_WIZARD])
     */
    fun upsertByStockCodeOrName(
        tenantId: String,
        name: String,
        stockCode: String?,
        origin: String = OrganizationOrigins.USER_WIZARD,
    ): Organization {
        // stockCode 가 있으면 먼저 종목코드로 기존 조직을 찾는다.
        if (stockCode != null) {
            store.findByTenantAndStockCode(tenantId, stockCode)?.let { return it }
        }

        // stockCode 매치 없으면 name 으로 기존 조직을 조회한다.
        store.findByTenantAndName(tenantId, name)?.let { existing ->
            // 기존 row 의 stockCode 가 null 이고 새 stockCode 가 있으면 보강 UPDATE.
            if (existing.stockCode == null && stockCode != null) {
                return store.updateStockCode(existing.id, stockCode)
            }
            // 기존 stockCode 와 다른 값을 요청하면 충돌로 거부한다.
            if (existing.stockCode != null && stockCode != null && existing.stockCode != stockCode) {
                throw ConflictException(
                    "조직 '$name' 은 이미 종목코드 ${existing.stockCode} 와 연결되어 있어요"
                )
            }
            return existing
        }

        // 기존 row 가 없으면 신규 INSERT 를 시도한다.
        return try {
            val id = UUID.randomUUID().toString()
            store.insert(
                id = id,
                tenantId = tenantId,
                name = name,
                type = OrganizationType.CUSTOMER.name,
                domain = null,
                stockCode = stockCode,
                aliases = null,
                origin = origin,
            )
            store.findByTenantAndName(tenantId, name)
                ?: throw ConflictException("Inserted org not found — tenant=$tenantId name=$name")
        } catch (e: DataIntegrityViolationException) {
            // 동시성 race — 다른 트랜잭션이 먼저 INSERT 한 경우 re-select 로 복구한다.
            store.findByTenantAndName(tenantId, name)
                ?: stockCode?.let { store.findByTenantAndStockCode(tenantId, it) }
                ?: throw ConflictException("Concurrent upsert race — name=$name stockCode=$stockCode")
        }
    }

    /** type 문자열을 enum 으로 변환. 허용 밖이면 InvalidInputException. */
    private fun parseType(raw: String): OrganizationType =
        runCatching { OrganizationType.valueOf(raw.trim().uppercase()) }
            .getOrElse {
                throw InvalidInputException(
                    "Invalid organization type: '$raw'. Allowed: COMPETITOR, CUSTOMER, PARTNER, OTHER"
                )
            }
}
