package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.Organization
import com.clipping.mcpserver.model.OrganizationType

/**
 * Organization 저장소 포트.
 *
 * Phase 3 PR2 에서 도입. Clean Architecture 포트 패턴을 따라 서비스 계층이 의존한다.
 * 현재 구현은 `JpaOrganizationStore` (`@Primary`).
 */
interface OrganizationStore {

    /** 목록 조회. 선택적으로 [type] 필터. tenant_id 는 항상 'default'. */
    fun findAll(type: OrganizationType? = null): List<Organization>

    /** ID로 단건 조회. 없으면 null. */
    fun findById(id: String): Organization?

    /** tenant 내 이름으로 단건 조회 (중복 검증용). */
    fun findByName(name: String): Organization?

    /**
     * tenant + name 으로 단건 조회한다.
     *
     * @return 일치하는 조직이 없으면 null.
     */
    fun findByTenantAndName(tenantId: String, name: String): Organization?

    /** 신규 저장. id 가 blank 이면 UUID 생성. */
    fun save(organization: Organization): Organization

    /** 기존 레코드 갱신. id 없으면 IllegalArgumentException. */
    fun update(organization: Organization): Organization

    /** 삭제. 연결된 category_organizations 는 ON DELETE CASCADE 로 정리됨. */
    fun delete(id: String)

    /** 특정 카테고리에 연결된 조직 목록 (name 오름차순). */
    fun findByCategoryId(categoryId: String): List<Organization>

    /**
     * 카테고리 ↔ 조직 링크를 완전 대체한다.
     *
     * 동작: 기존 링크 전체 삭제 후 [organizationIds] 에 대한 링크만 재생성.
     * 동일 호출에 중복 id 가 섞여 있어도 UNIQUE 로 인해 한 번만 저장된다.
     * [organizationIds] 가 비어 있으면 카테고리의 모든 링크를 해제한다.
     */
    fun setCategoryOrganizations(categoryId: String, organizationIds: List<String>)

    /**
     * 신규 레코드를 직접 삽입한다.
     *
     * stockCode/aliases/origin 을 포함하는 V134 확장 필드를 지원한다.
     * aliases 는 JSON 문자열로 직접 전달한다 (예: `"[\"SEC\",\"samsung\"]"`).
     * 기존 호출자와의 하위 호환을 위해 새 파라미터는 모두 null 기본값을 갖는다.
     *
     * @param id 레코드 식별자 (UUID 문자열)
     * @param tenantId 테넌트 (현재 항상 "default")
     * @param name 조직명
     * @param type 조직 유형 문자열 (COMPETITOR / CUSTOMER / PARTNER / OTHER)
     * @param domain 도메인 (nullable)
     * @param stockCode 한국 주식 종목 코드 (nullable, V134)
     * @param aliases JSON 문자열로 직렬화된 별칭 목록 (nullable, V134)
     * @param origin 생성 경로 (nullable, V134)
     * @return 저장된 [Organization]
     */
    fun insert(
        id: String,
        tenantId: String,
        name: String,
        type: String,
        domain: String? = null,
        stockCode: String? = null,
        aliases: String? = null,
        origin: String? = null,
    ): Organization

    /**
     * tenant 내에서 stockCode 로 단건 조회한다.
     *
     * @return 일치하는 조직이 없으면 null.
     */
    fun findByTenantAndStockCode(tenantId: String, stockCode: String): Organization?

    /**
     * 기존 레코드의 stockCode 를 갱신한다.
     *
     * @throws com.clipping.mcpserver.error.NotFoundException id 가 존재하지 않으면.
     */
    fun updateStockCode(id: String, stockCode: String): Organization

    /**
     * 조직 ID 목록에 대해 카테고리 링크 수를 한 번에 집계한다.
     *
     * 어드민 목록 페이지에서 N+1 쿼리 없이 각 조직의 usageCount 를 채우기 위해 사용한다.
     * 링크가 없는 조직 ID 는 결과 Map 에 포함되지 않는다.
     *
     * @param orgIds 조회할 조직 ID 목록. 빈 리스트 입력 시 emptyMap() 반환.
     * @return orgId → 링크된 카테고리 수 Map
     */
    fun countCategoryLinksByOrganizationIds(orgIds: List<String>): Map<String, Int>
}
