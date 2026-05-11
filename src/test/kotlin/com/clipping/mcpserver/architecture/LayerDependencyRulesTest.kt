package com.clipping.mcpserver.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.streams.asSequence

/**
 * 레이어 간 import 의존성 규칙을 검증해
 * Inbound -> Application -> Outbound 방향을 유지한다.
 */
class LayerDependencyRulesTest {

    private val sourceRoot: Path = Path.of("src/main/kotlin/com/clipping/mcpserver")

    @Test
    fun `admin layer should not depend on outbound adapters or persistence details`() {
        val violations = collectViolations(
            packagePath = "admin",
            forbiddenPatterns = inboundForbiddenImports()
        )
        assertNoViolation(violations)
    }

    @Test
    fun `user inbound layer should not depend on outbound adapters or persistence details`() {
        val violations = collectViolations(
            packagePath = "user",
            forbiddenPatterns = inboundForbiddenImports()
        )
        assertNoViolation(violations)
    }

    @Test
    fun `tool layer should not depend on outbound adapters or persistence details when present`() {
        val violations = collectViolations(
            packagePath = "tool",
            forbiddenPatterns = inboundForbiddenImports()
        )
        assertNoViolation(violations)
    }

    @Test
    fun `service layer should not import inbound adapters or persistence details`() {
        val violations = collectViolations(
            packagePath = "service",
            forbiddenPatterns = listOf(
                Regex("^import com\\.clipping\\.mcpserver\\.admin(\\.|$)"),
                Regex("^import com\\.clipping\\.mcpserver\\.tool(\\.|$)"),
                Regex("^import com\\.clipping\\.mcpserver\\.user(\\.|$)"),
                Regex("^import com\\.clipping\\.mcpserver\\.adapter\\.out(\\.|$)"),
                Regex("^import com\\.clipping\\.mcpserver\\.repository(\\.|$)"),
                Regex("^import com\\.clipping\\.mcpserver\\.entity(\\.|$)"),
                Regex("^import com\\.clipping\\.mcpserver\\.store\\.Jdbc")
            )
        )
        assertNoViolation(violations)
    }

    /**
     * Inbound adapter는 service만 조합하고 저장소/외부 어댑터 세부사항을 직접 참조하지 않는다.
     */
    private fun inboundForbiddenImports(): List<Regex> = listOf(
        Regex("^import com\\.clipping\\.mcpserver\\.store(\\.|$)"),
        Regex("^import com\\.clipping\\.mcpserver\\.repository(\\.|$)"),
        Regex("^import com\\.clipping\\.mcpserver\\.entity(\\.|$)"),
        Regex("^import com\\.clipping\\.mcpserver\\.rss(\\.|$)"),
        Regex("^import com\\.clipping\\.mcpserver\\.content(\\.|$)"),
        Regex("^import com\\.clipping\\.mcpserver\\.ai(\\.|$)"),
        Regex("^import com\\.clipping\\.mcpserver\\.adapter\\.out(\\.|$)")
    )

    private fun collectViolations(
        packagePath: String,
        forbiddenPatterns: List<Regex>
    ): List<String> {
        val packageRoot = sourceRoot.resolve(packagePath)
        if (!Files.exists(packageRoot)) return emptyList()

        return Files.walk(packageRoot).use { paths ->
            paths.asSequence()
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .flatMap { file ->
                    Files.readAllLines(file).asSequence().mapIndexedNotNull { index, line ->
                        val trimmed = line.trim()
                        if (!trimmed.startsWith("import ")) return@mapIndexedNotNull null
                        val isViolation = forbiddenPatterns.any { it.containsMatchIn(trimmed) }
                        if (!isViolation) return@mapIndexedNotNull null
                        "${file.invariantSeparatorsPathString}:${index + 1} -> $trimmed"
                    }
                }
                .toList()
        }
    }

    /**
     * 실패 시 규칙 위반 import를 모두 노출해 레이어 오염 지점을 빠르게 찾도록 한다.
     */
    private fun assertNoViolation(violations: List<String>) {
        assertTrue(
            violations.isEmpty(),
            "Layer dependency rule violations found:\n${violations.joinToString(separator = "\n")}"
        )
    }
}
