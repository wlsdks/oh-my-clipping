package com.clipping.mcpserver.store

import com.clipping.mcpserver.entity.KnownNewsSourceEntity
import com.clipping.mcpserver.model.KnownNewsSource
import com.clipping.mcpserver.repository.KnownNewsSourceRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository

/**
 * 주요 뉴스사이트 매핑 JPA 구현. JdbcKnownNewsSourceStore를 대체한다.
 */
@Repository
@Primary
class JpaKnownNewsSourceStore(
    private val repository: KnownNewsSourceRepository
) : KnownNewsSourceStore {

    private val mapper = jacksonObjectMapper()

    override fun listAll(): List<KnownNewsSource> =
        repository.findAll(Sort.by("name")).map { it.toModel() }

    override fun search(query: String): List<KnownNewsSource> =
        repository.search(query).map { it.toModel() }

    private fun KnownNewsSourceEntity.toModel() = KnownNewsSource(
        id = id,
        name = name,
        aliases = runCatching { mapper.readValue<List<String>>(aliases) }.getOrDefault(emptyList()),
        domain = domain,
        rssUrl = rssUrl,
        region = region,
        createdAt = createdAt
    )
}
