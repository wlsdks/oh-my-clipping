package com.ohmyclipping.service

import com.ohmyclipping.config.ClippingFeatureFlags
import com.ohmyclipping.config.SlackProperties
import com.ohmyclipping.error.DependencyFailureException
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val log = KotlinLogging.logger {}

/**
 * Slack Socket Mode 연결을 유지하면서 이벤트를 수신한다.
 */
@Component
class SlackSocketModeService(
    private val slackProperties: SlackProperties,
    private val summaryFeedbackService: SummaryFeedbackService,
    private val slackMessageSender: SlackMessageSender,
    private val categoryStore: CategoryStore,
    private val batchSummaryStore: BatchSummaryStore,
    private val featureFlags: ClippingFeatureFlags,
    /**
     * Phase 3 PR3b: Slack `link_shared` 이벤트로 수신된 URL 중
     * 우리 tracking endpoint 를 passive share 로 기록하는 핸들러.
     */
    private val linkSharedHandler: SlackLinkSharedHandler
) {
    private val httpClient = HttpClient.newHttpClient()
    private val mapper = jacksonObjectMapper().apply { findAndRegisterModules() }

    private val connecting = AtomicBoolean(false)
    private val socket = AtomicReference<WebSocket?>(null)
    private val connected = AtomicBoolean(false)
    private val connectedAppId = AtomicReference<String?>(null)
    private val connectedUrl = AtomicReference<String?>(null)
    private val lastError = AtomicReference<String?>(null)
    private val lastEnvelopeId = AtomicReference<String?>(null)

    @PostConstruct
    fun startOnBoot() {
        if (!featureFlags.slack.delivery.enabled) {
            log.info { "Slack delivery disabled by feature flag; skipping socket mode boot" }
            return
        }
        maintainConnection()
    }

    @PreDestroy
    fun stopOnShutdown() {
        closeActiveSocket("application shutdown")
    }

    @Scheduled(fixedDelayString = "15000")
    fun maintainConnection() {
        if (!slackProperties.socketModeEnabled) {
            closeActiveSocket("socket mode disabled")
            return
        }
        if (!connecting.compareAndSet(false, true)) return
        try {
            if (connected.get()) return
            reconnectIfNeeded()
        } finally {
            connecting.set(false)
        }
    }

    /**
     * 연결 상태를 관리 화면에서 조회할 수 있도록 반환한다.
     */
    fun currentStatus(): SlackSocketModeStatus {
        return SlackSocketModeStatus(
            enabled = slackProperties.socketModeEnabled,
            configured = slackProperties.appLevelToken.isNotBlank(),
            connected = connected.get(),
            appId = connectedAppId.get(),
            socketUrl = connectedUrl.get(),
            lastEnvelopeId = lastEnvelopeId.get(),
            lastError = lastError.get()
        )
    }

    private fun reconnectIfNeeded() {
        val token = slackProperties.appLevelToken.trim()
        if (token.isBlank()) {
            lastError.set("Slack app token is not configured.")
            return
        }

        val connection = openSocketModeSession(token)
        if (!connection.ok) {
            lastError.set(connection.rawError ?: "socket mode connection request failed")
            log.warn { "Slack Socket Mode connection request failed: ${connection.rawError}" }
            return
        }

        val socketUrl = connection.socketUrl
        if (socketUrl.isNullOrBlank()) {
            lastError.set("Slack Socket Mode URL is empty.")
            log.warn { "Slack Socket Mode returned empty websocket URL." }
            return
        }

        try {
            closeActiveSocket("reconnect")
            val newSocket = connectToSocket(socketUrl)
            socket.set(newSocket)
            connectedAppId.set(connection.appId)
            connectedUrl.set(socketUrl)
            lastError.set(null)
        } catch (exception: Exception) {
            lastError.set(exception.message ?: "failed to open socket mode websocket")
            connected.set(false)
            log.warn(exception) { "Slack Socket Mode websocket open failed." }
        }
    }

    private fun connectToSocket(socketUrl: String): WebSocket {
        val builder = httpClient.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(5))

        return builder.buildAsync(
            URI.create(socketUrl),
            createSocketListener()
        ).get(10, TimeUnit.SECONDS)
    }

    private fun closeActiveSocket(reason: String) {
        val current = socket.getAndSet(null)
        if (current == null) return
        connected.set(false)
        runCatching {
            current.sendClose(WebSocket.NORMAL_CLOSURE, reason).get(3, TimeUnit.SECONDS)
        }
    }

    private fun openSocketModeSession(appLevelToken: String): SlackSocketModeOpenResult {
        val token = appLevelToken.trim()
        if (token.isBlank()) throw InvalidInputException("Slack 앱 토큰이 설정되지 않았습니다.")

        val response = callSlackApi(token, "apps.connections.open")
        if (!response.path("ok").asBoolean(false)) {
            return SlackSocketModeOpenResult(
                ok = false,
                appId = null,
                socketUrl = null,
                rawError = "apps.connections.open failed: ${response.path("error").asText("unknown_error")}"
            )
        }

        val socketUrl = response.path("url").asText(null)
        return SlackSocketModeOpenResult(
            ok = true,
            appId = response.path("app_id").asText(null) ?: extractAppIdFromSocketUrl(socketUrl),
            socketUrl = socketUrl,
            rawError = null
        )
    }

    private fun createSocketListener(): WebSocket.Listener {
        return object : WebSocket.Listener {
            private val fragmentBuffer = StringBuilder()

            override fun onOpen(webSocket: WebSocket) {
                socket.set(webSocket)
                connected.set(true)
                lastError.set(null)
                log.info { "Slack Socket Mode websocket opened." }
                webSocket.request(1)
            }

            override fun onText(
                webSocket: WebSocket,
                data: CharSequence,
                last: Boolean
            ): CompletionStage<Void> {
                fragmentBuffer.append(data)
                if (last) {
                    val rawPayload = fragmentBuffer.toString()
                    fragmentBuffer.setLength(0)
                    handleIncomingEvent(webSocket, rawPayload)
                }
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }

            override fun onClose(
                webSocket: WebSocket,
                statusCode: Int,
                reason: String
            ): CompletionStage<Void> {
                connected.set(false)
                lastError.set("WebSocket closed: status=$statusCode, reason=$reason")
                log.warn { "Slack Socket Mode websocket closed: status=$statusCode reason=$reason" }
                return CompletableFuture.completedFuture(null)
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                connected.set(false)
                lastError.set(error.message ?: "socket error")
                log.warn(error) { "Slack Socket Mode websocket error occurred." }
            }
        }
    }

    private fun handleIncomingEvent(webSocket: WebSocket, rawPayload: String) {
        val root = parseJson(rawPayload) ?: return
        val envelopeId = root.path("envelope_id").asText(null)
        if (!envelopeId.isNullOrBlank()) {
            lastEnvelopeId.set(envelopeId)
            acknowledgeEnvelope(webSocket, envelopeId)
        }

        // Phase 3 PR3b: Events API envelope (link_shared 등) 은 interactive payload 와 shape 이 달라 분기 처리한다.
        // 우리 tracking URL 이 Slack 내에서 재공유될 때 passive capture 용.
        if (tryDispatchEventsApi(root)) {
            return
        }

        val interactionCandidate = extractFeedbackPayload(root) ?: return

        // 액션 ID를 확인하여 공유 버튼인지 피드백 버튼인지 분기한다
        val actionId = extractFirstActionId(interactionCandidate)
        if (actionId != null && actionId.startsWith("share_to_channel:")) {
            handleShareToChannel(root, interactionCandidate)
            return
        }

        try {
            val (feedback, message) = summaryFeedbackService.recordFromSlackPayload(
                interactionCandidate.toString()
            )
            log.info {
                "Slack feedback received from Socket Mode: " +
                    "summaryId=${feedback.summaryId}, " +
                    "feedbackType=${feedback.feedbackType}, " +
                    "message=\"$message\""
            }

            // 피드백 기록 성공 후 원본 메시지의 버튼을 확인 텍스트로 교체한다
            sendFeedbackVisualResponse(root, feedback.feedbackType)
        } catch (_: InvalidInputException) {
            log.debug { "Received Socket Mode payload is not summary feedback event: envelopeId=$envelopeId" }
        } catch (exception: Exception) {
            log.warn(exception) {
                "Failed to process Socket Mode payload: envelopeId=$envelopeId"
            }
        }
    }

    /**
     * 인터랙션 페이로드에서 첫 번째 액션의 action_id를 추출한다.
     */
    private fun extractFirstActionId(candidate: JsonNode): String? {
        val actions = candidate.path("actions")
        if (!actions.isArray || actions.isEmpty) return null
        return actions[0].path("action_id").asText(null)
    }

    /**
     * Socket Mode envelope 에서 Events API payload (event_callback) 을 추출해
     * 해당 이벤트 타입에 따라 dispatch 한다. 처리한 경우 true, 아니면 false 를 반환한다.
     *
     * 현재 지원: `link_shared` → [SlackLinkSharedHandler].
     * 다른 Events API 타입은 앞으로 필요할 때 추가한다.
     *
     * Visible for testing — production 에서는 [handleIncomingEvent] 에서만 호출된다.
     */
    internal fun tryDispatchEventsApi(root: JsonNode): Boolean {
        // Events API payload 는 root.payload.type=="event_callback" + root.payload.event.type 에 구체 이벤트명
        val payload = root.path("payload")
        if (!payload.isObject) return false
        if (payload.path("type").asText() != "event_callback") return false

        val event = payload.path("event")
        if (!event.isObject) return false
        val eventType = event.path("type").asText()

        return when (eventType) {
            "link_shared" -> {
                dispatchLinkShared(event)
                true
            }
            else -> false
        }
    }

    /**
     * Slack `link_shared` 이벤트 payload 에서 필드를 추출해 핸들러로 위임한다.
     * 예외는 전부 삼킨다 — 이벤트 처리 실패가 WebSocket 수신을 중단시키지 않도록.
     */
    private fun dispatchLinkShared(event: JsonNode) {
        try {
            val userId = event.path("user").asText("")
            val channelId = event.path("channel").asText("")
            val messageTs = event.path("message_ts").asText("")
            val links = event.path("links")
            val urls = if (links.isArray) {
                links.mapNotNull { link -> link.path("url").asText(null) }
            } else {
                emptyList()
            }
            linkSharedHandler.handle(
                userId = userId,
                channelId = channelId,
                messageTs = messageTs,
                urls = urls
            )
        } catch (e: Exception) {
            log.warn(e) { "link_shared dispatch failure" }
        }
    }

    /**
     * 채널 공유 버튼 클릭을 처리한다.
     * 요약 내용을 카테고리의 기본 Slack 채널로 전송하고,
     * 원본 메시지의 공유 버튼을 확인 텍스트로 교체한다.
     */
    private fun handleShareToChannel(root: JsonNode, interaction: JsonNode) {
        try {
            val action = interaction.path("actions").let {
                if (it.isArray && it.size() > 0) it[0] else null
            } ?: return

            // value 형식: "summaryId:categoryId"
            val value = action.path("value").asText("")
            val parts = value.split(":", limit = 2)
            if (parts.size < 2) {
                log.warn { "share_to_channel 버튼의 value 형식이 올바르지 않습니다: $value" }
                return
            }

            val summaryId = parts[0]
            val categoryId = parts[1]
            val userId = interaction.path("user").path("id").asText("unknown")

            // 카테고리에서 채널 ID 조회
            val category = categoryStore.findById(categoryId)
            if (category == null) {
                log.warn { "share_to_channel: 카테고리를 찾을 수 없습니다 (categoryId=$categoryId)" }
                return
            }

            val targetChannelId = category.slackChannelId
            if (targetChannelId.isNullOrBlank()) {
                log.warn { "share_to_channel: 카테고리에 Slack 채널이 설정되지 않았습니다 (categoryId=$categoryId)" }
                sendShareErrorResponse(root, "채널이 설정되지 않았어요")
                return
            }

            // 요약 데이터 조회
            val summary = batchSummaryStore.findById(summaryId)
            if (summary == null) {
                log.warn { "share_to_channel: 요약을 찾을 수 없습니다 (summaryId=$summaryId)" }
                return
            }

            // 공유 메시지 구성
            val shareTitle = summary.translatedTitle ?: summary.originalTitle
            val shareText = buildString {
                append("*$shareTitle*\n\n")
                append(summary.summary.take(2800))
                if (summary.sourceLink.isNotBlank()) {
                    append("\n\n<${summary.sourceLink}|원문 보기>")
                }
                append("\n\n_<@$userId> 님이 공유했어요_")
            }

            // 카테고리 채널로 메시지 전송
            slackMessageSender.sendMessage(
                channelId = targetChannelId,
                text = shareText
            )

            log.info {
                "share_to_channel 성공: summaryId=$summaryId, " +
                    "categoryId=$categoryId, channelId=$targetChannelId, userId=$userId"
            }

            // 채널 이름을 조회해 확인 메시지에 표시한다
            val channelName = resolveChannelName(targetChannelId)
            val confirmText = if (channelName != null) {
                "✅ #$channelName 에 공유했어요"
            } else {
                "✅ 채널에 공유했어요"
            }
            sendShareConfirmationResponse(root, confirmText)
        } catch (exception: Exception) {
            log.warn(exception) { "share_to_channel 처리 중 오류 발생" }
        }
    }

    /**
     * 채널 ID로 채널 이름을 조회한다. 실패 시 null을 반환한다.
     */
    private fun resolveChannelName(channelId: String): String? {
        return try {
            slackMessageSender.getChannelInfo(botToken = null, channelId = channelId).name
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 공유 성공 후 원본 메시지의 공유 버튼을 확인 텍스트로 교체한다.
     */
    private fun sendShareConfirmationResponse(root: JsonNode, confirmationText: String) {
        updateShareBlock(root, confirmationText)
    }

    /**
     * 공유 실패 시 원본 메시지의 공유 버튼을 에러 메시지로 교체한다.
     */
    private fun sendShareErrorResponse(root: JsonNode, errorText: String) {
        updateShareBlock(root, "⚠️ $errorText")
    }

    /**
     * 원본 메시지에서 공유 버튼 블록을 찾아 텍스트로 교체한다.
     */
    private fun updateShareBlock(root: JsonNode, replacementText: String) {
        try {
            val payload = root.path("payload")
            val interaction = when {
                payload.isTextual -> parseJson(payload.asText()) ?: return
                payload.isObject -> payload
                else -> root
            }

            val channelId = interaction.path("channel").path("id").asText(null)
                ?: interaction.path("container").path("channel_id").asText(null)
            val messageTs = interaction.path("message").path("ts").asText(null)
                ?: interaction.path("container").path("message_ts").asText(null)

            if (channelId.isNullOrBlank() || messageTs.isNullOrBlank()) {
                log.debug { "공유 시각 응답에 필요한 channel/ts 정보가 없습니다." }
                return
            }

            val originalBlocks = interaction.path("message").path("blocks")
            if (!originalBlocks.isArray || originalBlocks.isEmpty) return

            val updatedBlocks = replaceShareBlocks(originalBlocks, replacementText)
            slackMessageSender.updateMessage(
                channelId = channelId,
                messageTs = messageTs,
                blocks = updatedBlocks.filterIsInstance<Map<String, Any?>>(),
                fallbackText = replacementText
            )
        } catch (e: Exception) {
            log.warn(e) { "공유 시각 응답 전송 실패" }
        }
    }

    /**
     * 원본 블록 배열에서 공유 버튼 actions 블록을 확인 텍스트 context로 교체한다.
     * share_ 접두어가 포함된 block_id 또는 share_to_channel action_id를 가진 블록을 식별한다.
     */
    internal fun replaceShareBlocks(
        originalBlocks: JsonNode,
        confirmationText: String
    ): List<Any> {
        val result = mutableListOf<Any>()
        for (block in originalBlocks) {
            if (isShareActionsBlock(block)) {
                result.add(
                    mapOf(
                        "type" to "context",
                        "elements" to listOf(
                            mapOf(
                                "type" to "mrkdwn",
                                "text" to confirmationText
                            )
                        )
                    )
                )
            } else {
                result.add(mapper.treeToValue(block, Map::class.java))
            }
        }
        return result
    }

    /**
     * 블록이 공유 버튼 actions 블록인지 판별한다.
     */
    private fun isShareActionsBlock(block: JsonNode): Boolean {
        val blockType = block.path("type").asText("")
        if (blockType != "actions") return false

        val blockId = block.path("block_id").asText("")
        if (blockId.startsWith("share_")) return true

        val elements = block.path("elements")
        if (elements.isArray) {
            for (element in elements) {
                val actionId = element.path("action_id").asText("")
                if (actionId.startsWith("share_to_channel")) return true
            }
        }
        return false
    }

    /**
     * 피드백 버튼이 포함된 원본 메시지를 업데이트하여 확인 텍스트로 교체한다.
     * 실패해도 피드백은 이미 저장되었으므로 경고만 남긴다.
     */
    private fun sendFeedbackVisualResponse(root: JsonNode, feedbackType: String) {
        try {
            val payload = root.path("payload")
            // 인터랙션 노드: payload가 문자열이면 파싱, 아니면 그대로 사용
            val interaction = when {
                payload.isTextual -> parseJson(payload.asText()) ?: return
                payload.isObject -> payload
                else -> root
            }

            // 채널 ID와 메시지 타임스탬프 추출
            val channelId = interaction.path("channel").path("id").asText(null)
                ?: interaction.path("container").path("channel_id").asText(null)
            val messageTs = interaction.path("message").path("ts").asText(null)
                ?: interaction.path("container").path("message_ts").asText(null)

            if (channelId.isNullOrBlank() || messageTs.isNullOrBlank()) {
                log.debug { "피드백 시각 응답에 필요한 channel/ts 정보가 없습니다." }
                return
            }

            // 원본 메시지 블록 추출 및 피드백 버튼 블록 교체
            val originalBlocks = interaction.path("message").path("blocks")
            if (!originalBlocks.isArray || originalBlocks.isEmpty) {
                log.debug { "원본 메시지에 블록이 없어 시각 응답을 건너뜁니다." }
                return
            }

            // 클릭된 버튼이 속한 블록만 교체하도록 타겟 block_id를 추출한다
            val targetBlockId = extractClickedBlockId(interaction)

            val confirmationText = feedbackConfirmationText(feedbackType)
            val updatedBlocks = replaceFeedbackBlocks(originalBlocks, confirmationText, targetBlockId)
            slackMessageSender.updateMessage(
                channelId = channelId,
                messageTs = messageTs,
                blocks = updatedBlocks.filterIsInstance<Map<String, Any?>>(),
                fallbackText = confirmationText
            )
        } catch (e: Exception) {
            log.warn(e) { "피드백 시각 응답 전송 실패 (피드백은 이미 저장됨)" }
        }
    }

    /**
     * 인터랙션 페이로드에서 클릭된 버튼이 속한 actions 블록의 block_id를 추출한다.
     * digest 내 여러 기사 중 정확히 클릭된 기사 블록만 교체하기 위해 사용한다.
     */
    private fun extractClickedBlockId(interaction: JsonNode): String? {
        val actions = interaction.path("actions")
        if (!actions.isArray || actions.isEmpty) return null
        val blockId = actions[0].path("block_id").asText("")
        return blockId.ifBlank { null }
    }

    /**
     * 원본 블록 배열에서 피드백 버튼 actions 블록을 확인 텍스트 section으로 교체한다.
     * targetBlockId가 주어지면 해당 block_id와 일치하는 블록만 교체하고, 같은 digest 내
     * 다른 기사의 피드백 버튼은 그대로 유지한다. null이면 모든 피드백 블록을 교체한다(폴백).
     */
    internal fun replaceFeedbackBlocks(
        originalBlocks: JsonNode,
        confirmationText: String,
        targetBlockId: String? = null
    ): List<Any> {
        val result = mutableListOf<Any>()
        for (block in originalBlocks) {
            // 교체 대상 판별: 타겟이 지정되면 block_id 정확 일치, 아니면 피드백 블록 전부
            val shouldReplace = if (targetBlockId != null) {
                block.path("type").asText("") == "actions" &&
                    block.path("block_id").asText("") == targetBlockId
            } else {
                isFeedbackActionsBlock(block)
            }

            if (shouldReplace) {
                // 피드백 버튼 블록을 확인 메시지로 교체
                result.add(
                    mapOf(
                        "type" to "section",
                        "text" to mapOf(
                            "type" to "mrkdwn",
                            "text" to confirmationText
                        )
                    )
                )
            } else {
                // 원본 블록 유지 (JsonNode를 Map으로 변환)
                result.add(mapper.treeToValue(block, Map::class.java))
            }
        }
        return result
    }

    /**
     * 블록이 피드백 버튼 actions 블록인지 판별한다.
     * block_id가 "feedback_"로 시작하거나, elements의 action_id가 "feedback_"로 시작하면 해당 블록이다.
     */
    private fun isFeedbackActionsBlock(block: JsonNode): Boolean {
        val blockType = block.path("type").asText("")
        if (blockType != "actions") return false

        // block_id로 빠르게 판별
        val blockId = block.path("block_id").asText("")
        if (blockId.startsWith("feedback_")) return true

        // elements 내 action_id로 판별
        val elements = block.path("elements")
        if (elements.isArray) {
            for (element in elements) {
                val actionId = element.path("action_id").asText("")
                if (actionId.startsWith("feedback_")) return true
            }
        }
        return false
    }

    /**
     * 피드백 타입별 확인 텍스트를 반환한다.
     */
    internal fun feedbackConfirmationText(feedbackType: String): String = when (feedbackType) {
        "LIKE" -> "\uD83D\uDC4D 좋아요로 반영했어요"
        "NEUTRAL" -> "\uD83D\uDE10 보통으로 반영했어요"
        "DISLIKE" -> "\uD83D\uDC4E 별로로 반영했어요"
        else -> "\u2705 피드백이 반영되었어요"
    }

    private fun acknowledgeEnvelope(webSocket: WebSocket, envelopeId: String) {
        runCatching {
            val ackPayload = mapper.writeValueAsString(
                mapOf("envelope_id" to envelopeId)
            )
            webSocket.sendText(ackPayload, true).get(3, TimeUnit.SECONDS)
        }.onFailure { error ->
            log.warn { "Slack Socket Mode ack failed: envelopeId=$envelopeId, error=${error.message}" }
        }
    }

    private fun extractFeedbackPayload(root: JsonNode): JsonNode? {
        val candidates = mutableListOf<JsonNode>()
        val rootPayload = root.path("payload")

        if (root.isObject) {
            candidates.add(root)
        }
        if (rootPayload.isObject) {
            candidates.add(rootPayload)
            val eventPayload = rootPayload.path("event")
            if (eventPayload.isObject) {
                candidates.add(eventPayload)
            }
        } else if (rootPayload.isTextual) {
            parseJson(rootPayload.asText())?.let { parsedPayload ->
                candidates.add(parsedPayload)
                val nestedPayload = parsedPayload.path("payload")
                if (nestedPayload.isObject) {
                    candidates.add(nestedPayload)
                }
            }
        }

        for (candidate in candidates) {
            val actionNode = candidate.path("actions")
            if (actionNode.isArray && actionNode.size() > 0) {
                return candidate
            }
            val type = candidate.path("type").asText("")
            if (type in setOf(
                    "block_actions",
                    "view_submission",
                    "block_suggestion",
                    "message_action",
                    "interactive_message",
                    "shortcut"
                )
            ) {
                return candidate
            }
        }

        return null
    }

    private fun callSlackApi(token: String, method: String): JsonNode {
        if (token.isBlank()) throw InvalidInputException("Slack app token is not configured")

        val endpoint = "${slackProperties.apiBaseUrl.trimEnd('/')}/$method"
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = slackProperties.connectTimeoutMs
            connection.readTimeout = slackProperties.readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write("{}") }

            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readText()
                .orEmpty()

            if (status !in 200..299) {
                throw DependencyFailureException("Slack API request failed with status $status")
            }

            return runCatching { mapper.readTree(body) }.getOrElse {
                throw DependencyFailureException("Slack API response parse error: ${it.message}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseJson(raw: String): JsonNode? {
        if (raw.isBlank()) return null
        return runCatching { mapper.readTree(raw) }.getOrNull()
    }

    private fun extractAppIdFromSocketUrl(socketUrl: String?): String? {
        if (socketUrl.isNullOrBlank()) return null
        return runCatching {
            val rawQuery = URI(socketUrl).query ?: return null
            rawQuery
                .split("&")
                .firstOrNull { it.startsWith("app_id=") }
                ?.removePrefix("app_id=")
                ?.takeIf { it.isNotBlank() }
                ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
        }.getOrNull()
    }
}

/**
 * Slack Socket Mode 연결 시도 결과.
 */
data class SlackSocketModeOpenResult(
    val ok: Boolean,
    val appId: String?,
    val socketUrl: String?,
    val rawError: String?
)

/**
 * Socket Mode 상태 조회용 정보.
 */
data class SlackSocketModeStatus(
    val enabled: Boolean,
    val configured: Boolean,
    val connected: Boolean,
    val appId: String?,
    val socketUrl: String?,
    val lastEnvelopeId: String?,
    val lastError: String?
)
