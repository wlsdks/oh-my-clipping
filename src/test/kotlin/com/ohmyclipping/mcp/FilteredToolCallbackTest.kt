package com.ohmyclipping.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.DefaultToolDefinition

/**
 * [FilteredToolCallback] 단위 테스트.
 * tools/list 스키마에서 언더스코어 prefix 파라미터가 제거되는지,
 * delegate 호출은 그대로 위임되는지를 검증한다.
 */
class FilteredToolCallbackTest {

    private val objectMapper = ObjectMapper()

    private val slf4jLogger = LoggerFactory.getLogger(FilteredToolCallback::class.java) as LogbackLogger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.list.clear()
        appender.start()
        slf4jLogger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        slf4jLogger.detachAppender(appender)
    }

    @Test
    fun `underscore prefix properties and required entries are removed from schema`() {
        val originalSchema = """
            {
              "type": "object",
              "properties": {
                "category": {"type": "string"},
                "_onBehalfOfSlackUserId": {"type": "string"},
                "limit": {"type": "integer"}
              },
              "required": ["category", "_onBehalfOfSlackUserId"]
            }
        """.trimIndent()

        val delegate = mockk<ToolCallback>()
        every { delegate.toolDefinition } returns DefaultToolDefinition.builder()
            .name("demo_tool")
            .description("demo")
            .inputSchema(originalSchema)
            .build()

        val wrapper = FilteredToolCallback(delegate, objectMapper)
        val filteredSchema = wrapper.toolDefinition.inputSchema()

        filteredSchema shouldContain "\"category\""
        filteredSchema shouldContain "\"limit\""
        filteredSchema shouldNotContain "_onBehalfOfSlackUserId"

        // required 배열에서도 제거되어야 한다.
        val required = objectMapper.readTree(filteredSchema).get("required")
        required.map { it.asText() }.toSet() shouldBe setOf("category")
    }

    @Test
    fun `required 배열에서 숨김 파라미터를 제거할 때 WARN 로그가 남는다`() {
        val originalSchema = """
            {
              "type": "object",
              "properties": {
                "category": {"type": "string"},
                "_onBehalfOfUserId": {"type": "string"}
              },
              "required": ["category", "_onBehalfOfUserId"]
            }
        """.trimIndent()

        val delegate = mockk<ToolCallback>()
        every { delegate.toolDefinition } returns DefaultToolDefinition.builder()
            .name("user_toggle_bookmark")
            .description("demo")
            .inputSchema(originalSchema)
            .build()

        // 스키마 필터링 트리거: toolDefinition 의 lazy 로직이 돌아가야 한다.
        val wrapper = FilteredToolCallback(delegate, objectMapper)
        wrapper.toolDefinition.inputSchema()

        val warnEvent = appender.list.firstOrNull { it.level == Level.WARN }
        warnEvent shouldNotBe null
        // 구체적 도구명 + 숨긴 required 이름이 로그에 포함되어야 한다.
        warnEvent!!.formattedMessage shouldContain "user_toggle_bookmark"
        warnEvent.formattedMessage shouldContain "_onBehalfOfUserId"
    }

    @Test
    fun `nested object schema also removes hidden properties and required entries`() {
        val originalSchema = """
            {
              "type": "object",
              "properties": {
                "payload": {
                  "type": "object",
                  "properties": {
                    "summaryId": {"type": "string"},
                    "_onBehalfOfUserId": {"type": "string"}
                  },
                  "required": ["summaryId", "_onBehalfOfUserId"]
                }
              },
              "required": ["payload"]
            }
        """.trimIndent()

        val delegate = mockk<ToolCallback>()
        every { delegate.toolDefinition } returns DefaultToolDefinition.builder()
            .name("nested_user_tool")
            .description("demo")
            .inputSchema(originalSchema)
            .build()

        val wrapper = FilteredToolCallback(delegate, objectMapper)
        val filteredSchema = wrapper.toolDefinition.inputSchema()
        val root = objectMapper.readTree(filteredSchema)
        val payload = root.get("properties").get("payload")

        filteredSchema shouldContain "summaryId"
        filteredSchema shouldNotContain "_onBehalfOfUserId"
        payload.get("required").map { it.asText() }.toSet() shouldBe setOf("summaryId")

        val warnEvent = appender.list.firstOrNull { it.level == Level.WARN }
        warnEvent shouldNotBe null
        warnEvent!!.formattedMessage shouldContain "nested_user_tool"
        warnEvent.formattedMessage shouldContain "_onBehalfOfUserId"
    }

    @Test
    fun `숨김 파라미터가 required 에 없으면 WARN 로그가 남지 않는다`() {
        val originalSchema = """
            {
              "type": "object",
              "properties": {
                "category": {"type": "string"},
                "_onBehalfOfUserId": {"type": "string"}
              },
              "required": ["category"]
            }
        """.trimIndent()

        val delegate = mockk<ToolCallback>()
        every { delegate.toolDefinition } returns DefaultToolDefinition.builder()
            .name("user_list_bookmarks")
            .description("demo")
            .inputSchema(originalSchema)
            .build()

        val wrapper = FilteredToolCallback(delegate, objectMapper)
        wrapper.toolDefinition.inputSchema()

        appender.list.none { it.level == Level.WARN } shouldBe true
    }

    @Test
    fun `call delegates to underlying callback unchanged`() {
        val delegate = mockk<ToolCallback>()
        every { delegate.toolDefinition } returns DefaultToolDefinition.builder()
            .name("demo_tool")
            .description("demo")
            .inputSchema("{\"type\":\"object\",\"properties\":{}}")
            .build()
        every { delegate.call("{}") } returns "ok"

        val wrapper = FilteredToolCallback(delegate, objectMapper)
        wrapper.call("{}") shouldBe "ok"
    }
}
