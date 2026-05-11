package com.ohmyclipping.store

import com.ohmyclipping.model.KeywordEntity

/**
 * 키워드 엔티티 분류 저장소 인터페이스.
 * 키워드를 PERSON, ORG, TECH, TOPIC, LOCATION 등으로 분류하여 관리한다.
 */
interface KeywordEntityStore {
    /** 전체 키워드 엔티티를 조회한다. */
    fun findAll(): List<KeywordEntity>

    /** 특정 카테고리의 키워드 엔티티를 조회한다. */
    fun findByCategory(category: String): List<KeywordEntity>

    /** 키워드 목록으로 엔티티 분류를 조회한다. */
    fun findByKeywords(keywords: List<String>): List<KeywordEntity>

    /** 키워드 엔티티를 등록하거나 갱신한다. */
    fun upsert(keyword: String, category: String): KeywordEntity

    /** 여러 키워드 엔티티를 일괄 등록/갱신한다. 처리된 건수를 반환한다. */
    fun upsertBatch(entries: List<Pair<String, String>>): Int
}
