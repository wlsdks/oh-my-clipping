package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.OpsRequestNotificationEvent

import com.clipping.mcpserver.service.notification.OperationsNotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import com.clipping.mcpserver.service.dto.ApproveClippingRequestCommand
import com.clipping.mcpserver.error.ConflictException
import com.clipping.mcpserver.error.InvalidStateException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.error.ensureValid
import com.clipping.mcpserver.model.AccountApprovalStatus
import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.model.SourceLegalBasis
import com.clipping.mcpserver.model.UserClippingRequest
import com.clipping.mcpserver.model.UserClippingRequestStatus
import com.clipping.mcpserver.security.UrlSafetyValidator
import com.clipping.mcpserver.service.event.SubscriptionReviewNotificationEvent
import com.clipping.mcpserver.service.event.SubscriptionReviewNotificationType
import com.clipping.mcpserver.service.source.CategorySourceBuilder
import com.clipping.mcpserver.service.dto.EntryDto
import com.clipping.mcpserver.service.dto.EntryError
import com.clipping.mcpserver.service.dto.EntryErrorReason
import com.clipping.mcpserver.service.dto.SubmitWithEntriesRequest
import com.clipping.mcpserver.service.dto.SubmitWithEntriesResponse
import com.clipping.mcpserver.service.dto.UpdateUserSubscriptionPreferenceCommand
import com.clipping.mcpserver.service.dto.UserAdditionalRssSourcesSubmission
import com.clipping.mcpserver.service.dto.UserClippingRequestSubmission
import com.clipping.mcpserver.service.dto.UserRequestDeliveryStatusView
import com.clipping.mcpserver.service.dto.UserRssSourceSubmission
import com.clipping.mcpserver.service.dto.UserSubscriptionPreferenceView
import com.clipping.mcpserver.service.dto.ApprovalResult
import com.clipping.mcpserver.service.tx.publishAfterCommit
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.CompetitorWatchlistStore
import com.clipping.mcpserver.store.RssSourceStore
import com.clipping.mcpserver.store.UserClippingRequestStore
import com.clipping.mcpserver.model.DeliveryPreset
import com.clipping.mcpserver.model.UserDeliverySchedule
import com.clipping.mcpserver.store.UserDeliveryScheduleStore
import com.clipping.mcpserver.model.OrganizationOrigins
import com.clipping.mcpserver.support.InputSanitizer
import com.clipping.mcpserver.support.SlackMentionGuard
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 사용자 클리핑 요청의 등록/승인/반려 흐름을 관리한다.
 */
private val log = KotlinLogging.logger {}

