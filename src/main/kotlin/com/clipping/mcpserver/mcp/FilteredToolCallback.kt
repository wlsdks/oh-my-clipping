package com.clipping.mcpserver.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.DefaultToolDefinition
import org.springframework.ai.tool.definition.ToolDefinition
import org.springframework.ai.tool.metadata.ToolMetadata

private val log = KotlinLogging.logger {}

/**
 * Spring AI [ToolCallback] 래퍼 — `tools/list` 스키마에서
 * 언더스코어(`_`) prefix가 붙은 시스템 주입용 파라미터를 숨긴다.
 *
 * arc-reactor orchestrator가 `_onBehalfOfSlackUserId` 등 컨텍스트를
 * 런타임에 주입하더라도, LLM에 노출되는 스키마에는 포함되지 않도록 하여
 * LLM이 임의 값을 채워 넣는 실수를 원천 차단한다.
 */
class FilteredToolCallback(
    private val delegate: ToolCallback,
    private val objectMapper: ObjectMapper,
    private val hiddenParamPrefix: String = "_",
) : ToolCallback {

    private val filteredDefinition: ToolDefinition by lazy {
        val original = delegate.toolDefinition
        val filteredSchemaJson = filterSchemaJson(original.inputSchema())
        DefaultToolDefinition.builder()
            .name(original.name())
            .description(original.description())
            .inputSchema(filteredSchemaJson)
            .build()
    }

    override fun getToolDefinition(): ToolDefinition = filteredDefinition

    override fun getToolMetadata(): ToolMetadata = delegate.toolMetadata

    override fun call(toolInput: String): String = delegate.call(toolInput)

    override fun call(toolInput: String, toolContext: ToolContext?): String =
        delegate.call(toolInput, toolContext)

    /**
     * JSON Schema 루트의 `properties`와 `required` 목록에서
     * [hiddenParamPrefix]로 시작하는 키를 제거한다.
     * 스키마 루트가 ObjectNode가 아니면 원본을 그대로 반환한다.
     *
     * required 배열에서 숨김 파라미터를 제거할 때는 WARN 로그를 남긴다 — 도구 작성자가
     * 실수로 `@ToolParam(required = true)` 를 붙인 `_foo` 를 LLM 에게 노출 안 하면서
     * 필수로 선언한 상태라는 뜻이라, orchestrator 주입이 누락되면 호출 시점에만
     * InvalidInputException 이 터져 원인 파악이 어렵다. 기동 시 로그로 미리 알린다.
     */
    private fun filterSchemaJson(schemaJson: String): String {
        val root = objectMapper.readTree(schemaJson) as? ObjectNode ?: return schemaJson
        val toolName = delegate.toolDefinition.name()

        // properties 맵에서 hidden prefix 키를 제거한다.
        (root.get("properties") as? ObjectNode)?.let { props ->
            val toRemove = props.properties().map { it.key }
                .filter { it.startsWith(hiddenParamPrefix) }
            toRemove.forEach { props.remove(it) }
        }

        // required 배열에서도 hidden prefix 이름을 제거한다.
        (root.get("required") as? ArrayNode)?.let { req ->
            val requiredHidden = req.map { it.asText() }.filter { it.startsWith(hiddenParamPrefix) }
            if (requiredHidden.isNotEmpty()) {
                // 도구 작성자는 숨김 파라미터를 required 로 선언하면 안 된다 — 스키마에서 빠지므로
                // LLM 이 채울 방법이 없어 실질적 "orchestrator 필수 주입" 이 된다. 의도한 것이어도
                // 가시성을 위해 WARN 으로 남긴다.
                log.warn {
                    "Tool '$toolName' has hidden required params $requiredHidden — " +
                        "schema will omit them; orchestrator must inject at call time or tool will reject."
                }
            }
            val filtered = req.filter { !it.asText().startsWith(hiddenParamPrefix) }
            req.removeAll()
            filtered.forEach { req.add(it) }
        }

        return objectMapper.writeValueAsString(root)
    }
}
