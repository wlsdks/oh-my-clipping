package com.ohmyclipping.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 외부 조직(경쟁사/고객사/파트너) JPA 엔티티.
 * organizations 테이블(V125)에 매핑된다.
 */
@Entity
@Table(name = "organizations")
class OrganizationEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(name = "tenant_id", length = 36, nullable = false)
    var tenantId: String = "default",

    @Column(length = 200, nullable = false)
    var name: String = "",

    /** DB CHECK 제약: COMPETITOR / CUSTOMER / PARTNER / OTHER */
    @Column(length = 32, nullable = false)
    var type: String = "OTHER",

    @Column(length = 255)
    var domain: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    /** 한국 주식 종목 코드 (V134 추가). */
    @Column(name = "stock_code", length = 20)
    var stockCode: String? = null,

    /** 조직 별칭 목록, JSON TEXT 직렬화 (V134 추가). */
    @Column(columnDefinition = "TEXT")
    var aliases: String? = null,

    /** 조직 생성 경로 (V134 추가). */
    @Column(length = 32)
    var origin: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
