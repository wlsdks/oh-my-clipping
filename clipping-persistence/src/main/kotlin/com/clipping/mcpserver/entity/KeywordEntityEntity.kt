package com.clipping.mcpserver.entity

import jakarta.persistence.*
import java.time.Instant

/**
 * 키워드 엔티티(NER 추출 키워드 분류) 엔티티.
 * keyword_entities 테이블에 매핑된다.
 */
@Entity
@Table(name = "keyword_entities")
class KeywordEntityEntity(
    @Id
    @Column(length = 36)
    val id: String = "",

    @Column(length = 100, nullable = false, unique = true)
    var keyword: String = "",

    @Column(length = 20, nullable = false)
    var category: String = "",

    @Column(name = "first_seen", nullable = false)
    val firstSeen: Instant = Instant.now()
)
