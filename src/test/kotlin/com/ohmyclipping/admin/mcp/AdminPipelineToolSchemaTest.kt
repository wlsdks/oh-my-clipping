package com.ohmyclipping.admin.mcp

import com.ohmyclipping.mcp.FilteredToolCallback
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.service.AdminClippingService
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.tool.method.MethodToolCallbackProvider

/**
 * admin_pipeline 도구의 tools/list 스키마 검증.
 *
 * Ralph 루프 override 파라미터(`_ralphLoop*`)는 `FilteredToolCallback` 이
 * tools/list 응답 생성 시 `_` prefix 를 제거해야 한다. LLM 이 내부 override
 * 값을 임의로 채우지 못하도록 경계선을 확인한다.
 */
class AdminPipelineToolSchemaTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `admin_pipeline schema omits _ralphLoop parameters`() {
        val tool = AdminPipelineTool(
            adminClippingService = mockk<AdminClippingService>(relaxed = true),
            rateLimiter = mockk<McpRateLimiter>(relaxed = true),
        )
        // MethodToolCallback 을 만들어 원본 스키마를 확보하고,
        // FilteredToolCallback 로 감쌌을 때 `_` prefix 가 제거되는지 검증한다.
        val provider = MethodToolCallbackProvider.builder()
            .toolObjects(tool)
            .build()
        val original = provider.toolCallbacks.single { it.toolDefinition.name() == "admin_pipeline" }

        val originalSchema = original.toolDefinition.inputSchema()
        // 원본 스키마에는 `_ralphLoopEnabled` 등이 존재함을 확인해 FilteredToolCallback 이
        // 실제로 필터를 수행하는지에 대한 대조군을 만든다.
        originalSchema shouldContain "_ralphLoopEnabled"

        val wrapped = FilteredToolCallback(original, objectMapper)
        val filteredSchema = wrapped.toolDefinition.inputSchema()

        // 필터 적용 후에는 `_ralphLoop*` 가 보이지 않아야 한다.
        filteredSchema shouldNotContain "_ralphLoopEnabled"
        filteredSchema shouldNotContain "_ralphLoopMaxIterations"
        filteredSchema shouldNotContain "_ralphLoopStopPhrase"
        // 반면 일반 파라미터는 유지된다.
        filteredSchema shouldContain "categoryId"
        filteredSchema shouldContain "hoursBack"
        filteredSchema shouldContain "maxItems"
        filteredSchema shouldContain "unsentOnly"

        // required 배열에서도 `_` prefix 가 제거되어야 한다.
        val required = objectMapper.readTree(filteredSchema).get("required")
        val names = required?.map { it.asText() }?.toSet() ?: emptySet()
        names.any { it.startsWith("_") } shouldBe false
    }
}
