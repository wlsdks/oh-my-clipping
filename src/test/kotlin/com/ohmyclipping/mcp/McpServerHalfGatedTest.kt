package com.ohmyclipping.mcp

import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

/**
 * clipping.mcp.server.enabled=true 이고 spring.ai.mcp.server.enabled=false 인 "반쪽 게이트" 상태.
 * McpServerConfig는 clipping.mcp.server.enabled만 보므로 ToolCallbackProvider는 존재해야 한다.
 * Spring AI MCP 자동 설정이 꺼져도 우리 수동 빈 등록에는 영향이 없음을 검증한다.
 */
@SpringBootTest(
    properties = [
        "clipping.mcp.server.enabled=true",
        "spring.ai.mcp.server.enabled=false",
        "clipping.mcp.service-token=test-mcp-service-token-for-integration-tests-32chars",
    ],
)
@ActiveProfiles("test")
class McpServerHalfGatedTest {

    @Autowired
    lateinit var ctx: ApplicationContext

    @Test
    fun `커스텀 flag만 true여도 clippingToolCallbackProvider 존재`() {
        // McpServerConfig는 clipping.mcp.server.enabled만 보므로
        // Spring AI auto-config가 꺼져도 우리 수동 빈은 존재해야 한다.
        ctx.containsBean("clippingToolCallbackProvider") shouldNotBe false
    }
}
