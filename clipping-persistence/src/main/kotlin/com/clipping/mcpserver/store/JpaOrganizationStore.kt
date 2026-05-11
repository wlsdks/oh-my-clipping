package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.CategoryOrganizationEntity
import com.clipping.mcpserver.entity.OrganizationEntity
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.Organization
import com.clipping.mcpserver.model.OrganizationType
import com.clipping.mcpserver.repository.CategoryOrganizationRepository
import com.clipping.mcpserver.repository.OrganizationRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Organization JPA 구현.
 *
 * tenant_id 는 현재 항상 "default". 향후 다중 테넌트 지원 시 확장한다.
 */
@Repository
@Primary
class JpaOrganizationStore(
    private val repository: OrganizationRepository,
    private val linkRepository: CategoryOrganizationRepository,
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : OrganizationStore {

    override fun findAll(type: OrganizationType?): List<Organization> =
        if (type == null) {
            repository.findAllByTenantIdOrderByNameAsc(DEFAULT_TENANT).map { it.toModel() }
        } else {
            repository.findAllByTenantIdAndTypeOrderByNameAsc(DEFAULT_TENANT, type.name).map { it.toModel() }
        }

    override fun findById(id: String): Organization? =
        repository.findById(id).orElse(null)?.toModel()

    override fun findByName(name: String): Organization? =
        repository.findByTenantIdAndName(DEFAULT_TENANT, name)?.toModel()

    override fun findByTenantAndName(tenantId: String, name: String): Organization? =
        repository.findByTenantIdAndName(tenantId, name)?.toModel()

    override fun save(organization: Organization): Organization {
        val now = Instant.now()
        // id 가 비어 있으면 UUID 생성 — 도메인 객체를 그대로 받되 식별자는 저장소가 관리한다.
        val id = organization.id.ifBlank { UUID.randomUUID().toString() }
        val aliasesJson = if (organization.aliases.isEmpty()) null
                          else objectMapper.writeValueAsString(organization.aliases)
        val entity = OrganizationEntity(
            id = id,
            tenantId = organization.tenantId,
            name = organization.name,
            type = organization.type.name,
            domain = organization.domain,
            description = organization.description,
            stockCode = organization.stockCode,
            aliases = aliasesJson,
            origin = organization.origin,
            createdAt = now,
            updatedAt = now,
        )
        return repository.save(entity).toModel()
    }

    override fun update(organization: Organization): Organization {
        val entity = repository.findById(organization.id).orElseThrow {
            NotFoundException("Organization not found: ${organization.id}")
        }
        // 사용자 편집 시각만 갱신. tenant_id 는 변경 불가.
        entity.name = organization.name
        entity.type = organization.type.name
        entity.domain = organization.domain
        entity.description = organization.description
        entity.stockCode = organization.stockCode
        entity.aliases = if (organization.aliases.isEmpty()) null
                         else objectMapper.writeValueAsString(organization.aliases)
        entity.origin = organization.origin
        entity.updatedAt = Instant.now()
        return repository.save(entity).toModel()
    }

    override fun insert(
        id: String,
        tenantId: String,
        name: String,
        type: String,
        domain: String?,
        stockCode: String?,
        aliases: String?,
        origin: String?,
    ): Organization {
        // PostgreSQL JDBC 는 java.time.Instant 타입 추론을 못 하므로 Timestamp 로 래핑한다 (H2 는 허용).
        val now = java.sql.Timestamp.from(Instant.now())
        // raw INSERT 로 V134 신규 컬럼을 포함해 한 번에 저장한다.
        jdbc.update(
            """INSERT INTO organizations
               (id, tenant_id, name, type, domain, stock_code, aliases, origin, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            id, tenantId, name, type, domain, stockCode, aliases, origin, now, now,
        )
        return findById(id) ?: throw NotFoundException("Organization not found after insert: $id")
    }

    override fun findByTenantAndStockCode(tenantId: String, stockCode: String): Organization? {
        // stock_code 인덱스를 활용한 단건 조회.
        val results = repository.findAllByTenantIdOrderByNameAsc(tenantId)
            .filter { it.stockCode == stockCode }
        return results.firstOrNull()?.toModel()
    }

    override fun updateStockCode(id: String, stockCode: String): Organization {
        val entity = repository.findById(id).orElseThrow {
            NotFoundException("Organization not found: $id")
        }
        // stockCode 만 갱신하고 updated_at 을 현재 시각으로 설정한다.
        entity.stockCode = stockCode
        entity.updatedAt = Instant.now()
        return repository.save(entity).toModel()
    }

    override fun countCategoryLinksByOrganizationIds(orgIds: List<String>): Map<String, Int> {
        // 빈 입력은 DB 쿼리 없이 즉시 반환 — IN () 절은 일부 DB 에서 문법 오류.
        if (orgIds.isEmpty()) return emptyMap()
        val rows = linkRepository.countByOrganizationIds(orgIds)
        // COUNT 결과는 H2 MODE=PostgreSQL 에서 Integer, 실제 PostgreSQL 에서 Long 으로 반환된다.
        // Number 로 캐스팅해야 양쪽 DB 에서 ClassCastException 을 피할 수 있다 (JpaUserClippingRequestStore 동일 패턴).
        return rows.mapNotNull { row ->
            val organizationId = row[0] as? String ?: return@mapNotNull null
            val count = (row[1] as? Number)?.toInt() ?: return@mapNotNull null
            organizationId to count
        }.toMap()
    }

    override fun delete(id: String) {
        // DB 의 ON DELETE CASCADE 가 category_organizations 를 정리한다.
        repository.deleteById(id)
    }

    override fun findByCategoryId(categoryId: String): List<Organization> {
        val links = linkRepository.findAllByCategoryId(categoryId)
        if (links.isEmpty()) return emptyList()
        val ids = links.map { it.organizationId }
        return repository.findAllById(ids)
            .map { it.toModel() }
            .sortedBy { it.name }
    }

    @Transactional
    override fun setCategoryOrganizations(categoryId: String, organizationIds: List<String>) {
        // 기존 링크를 모두 제거하고 요청된 id 집합으로 재설정한다.
        linkRepository.deleteAllByCategoryId(categoryId)
        // 중복 제거 + 비어있으면 바로 종료.
        val unique = organizationIds.distinct().filter { it.isNotBlank() }
        if (unique.isEmpty()) return
        val now = Instant.now()
        val entities = unique.map { orgId ->
            CategoryOrganizationEntity(
                categoryId = categoryId,
                organizationId = orgId,
                tenantId = DEFAULT_TENANT,
                createdAt = now,
            )
        }
        linkRepository.saveAll(entities)
    }

    private fun OrganizationEntity.toModel(): Organization {
        // aliases JSON 파싱 — null 이거나 파싱 실패 시 빈 리스트로 안전하게 처리한다.
        val parsedAliases: List<String> = if (aliases.isNullOrBlank()) emptyList()
        else runCatching {
            objectMapper.readValue(aliases, object : TypeReference<List<String>>() {})
        }.getOrDefault(emptyList())

        return Organization(
            id = id,
            tenantId = tenantId,
            name = name,
            // DB CHECK 제약으로 허용값이 강제되므로 valueOf 안전. 이탈 값은 fallback 으로 OTHER.
            type = runCatching { OrganizationType.valueOf(type) }.getOrDefault(OrganizationType.OTHER),
            domain = domain,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt,
            stockCode = stockCode,
            aliases = parsedAliases,
            origin = origin,
        )
    }

    private companion object {
        const val DEFAULT_TENANT = "default"
    }
}
