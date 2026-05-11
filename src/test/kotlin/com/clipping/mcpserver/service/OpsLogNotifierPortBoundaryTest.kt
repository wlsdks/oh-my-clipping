package com.clipping.mcpserver.service

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class OpsLogNotifierPortBoundaryTest {

    @Test
    fun `ops log notifier port should own its notification DTO contract`() {
        val portSource = Files.readString(
            Paths.get("clipping-app-ports/src/main/kotlin/com/clipping/mcpserver/service/port/OpsLogNotifier.kt")
        )
        val dtoSource = Files.readString(
            Paths.get("clipping-app-ports/src/main/kotlin/com/clipping/mcpserver/service/port/OpsLogDtos.kt")
        )
        val notificationEventSource = Files.readString(
            Paths.get("clipping-app-ports/src/main/kotlin/com/clipping/mcpserver/service/port/NotificationEvent.kt")
        )

        portSource shouldContain "interface OpsLogNotifier"
        portSource shouldNotContain "com.clipping.mcpserver.service.dto.ops"
        dtoSource shouldContain "data class PipelineRunOpsEvent"
        dtoSource shouldContain "data class DigestFailure"
        dtoSource shouldContain "data class DailyForecast"
        dtoSource shouldContain "data class WeeklyActionReport"
        notificationEventSource shouldContain "sealed interface NotificationEvent"
        notificationEventSource shouldContain "enum class OpsNotificationEvent"
        notificationEventSource shouldContain "enum class UserNotificationEvent"
        notificationEventSource shouldContain "enum class OpsRequestNotificationEvent"
        notificationEventSource shouldContain "enum class NotificationSeverity"
    }

    @Test
    fun `ops notification contract should not remain in root app module`() {
        Files.exists(Paths.get("src/main/kotlin/com/clipping/mcpserver/service/port/OpsLogNotifier.kt")) shouldBe false
        Files.exists(Paths.get("src/main/kotlin/com/clipping/mcpserver/service/port/OpsLogDtos.kt")) shouldBe false
        Files.exists(Paths.get("src/main/kotlin/com/clipping/mcpserver/service/NotificationEvent.kt")) shouldBe false
        Files.exists(Paths.get("src/main/kotlin/com/clipping/mcpserver/service/port/NotificationEvent.kt")) shouldBe false
    }

    @Test
    fun `ops log notifier adapter should not use legacy service dto ops package`() {
        val source = Files.readString(
            Paths.get("src/main/kotlin/com/clipping/mcpserver/adapter/out/slack/SlackOpsLogNotifier.kt")
        )

        source shouldContain "OpsLogNotifier"
        source shouldNotContain "com.clipping.mcpserver.service.dto.ops"
    }
}