@Service
class UserClippingRequestService(
    private val requestStore: UserClippingRequestStore,
    private val adminUserStore: AdminUserStore,
    private val adminPersonaService: AdminPersonaService,
    private val adminCategoryService: AdminCategoryService,
    private val categoryStore: CategoryStore,
    private val adminCategoryRuleService: AdminCategoryRuleService,
    private val adminSourceService: AdminSourceService,
    private val sourceStore: RssSourceStore,
    private val slackMessageSender: SlackMessageSender,
    private val runtimeSettingService: RuntimeSettingService,
    private val urlSafetyValidator: UrlSafetyValidator,
    private val userSetupOwnershipService: UserSetupOwnershipService,
    private val auditLogStore: AuditLogStore,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val userDeliveryScheduleStore: UserDeliveryScheduleStore,
    private val operationsNotificationService: OperationsNotificationService,
    private val competitorWatchlistStore: CompetitorWatchlistStore,
    private val organizationService: OrganizationService,
    private val categoryRuleStore: CategoryRuleStore,
    private val categorySourceBuilder: CategorySourceBuilder
) {
    private val objectMapper = jacksonObjectMapper()
    /** 한 번에 추가 가능한 RSS 소스 최대 건수 */
    private val MAX_ADDITIONAL_SOURCES_PER_BATCH = 10
    /** 월간 신청 요청 생성 한도 */
    private val MAX_MONTHLY_NEW_REQUESTS = 5
    private val additionalSourceBaseRequestNotePattern = Regex("""\[baseRequestId=([^\]]+)]""")

    companion object {
        /** 요청 이름 최대 길이 — DB: user_clipping_requests.request_name VARCHAR(120) */
        const val REQUEST_NAME_MAX = 120

        /** 소스 이름 최대 길이 — DB: user_clipping_requests.source_name VARCHAR(120) */
        const val SOURCE_NAME_MAX = 120

        /** 소스 URL 최대 길이 — DB: user_clipping_requests.source_url VARCHAR(2000) */
        const val SOURCE_URL_MAX = 2000

        /** 페르소나 이름 최대 길이 — DB: user_clipping_requests.persona_name VARCHAR(120) */
        const val PERSONA_NAME_MAX = 120

        /** 페르소나 프롬프트 최대 길이 — LLM maxInputTokens 보호용 */
        const val PERSONA_PROMPT_MAX = 5000

        /** 요청 메모 최대 길이 */
        const val REQUEST_NOTE_MAX = 1000
    }

    /**
     * 로그인 사용자가 등록한 본인 요청 목록을 조회한다.
     */
    fun listOwnRequests(requesterUsername: String): List<UserClippingRequest> {
        // 조회 권한이 있는 USER 계정인지 먼저 확인한다.
        val requester = requireUserRequester(requesterUsername, "access user requests")
        return requestStore.listByRequesterUserId(requester.id)
    }

    /**
     * 승인된 내 구독의 즉시 반영 설정을 조회한다.
     */
    fun getImmediateSubscriptionPreference(
        requesterUsername: String,
        requestId: String
    ): UserSubscriptionPreferenceView {
        // 승인된 본인 구독만 즉시 반영 설정을 조회할 수 있다.
        val request = requireOwnedApprovedRequest(requesterUsername, requestId)
        return buildPreferenceView(request)
    }

    /**
     * 승인된 내 구독의 즉시 반영 설정을 저장한다.
     */
    fun updateImmediateSubscriptionPreference(
        requesterUsername: String,
        requestId: String,
        command: UpdateUserSubscriptionPreferenceCommand
    ): UserSubscriptionPreferenceView {
        // 승인된 본인 구독만 즉시 반영 설정을 수정할 수 있다.
        val request = requireOwnedApprovedRequest(requesterUsername, requestId)
        val categoryId = request.approvedCategoryId ?: throw NotFoundException("Category not found for request: $requestId")
        // 카테고리 본문 설정과 규칙 설정을 각각 필요한 경우에만 갱신한다.
        updateImmediateCategoryPreference(categoryId, command)
        updateImmediateRulePreference(requesterUsername, categoryId, command)

        return buildPreferenceView(request)
    }

    /**
     * 위자드에서 직접 생성한 리소스의 소유권 레코드를 자동 APPROVED 상태로 등록한다.
     * 이미 동일 카테고리에 대한 APPROVED 레코드가 있으면 중복 생성하지 않는다.
     */
    @Transactional
    fun registerWizardOwnership(
        requesterUsername: String,
        requestName: String,
        sourceName: String,
        sourceUrl: String,
        slackChannelId: String,
        personaName: String,
        personaPrompt: String,
        summaryStyle: String?,
        targetAudience: String?,
        selectedPresetId: String?,
        categoryId: String,
        personaId: String?,
        sourceId: String?
    ): UserClippingRequest {
        val requester = requireUserRequester(requesterUsername, "register wizard ownership")
        // 위자드의 DM 선택도 저장 전에 사용자 본인의 DM 채널 ID로 치환한다.
        val resolvedSlackChannelId = resolveRequestedSlackDestination(requester, slackChannelId)
        // 구독 한도 체크: 승인된 구독 + 검토 중 요청 합산 5개 초과 불가
        validateSubscriptionLimit(requester.id)
        // 프리셋 사용 시 persona 소유권 등록 스킵(프리셋 공유 리소스이므로 개인 소유 매핑 불필요)
        userSetupOwnershipService.registerOwnedResources(
            userId = requester.id,
            categoryId = categoryId,
            personaId = if (selectedPresetId != null) null else personaId,
            sourceId = sourceId
        )
        // 이미 동일 카테고리에 대한 APPROVED 소유권이 존재하면 기존 레코드를 반환한다.
        val existing = requestStore.listByRequesterUserId(requester.id).find { req ->
            req.isApprovedForCategory(categoryId)
        }
        if (existing != null) return existing

        val saved = requestStore.save(
            UserClippingRequest(
                id = "",
                requesterUserId = requester.id,
                requestName = requestName.trim(),
                sourceName = sourceName.trim(),
                sourceUrl = sourceUrl.trim(),
                slackChannelId = resolvedSlackChannelId,
                personaName = personaName.trim(),
                personaPrompt = personaPrompt.trim(),
                summaryStyle = summaryStyle?.trim()?.ifBlank { null },
                targetAudience = targetAudience?.trim()?.ifBlank { null },
                selectedPresetId = selectedPresetId,
                requestNote = "[위자드] 빠른 세팅으로 등록 — 관리자 검토 대기",
                status = UserClippingRequestStatus.PENDING,
                reviewNote = null,
                reviewedByUserId = null,
                reviewedAt = null,
                approvedCategoryId = categoryId,
                approvedPersonaId = personaId,
                approvedSourceId = sourceId
            )
        )
        // 운영 요청 알림은 DB 커밋 이후에 보내 저장 실패와 외부 알림 불일치를 막는다.
        publishAfterCommit {
            runCatching {
                operationsNotificationService.sendOpsRequest(
                    OpsRequestNotificationEvent.SUBSCRIPTION_SUBMITTED,
                    ":memo: 새 구독 요청 (위자드) — ${requester.username} / \"${requestName.trim()}\""
                )
            }
        }
        return saved
    }

    /**
     * 카테고리 ID로 이름을 조회한다. 존재하지 않으면 null을 반환한다.
     */
    fun resolveCategoryName(categoryId: String): String? =
        categoryStore.findById(categoryId)?.name

    /**
     * 관리자 화면에서 전체 요청을 상태별로 조회한다.
     */
    fun listAllRequests(status: UserClippingRequestStatus?): List<UserClippingRequest> =
        requestStore.listAll(status)

    /**
     * 관리자 목록과 MCP 응답에서 사용할 최근 요청을 제한 조회한다.
     * UI size/도구 limit만큼 DB에서 가져와 대량 요청 테이블의 메모리 사용을 제한한다.
     */
    fun listRecentRequests(status: UserClippingRequestStatus?, limit: Int): List<UserClippingRequest> =
        requestStore.listRecent(status, limit)

    /**
     * ID 로 요청 단건을 조회한다. MCP 도구에서 승인 전 상태/메타 확인에 사용한다.
     * 존재하지 않으면 null 을 반환한다.
     */
    fun findRequestById(requestId: String): UserClippingRequest? =
        requestStore.findById(requestId)

    /**
     * 신청자(requesterUserId) 의 사용자명을 반환한다.
     * MCP echo 확인 문구 조립에 쓰이며, 존재하지 않는 유저 id 는 null 을 돌려준다.
     */
    fun findRequesterUsername(requesterUserId: String): String? =
        adminUserStore.findById(requesterUserId)?.username

    /**
     * 요청 승인 여부와 별도로 실제 전달 가능 상태를 계산한다.
     */
    fun getDeliveryStatus(request: UserClippingRequest): UserRequestDeliveryStatusView =
        when (request.status) {
            UserClippingRequestStatus.PENDING -> deliveryStatus("PENDING_REVIEW")
            UserClippingRequestStatus.REJECTED -> deliveryStatus("REJECTED")
            UserClippingRequestStatus.WITHDRAWN -> deliveryStatus("WITHDRAWN")
            UserClippingRequestStatus.APPROVED -> resolveApprovedDeliveryStatus(request)
        }

    /**
     * 사용자 신규 클리핑 요청을 등록한다.
     */
    fun submitRequest(
        requesterUsername: String,
        submission: UserClippingRequestSubmission
    ): UserClippingRequest {
        // 요청 생성 주체가 USER 역할인지 확인한다.
        val requester = requireUserRequester(requesterUsername, "submit requests")
        // 중복 제출 방지: 같은 사용자가 동일 이름으로 PENDING 요청이 있으면 거부한다.
        val existingPending = requestStore.listByRequesterUserId(requester.id)
            .filter { it.status == UserClippingRequestStatus.PENDING }
            .any { it.requestName.equals(submission.requestName.trim(), ignoreCase = true) }
        ensureValid(!existingPending) { "동일한 이름의 검토 대기 중인 요청이 이미 있습니다." }
        // 월간 생성 제한: 이번 달 유효 요청이 3건 이상이면 거부한다.
        ensureMonthlyCreationLimitNotExceeded(requester.id)
        // 구독 한도 체크: 승인된 구독 + 검토 중 요청 합산 5개 초과 불가
        validateSubscriptionLimit(requester.id)
        // DM 의도 입력은 저장 전에 사용자 본인의 실제 DM 채널 ID로 정규화한다.
        val resolvedSlackChannelId = resolveRequestedSlackDestination(requester, submission.slackChannelId)
        // 공개 채널 중복 구독 방지: PENDING/APPROVED 요청이 이미 있는 채널이면 거부한다.
        validateChannelNotOccupied(resolvedSlackChannelId)
        // 필수 입력값과 URL 안전성은 저장 전에 공통 검증으로 묶는다.
        validateRequestSubmission(submission.copy(slackChannelId = resolvedSlackChannelId))
        // 외부 URL은 SSRF/사설망 접근을 막기 위해 안전 검증 후 저장한다.
        val safeSourceUrl = urlSafetyValidator.validatePublicHttpUrl(submission.sourceUrl).toString()

        // 사용자 입력 필드는 저장 전에 Slack 멘션 패턴을 중립화한다.
        val saved = requestStore.save(
            UserClippingRequest(
                id = "",
                requesterUserId = requester.id,
                requestName = SlackMentionGuard.neutralize(submission.requestName.trim()),
                sourceName = SlackMentionGuard.neutralize(submission.sourceName.trim()),
                sourceUrl = safeSourceUrl,
                slackChannelId = resolvedSlackChannelId,
                personaName = SlackMentionGuard.neutralize(submission.personaName.trim()),
                personaPrompt = submission.personaPrompt.trim(),
                summaryStyle = SlackMentionGuard.neutralizeOrNull(submission.summaryStyle?.trim()?.ifBlank { null }),
                targetAudience = SlackMentionGuard.neutralizeOrNull(submission.targetAudience?.trim()?.ifBlank { null }),
                selectedPresetId = submission.selectedPresetId,
                requestNote = SlackMentionGuard.neutralizeOrNull(submission.requestNote?.trim()?.ifBlank { null })
            )
        )
        // 운영 요청 알림은 DB 커밋 이후에 보내 저장 실패와 외부 알림 불일치를 막는다.
        publishAfterCommit {
            runCatching {
                operationsNotificationService.sendOpsRequest(
                    OpsRequestNotificationEvent.SUBSCRIPTION_SUBMITTED,
                    ":memo: 새 구독 요청 — ${requester.username} / \"${submission.requestName.trim()}\""
                )
            }
        }
        return saved
    }

    /**
     * 승인된 기존 요청을 기준으로 RSS 소스 추가 요청을 여러 건 등록한다.
     */
    @Transactional
    fun submitAdditionalRssSources(
        requesterUsername: String,
        submission: UserAdditionalRssSourcesSubmission
    ): List<UserClippingRequest> {
        // 추가 RSS 요청도 기존 요청자 본인만 등록할 수 있다.
        val requester = requireUserRequester(requesterUsername, "submit requests")
        // 한 번에 추가 가능한 소스 수를 제한한다 (최대 10건)
        ensureValid(submission.sources.size <= MAX_ADDITIONAL_SOURCES_PER_BATCH) {
            "한 번에 최대 ${MAX_ADDITIONAL_SOURCES_PER_BATCH}개의 소스만 추가할 수 있습니다."
        }
        // 기준 요청과 소스 목록의 기본 유효성을 먼저 확인한다.
        validateAdditionalSourcesSubmission(submission)
        // 기준 요청은 본인 소유 + APPROVED 상태여야 한다.
        val baseRequest = requireApprovedBaseRequest(submission.baseRequestId, requester.id)
        // 공통 메모를 정규화해 각 하위 요청에 동일 적용한다.
        val normalizedNote = normalizeOptionalValue(submission.requestNote)
        // 중복 제출 방지: 같은 baseRequestId로 이미 PENDING인 URL은 건너뛴다.
        val existingPendingUrls = requestStore.listByRequesterUserId(requester.id)
            .filter { it.status == UserClippingRequestStatus.PENDING }
            .filter { it.requestNote?.contains("[baseRequestId=${baseRequest.id}]") == true }
            .mapNotNull { it.sourceUrl }
            .toSet()
        val newSources = submission.sources.filter { it.sourceUrl !in existingPendingUrls }
        ensureValid(newSources.isNotEmpty()) { "모든 소스가 이미 대기 중인 요청에 포함되어 있습니다." }
        return newSources.map { source ->
            saveAdditionalSourceRequest(requester.id, baseRequest, source, normalizedNote)
        }
    }

    /**
     * 사용자가 승인된(APPROVED) 구독을 해제한다.
     * 상태를 WITHDRAWN으로 변경하고 감사 로그를 남긴다.
     */
    @Transactional
    fun unsubscribeRequest(requestId: String, requesterUsername: String): UserClippingRequest {
        // 구독 해제 주체가 USER 역할인지 확인한다.
        val requester = requireUserRequester(requesterUsername, "unsubscribe")
        val request = requestStore.findById(requestId)
            ?: throw NotFoundException("Request not found: $requestId")
        // 본인 구독만 해제할 수 있다.
        ensureValid(request.isOwnedBy(requester.id)) { "본인의 구독만 해제할 수 있습니다." }
        // 승인 상태의 구독만 해제할 수 있다.
        ensureValid(request.isApproved()) { "활성 구독만 해제할 수 있습니다." }
        // 공유 카테고리 모델을 보호하기 위해 요청 상태만 WITHDRAWN으로 바꾼다.
        val unsubscribed = requestStore.update(request.unsubscribeApproved())
        // 구독 해제 감사 로그 기록
        auditLogStore.log(
            requester.id, requester.username, "UNSUBSCRIBE",
            "SUBSCRIPTION", request.id, request.requestName
        )
        return unsubscribed
    }

    /**
     * 사용자가 검토 전(PENDING) 요청을 직접 철회한다.
     */
    fun withdrawRequest(requestId: String, requesterUsername: String): UserClippingRequest {
        // 철회 주체가 USER 역할인지 확인한다.
        val requester = requireUserRequester(requesterUsername, "withdraw requests")
        val request = requestStore.findById(requestId)
            ?: throw NotFoundException("Request not found: $requestId")
        // 본인 요청만 철회할 수 있다.
        ensureValid(request.isOwnedBy(requester.id)) { "본인의 요청만 철회할 수 있습니다." }
        // 이미 관리자 검토가 완료된 요청은 철회할 수 없다.
        ensureValid(request.isPendingReview()) { "검토 전 요청만 철회할 수 있습니다." }
        val withdrawn = requestStore.update(request.withdrawPending())
        // 운영 요청 알림은 DB 커밋 이후에 보내 저장 실패와 외부 알림 불일치를 막는다.
        publishAfterCommit {
            runCatching {
                operationsNotificationService.sendOpsRequest(
                    OpsRequestNotificationEvent.SUBSCRIPTION_WITHDRAWN,
                    ":back: 구독 철회 — ${requester.username} / \"${request.requestName}\""
                )
            }
        }
        return withdrawn
    }

    /**
     * 사용자가 반려(REJECTED) 또는 철회(WITHDRAWN) 상태의 요청을 삭제한다.
     * 완료(APPROVED)나 검토 중(PENDING)인 요청은 삭제할 수 없다.
     */
    fun deleteRequest(requestId: String, requesterUsername: String) {
        val requester = requireUserRequester(requesterUsername, "delete requests")
        val request = requestStore.findById(requestId)
            ?: throw NotFoundException("Request not found: $requestId")
        // 본인 요청만 삭제할 수 있다.
        ensureValid(request.isOwnedBy(requester.id)) { "본인의 요청만 삭제할 수 있습니다." }
        // 반려 또는 철회 상태만 삭제 가능하다.
        ensureValid(request.isDeletable()) { "반려 또는 철회된 요청만 삭제할 수 있습니다." }
        requestStore.delete(requestId)
    }

    /**
     * 승인된 구독의 표시 이름을 변경한다.
     * 이름은 1~60자 이내여야 하며, 본인의 APPROVED 요청만 이름을 바꿀 수 있다.
     */
    fun renameRequest(requestId: String, requesterUsername: String, newName: String): UserClippingRequest {
        // 이름 변경 주체가 USER 역할인지 확인한다.
        val requester = requireUserRequester(requesterUsername, "rename requests")
        val trimmedName = newName.trim()
        // 이름 길이 제약을 검증한다.
        ensureValid(trimmedName.length in 1..60) { "이름은 1~60자 이내여야 합니다." }
        val request = requestStore.findById(requestId)
            ?: throw NotFoundException("Request not found: $requestId")
        // 본인 구독만 이름을 변경할 수 있다.
        ensureValid(request.isOwnedBy(requester.id)) { "본인의 구독만 이름을 변경할 수 있습니다." }
        // 승인 상태의 구독만 이름을 변경할 수 있다.
        ensureValid(request.isApproved()) { "승인된 구독만 이름을 변경할 수 있습니다." }
        return requestStore.update(request.copy(requestName = trimmedName))
    }

    /**
     * 여러 요청을 일괄 승인한다.
     * 빈 목록은 InvalidInputException으로 거부하고,
     * 부분 실패를 허용하여 각 항목별 결과를 개별 리포트한다.
     *
     * @throws InvalidInputException 대상 ID 목록이 비어있는 경우
     */
    fun bulkApprove(
        ids: List<String>,
        reviewerUsername: String,
        command: ApproveClippingRequestCommand
    ): List<Pair<String, Result<UserClippingRequest>>> {
        // 빈 목록은 서비스 레이어에서 즉시 거부한다.
        ensureValid(ids.isNotEmpty()) { "처리할 요청 ID 목록이 비어있습니다." }
        return ids.map { id ->
            id to runCatching { approveRequest(id, reviewerUsername, command) }
        }
    }

    /**
     * 여러 요청을 일괄 반려한다.
     * 빈 목록은 InvalidInputException으로 거부하고,
     * 부분 실패를 허용하여 각 항목별 결과를 개별 리포트한다.
     *
     * @throws InvalidInputException 대상 ID 목록이 비어있는 경우
     */
    fun bulkReject(
        ids: List<String>,
        reviewerUsername: String,
        reviewNote: String?
    ): List<Pair<String, Result<UserClippingRequest>>> {
        // 빈 목록은 서비스 레이어에서 즉시 거부한다.
        ensureValid(ids.isNotEmpty()) { "처리할 요청 ID 목록이 비어있습니다." }
        return ids.map { id ->
            id to runCatching { rejectRequest(id, reviewerUsername, reviewNote) }
        }
    }

    /**
     * 관리자 승인 시 페르소나/카테고리/소스를 생성하고 요청 상태를 APPROVED로 전환한다.
     * command에는 어드민이 명시적으로 결정한 법적 검토 정보가 담긴다.
     */
    @Transactional
    fun approveRequest(
        requestId: String,
        reviewerUsername: String,
        command: ApproveClippingRequestCommand
    ): UserClippingRequest {
        // 위자드 요청(form_entries 있음)은 신규 경로로 위임.
        // wizard 요청은 유저가 keyword/company를 이미 명시한 형태라 legalBasis/summaryAllowed/fulltextAllowed
        // 등 legacy legal 메타를 요구하지 않는다. Phase A+B 설계 의도.
        val formEntriesJson = requestStore.findFormEntries(requestId)
        if (formEntriesJson != null) {
            approveRequestWithEntries(requestId, reviewerUsername)
            return requestStore.findById(requestId)
                ?: throw NotFoundException("Request $requestId disappeared after approval")
        }
        // 승인자는 ADMIN 권한이어야 한다.
        val reviewer = requireAdminReviewer(reviewerUsername, "approve requests")
        // 법적 근거 enum 검증 (서비스 이중 검증 — 컨트롤러 @Pattern과 별개)
        try {
            SourceLegalBasis.valueOf(command.legalBasis)
        } catch (e: IllegalArgumentException) {
            ensureValid(false) { "올바르지 않은 법적 근거: ${command.legalBasis}" }
        }
        // 메모 길이 검증 (200자 초과 방지)
        val notes = command.reviewNotes?.takeIf { it.isNotBlank() }
        ensureValid((notes?.length ?: 0) <= 200) { "검토 메모는 200자 이내여야 합니다" }
        // 이미 처리된 요청 재승인을 막기 위해 PENDING 상태를 강제한다.
        val request = requirePendingRequest(requestId)
        // 관리자가 채널을 재지정한 경우 override를 우선 사용하고, 없으면 저장된 채널을 정규화해 사용한다.
        val overrideSlackChannelId = command.overrideSlackChannelId
        val resolvedSlackChannelId = if (!overrideSlackChannelId.isNullOrBlank()) {
            overrideSlackChannelId.trim()
        } else {
            resolveStoredSlackDestination(request)
        }
        // RSS 추가 요청이면 기존 주제의 전달 정책을 재사용하고, 아니면 신규 리소스를 생성한다.
        val approvedIds = provisionApprovedRequestResources(request, resolvedSlackChannelId, reviewer.username, command)
        // 승인 직후 user setup API에서 접근 가능하도록 owner 매핑을 함께 남긴다.
        // 프리셋 페르소나는 공유 리소스이므로 개인 소유 매핑에 등록하지 않는다.
        userSetupOwnershipService.registerOwnedResources(
            userId = request.requesterUserId,
            categoryId = approvedIds.first,
            personaId = if (request.selectedPresetId != null) null else approvedIds.second,
            sourceId = approvedIds.third
        )
        val approved = requestStore.update(
            request.approve(
                reviewerUserId = reviewer.id,
                slackChannelId = resolvedSlackChannelId,
                reviewNote = notes,
                approvedCategoryId = approvedIds.first,
                approvedPersonaId = approvedIds.second,
                approvedSourceId = approvedIds.third
            )
        )
        // 승인 감사 로그 기록 (정책 정보 포함)
        auditLogStore.log(
            reviewer.id, reviewer.username, "APPROVE",
            "SUBSCRIPTION", request.id,
            "${request.requestName} · 법적: ${command.legalBasis} · 요약:${command.summaryAllowed} · 전문:${command.fulltextAllowed}"
        )
        // 사용자 DM 알림은 커밋 이후에만 보내 롤백된 승인 결과가 먼저 노출되지 않게 한다.
        publishSubscriptionReviewNotification(
            userId = request.requesterUserId,
            requestName = request.requestName,
            reviewType = SubscriptionReviewNotificationType.APPROVED,
            reviewNote = null
        )
        // 운영 요청 알림은 DB 커밋 이후에 보내 승인 롤백과 외부 알림 불일치를 막는다.
        publishAfterCommit {
            runCatching {
                val requester = adminUserStore.findById(request.requesterUserId)
                operationsNotificationService.sendOpsRequest(
                    OpsRequestNotificationEvent.SUBSCRIPTION_APPROVED_OPS,
                    ":white_check_mark: 구독 승인 — ${requester?.username ?: request.requesterUserId} / \"${request.requestName}\""
                )
            }
        }
        return approved
    }

    /**
     * 위자드 요청(form_entries 포함)을 승인한다.
     *
     * form_entries 가 있으면 새 경로:
     * 1. 카테고리 생성 (persona 없이 requestName 사용)
     * 2. keyword entry → include_keywords 설정
     * 3. company entry → organization upsert + category 링크
     * 4. CategorySourceBuilder.syncSourcesForCategory 호출
     * 5. request 상태 APPROVED 로 전환
     *
     * form_entries 가 NULL 이면 NotFoundException: 이 overload 는 위자드 전용.
     */
    @Transactional
    fun approveRequestWithEntries(
        requestId: String,
        approverUsername: String
    ): ApprovalResult {
        // 승인 대상 요청을 조회한다.
        val request = requestStore.findById(requestId)
            ?: throw NotFoundException("Request $requestId not found")
        // 이 경로는 PENDING 상태의 위자드 요청만 처리한다.
        if (!request.isPendingReview()) {
            throw InvalidStateException("Only pending requests can be approved")
        }

        // form_entries JSON 을 로드하여 파싱한다. NULL 이면 레거시 경로로 처리해야 한다.
        val formEntriesJson = requestStore.findFormEntries(requestId)
            ?: throw NotFoundException("Request $requestId has no form_entries — use legacy approveRequest")
        val entries: List<EntryDto> = objectMapper.readValue(formEntriesJson)

        val keywords = entries.filter { it.type == "keyword" }.map { it.value }
        val companies = entries.filter { it.type == "company" }

        // 1. 카테고리 생성 — persona 없이 requestName 기준으로 비공개 카테고리를 만든다.
        val normalizedName = request.requestName.trim()
        ensureValid(normalizedName.isNotBlank()) { "requestName is required" }
        val category = categoryStore.save(
            com.clipping.mcpserver.model.Category(
                id = "",
                name = normalizedName,
                description = "위자드 요청 승인으로 생성",
                slackChannelId = null,
                isPublic = false,
                maxItems = 5,
                personaId = null
            )
        )
        val categoryId = category.id

        // 2. keyword entry 를 include_keywords 로 저장한다.
        if (keywords.isNotEmpty()) {
            categoryRuleStore.setIncludeKeywords(categoryId, keywords)
        }

        // 3. company entry 별로 organization upsert 후 category 에 링크한다.
        companies.forEach { company ->
            val org = organizationService.upsertByStockCodeOrName(
                tenantId = "default",
                name = company.value,
                stockCode = company.stockCode,
                origin = OrganizationOrigins.USER_WIZARD
            )
            organizationService.linkToCategoryIfAbsent(categoryId, org.id)
        }

        // 4. include_keywords + linked orgs 기반으로 RSS sources 를 자동 생성한다.
        categorySourceBuilder.syncSourcesForCategory(categoryId)

        // 4-1. 위자드 구독 승인은 "소스 승인 포함"이 정책이다.
        // syncSourcesForCategory 가 만든 미승인 소스를 자동으로 crawl_approved + VERIFIED 로 표시해
        // CollectionService 가 다음 사이클에 바로 수집할 수 있게 한다.
        // 승인자는 요청을 승인한 관리자. legal basis 기본값(QUOTATION_ONLY/summary=true)은 카테고리 생성 시 이미 세팅됨.
        val createdSources = sourceStore.listByCategoryId(categoryId)
        createdSources.forEach { source ->
            if (!source.crawlApproved) {
                sourceStore.updateApproval(source.id, approved = true, approvedBy = approverUsername)
                sourceStore.updateVerificationStatus(source.id, "VERIFIED")
            }
        }

        // 5. 요청 상태를 APPROVED 로 전환하고 저장한다.
        // reviewed_by_user_id 는 UUID FK — 승인자 username 을 ID 로 변환한다.
        val approverUserId = adminUserStore.findByUsername(approverUsername)?.id
        val now = java.time.Instant.now()
        val approved = request.copy(
            status = UserClippingRequestStatus.APPROVED,
            approvedCategoryId = categoryId,
            reviewedByUserId = approverUserId,
            reviewedAt = now,
            updatedAt = now
        )
        requestStore.update(approved)

        return ApprovalResult(requestId = requestId, createdCategoryId = categoryId)
    }

    /**
     * 관리자 반려 시 반려 메모를 남기고 요청 상태를 REJECTED로 전환한다.
     */
    @Transactional
    fun rejectRequest(requestId: String, reviewerUsername: String, reviewNote: String?): UserClippingRequest {
        // 반려 처리도 ADMIN 권한을 가진 계정만 수행한다.
        val reviewer = requireAdminReviewer(reviewerUsername, "reject requests")
        // 반려 대상은 아직 검토 전(PENDING) 요청이어야 한다.
        val request = requirePendingRequest(requestId)
        // 반려 사유는 운영 추적을 위해 공백 제거 후 필수로 검증한다.
        val normalizedReviewNote = normalizeOptionalValue(reviewNote)
        ensureValid(!normalizedReviewNote.isNullOrBlank()) { "reviewNote is required when rejecting a request" }
        val requiredReviewNote = normalizedReviewNote ?: error("validated above")

        val rejected = requestStore.update(
            request.reject(
                reviewerUserId = reviewer.id,
                reviewNote = requiredReviewNote
            )
        )
        // 반려 감사 로그 기록
        auditLogStore.log(
            reviewer.id, reviewer.username, "REJECT",
            "SUBSCRIPTION", request.id, request.requestName, requiredReviewNote
        )
        // 사용자 DM 알림은 커밋 이후에만 보내 롤백된 반려 결과가 먼저 노출되지 않게 한다.
        publishSubscriptionReviewNotification(
            userId = request.requesterUserId,
            requestName = request.requestName,
            reviewType = SubscriptionReviewNotificationType.REJECTED,
            reviewNote = requiredReviewNote
        )
        // 운영 요청 알림은 DB 커밋 이후에 보내 반려 롤백과 외부 알림 불일치를 막는다.
        publishAfterCommit {
            runCatching {
                val requester = adminUserStore.findById(request.requesterUserId)
                operationsNotificationService.sendOpsRequest(
                    OpsRequestNotificationEvent.SUBSCRIPTION_REJECTED_OPS,
                    ":x: 구독 반려 — ${requester?.username ?: request.requesterUserId} / \"${request.requestName}\""
                )
            }
        }
        return rejected
    }

    /** 승인/반려 결과 알림을 AFTER_COMMIT 이벤트로 전파한다. */
    private fun publishSubscriptionReviewNotification(
        userId: String,
        requestName: String,
        reviewType: SubscriptionReviewNotificationType,
        reviewNote: String?
    ) {
        // 트랜잭션 내부에서는 이벤트만 발행하고 실제 DM 전송은 커밋 이후 리스너가 맡는다.
        applicationEventPublisher.publishEvent(
            SubscriptionReviewNotificationEvent(
                userId = userId,
                requestName = requestName,
                reviewType = reviewType,
                reviewNote = reviewNote
            )
        )
    }

    private fun validateAdditionalSourcesSubmission(submission: UserAdditionalRssSourcesSubmission) {
        // 기준 요청 ID와 신규 소스 목록이 존재해야 추가 등록을 진행한다.
        ensureValid(submission.baseRequestId.isNotBlank()) { "baseRequestId is required" }
        ensureValid(submission.sources.isNotEmpty()) { "sources must not be empty" }
    }

    /**
     * 신규 요청 등록 시 필수 입력값 누락을 공통 규칙으로 검증한다.
     * selectedPresetId가 있으면 persona 필드는 빈 값을 허용한다.
     */
    private fun validateRequestSubmission(submission: UserClippingRequestSubmission) {
        // 프런트 우회 호출에도 동일한 필수 입력 규칙을 강제한다.
        // InputSanitizer는 trim + 제어 문자 제거 + 길이 상한을 일관되게 적용한다.
        InputSanitizer.sanitizeRequired(submission.requestName, "요청 이름", REQUEST_NAME_MAX)
        InputSanitizer.sanitizeRequired(submission.sourceName, "소스 이름", SOURCE_NAME_MAX)
        // URL은 scheme 검증 별도 수행 — 여기에서는 길이만 방어한다.
        ensureValid(submission.sourceUrl.isNotBlank()) { "sourceUrl is required" }
        ensureValid(submission.sourceUrl.length <= SOURCE_URL_MAX) {
            "sourceUrl은 ${SOURCE_URL_MAX}자 이하여야 합니다."
        }
        ensureValid(submission.slackChannelId.isNotBlank()) { "slackChannelId is required" }
        // 프리셋 선택 시 persona 필드 빈 값 허용
        if (submission.selectedPresetId == null) {
            InputSanitizer.sanitizeRequired(submission.personaName, "페르소나 이름", PERSONA_NAME_MAX)
            InputSanitizer.sanitizeRequired(submission.personaPrompt, "페르소나 프롬프트", PERSONA_PROMPT_MAX)
        }
        // requestNote는 optional이므로 길이만 방어한다.
        InputSanitizer.sanitizeOptional(submission.requestNote, "요청 메모", REQUEST_NOTE_MAX)
    }

    /**
     * 카테고리 활성 상태와 기사 수 같은 즉시 반영 본문 설정을 갱신한다.
     */
    private fun updateImmediateCategoryPreference(
        categoryId: String,
        command: UpdateUserSubscriptionPreferenceCommand
    ) {
        // 카테고리 본문 설정이 없으면 불필요한 update 호출을 생략한다.
        if (command.isActive == null && command.maxItems == null) return
        adminCategoryService.updateCategory(
            id = categoryId,
            name = null,
            description = null,
            slackChannelId = null,
            isActive = command.isActive,
            isPublic = null,
            maxItems = command.maxItems,
            personaId = null,
            expectedUpdatedAt = null
        )
    }

    /**
     * 제외 키워드와 중요도 기준 같은 즉시 반영 규칙을 갱신한다.
     */
    private fun updateImmediateRulePreference(
        requesterUsername: String,
        categoryId: String,
        command: UpdateUserSubscriptionPreferenceCommand
    ) {
        // 규칙 필드가 없으면 사용자 조회와 rule update를 모두 생략한다.
        val hasRuleChange = command.excludeKeywords != null ||
            command.includeThreshold != null ||
            command.deliveryDays != null ||
            command.deliveryHour != null ||
            command.deliveryPreset != null
        if (!hasRuleChange) return
        val user = requireUserRequester(requesterUsername, "update subscription preferences")
        adminCategoryRuleService.updateCategoryRule(
            categoryId = categoryId,
            includeKeywords = null,
            excludeKeywords = command.excludeKeywords,
            riskTags = null,
            includeThreshold = command.includeThreshold,
            reviewThreshold = command.includeThreshold,
            uncertainToReview = null,
            autoExcludeEnabled = true,
            updatedBy = user.username,
            deliveryDays = command.deliveryDays,
            deliveryHour = command.deliveryHour,
            deliveryPreset = command.deliveryPreset
        )
    }

    /**
     * 즉시 반영 설정 응답 모델을 현재 카테고리/규칙 상태로 조합한다.
     *
     * 카테고리 rule 의 delivery_* 가 null 인 경우(PR #492 이전 승인 레코드, 또는 rule 미생성 상태)는
     * 사용자의 글로벌 [UserDeliverySchedule] 을 폴백으로 merge 해 응답에 채운다.
     * 이렇게 해야 변경 모달이 위자드에서 선택했던 값을 그대로 pre-select 해 보여줄 수 있다.
     */
    private fun buildPreferenceView(request: UserClippingRequest): UserSubscriptionPreferenceView {
        val categoryId = request.approvedCategoryId ?: throw NotFoundException("Category not found for request: ${request.id}")
        // 카테고리와 규칙을 함께 읽어 현재 적용 중인 값을 응답에 담는다.
        val category = adminCategoryService.getCategory(categoryId)
        val rule = adminCategoryRuleService.getCategoryRule(categoryId)
        // 글로벌 스케줄은 rule 에 값이 비어있을 때만 조회해 불필요한 DB 호출을 피한다.
        val needsGlobalFallback = rule.deliveryDays == null || rule.deliveryHour == null || rule.deliveryPreset == null
        val globalSchedule = if (needsGlobalFallback) userDeliveryScheduleStore.findByUserId(request.requesterUserId) else null
        return UserSubscriptionPreferenceView(
            requestId = request.id,
            categoryId = category.id,
            requestName = request.requestName,
            isActive = category.isActive,
            maxItems = category.maxItems,
            excludeKeywords = rule.excludeKeywords,
            includeThreshold = rule.includeThreshold,
            deliveryDays = rule.deliveryDays ?: globalSchedule?.deliveryDays,
            deliveryHour = rule.deliveryHour ?: globalSchedule?.deliveryHour,
            deliveryPreset = rule.deliveryPreset?.name ?: globalSchedule?.preset?.name,
            updatedAt = maxOf(category.updatedAt, rule.updatedAt)
        )
    }

    /**
     * 승인된 요청은 카테고리 활성화 여부와 연결 완료된 RSS 소스 수를 기준으로 실제 전달 상태를 계산한다.
     */
    private fun resolveApprovedDeliveryStatus(request: UserClippingRequest): UserRequestDeliveryStatusView {
        val categoryId = request.approvedCategoryId
            ?: return deliveryStatus("ACTION_REQUIRED")
        val category = categoryStore.findById(categoryId)
            ?: return deliveryStatus("ACTION_REQUIRED")
        val sources = sourceStore.listByCategoryId(categoryId)
        val readySources = sources.filter(::isCollectingReadySource)
        // 대표 소스 상태는 미준비 소스를 우선 노출하고, 모두 준비된 경우에는 첫 준비 소스를 사용한다.
        val representativeSource = sources.firstOrNull { !isCollectingReadySource(it) } ?: readySources.firstOrNull()

        // 사용자가 일시정지를 선택한 경우 가장 우선적으로 paused 상태를 노출한다.
        if (!category.status.isOperational) {
            return deliveryStatus(
                deliveryState = "PAUSED",
                totalSourceCount = sources.size,
                readySourceCount = readySources.size,
                representativeSourceVerificationStatus = representativeSource?.verificationStatus
            )
        }

        // 연결이 확인된 소스가 하나라도 있으면 실제 수집 가능 상태로 본다.
        if (readySources.isNotEmpty()) {
            return deliveryStatus(
                deliveryState = "ACTIVE",
                collectingReady = true,
                totalSourceCount = sources.size,
                readySourceCount = readySources.size,
                representativeSourceVerificationStatus = representativeSource?.verificationStatus
            )
        }

        // 아직 승인된 카테고리에 소스가 없거나 확인 전 상태면 연결 확인 중으로 보여준다.
        val pendingSource = sources.firstOrNull(::isPendingVerificationSource)
        if (sources.isEmpty() || pendingSource != null) {
            return deliveryStatus(
                deliveryState = "VERIFYING_SOURCE",
                totalSourceCount = sources.size,
                readySourceCount = 0,
                representativeSourceVerificationStatus = pendingSource?.verificationStatus
            )
        }

        // 나머지 경우는 사용 불가/검증 실패 등으로 운영 확인이 필요한 상태다.
        return deliveryStatus(
            deliveryState = "ACTION_REQUIRED",
            totalSourceCount = sources.size,
            readySourceCount = 0,
            representativeSourceVerificationStatus = representativeSource?.verificationStatus
        )
    }

    /**
     * 추가 RSS 요청 등록 시 기준이 되는 승인 완료 요청을 조회한다.
     */
    private fun requireApprovedBaseRequest(
        baseRequestId: String,
        requesterUserId: String
    ): UserClippingRequest =
        requireOwnedApprovedBaseRequest(
            baseRequestId = baseRequestId,
            requesterUserId = requesterUserId,
            statusErrorMessage = "Base request must be APPROVED to request additional RSS sources",
            requireApprovedCategory = false
        )

    /**
     * 로그인 사용자가 소유한 승인 완료 구독을 조회한다.
     */
    private fun requireOwnedApprovedRequest(
        requesterUsername: String,
        requestId: String
    ): UserClippingRequest {
        // 요청자 정보와 요청 레코드를 함께 확인해 타인 구독 접근을 차단한다.
        val requester = requireUserRequester(requesterUsername, "access subscription preferences")
        return requireOwnedApprovedBaseRequest(
            baseRequestId = requestId,
            requesterUserId = requester.id,
            notFoundMessage = "Request not found: $requestId",
            ownershipErrorMessage = "본인 구독만 조회할 수 있습니다.",
            statusErrorMessage = "승인된 구독만 즉시 설정을 변경할 수 있습니다.",
            requireApprovedCategory = true,
            missingCategoryMessage = "승인된 카테고리 정보가 없습니다."
        )
    }

    private fun saveAdditionalSourceRequest(
        requesterUserId: String,
        baseRequest: UserClippingRequest,
        source: UserRssSourceSubmission,
        requestNote: String?
    ): UserClippingRequest {
        // 신규 소스명 공백 입력을 방지한다.
        val sourceName = source.sourceName.trim()
        ensureValid(sourceName.isNotBlank()) { "sourceName is required" }
        // 추가 소스 URL도 기존과 동일한 안전 검증 정책을 적용한다.
        val sourceUrl = urlSafetyValidator.validatePublicHttpUrl(source.sourceUrl).toString()
        return requestStore.save(
            UserClippingRequest(
                id = "",
                requesterUserId = requesterUserId,
                requestName = "${baseRequest.requestName} - RSS 추가 ${sourceName.take(40)}",
                sourceName = sourceName,
                sourceUrl = sourceUrl,
                slackChannelId = baseRequest.slackChannelId,
                personaName = baseRequest.personaName,
                personaPrompt = baseRequest.personaPrompt,
                summaryStyle = baseRequest.summaryStyle,
                targetAudience = baseRequest.targetAudience,
                requestNote = buildAdditionalSourceRequestNote(baseRequest.id, requestNote)
            )
        )
    }

    /**
     * 사용자 요청에 들어온 Slack 목적지를 현재 정책 기준으로 정규화한다.
     * DM 의도(blank, `DM`, `D...`)는 사용자 프로필의 실제 DM 채널 ID로 치환한다.
     */
    private fun resolveRequestedSlackDestination(requester: AdminUser, rawChannelId: String): String {
        val normalizedDestination = rawChannelId.trim()
        // DM 의도는 임의 입력값을 저장하지 않고 사용자 프로필의 실제 DM 채널을 사용한다.
        if (isSlackDirectMessageIntent(normalizedDestination)) {
            return resolveRequesterSlackDirectMessageChannel(requester)
        }
        return normalizedDestination
    }

    /**
     * 승인 시점에는 저장된 legacy blank/DM 입력만 현재 DM 정책으로 다시 정규화한다.
     * 이미 공유 채널이나 명시적 DM 채널로 저장된 값은 그대로 사용한다.
     */
    private fun resolveStoredSlackDestination(request: UserClippingRequest): String {
        val storedDestination = request.slackChannelId.trim()
        if (!requiresRequesterSlackResolution(storedDestination)) {
            return storedDestination
        }
        val requester = adminUserStore.findById(request.requesterUserId)
            ?: throw NotFoundException("Requester not found for request: ${request.id}")
        return resolveRequestedSlackDestination(requester, storedDestination)
    }

    /**
     * 사용자 프로필에 저장된 DM 채널 ID를 읽어 요청의 명시적 목적지로 사용한다.
     */
    private fun resolveRequesterSlackDirectMessageChannel(requester: AdminUser): String {
        val dmChannelId = requester.slackDmChannelId?.trim().orEmpty()
        ensureValid(dmChannelId.isNotBlank()) { "Slack DM 채널 ID가 설정되지 않았습니다. 프로필에서 설정해 주세요." }
        ensureValid(isSlackDirectMessageDestination(dmChannelId)) {
            "사용자 DM 채널 ID 형식이 올바르지 않습니다. 프로필에서 다시 연결해 주세요."
        }
        return dmChannelId
    }

    /**
     * Slack DM은 별도 채널 검증 없이 허용하고, 공유 채널만 실제 Slack 연결을 검증한다.
     */
    private fun verifySlackChannelIfNeeded(rawChannelId: String): String? {
        val normalizedChannelId = rawChannelId.trim()
        // 명시적 DM 목적지는 실제 채널 검증 없이 승인 흐름을 계속 진행한다.
        if (isSlackDirectMessageDestination(normalizedChannelId)) {
            return null
        }
        // 런타임 설정 토큰이 있으면 우선 사용해 실제 연결 가능 여부를 점검한다.
        val runtimeToken = runtimeSettingService.current().slackBotToken.takeIf { it.isNotBlank() }
        val slackCheck = slackMessageSender.testConnection(
            botToken = runtimeToken,
            channelId = normalizedChannelId
        )
        // Slack API 응답에서 확정 채널 ID를 추출해 저장 기준으로 사용한다.
        val verifiedChannelId = slackCheck.channelId?.trim().orEmpty()
        ensureValid(slackCheck.ok && verifiedChannelId.isNotBlank()) {
            "Slack 채널 검증 실패: ${slackCheck.rawError ?: "invalid_channel"}"
        }
        return verifiedChannelId
    }

    /**
     * 요청 종류에 따라 신규 주제를 만들지, 기존 주제에 RSS만 덧붙일지 결정한다.
     */
    private fun provisionApprovedRequestResources(
        request: UserClippingRequest,
        resolvedSlackChannelId: String,
        reviewerUsername: String,
        command: ApproveClippingRequestCommand
    ): Triple<String, String?, String> {
        val baseRequest = resolveAdditionalSourceBaseRequest(request)
        if (baseRequest != null) {
            return provisionAdditionalSourceForBaseRequest(request, baseRequest, reviewerUsername, command)
        }

        // 신규 주제 요청은 Slack 채널 정책을 확인한 뒤 전용 리소스를 만든다.
        val verifiedChannelId = verifySlackChannelIfNeeded(resolvedSlackChannelId)
        return provisionApprovedResources(request, resolvedSlackChannelId, verifiedChannelId, reviewerUsername, command)
    }

    private fun provisionApprovedResources(
        request: UserClippingRequest,
        resolvedSlackChannelId: String,
        verifiedChannelId: String?,
        reviewerUsername: String,
        command: ApproveClippingRequestCommand
    ): Triple<String, String?, String> {
        // 프리셋 경로: 기존 프리셋 ID를 직접 사용하고 페르소나 생성을 건너뛴다.
        val selectedPresetId = request.selectedPresetId
        val personaId = if (selectedPresetId != null) {
            selectedPresetId
        } else {
            // 의존 순서(페르소나 -> 카테고리 -> 소스)를 지켜 생성한다.
            createPersonaForRequest(request).id
        }
        val categoryId = createCategoryForRequest(request, resolvedSlackChannelId, verifiedChannelId, personaId).id
        // 카테고리 생성 직후 사용자의 발송 스케줄을 카테고리 규칙으로 복사한다.
        createDefaultDeliveryRuleForCategory(categoryId, request.requesterUserId, reviewerUsername)
        val sourceId = createAndApproveSourceForRequest(request, categoryId, reviewerUsername, command).id
        return Triple(categoryId, personaId, sourceId)
    }

    /**
     * 추가 RSS 요청은 기존 승인 주제의 카테고리/스타일/전달 정책을 재사용한다.
     */
    private fun provisionAdditionalSourceForBaseRequest(
        request: UserClippingRequest,
        baseRequest: UserClippingRequest,
        reviewerUsername: String,
        command: ApproveClippingRequestCommand
    ): Triple<String, String?, String> {
        val categoryId = baseRequest.approvedCategoryId
            ?: throw NotFoundException("Base request category not found: ${baseRequest.id}")
        val sourceId = createAndApproveSourceForRequest(request, categoryId, reviewerUsername, command).id
        return Triple(categoryId, baseRequest.approvedPersonaId, sourceId)
    }

    /**
     * 검토 승인으로 생성되는 페르소나는 사용자가 입력한 표시 이름을 그대로 유지한다.
     */
    private fun createPersonaForRequest(request: UserClippingRequest) =
        adminPersonaService.createPersona(
            name = request.personaName.trim(),
            description = "사용자 요청 승인으로 생성",
            systemPrompt = request.personaPrompt,
            summaryStyle = request.summaryStyle,
            targetAudience = request.targetAudience,
            maxItems = 5,
            language = "ko"
        )

    /**
     * 검토 승인으로 생성되는 카테고리는 사용자가 입력한 표시 이름을 그대로 유지한다.
     */
    private fun createCategoryForRequest(
        request: UserClippingRequest,
        resolvedSlackChannelId: String,
        verifiedChannelId: String?,
        personaId: String
    ): Category {
        val normalizedName = request.requestName.trim()
        // 승인 생성 카테고리도 self-serve와 동일하게 표시 이름 중복을 허용한다.
        ensureValid(normalizedName.isNotBlank()) { "requestName is required" }
        // 같은 채널에 같은 이름의 카테고리가 이미 있는지 확인한다.
        val effectiveChannelId = verifiedChannelId ?: resolvedSlackChannelId
        val existingCategories = categoryStore.findOperational()
        val duplicateInChannel = existingCategories.any {
            it.slackChannelId == effectiveChannelId &&
                it.name.equals(normalizedName, ignoreCase = true)
        }
        ensureValid(!duplicateInChannel) {
            "이 채널에 이미 '${normalizedName}'이(가) 발송 중입니다. 기존 카테고리 구독을 권장합니다."
        }
        // 사용자 요청으로 생성된 카테고리는 비공개로 설정한다.
        return categoryStore.save(
            Category(
                id = "",
                name = normalizedName,
                description = "사용자 요청 승인으로 생성",
                slackChannelId = verifiedChannelId,
                isPublic = false,
                maxItems = 5,
                personaId = personaId
            )
        )
    }

    private fun createAndApproveSourceForRequest(
        request: UserClippingRequest,
        categoryId: String,
        reviewerUsername: String,
        command: ApproveClippingRequestCommand
    ): RssSource {
        // URL 재사용 체크: 동일 URL + 동일 카테고리 소스가 이미 있으면 그대로 재사용한다.
        val existing = sourceStore.findByUrlAndCategoryId(request.sourceUrl, categoryId)
        if (existing != null) {
            return existing
        }
        // 신규 생성: 어드민이 모달에서 선택한 정책을 그대로 적용하고 즉시 활성화한다.
        return adminSourceService.createSource(
            name = request.sourceName,
            url = request.sourceUrl,
            sourceRegionRaw = null,
            emoji = null,
            categoryId = categoryId,
            legalBasisRaw = command.legalBasis,
            summaryAllowed = command.summaryAllowed,
            fulltextAllowed = command.fulltextAllowed,
            reviewNotes = command.reviewNotes ?: "사용자 요청 승인 자동 등록",
            crawlApproved = true,
            approvedBy = reviewerUsername
        )
    }

    /**
     * 신규 카테고리에 사용자의 글로벌 발송 스케줄을 기본값으로 복사한 규칙을 생성한다.
     * 사용자 스케줄이 없으면 기본값(평일 오전 8시)으로 초기화한다.
     * 규칙 생성 실패는 승인 트랜잭션 전체를 롤백하지 않도록 예외를 catch한다.
     */
    private fun createDefaultDeliveryRuleForCategory(
        categoryId: String,
        requesterUserId: String,
        reviewerUsername: String
    ) {
        try {
            // 사용자의 글로벌 발송 스케줄을 조회한다. 없으면 기본값을 사용한다.
            val userSchedule = userDeliveryScheduleStore.findByUserId(requesterUserId)
            val deliveryDays = userSchedule?.deliveryDays ?: listOf("MON", "TUE", "WED", "THU", "FRI")
            val deliveryHour = userSchedule?.deliveryHour ?: 8
            val deliveryPreset = userSchedule?.preset?.name ?: "WEEKDAYS"
            adminCategoryRuleService.updateCategoryRule(
                categoryId = categoryId,
                includeKeywords = null,
                excludeKeywords = null,
                riskTags = null,
                includeThreshold = null,
                reviewThreshold = null,
                uncertainToReview = null,
                autoExcludeEnabled = null,
                updatedBy = reviewerUsername,
                deliveryDays = deliveryDays,
                deliveryHour = deliveryHour,
                deliveryPreset = deliveryPreset
            )
        } catch (e: Exception) {
            log.warn(e) { "발송 규칙 초기화 실패 (categoryId=$categoryId): ${e.message}" }
        }
    }

    /**
     * 추가 RSS 요청이면 requestNote 또는 요청명 규칙에서 기준 구독을 찾아 재사용한다.
     */
    private fun resolveAdditionalSourceBaseRequest(request: UserClippingRequest): UserClippingRequest? {
        val explicitBaseRequestId = extractBaseRequestId(request.requestNote)
        if (!explicitBaseRequestId.isNullOrBlank()) {
            return loadApprovedBaseRequestForAdditionalRequest(request, explicitBaseRequestId)
        }

        // 예전 요청은 note 메타데이터가 없을 수 있어 요청명 접두사로 한 번 더 추론한다.
        val inferredBaseRequestName = extractLegacyAdditionalSourceBaseName(request.requestName) ?: return null
        val matchingRequests = requestStore.listByRequesterUserId(request.requesterUserId)
            .filter { candidate ->
                candidate.isApproved() &&
                    candidate.requestName == inferredBaseRequestName &&
                    !candidate.approvedCategoryId.isNullOrBlank()
            }
        return matchingRequests.singleOrNull()
    }

    /**
     * 메타데이터로 전달된 기준 요청이 실제 같은 사용자 소유의 승인 건인지 확인한다.
     */
    private fun loadApprovedBaseRequestForAdditionalRequest(
        request: UserClippingRequest,
        baseRequestId: String
    ): UserClippingRequest =
        requireOwnedApprovedBaseRequest(
            baseRequestId = baseRequestId,
            requesterUserId = request.requesterUserId,
            statusErrorMessage = "Base request must be APPROVED before approving additional RSS sources",
            requireApprovedCategory = true,
            missingCategoryMessage = "Base request category is missing"
        )

    /**
     * 추가 RSS 요청 메모에 기준 요청 ID를 숨은 메타데이터로 함께 저장한다.
     */
    private fun buildAdditionalSourceRequestNote(baseRequestId: String, requestNote: String?): String =
        listOf("[baseRequestId=$baseRequestId]", normalizeOptionalValue(requestNote))
            .filterNotNull()
            .joinToString(" ")

    private fun extractBaseRequestId(requestNote: String?): String? =
        requestNote
            ?.let(additionalSourceBaseRequestNotePattern::find)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.ifBlank { null }

    private fun extractLegacyAdditionalSourceBaseName(requestName: String): String? {
        val marker = " - RSS 추가 "
        val separatorIndex = requestName.indexOf(marker)
        if (separatorIndex <= 0) return null
        return requestName.substring(0, separatorIndex).trim().ifBlank { null }
    }

    /**
     * 요청 입력 단계에서 DM 의도를 나타내는 값인지 판별한다.
     */
    private fun isSlackDirectMessageIntent(rawChannelId: String): Boolean {
        val normalized = rawChannelId.trim()
        return normalized.isBlank() ||
            normalized.equals("DM", ignoreCase = true) ||
            isSlackDirectMessageDestination(normalized)
    }

    /**
     * 승인 시점에 requester 프로필을 다시 조회해야 하는 legacy DM 입력인지 판별한다.
     */
    private fun requiresRequesterSlackResolution(rawChannelId: String): Boolean {
        val normalized = rawChannelId.trim()
        return normalized.isBlank() || normalized.equals("DM", ignoreCase = true)
    }

    /**
     * 실제 발송 목적지로 저장 가능한 명시적 DM 대상인지 판별한다.
     * D(DM 채널 ID)와 U(사용자 멤버 ID) 모두 Slack DM 발송에 유효하다.
     */
    private fun isSlackDirectMessageDestination(rawChannelId: String): Boolean {
        val normalized = rawChannelId.trim().uppercase()
        return normalized.startsWith("D") || normalized.startsWith("U")
    }

    /**
     * 실제 수집 대상이 되려면 활성화/승인/법적 근거/연결 확인이 모두 통과해야 한다.
     */
    private fun isCollectingReadySource(source: RssSource): Boolean =
        source.isActive &&
            source.crawlApproved &&
            source.summaryAllowed &&
            source.legalBasis != SourceLegalBasis.PROHIBITED &&
            source.verificationStatus.equals("VERIFIED", ignoreCase = true)

    /**
     * 연결 확인이 아직 끝나지 않은 소스 상태를 판별한다.
     */
    private fun isPendingVerificationSource(source: RssSource): Boolean =
        source.verificationStatus.equals("PENDING", ignoreCase = true) ||
            source.verificationStatus.equals("UNKNOWN", ignoreCase = true)

    /**
     * 요청 상태 응답을 동일한 기본값 규칙으로 생성한다.
     */
    private fun deliveryStatus(
        deliveryState: String,
        collectingReady: Boolean = false,
        totalSourceCount: Int = 0,
        readySourceCount: Int = 0,
        representativeSourceVerificationStatus: String? = null
    ) = UserRequestDeliveryStatusView(
        deliveryState = deliveryState,
        collectingReady = collectingReady,
        totalSourceCount = totalSourceCount,
        readySourceCount = readySourceCount,
        representativeSourceVerificationStatus = representativeSourceVerificationStatus
    )

    /**
     * 기준 요청이 본인 소유의 승인 완료 상태인지 공통 규칙으로 검증한다.
     */
    private fun requireOwnedApprovedBaseRequest(
        baseRequestId: String,
        requesterUserId: String,
        notFoundMessage: String = "Base request not found: $baseRequestId",
        ownershipErrorMessage: String = "Base request does not belong to requester",
        statusErrorMessage: String,
        requireApprovedCategory: Boolean,
        missingCategoryMessage: String = "Base request category is missing"
    ): UserClippingRequest {
        // 기준 요청 조회 실패 시 즉시 404를 반환한다.
        val baseRequest = requestStore.findById(baseRequestId)
            ?: throw NotFoundException(notFoundMessage)
        // 타 사용자 요청을 악용하지 못하도록 소유자와 승인 상태를 함께 검증한다.
        ensureValid(baseRequest.isOwnedBy(requesterUserId)) { ownershipErrorMessage }
        ensureValid(baseRequest.isApproved()) { statusErrorMessage }
        // 후속 리소스 승인에 필요한 카테고리 연결 여부를 선택적으로 강제한다.
        if (requireApprovedCategory) {
            ensureValid(!baseRequest.approvedCategoryId.isNullOrBlank()) { missingCategoryMessage }
        }
        return baseRequest
    }

    private fun requirePendingRequest(requestId: String): UserClippingRequest {
        // 승인/반려 중복 처리를 막기 위해 상태를 강제 검증한다.
        val request = requestStore.findById(requestId)
            ?: throw NotFoundException("Request not found: $requestId")
        ensureValid(request.isPendingReview()) { "Request is already reviewed" }
        return request
    }

    private fun requireAdminReviewer(username: String, action: String) =
        resolveAdminReviewer(username).also { reviewer ->
            ensureValid(reviewer.role == AccountRole.ADMIN) {
                "Only ADMIN accounts can $action"
            }
        }

    private fun requireUserRequester(username: String, action: String) =
        requireUserByUsername(username).also { requester ->
            ensureValid(requester.role == AccountRole.USER) {
                "Only USER accounts can $action"
            }
        }

    private fun requireUserByUsername(username: String) =
        adminUserStore.findByUsername(username.trim().lowercase())
            ?: throw NotFoundException("User not found: $username")

    /**
     * 관리자 리뷰어를 조회한다.
     * form-login 사용자는 그대로 조회하고, API 토큰 principal(admin-api)은 활성 ADMIN 계정으로 대체한다.
     */
    private fun resolveAdminReviewer(username: String) =
        adminUserStore.findByUsername(username.trim().lowercase())
            ?: resolveAdminReviewerFromTokenPrincipal(username)

    /**
     * admin-api principal이 전달된 경우 실제 승인 가능한 ADMIN 계정을 대체 리뷰어로 찾는다.
     */
    private fun resolveAdminReviewerFromTokenPrincipal(username: String) =
        username.trim().lowercase()
            .takeIf { it == "admin-api" }
            ?.let {
                // 승인 가능한 활성 ADMIN이 있어야 요청 승인/반려 이력을 기록할 수 있다.
                adminUserStore
                    .listByRole(AccountRole.ADMIN, AccountApprovalStatus.APPROVED)
                    .firstOrNull { user -> user.isActive }
                    ?: throw NotFoundException("Approved ADMIN reviewer not found for admin-api token")
            }
            ?: throw NotFoundException("User not found: $username")

    /**
     * 공개 채널 중복 구독을 방지한다.
     * DM(D/U 접두어)은 개인별이므로 검사를 건너뛴다.
     */
    private fun validateChannelNotOccupied(slackChannelId: String) {
        val normalized = slackChannelId.trim().uppercase()
        // DM 채널은 사용자 개인 채널이므로 공유 여부 검사를 생략한다.
        if (normalized.isBlank() || normalized.startsWith("D") || normalized.startsWith("U")) return
        val occupied = requestStore.existsBySlackChannelIdAndStatusIn(
            slackChannelId = slackChannelId,
            statuses = listOf(UserClippingRequestStatus.PENDING, UserClippingRequestStatus.APPROVED)
        )
        if (occupied) {
            throw ConflictException("이미 다른 구독이 연결된 채널입니다.")
        }
    }

    /**
     * 구독 한도(5개)를 검증한다. 승인된 구독 + 검토 중 요청 합산으로 판단한다.
     */
    private fun validateSubscriptionLimit(requesterUserId: String) {
        // 한도 검증에는 개별 요청 내용이 필요 없으므로 DB count로 대량 row 로드를 피한다.
        val existingCount = requestStore.countActiveSubscriptionsByRequesterUserId(requesterUserId)
        ensureValid(existingCount < 5) { "구독 한도(5개)에 도달했습니다. 기존 구독을 해제한 후 다시 시도해 주세요." }
    }

    /**
     * 이번 달 신규 요청 생성 수가 월 한도를 초과하면 요청을 거부한다.
     * REJECTED/WITHDRAWN 상태의 요청은 한도에 포함하지 않는다.
     */
    private fun ensureMonthlyCreationLimitNotExceeded(requesterUserId: String) {
        val kst = java.time.ZoneId.of("Asia/Seoul")
        val startOfMonth = java.time.YearMonth.now(kst)
            .atDay(1).atStartOfDay(kst).toInstant()
        // 이번 달에 생성된 유효 요청(PENDING + APPROVED)만 DB에서 카운트한다.
        val thisMonthCount = requestStore.countCreatedSinceByRequesterUserId(requesterUserId, startOfMonth)
        ensureValid(thisMonthCount < MAX_MONTHLY_NEW_REQUESTS) {
            "이번 달 신규 요청 한도(${MAX_MONTHLY_NEW_REQUESTS}건)에 도달했습니다. 다음 달에 다시 시도해 주세요."
        }
    }

    /**
     * 계정 기반 위자드 entries 배열로 구독 요청을 등록한다.
     * 유효하지 않은 entry는 errors 로 기록하고, 유효한 entry 만 저장한다.
     * 모든 entry 가 실패하면 status="rejected" 이며 DB 저장을 하지 않는다.
     *
     * @param request entries, categoryName, optional description
     * @param username 요청 등록 사용자명
     */
    @Transactional
    fun submitRequestWithEntries(
        request: SubmitWithEntriesRequest,
        username: String
    ): SubmitWithEntriesResponse {
        // 신청자 username → UUID 조회 (FK 제약 만족)
        val requester = requireUserRequester(username, "submit requests with entries")
        // 월간 생성 제한: 이번 달 유효 요청이 3건 이상이면 거부한다.
        ensureMonthlyCreationLimitNotExceeded(requester.id)
        val errors = mutableListOf<EntryError>()
        val validEntries = mutableListOf<EntryDto>()
        val seenCompanyKeys = mutableSetOf<String>()

        // 경쟁사 워치리스트 가드: company 타입 entry 이름을 한 번에 일괄 조회하여 N+1 제거.
        val companyNames = request.entries
            .filter { it.type == "company" }
            .map { it.value }
        val competitorNameSet: Set<String> = competitorWatchlistStore
            .findByNamesIgnoreCase(companyNames)
            .map { it.name.lowercase() }
            .toHashSet()

        request.entries.forEachIndexed { idx, entry ->
            // stockCode 형식 검증 — 빈 문자열은 optional 로 통과, 있으면 6-digit 만 허용
            val stockCode = entry.stockCode
            if (entry.type == "company" && !stockCode.isNullOrEmpty()
                && !stockCode.matches(Regex("^\\d{6}$"))) {
                errors.add(EntryError(idx, entry.value, EntryErrorReason.INVALID_STOCK_CODE))
                return@forEachIndexed
            }
            // 중복 company 체크 (stockCode 있으면 stockCode 키, 없으면 lowercase name)
            if (entry.type == "company") {
                val key = if (!stockCode.isNullOrEmpty()) stockCode else entry.value.lowercase()
                if (key in seenCompanyKeys) {
                    errors.add(EntryError(idx, entry.value, EntryErrorReason.DUPLICATE_IN_REQUEST))
                    return@forEachIndexed
                }
                seenCompanyKeys.add(key)
            }
            // COMPETITOR watchlist 가드 — 배치 조회 결과 맵으로 DB 재접근 없이 판정한다.
            if (entry.type == "company" && entry.value.trim().lowercase() in competitorNameSet) {
                errors.add(EntryError(idx, entry.value, EntryErrorReason.COMPETITOR_WATCHLIST_CONFLICT))
                return@forEachIndexed
            }
            validEntries.add(entry)
        }

        // 모든 entry 실패 → rejected, DB 저장하지 않는다
        if (validEntries.isEmpty()) {
            return SubmitWithEntriesResponse(requestId = "", status = "rejected", errors = errors)
        }

        val requestId = java.util.UUID.randomUUID().toString()
        val formJson = objectMapper.writeValueAsString(validEntries)
        val firstEntry = validEntries.first()
        // 대표 source — legacy 호환용. 첫 entry 값을 Google News RSS URL 로 변환한다.
        val representativeSourceUrl = buildRepresentativeSourceUrl(firstEntry.value)

        requestStore.saveWithFormEntries(
            request = UserClippingRequest(
                id = requestId,
                requesterUserId = requester.id,
                requestName = request.categoryName,
                sourceName = firstEntry.value,
                sourceUrl = representativeSourceUrl,
                slackChannelId = "",
                personaName = "",
                personaPrompt = "",
                requestNote = request.description
            ),
            formEntries = formJson
        )

        // 위자드에서 고른 발송 프리셋/요일/시각을 유저 전역 스케줄에 영속화한다.
        // 값이 누락된 경우 기존 스케줄 유지, 없으면 기본값(WEEKDAYS 9시)으로 생성 —
        // 즉 구독 성공한 유저는 반드시 스케줄 row 가 존재하도록 보장한다.
        persistDeliverySchedule(
            userId = requester.id,
            preset = request.deliveryPreset,
            deliveryDays = request.deliveryDays,
            deliveryHour = request.deliveryHour
        )

        val status = if (errors.isEmpty()) "submitted" else "partial"
        return SubmitWithEntriesResponse(requestId, status, errors)
    }

    /**
     * 구독 신청 시 사용자의 발송 스케줄을 upsert 한다.
     * - 클라이언트 값이 있으면 그걸 우선, 없으면 기존 스케줄 유지, 기존도 없으면 기본값(WEEKDAYS 09시).
     * - 누구든 위자드로 구독하면 반드시 스케줄 row 가 존재하게 만들어 `SlackDigestWorker` 가 찾을 수 있게 한다.
     */
    private fun persistDeliverySchedule(
        userId: String,
        preset: String?,
        deliveryDays: List<String>?,
        deliveryHour: Int?
    ) {
        val existing = userDeliveryScheduleStore.findByUserId(userId)
        val resolvedPreset = preset?.trim()?.uppercase()?.let {
            runCatching { DeliveryPreset.valueOf(it) }.getOrNull()
        } ?: existing?.preset ?: DeliveryPreset.WEEKDAYS
        val resolvedHour = deliveryHour?.takeIf { it in 0..23 } ?: existing?.deliveryHour ?: 9
        val resolvedDays = when (resolvedPreset) {
            DeliveryPreset.WEEKDAYS -> listOf("MON", "TUE", "WED", "THU", "FRI")
            DeliveryPreset.EVERYDAY -> listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
            DeliveryPreset.CUSTOM -> deliveryDays?.takeIf { it.isNotEmpty() }
                ?: existing?.deliveryDays
                ?: listOf("MON", "TUE", "WED", "THU", "FRI")
        }
        userDeliveryScheduleStore.upsert(
            UserDeliverySchedule(
                userId = userId,
                deliveryDays = resolvedDays,
                deliveryHour = resolvedHour,
                preset = resolvedPreset
            )
        )
    }

    /**
     * query 를 Google News RSS 검색 URL 로 인코딩한다.
     * legacy single-source 호환용 대표 URL 로 사용한다.
     */
    private fun buildRepresentativeSourceUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8)
        return "https://news.google.com/rss/search?q=$encoded&hl=ko&gl=KR&ceid=KR:ko"
    }

    private fun normalizeOptionalValue(value: String?): String? =
        value?.trim()?.ifBlank { null }

}
