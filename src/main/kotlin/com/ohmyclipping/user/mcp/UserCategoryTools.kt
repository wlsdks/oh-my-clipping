package com.ohmyclipping.user.mcp

import com.ohmyclipping.mcp.dto.CategoryView
import com.ohmyclipping.mcp.dto.SourceView
import com.ohmyclipping.mcp.mcpToolCall
import com.ohmyclipping.service.CategoryService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 사용자 대상 MCP 도구 — 카테고리 및 소스 조회.
 *
 * arc-reactor orchestrator 또는 외부 MCP 클라이언트가 사용하는 진입점이다.
 * 응답에서 제외되는 항목:
 *  - 내부 카테고리 (id 가 `__` 로 시작): 시스템/내부 용도.
 *  - 비공개 카테고리 (`isPublic = false`): 특정 조직/팀 전용이거나 운영 전용 등.
 */
@Component
@ConditionalOnProperty(name = ["clipping.mcp.server.enabled"], havingValue = "true")
class UserCategoryTools(
    private val categoryService: CategoryService,
) {

    @Tool(
        description = """
        사용자에게 노출 가능한 클리핑 카테고리를 ID 와 이름과 함께 반환한다.
        **언제 쓰나:** 사용자가 "어떤 카테고리가 있지", "뭐 있어" 를 물어볼 때, 또는 카테고리가 필요한 다른 도구를 호출하기 전.
        **쓰지 말 것:** 사용자가 이미 카테고리 이름을 알려준 경우 — user_list_sources 나 user_list_recent_summaries 를 바로 호출.
        **반환:** id, name, description 이 담긴 CategoryView 리스트.

        <examples>
        <example>
        user: "clipping 에 어떤 카테고리 있어?"
        call: user_list_categories()
        </example>
        </examples>

        [category: list]
        """,
    )
    fun user_list_categories(): String = mcpToolCall {
        // 내부(`__` prefix) 카테고리와 비공개(isPublic=false) 카테고리를 모두 제외한다.
        // 비공개 카테고리는 조직 전용 등 LLM/외부 클라이언트에 노출되면 안 되는 컨텐츠를 담을 수 있다.
        categoryService.listCategories()
            .filter { !it.id.startsWith("__") && it.isPublic }
            .map { CategoryView.from(it) }
    }

    @Tool(
        description = """
        단일 카테고리에 속한 RSS/뉴스 소스 목록을 반환한다.
        **언제 쓰나:** 사용자가 "어느 매체", "어디에서", "which sources" 등 소스 목록을 물어볼 때.
        **쓰지 말 것:** 사용자가 뉴스 내용을 요구할 때 — user_list_recent_summaries 를 사용.
        **파라미터:** category = 카테고리 ID 또는 이름 (부분 일치 허용).
        **반환:** SourceView 리스트.

        <examples>
        <example>
        user: "경제 카테고리 소스 어디에서 가져와?"
        call: user_list_sources(category="경제")
        </example>
        </examples>

        [category: list]
        """,
    )
    fun user_list_sources(
        @ToolParam(description = "카테고리 ID 또는 이름") category: String,
    ): String = mcpToolCall {
        // 카테고리 ID/이름 해석 후 활성 소스만 노출한다.
        val cat = categoryService.resolveCategory(category)
        categoryService.listSources(cat.id)
            .filter { it.isActive }
            .map { SourceView.from(it) }
    }
}
