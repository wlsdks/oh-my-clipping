package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.KeywordEntityEntity
import org.springframework.data.jpa.repository.JpaRepository

interface KeywordEntityRepository : JpaRepository<KeywordEntityEntity, String> {
    fun findByCategory(category: String): List<KeywordEntityEntity>
    fun findByKeywordIn(keywords: List<String>): List<KeywordEntityEntity>
    fun findByKeyword(keyword: String): KeywordEntityEntity?
}
