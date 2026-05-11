package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.KnownNewsSourceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface KnownNewsSourceRepository : JpaRepository<KnownNewsSourceEntity, String> {
    @Query(
        """SELECT e FROM KnownNewsSourceEntity e
           WHERE LOWER(e.name) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(e.aliases) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(e.domain) LIKE LOWER(CONCAT('%', :query, '%'))"""
    )
    fun search(query: String): List<KnownNewsSourceEntity>
}
