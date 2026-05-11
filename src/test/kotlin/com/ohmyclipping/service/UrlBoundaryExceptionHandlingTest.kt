package com.ohmyclipping.service

import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class UrlBoundaryExceptionHandlingTest {

    @Test
    fun `URL and RSS boundary helpers should not use broad exception catches`() {
        val paths = listOf(
            "modules/source/src/main/kotlin/com/ohmyclipping/service/source/DomainExtractor.kt",
            "modules/source/src/main/kotlin/com/ohmyclipping/service/source/RssFeedDiscoveryService.kt",
            "src/main/kotlin/com/ohmyclipping/rss/HttpRobotsPolicyClient.kt",
            "src/main/kotlin/com/ohmyclipping/rss/HttpSourceVerificationClient.kt"
        )

        for (path in paths) {
            val source = Files.readString(Path.of(path))

            source shouldNotContain "catch (e: Exception)"
            source shouldNotContain "catch (_: Exception)"
        }
    }
}
