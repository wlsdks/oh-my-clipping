package com.ohmyclipping.model

import java.time.Instant

/**
 * 외부 조직(경쟁사/고객사/파트너 등) 분류.
 * DB `organizations.type` CHECK 제약과 일치한다.
 */
enum class OrganizationType {
    COMPETITOR,
    CUSTOMER,
    PARTNER,
    OTHER
}

/**
 * 외부 조직 엔티티.
 *
 * Phase 3 PR2 에서 도입된 도메인. Category 와 many-to-many 로 연결하여
 * "경쟁사 A 관련 카테고리 클릭률" 같은 분석에 사용한다.
 *
 * @property tenantId 다중 테넌트 대비 placeholder. 현재는 항상 `"default"`.
 */
data class Organization(
    val id: String,
    val tenantId: String = "default",
    val name: String,
    val type: OrganizationType,
    val domain: String? = null,
    val description: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    /** 종목 코드 (예: 999930). V134 에서 추가. */
    val stockCode: String? = null,
    /** 조직 별칭 목록. DB 에는 JSON TEXT 로 저장. V134 에서 추가. */
    val aliases: List<String> = emptyList(),
    /** 조직 생성 경로 (user_wizard / admin_created / competitor_mirror / backfill / legacy). V134 에서 추가. */
    val origin: String? = null,
)
