package com.ohmyclipping.service

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class SlackDeliveryPortBoundaryTest {

    @Test
    fun `slack delivery port should expose delivery operations only`() {
        val source = Files.readString(
            Paths.get("clipping-engine/src/main/kotlin/com/ohmyclipping/service/port/SlackDeliveryPort.kt")
        )

        source shouldNotContain "interface SlackDeliveryPort : SlackMessageSender"
        source shouldContain "data class SlackDeliveryResult"
        source shouldContain "data class SlackDeliveryMetadata"
        source shouldContain "enum class SlackDeliveryColor"
        source shouldNotContain "SlackMessageSender.SendResult"
        source shouldNotContain "SlackMetadata"
        source shouldNotContain "SlackStatusColor"
        source shouldNotContain "adapter.out.slack"
        source shouldContain "fun sendMessage("
        source shouldContain "fun updateMessage("
        source shouldNotContain "fun testConnection("
        source shouldNotContain "fun listChannels("
        source shouldNotContain "fun getChannelInfo("
        source shouldNotContain "fun getChannelMembers("
        source shouldNotContain "fun openDmChannel("
    }

    @Test
    fun `slack message sender contract should not depend on slack adapter types`() {
        val source = Files.readString(
            Paths.get("src/main/kotlin/com/ohmyclipping/service/SlackMessageSender.kt")
        )

        source shouldContain "SlackDeliveryColor"
        source shouldNotContain "adapter.out.slack"
        source shouldNotContain "SlackStatusColor"
    }

    @Test
    fun `batch and notification senders should depend on slack delivery port`() {
        val sourceRoot = Paths.get("src/main/kotlin/com/ohmyclipping")
        val rootDeliveryCallers = listOf(
            "service/digest/DigestService.kt",
            "service/digest/SlackDigestWorker.kt",
            "service/competitor/CompetitorWeeklyDigestScheduler.kt",
            "service/WeeklySummaryScheduler.kt",
            "service/AutoReportScheduler.kt",
            "service/pipeline/PipelineLogService.kt",
            "observability/SchedulerErrorNotifier.kt",
            "observability/ErrorSlackNotifier.kt",
            "adapter/out/slack/SlackOpsLogNotifier.kt",
        )

        rootDeliveryCallers.forEach { relativePath ->
            val source = Files.readString(sourceRoot.resolve(relativePath))

            source shouldContain "SlackDeliveryPort"
            source shouldNotContain "private val slackMessageSender: SlackMessageSender"
            source shouldNotContain "private val slackSender: SlackMessageSender"
            source shouldNotContain ".testConnection("
            source shouldNotContain ".listChannels("
            source shouldNotContain ".getChannelInfo("
            source shouldNotContain ".getChannelMembers("
            source shouldNotContain ".openDmChannel("
        }

        val notificationSource = Files.readString(
            Paths.get(
                "clipping-notification/src/main/kotlin/com/ohmyclipping/service/notification/OperationsNotificationService.kt"
            )
        )

        notificationSource shouldContain "SlackDeliveryPort"
        notificationSource shouldNotContain "private val slackMessageSender: SlackMessageSender"
        notificationSource shouldNotContain ".testConnection("
        notificationSource shouldNotContain ".listChannels("
        notificationSource shouldNotContain ".getChannelInfo("
        notificationSource shouldNotContain ".getChannelMembers("
        notificationSource shouldNotContain ".openDmChannel("
    }
}
