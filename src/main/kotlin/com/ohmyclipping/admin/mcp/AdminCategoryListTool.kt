package com.ohmyclipping.admin.mcp

import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.dto.clipping.CategoryListResult
import com.ohmyclipping.service.CategoryService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

/**
 * Admin 도구 — 전체 카테고리 목록 조회.
 *
 * 내부 카테고리(`__` 접두어 포함)도 포함해 운영자가 전체 상태를 확인할 수 있다.
 */
@Component
class AdminCategoryListTool(private val categoryService: CategoryService) {

    @Tool(
        description = """
            전체 클리핑 카테고리를 소스 개수와 함께 반환한다 (내부 카테고리 포함, 운영자 뷰).
            **언제 쓰나:** 운영자가 "어떤 카테고리가 있지"를 물어볼 때, 또는 다른 admin_* 도구에서 쓸 카테고리 ID가 필요할 때.
            **쓰지 말 것:** 일반 사용자 요청인 경우 — 내부 카테고리를 숨기는 user_list_categories 를 사용.
            **반환:** id, name, sourceCount 가 포함된 CategoryListResult.
        """,
    )
    fun admin_list_categories(): String = mcpToolCall {
        CategoryListResult(categories = categoryService.listCategories())
    }
}
