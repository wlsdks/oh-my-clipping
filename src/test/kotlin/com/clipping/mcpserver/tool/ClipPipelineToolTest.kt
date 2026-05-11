package com.clipping.mcpserver.tool

import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.RuntimeSetting
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RuntimeSettingStore
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ClipPipelineToolTest {

    @Autowired
    lateinit var toolCallbackProvider: ToolCallbackProvider

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var runtimeSettingStore: RuntimeSettingStore

    private lateinit var categoryId: String

    @BeforeEach
    fun setup() {
        runtimeSettingStore.deleteAll()
        runtimeSettingStore.saveAll(
            listOf(
                RuntimeSetting("ralph_orchestration_enabled", "true"),
                RuntimeSetting("ralph_loop_enabled", "true"),
                RuntimeSetting("ralph_loop_max_iterations", "4"),
                RuntimeSetting("ralph_loop_stop_phrase", "RALPH_STOP")
            )
        )
        categoryId = categoryStore.save(
            Category(
                id = "",
                name = "PipelineTool-${System.nanoTime()}"
            )
        ).id
    }

    @Test
    fun `admin_pipeline should apply loop overrides and return loop metadata`() {
        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_pipeline" }
        val result = tool.call(
            """
            {
              "categoryId":"$categoryId",
              "_ralphLoopEnabled":true,
              "_ralphLoopMaxIterations":5,
              "_ralphLoopStopPhrase":"다이제스트"
            }
            """.trimIndent()
        )

        result.shouldContain("\"orchestrationMode\":\"RALPH\"")
        result.shouldContain("\"loopEnabled\":true")
        result.shouldContain("\"loopIterationCount\":1")
        result.shouldContain("\"loopStopReason\":\"STOP_PHRASE_DETECTED\"")
        result.shouldContain("\"loopStopPhrase\":\"다이제스트\"")
        result.shouldContain("\"ITERATION_1_PLAN\"")
    }

    @Test
    fun `admin_pipeline should run single iteration when loop override is disabled`() {
        val tool = toolCallbackProvider.toolCallbacks.first { it.toolDefinition.name() == "admin_pipeline" }
        val result = tool.call(
            """
            {
              "categoryId":"$categoryId",
              "_ralphLoopEnabled":false
            }
            """.trimIndent()
        )

        result.shouldContain("\"orchestrationMode\":\"RALPH\"")
        result.shouldContain("\"loopEnabled\":false")
        result.shouldContain("\"loopIterationCount\":1")
        result.shouldContain("\"loopStopReason\":\"LOOP_DISABLED\"")
    }
}
