package com.ohmyclipping.store

import com.ohmyclipping.entity.KeywordEntityEntity
import com.ohmyclipping.model.KeywordEntity
import com.ohmyclipping.repository.KeywordEntityRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 키워드 엔티티 분류 JPA 구현. JdbcKeywordEntityStore를 대체한다.
 * JDBC 원본의 MERGE 문 대신 find-then-insert/update 패턴을 사용한다.
 */
@Repository
@Primary
class JpaKeywordEntityStore(
    private val repository: KeywordEntityRepository
) : KeywordEntityStore {

    override fun findAll(): List<KeywordEntity> =
        repository.findAll()
            .map { it.toModel() }
            .sortedByDescending { it.firstSeen }

    override fun findByCategory(category: String): List<KeywordEntity> =
        repository.findByCategory(category)
            .map { it.toModel() }
            .sortedByDescending { it.firstSeen }

    override fun findByKeywords(keywords: List<String>): List<KeywordEntity> {
        if (keywords.isEmpty()) return emptyList()
        return repository.findByKeywordIn(keywords).map { it.toModel() }
    }

    @Transactional
    override fun upsert(keyword: String, category: String): KeywordEntity {
        // 기존 키워드가 있으면 카테고리만 갱신한다.
        val existing = repository.findByKeyword(keyword)
        if (existing != null) {
            existing.category = category
            return repository.save(existing).toModel()
        }
        // 새 키워드를 생성한다.
        return repository.save(
            KeywordEntityEntity(
                id = UUID.randomUUID().toString(),
                keyword = keyword,
                category = category
            )
        ).toModel()
    }

    @Transactional
    override fun upsertBatch(entries: List<Pair<String, String>>): Int {
        if (entries.isEmpty()) return 0
        var count = 0
        for ((keyword, category) in entries) {
            // 개별 upsert를 수행한다.
            val existing = repository.findByKeyword(keyword)
            if (existing != null) {
                existing.category = category
                repository.save(existing)
            } else {
                repository.save(
                    KeywordEntityEntity(
                        id = UUID.randomUUID().toString(),
                        keyword = keyword,
                        category = category
                    )
                )
            }
            count++
        }
        return count
    }

    private fun KeywordEntityEntity.toModel() = KeywordEntity(
        id = id,
        keyword = keyword,
        category = category,
        firstSeen = firstSeen
    )
}
