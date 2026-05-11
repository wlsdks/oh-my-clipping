package com.ohmyclipping.mcp

import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

/**
 * clipping.mcp.server.enabled=true 일 때
 * ToolCallbackProvider 빈이 정상 등록됨을 검증한다.
 */
@SpringBootTest(
    properties = [
        "clipping.mcp.server.enabled=true",
        "spring.ai.mcp.server.enabled=true",
        "clipping.mcp.service-token=test-mcp-service-token-for-integration-tests-32chars",
    ],
)
@ActiveProfiles("test")
class McpServerConfigEnabledTest {

    @Autowired
    lateinit var ctx: ApplicationContext

    @Test
    fun `MCP enabled면 clippingToolCallbackProvider 빈이 존재`() {
        // McpServerConfig의 clippingToolCallbackProvider가 등록되어야 한다.
        ctx.containsBean("clippingToolCallbackProvider").shouldBeTrue()
    }
}
