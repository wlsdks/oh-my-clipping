package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.source.SourceVerificationClient
import com.clipping.mcpserver.service.source.VerificationResult
import com.clipping.mcpserver.service.dto.SlackChannelDto
import com.clipping.mcpserver.service.dto.SlackChannelListResponse
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.KnownNewsSource
import com.clipping.mcpserver.service.dto.clipping.PipelineRunResult
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.security.UrlSafetyValidator
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.service.dto.UserSetupSourceUrlValidationView
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.BlockedSlackChannelStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.KnownNewsSourceStore
import com.clipping.mcpserver.store.RssSourceStore
import com.clipping.mcpserver.store.UserOwnedCategoryStore
import com.clipping.mcpserver.store.UserOwnedSourceStore
import com.clipping.mcpserver.support.SlackChannelIdNormalizer
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * user setup 위자드에서 사용하는 카테고리/소스/파이프라인 API를 담당한다.
 */
@Service
class UserSetupResourceService(
    private val categoryStore: CategoryStore,
    private val adminSourceService: AdminSourceService,
    private val adminClippingService: AdminClippingService,
    private val urlSafetyValidator: UrlSafetyValidator,
    private val sourceVerificationClient: SourceVerificationClient,
    private val runtimeSettingService: RuntimeSettingService,
    private val slackMessageSender: SlackMessageSender,
    private val userOwnedCategoryStore: UserOwnedCategoryStore,
    private val userOwnedSourceStore: UserOwnedSourceStore,
    private val userSetupOwnershipService: UserSetupOwnershipService,
    private val rssSourceStore: RssSourceStore,
    private val knownNewsSourceStore: KnownNewsSourceStore,
    private val adminUserStore: AdminUserStore,
    private val blockedSlackChannelStore: BlockedSlackChannelStore,
    private val cacheManager: CacheManager
) {

    companion object {
        /** 비공개 채널 멤버십 필터링 시 최대 처리 채널 수. */
        const val MAX_PRIVATE_CHANNELS = 50
    }

    /** curated 플래그가 true인 소스 목록을 반환한다. */
    fun listCuratedSources(): List<RssSource> =
        rssSourceStore.list().filter { it.curated }

    /** 주요 뉴스소스 전체 목록을 반환한다. */
    fun listAllKnownSources(): List<KnownNewsSource> =
        knownNewsSourceStore.listAll()

    /** 이름, 별칭, 도메인으로 주요 뉴스소스를 검색한다. */
    fun searchKnownSources(query: String): List<KnownNewsSource> =
        knownNewsSourceStore.search(query)

    /**
     * 사용자가 자기 전용 카테고리를 생성하고 owner 매핑을 함께 저장한다.
     */
    @Transactional
    fun createOwnCategory(
        requesterUsername: String,
        name: String,
        description: String?,
        slackChannelId: String?,
        maxItems: Int,
        personaId: String?
    ): Category {
        val requester = userSetupOwnershipService.requireUserRequester(requesterUsername, "create setup categories")
        // 선택된 페르소나도 본인 소유여야만 카테고리에 연결할 수 있다.
        personaId?.takeIf { it.isNotBlank() }?.let { userSetupOwnershipService.ensureOwnsPersona(requester.id, it) }
        val normalizedName = name.trim()
        // self-serve 카테고리는 표시 이름 중복을 허용하되 기본 입력 검증은 유지한다.
        ensureValid(normalizedName.isNotBlank()) { "Name is required" }
        ensureValid(maxItems in UserDeliveryScheduleService.ALLOWED_MAX_ITEMS) {
            "maxItems는 ${UserDeliveryScheduleService.ALLOWED_MAX_ITEMS.sorted().joinToString(", ")} 중 하나여야 합니다."
        }
        val category = categoryStore.save(
            Category(
                id = "",
                name = normalizedName,
                description = normalizeOptionalValue(description),
                slackChannelId = normalizeOptionalValue(slackChannelId),
                maxItems = maxItems,
                personaId = normalizeOptionalValue(personaId)
            )
        )
        // 파이프라인/소스 생성 전에 즉시 owner-scope가 가능하도록 매핑을 남긴다.
        userOwnedCategoryStore.save(requester.id, category.id)
        return category
    }

    /**
     * 사용자가 자기 전용 카테고리 아래에 RSS 소스를 생성한다.
     */
    @Transactional
    fun createOwnSource(
        requesterUsername: String,
        name: String,
        url: String,
        sourceRegionRaw: String?,
        emoji: String?,
        categoryId: String,
        legalBasisRaw: String?,
        summaryAllowed: Boolean?,
        fulltextAllowed: Boolean?,
        reviewNotes: String?
    ): RssSource {
        val requester = userSetupOwnershipService.requireUserRequester(requesterUsername, "create setup sources")
        // 타 사용자 카테고리에 소스를 붙이지 못하게 owner-scope를 먼저 확인한다.
        userSetupOwnershipService.ensureOwnsCategory(requester.id, categoryId)
        val source = adminSourceService.createSource(
            name = name,
            url = url,
            sourceRegionRaw = sourceRegionRaw,
            emoji = emoji,
            categoryId = categoryId,
            legalBasisRaw = legalBasisRaw,
            summaryAllowed = summaryAllowed,
            fulltextAllowed = fulltextAllowed,
            reviewNotes = reviewNotes
        )
        // verify/approve 경로에서 즉시 owner-scope를 타기 위해 매핑을 남긴다.
        userOwnedSourceStore.save(requester.id, source.id)
        return source
    }

    /**
     * 본인 소유 RSS 소스만 연결 상태 검증을 수행한다.
     */
    fun verifyOwnSource(requesterUsername: String, sourceId: String): String {
        val requester = userSetupOwnershipService.requireUserRequester(requesterUsername, "verify setup sources")
        // 타 사용자 소스 검증 호출을 차단한다.
        userSetupOwnershipService.ensureOwnsSource(requester.id, sourceId)
        return adminSourceService.verifySource(sourceId)
    }

    /**
     * 본인 소유 RSS 소스만 빠른 세팅용 승인 상태로 전환한다.
     */
    fun approveOwnSource(
        requesterUsername: String,
        sourceId: String,
        approved: Boolean,
        legalBasisRaw: String?,
        summaryAllowed: Boolean?,
        fulltextAllowed: Boolean?,
        reviewNotes: String?,
        expectedUpdatedAt: java.time.Instant?
    ): RssSource {
        val requester = userSetupOwnershipService.requireUserRequester(requesterUsername, "approve setup sources")
        // 타 사용자 소스 승인 호출을 차단한다.
        userSetupOwnershipService.ensureOwnsSource(requester.id, sourceId)
        return adminSourceService.approveSource(
            id = sourceId,
            approved = approved,
            approvedBy = requester.username,
            legalBasisRaw = legalBasisRaw,
            summaryAllowed = summaryAllowed,
            fulltextAllowed = fulltextAllowed,
            reviewNotes = reviewNotes,
            expectedUpdatedAt = expectedUpdatedAt
        )
    }

    /**
     * 본인 소유 카테고리만 파이프라인을 수동 실행할 수 있다.
     */
    fun runOwnPipeline(
        requesterUsername: String,
        categoryId: String,
        hoursBack: Int?,
        maxItems: Int?,
        unsentOnly: Boolean?,
        sendToSlack: Boolean?,
        slackChannelId: String?,
        ralphLoopEnabled: Boolean?,
        ralphLoopMaxIterations: Int?,
        ralphLoopStopPhrase: String?
    ): PipelineRunResult {
        val requester = userSetupOwnershipService.requireUserRequester(requesterUsername, "run setup pipelines")
        // 파이프라인은 본인 소유 카테고리 범위에서만 허용한다.
        userSetupOwnershipService.ensureOwnsCategory(requester.id, categoryId)
        return adminClippingService.runPipeline(
            categoryId = categoryId,
            hoursBack = hoursBack,
            maxItems = maxItems,
            unsentOnly = unsentOnly,
            sendToSlack = sendToSlack,
            slackChannelId = slackChannelId,
            ralphLoopEnabledOverride = ralphLoopEnabled,
            ralphLoopMaxIterationsOverride = ralphLoopMaxIterations,
            ralphLoopStopPhraseOverride = ralphLoopStopPhrase
        )
    }

    /**
     * 사용자 모드 커스텀 사이트 입력용 RSS URL 검증을 수행한다.
     */
    fun validateOwnSourceUrl(requesterUsername: String, url: String): UserSetupSourceUrlValidationView {
        userSetupOwnershipService.requireUserRequester(requesterUsername, "validate setup source urls")
        return try {
            // 외부 URL 형식과 SSRF 위험을 먼저 차단한다.
            val uri = urlSafetyValidator.validatePublicHttpUrl(url)
            // 실제 RSS 파싱 가능 여부를 외부 검증 클라이언트로 확인한다.
            val result = sourceVerificationClient.verify(uri)
            UserSetupSourceUrlValidationView(
                valid = result == VerificationResult.VERIFIED,
                status = result.name,
                reason = when (result) {
                    VerificationResult.VERIFIED -> "RSS 피드를 정상적으로 확인했어요."
                    VerificationResult.FEED_ERROR -> "이 주소에서 RSS 피드를 찾지 못했어요. URL을 다시 확인해 주세요."
                    VerificationResult.ROBOTS_BLOCKED -> "이 사이트가 자동 수집을 차단하고 있어요."
                    VerificationResult.TIMEOUT -> "응답이 너무 오래 걸려요. 잠시 후 다시 시도해 주세요."
                    VerificationResult.BLOCKED_URL -> "허용되지 않는 URL이에요."
                }
            )
        } catch (e: IllegalArgumentException) {
            UserSetupSourceUrlValidationView(
                valid = false,
                reason = e.message ?: "잘못된 URL 형식이에요."
            )
        } catch (_: Exception) {
            UserSetupSourceUrlValidationView(
                valid = false,
                reason = "URL 확인 중 오류가 발생했어요."
            )
        }
    }

    /**
     * user quick setup에서 사용할 Slack 채널 연결 확인을 수행한다.
     */
    fun verifyOwnSlackChannel(
        requesterUsername: String,
        slackChannelId: String?
    ): SlackMessageSender.SlackConnectionTestResult {
        userSetupOwnershipService.requireUserRequester(requesterUsername, "verify setup slack channels")
        val runtime = runtimeSettingService.current()
        // USER 모드에서는 서버에 저장된 운영 토큰만 사용해 채널 접근 권한을 확인한다.
        val normalizedChannelId = normalizeSlackVerificationTarget(slackChannelId)
        return slackMessageSender.testConnection(
            botToken = runtime.slackBotToken.takeIf { it.isNotBlank() },
            channelId = normalizedChannelId
        )
    }

    /**
     * 운영 Slack 봇 토큰으로 채널 목록을 조회한다.
     * channelType: "public_channel" 또는 "private_channel".
     *
     * 비공개 채널은 사용자의 Slack 멤버 ID로 멤버십 필터링을 수행한다.
     * 공개 채널은 전체 목록을 반환한다 (Phase 2에서 blocked 필터 추가 예정).
     *
     * refresh=true이면 slack-channels 캐시 전체를 무효화한 뒤 새 목록을 Slack API에서 조회한다.
     * 사용자가 새 채널에 봇을 초대한 직후 5분 TTL 캐시를 우회할 때 사용한다.
     */
    fun listOwnSlackChannels(
        requesterUsername: String,
        channelType: String,
        refresh: Boolean = false
    ): SlackChannelListResponse {
        val requester = userSetupOwnershipService.requireUserRequester(
            requesterUsername, "list setup slack channels"
        )
        ensureValid(channelType in listOf("public_channel", "private_channel")) {
            "channelType은 public_channel 또는 private_channel만 허용합니다."
        }
        val runtime = runtimeSettingService.current()
        val botToken = runtime.slackBotToken.takeIf { it.isNotBlank() }
        // refresh 요청이면 캐시를 먼저 비워 Slack API에서 최신 목록을 받아온다.
        if (refresh) {
            cacheManager.getCache("slack-channels")?.clear()
        }
        // 운영 토큰으로만 조회 — 사용자 토큰 입력 경로 없음.
        val channels = slackMessageSender.listChannels(
            botToken = botToken,
            channelType = channelType
        )

        return if (channelType == "private_channel") {
            // 비공개 채널은 멤버십 필터링 후 관리자 차단 목록도 함께 적용한다.
            val memberFiltered = filterPrivateChannels(requester, botToken, channels)
            val blockedIds = blockedSlackChannelStore.listBlockedChannelIds()
            memberFiltered.copy(
                channels = memberFiltered.channels.filter { it.id !in blockedIds }
            )
        } else {
            // 공개 채널은 관리자 차단 목록을 적용한 뒤 반환한다.
            val blockedIds = blockedSlackChannelStore.listBlockedChannelIds()
            val filtered = channels.filter { it.id !in blockedIds }
            SlackChannelListResponse(
                channels = filtered.map { SlackChannelDto(id = it.id, name = it.name, isPrivate = it.isPrivate) }
            )
        }
    }

    /**
     * 운영 Slack 봇 토큰으로 채널 단건 정보를 조회한다.
     * 수정 모드 진입 시 기존 channelId의 is_private 여부를 확인하는 데 사용한다.
     */
    fun getOwnSlackChannelInfo(
        requesterUsername: String,
        channelId: String
    ): SlackMessageSender.SlackChannel {
        userSetupOwnershipService.requireUserRequester(requesterUsername, "get setup slack channel info")
        val runtime = runtimeSettingService.current()
        return slackMessageSender.getChannelInfo(
            botToken = runtime.slackBotToken.takeIf { it.isNotBlank() },
            channelId = channelId
        )
    }

    /**
     * 비공개 채널 목록에서 사용자가 멤버인 채널만 필터링한다.
     * Slack 멤버 ID가 설정되지 않았으면 빈 목록과 함께 slackConnectRequired=true를 반환한다.
     */
    private fun filterPrivateChannels(
        requester: AdminUser,
        botToken: String?,
        channels: List<SlackMessageSender.SlackChannel>
    ): SlackChannelListResponse {
        val slackMemberId = requester.slackMemberId?.trim()?.takeIf { it.isNotBlank() }
        // Slack 멤버 ID가 미연동이면 멤버십 확인이 불가능하므로 빈 목록을 반환한다.
        if (slackMemberId == null) {
            return SlackChannelListResponse(
                channels = emptyList(),
                slackConnectRequired = true,
                totalBeforeFilter = channels.size
            )
        }
        // 상한 이내의 채널에 대해서만 멤버십 확인을 수행한다.
        val targetChannels = channels.take(MAX_PRIVATE_CHANNELS)
        val memberChannels = targetChannels.filter { channel ->
            val members = slackMessageSender.getChannelMembers(botToken, channel.id)
            slackMemberId in members
        }
        return SlackChannelListResponse(
            channels = memberChannels.map {
                SlackChannelDto(id = it.id, name = it.name, isPrivate = it.isPrivate)
            },
            totalBeforeFilter = channels.size
        )
    }

    /**
     * Slack 검증 입력값을 DM/빈 값/채널 ID 규칙에 맞게 정규화한다.
     */
    private fun normalizeSlackVerificationTarget(slackChannelId: String?): String? {
        val normalizedDestination = normalizeOptionalValue(slackChannelId)
        // DM 또는 빈 목적지는 auth.test만으로 접근 상태를 확인한다.
        // DM 대상(D: DM 채널, U: 사용자 멤버 ID)은 채널 검증을 건너뛴다.
        if (normalizedDestination.isNullOrBlank() || normalizedDestination.uppercase().let { it.startsWith("D") || it.startsWith("U") }) {
            return normalizedDestination
        }
        // 공개/비공개 채널은 올바른 Slack 채널 ID 형식일 때만 검증 API를 호출한다.
        val normalizedChannelId = SlackChannelIdNormalizer.normalize(normalizedDestination)
        ensureValid(!normalizedChannelId.isNullOrBlank()) { "Slack 채널 ID 형식이 올바르지 않습니다." }
        return normalizedChannelId
    }

    private fun normalizeOptionalValue(value: String?): String? =
        value?.trim()?.ifBlank { null }
}
