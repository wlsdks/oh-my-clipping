package com.ohmyclipping.service

import com.ohmyclipping.service.digest.*

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain as shouldContainElement
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldNotContainIgnoringCase
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class PipelinePortBoundaryTest {

    @Test
    fun `pipeline orchestrators should depend on pipeline port instead of concrete clipping service`() {
        val serviceSourceRoot = Paths.get("src/main/kotlin/com/ohmyclipping/service")
        val orchestratorSources = listOf(
            "AdminClippingService.kt",
            "pipeline/RalphPipelineOrchestrator.kt",
            "pipeline/DeterministicPipelineRunner.kt"
        )

        orchestratorSources.forEach { fileName ->
            val source = Files.readString(serviceSourceRoot.resolve(fileName))

            source shouldContain "ClippingPipelinePort"
            source shouldNotContain "private val clippingService: ClippingService"
        }
    }

    @Test
    fun `pipeline port should live in engine module and expose independent pipeline DTOs`() {
        val appPortPath = Paths.get("src/main/kotlin/com/ohmyclipping/service/port/ClippingPipelinePort.kt")
        val enginePortPath =
            Paths.get("modules/digest-policy/src/main/kotlin/com/ohmyclipping/service/port/ClippingPipelinePort.kt")
        val portSource = Files.readString(enginePortPath)

        Files.exists(appPortPath) shouldBe false
        portSource shouldNotContain "com.ohmyclipping.model"
        portSource shouldNotContain "): CollectResult"
        portSource shouldNotContain "): SummarizeResult"
        portSource shouldNotContain "): DigestResult"
        portSource shouldContain "PipelineCollectResult"
        portSource shouldContain "PipelineSummarizeResult"
        portSource shouldContain "PipelineDigestResult"
    }

    @Test
    fun `engine module should own pure external IO port contracts`() {
        val appPortRoot = Paths.get("src/main/kotlin/com/ohmyclipping/service/port")
        val enginePortRoot = Paths.get("modules/digest-policy/src/main/kotlin/com/ohmyclipping/service/port")
        val engineOwnedPorts = listOf(
            "ClippingPipelinePort.kt",
            "RssCollectionPort.kt",
            "LlmSummarizationPort.kt",
            "SlackDeliveryPort.kt",
        )

        engineOwnedPorts.forEach { fileName ->
            Files.exists(appPortRoot.resolve(fileName)) shouldBe false
            val source = Files.readString(enginePortRoot.resolve(fileName))

            source shouldNotContain "com.ohmyclipping.model"
            source shouldNotContain "org.springframework"
            source shouldNotContain "jakarta.persistence"
        }
    }

    @Test
    fun `app use case ports should remain outside engine module`() {
        val appPortRoot = Paths.get("src/main/kotlin/com/ohmyclipping/service/port")
        val appContractPortRoot =
            Paths.get("ports/workflow/src/main/kotlin/com/ohmyclipping/service/port")
        val enginePortRoot = Paths.get("modules/digest-policy/src/main/kotlin/com/ohmyclipping/service/port")

        Files.exists(appPortRoot.resolve("ClippingQueryPort.kt")) shouldBe false
        Files.exists(appContractPortRoot.resolve("ClippingQueryPort.kt")) shouldBe true
        Files.exists(enginePortRoot.resolve("ClippingQueryPort.kt")) shouldBe false

        val digestDeliveryPort = appContractPortRoot.resolve("DigestDeliveryWorkflowPort.kt")
        Files.exists(appPortRoot.resolve("DigestDeliveryWorkflowPort.kt")) shouldBe false
        Files.exists(digestDeliveryPort) shouldBe true
        Files.exists(enginePortRoot.resolve("DigestDeliveryWorkflowPort.kt")) shouldBe false

        val digestDeliveryPortSource = Files.readString(digestDeliveryPort)
        // PipelineDigestResult 는 core/api-models 에서 공유한다 (PreparedDigestResult 통합 후).
        digestDeliveryPortSource shouldContain "PipelineDigestResult"
        digestDeliveryPortSource shouldNotContain "PreparedDigestResult"
        digestDeliveryPortSource shouldNotContain "PreparedDigestItemResult"
        digestDeliveryPortSource shouldNotContain "com.ohmyclipping.model"
    }

    @Test
    fun `app ports should not own API or pipeline DTOs`() {
        val appPortDtoRoot =
            Paths.get("ports/workflow/src/main/kotlin/com/ohmyclipping/service/dto")
        val apiModelDtoRoot =
            Paths.get("core/api-models/src/main/kotlin/com/ohmyclipping/service/dto/clipping")
        val pipelineModelDtoRoot =
            Paths.get("core/api-models/src/main/kotlin/com/ohmyclipping/service/dto/pipeline")

        Files.exists(appPortDtoRoot) shouldBe false
        Files.exists(apiModelDtoRoot.resolve("ClippingResultDtos.kt")) shouldBe true
        Files.exists(pipelineModelDtoRoot.resolve("PipelineRunDtos.kt")) shouldBe true
    }

    @Test
    fun `persistence module should own entities and repositories`() {
        val appSourceRoot = Paths.get("src/main/kotlin/com/ohmyclipping")
        val persistenceSourceRoot = Paths.get("adapters/persistence/src/main/kotlin/com/ohmyclipping")

        Files.exists(appSourceRoot.resolve("entity")) shouldBe false
        Files.exists(appSourceRoot.resolve("repository")) shouldBe false
        Files.exists(persistenceSourceRoot.resolve("entity")) shouldBe true
        Files.exists(persistenceSourceRoot.resolve("repository")) shouldBe true
    }

    @Test
    fun `store SPI and implementations should be physically separated`() {
        val appSourceRoot = Paths.get("src/main/kotlin/com/ohmyclipping")
        val storeSpiRoot = Paths.get("ports/persistence/src/main/kotlin/com/ohmyclipping")
        val persistenceSourceRoot = Paths.get("adapters/persistence/src/main/kotlin/com/ohmyclipping")

        Files.exists(appSourceRoot.resolve("store")) shouldBe false
        Files.exists(storeSpiRoot.resolve("store")) shouldBe true
        Files.exists(persistenceSourceRoot.resolve("store")) shouldBe true

        Files.exists(storeSpiRoot.resolve("store/CategoryStore.kt")) shouldBe true
        Files.exists(persistenceSourceRoot.resolve("store/JpaCategoryStore.kt")) shouldBe true
        Files.exists(storeSpiRoot.resolve("store/pipeline/PipelineAnalyticsStore.kt")) shouldBe true
        Files.exists(storeSpiRoot.resolve("store/analytics/dto/PersonaBatchRun.kt")) shouldBe true
    }

    @Test
    fun `shared service exceptions should not live in root app`() {
        val appErrorRoot = Paths.get("src/main/kotlin/com/ohmyclipping/error")
        val errorTypeRoot = Paths.get("core/error-types/src/main/kotlin/com/ohmyclipping/error")

        Files.exists(appErrorRoot) shouldBe false
        Files.exists(errorTypeRoot.resolve("ServiceError.kt")) shouldBe true
        Files.exists(errorTypeRoot.resolve("SlackApiException.kt")) shouldBe true
    }

    @Test
    fun `clipping service should not implement pipeline port directly`() {
        val source = Files.readString(
            Paths.get("src/main/kotlin/com/ohmyclipping/service/ClippingService.kt")
        )

        source shouldNotContain ": ClippingPipelinePort"
        source shouldNotContainIgnoringCase "override fun collect"
        source shouldNotContainIgnoringCase "override fun summarize"
        source shouldNotContainIgnoringCase "override fun digest"
    }

    @Test
    fun `pipeline orchestration should not add broad exception catches`() {
        val serviceSourceRoot = Paths.get("src/main/kotlin/com/ohmyclipping/service")
        val orchestrationSources = listOf(
            "AdminClippingService.kt",
            "pipeline/RalphPipelineOrchestrator.kt",
            "pipeline/DeterministicPipelineRunner.kt"
        )

        orchestrationSources.forEach { fileName ->
            val source = Files.readString(serviceSourceRoot.resolve(fileName))

            source shouldNotContain "catch (e: Exception)"
            source shouldNotContain "catch (_: Exception)"
        }
    }

    @Test
    fun `concrete clipping service should only be injected by adapter boundary classes`() {
        val sourceRoot = Paths.get("src/main/kotlin/com/ohmyclipping")
        val allowed = setOf(
            Paths.get("service/pipeline/ClippingPipelineAdapter.kt"),
            Paths.get("service/ClippingQueryAdapter.kt"),
            Paths.get("service/digest/DigestDeliveryWorkflowAdapter.kt"),
        )
        val violations = Files.walk(sourceRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                .filter { path ->
                    val relative = path.relativeTo(sourceRoot)
                    val source = Files.readString(path)
                    relative !in allowed &&
                        (
                            source.contains("private val clippingService: ClippingService") ||
                                source.contains("import com.ohmyclipping.service.ClippingService")
                            )
                }
                .map { it.relativeTo(sourceRoot).toString() }
                .toList()
        }

        violations.shouldBeEmpty()
    }

    @Test
    fun `app service digest package should be root app orchestration boundary`() {
        val digestSourceRoot = Paths.get("src/main/kotlin/com/ohmyclipping/service/digest")
        val digestApplicationSourceRoot =
            Paths.get("modules/digest/src/main/kotlin/com/ohmyclipping/service/digest")
        val files = Files.list(digestSourceRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
        val applicationFiles = Files.list(digestApplicationSourceRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }

        applicationFiles.shouldContainElement("DigestOrganizationAdapter.kt")
        files.shouldContainElement("DigestService.kt")
        files.shouldContainElement("SlackDigestWorker.kt")
        files.shouldContainElement("DigestDeliveryWorkflowAdapter.kt")
    }
}
