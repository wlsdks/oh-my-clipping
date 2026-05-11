package com.clipping.mcpserver.mcp

import com.clipping.mcpserver.service.SlackSocketModeService
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

/**
 * clipping.mcp.server.enabled=false(기본값) 상태에서
 * McpServerConfig가 생성하는 clippingToolCallbackProvider 빈이 없음을 검증한다.
 * SlackSocketModeService 등 MCP와 무관한 빈은 정상 존재해야 한다.
 */
@SpringBootTest(properties = ["clipping.mcp.server.enabled=false"])
@ActiveProfiles("test")
class McpServerConfigDisabledTest {

    @Autowired
    lateinit var ctx: ApplicationContext

    @Test
    fun `MCP disabled이면 clippingToolCallbackProvider 빈이 없다`() {
        // McpServerConfig의 clippingToolCallbackProvider만 게이트 대상이다.
        ctx.containsBean("clippingToolCallbackProvider").shouldBeFalse()
    }

    @Test
    fun `MCP disabled여도 SlackSocketModeService 빈은 존재`() {
        ctx.getBeansOfType(SlackSocketModeService::class.java).size shouldNotBe 0
    }
}
