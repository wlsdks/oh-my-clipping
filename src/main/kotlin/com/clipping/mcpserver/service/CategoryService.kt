package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.service.dto.clipping.CategoryInfo
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.service.dto.clipping.SourceInfo
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RssSourceStore
import com.clipping.mcpserver.security.UrlSafetyValidator
import org.springframework.stereotype.Service

/**
 * 카테고리/소스의 일반 조회 및 등록 기능을 제공한다.
 */
@Service
class CategoryService(
    private val categoryStore: CategoryStore,
    private val sourceStore: RssSourceStore,
    private val urlSafetyValidator: UrlSafetyValidator
) {

    /**
     * 카테고리 목록을 소스 개수와 함께 조회한다.
     */
    fun listCategories(): List<CategoryInfo> {
        // 카테고리 목록을 먼저 읽고, 각 카테고리별 소스 개수를 매핑한다.
        val categories = categoryStore.list()
        val countMap = categoryStore.countSourcesByCategoryIds(categories.map { it.id })
        return categories.map { cat ->
            CategoryInfo(
                id = cat.id,
                name = cat.name,
                description = cat.description,
                slackChannelId = cat.slackChannelId,
                isActive = cat.isActive,
                sourceCount = countMap[cat.id] ?: 0,
                isPublic = cat.isPublic,
            )
        }
    }

    /**
     * 카테고리를 생성한다.
     */
    fun createCategory(
        name: String,
        description: String?,
        slackChannelId: String?,
        isPublic: Boolean = true
    ): Category {
        // 카테고리명 공백과 중복 이름을 저장 전에 차단한다.
        val normalizedName = name.trim()
        ensureValid(normalizedName.isNotBlank()) { "Category name is required" }
        val existing = categoryStore.findByName(normalizedName)
        ensureValid(existing == null) { "Category '$normalizedName' already exists" }
        return categoryStore.save(
            Category(
                id = "",
                name = normalizedName,
                description = description?.trim()?.ifBlank { null },
                slackChannelId = slackChannelId?.trim()?.ifBlank { null },
                isPublic = isPublic
            )
        )
    }

    /**
     * 카테고리 단건을 조회한다.
     */
    fun findById(id: String): Category? = categoryStore.findById(id)

    /**
     * ID 또는 이름(대소문자 무시)으로 카테고리를 찾는다.
     * 먼저 ID로 정확히 조회하고, 없으면 전체 목록에서 이름으로 탐색한다.
     *
     * @param term 카테고리 ID 또는 이름 (빈 문자열/공백 포함 시 NotFoundException 발생)
     * @throws NotFoundException 일치하는 카테고리가 없을 때
     */
    fun resolveCategory(term: String): Category {
        categoryStore.findById(term)?.let { return it }
        val all = categoryStore.list()
        return all.find { it.name.equals(term, ignoreCase = true) }
            ?: throw NotFoundException("Category not found")
    }

    /**
     * 소스 목록을 조회한다.
     */
    fun listSources(categoryId: String?): List<SourceInfo> {
        // categoryId가 있으면 필터 조회, 없으면 전체 조회를 수행한다.
        val sources = if (categoryId != null) {
            sourceStore.listByCategoryId(categoryId)
        } else {
            sourceStore.list()
        }
        return sources.map { src ->
            // 응답에는 categoryName을 함께 채워 관리 화면 가독성을 높인다.
            SourceInfo(
                id = src.id,
                name = src.name,
                url = src.url,
                emoji = src.emoji,
                isActive = src.isActive,
                sourceRegion = src.sourceRegion.name,
                categoryId = src.categoryId,
                categoryName = categoryStore.findById(src.categoryId)?.name
            )
        }
    }

    /**
     * 소스를 생성한다.
     */
    fun addSource(name: String, url: String, emoji: String?, categoryId: String): RssSource {
        // 대상 카테고리의 유효성부터 확인한다.
        val normalizedCategoryId = categoryId.trim()
        ensureValid(normalizedCategoryId.isNotBlank()) { "categoryId is required" }
        ensureValid(categoryStore.findById(normalizedCategoryId) != null) {
            "Category not found: $normalizedCategoryId"
        }
        // 소스명과 URL을 정규화/검증한 뒤 저장한다.
        val normalizedName = name.trim()
        ensureValid(normalizedName.isNotBlank()) { "Source name is required" }
        val safeUrl = urlSafetyValidator.validatePublicHttpUrl(url).toString()
        return sourceStore.save(
            RssSource(
                id = "",
                name = normalizedName,
                url = safeUrl,
                emoji = emoji?.trim()?.ifBlank { null },
                categoryId = normalizedCategoryId
            )
        )
    }

    /**
     * 소스를 삭제한다.
     */
    fun removeSource(sourceId: String): RssSource {
        // 삭제 전 조회로 404를 명확히 반환한다.
        val source = sourceStore.findById(sourceId)
            ?: throw NotFoundException("Source not found: $sourceId")
        sourceStore.delete(sourceId)
        return source
    }
}
