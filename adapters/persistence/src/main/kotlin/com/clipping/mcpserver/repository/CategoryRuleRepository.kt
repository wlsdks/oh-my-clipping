package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.CategoryRuleEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * 카테고리별 스크리닝/발송 규칙 JPA 리포지토리.
 * clipping_category_rules 테이블에 대한 CRUD 및 조회 메서드를 제공한다.
 * PK는 categoryId이다.
 */
interface CategoryRuleRepository : JpaRepository<CategoryRuleEntity, String> {

    /** 카테고리 ID로 규칙을 조회한다. findById와 동일하나 명시적 메서드. */
    fun findByCategoryId(categoryId: String): CategoryRuleEntity?

    /** 개별 발송 스케줄(delivery_preset)이 설정된 카테고리 ID 목록을 반환한다. */
    @Query("SELECT r.categoryId FROM CategoryRuleEntity r WHERE r.deliveryPreset IS NOT NULL")
    fun findCategoryIdsWithCustomSchedule(): Set<String>
}
