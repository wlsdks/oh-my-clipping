package com.clipping.mcpserver.mcp

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpServerSession
import io.modelcontextprotocol.spec.McpServerTransportProvider
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

/**
 * [McpServerConfig.annotationInjectingMcpSyncServerCustomizer] 동작 단위 테스트.
 *
 * Spring AI 가 채워 넣은 SyncToolSpecification 리스트(annotations=null) 에 대해
 * customizer 를 직접 실행하여 `McpToolAnnotations.BY_NAME` 매핑대로 annotations
 * 필드가 덮어씌워지는지 검증한다.
 */
class AnnotationInjectingCustomizerTest {

    private val config = McpServerConfig()

    @Test
    fun `매핑된 도구에 annotations 가 주입되고 callHandler 는 보존된다`() {
        val spec = McpServer.sync(NoopTransportProvider())
        val readOnlyName = "user_list_categories"
        val destructiveName = "admin_send_digest"
        val unknownName = "unknown_tool_xxx"

        val originalReadOnly = makeSpec(readOnlyName)
        val originalDestructive = makeSpec(destructiveName)
        val originalUnknown = makeSpec(unknownName)

        spec.tools(listOf(originalReadOnly, originalDestructive, originalUnknown))

        config.annotationInjectingMcpSyncServerCustomizer().customize(spec)

        val tools = readToolsField(spec)
        val byName = tools.associateBy { it.tool().name() }

        // read-only 도구: annotations 가 덮어씌워짐, callHandler 는 동일 참조로 유지
        byName[readOnlyName]!!.tool().annotations() shouldNotBe null
        byName[readOnlyName]!!.tool().annotations().readOnlyHint() shouldBe true
        byName[readOnlyName]!!.callHandler() shouldBe originalReadOnly.callHandler()

        // destructive 도구
        byName[destructiveName]!!.tool().annotations().destructiveHint() shouldBe true
        byName[destructiveName]!!.tool().annotations().idempotentHint() shouldBe false

        // 매핑되지 않은 이름은 annotations 가 여전히 null
        byName[unknownName]!!.tool().annotations() shouldBe null
    }

    private fun makeSpec(name: String): McpServerFeatures.SyncToolSpecification {
        val tool = McpSchema.Tool.builder()
            .name(name)
            .description("desc of $name")
            .inputSchema(
                McpSchema.JsonSchema(
                    "object",
                    emptyMap<String, Any>(),
                    emptyList<String>(),
                    false,
                    null,
                    null,
                ),
            )
            .build()
        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler { _, _ -> McpSchema.CallToolResult(emptyList(), false, null, null) }
            .build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun readToolsField(
        spec: McpServer.SyncSpecification<*>,
    ): MutableList<McpServerFeatures.SyncToolSpecification> {
        val f = McpServer.SyncSpecification::class.java.getDeclaredField("tools")
        f.isAccessible = true
        return f.get(spec) as MutableList<McpServerFeatures.SyncToolSpecification>
    }

    /** 테스트용 no-op transport — 실제 I/O 없이 SyncSpecification 을 얻기 위해 사용. */
    private class NoopTransportProvider : McpServerTransportProvider {
        override fun setSessionFactory(sessionFactory: McpServerSession.Factory) = Unit
        override fun notifyClients(method: String, params: Any?): Mono<Void> = Mono.empty()
        override fun closeGracefully(): Mono<Void> = Mono.empty()
    }
}
