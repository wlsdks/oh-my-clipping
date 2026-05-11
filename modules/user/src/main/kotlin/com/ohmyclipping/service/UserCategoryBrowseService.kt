package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.ensureValid
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.service.dto.UserCategoryBrowseItem
import com.ohmyclipping.service.dto.UserCategorySubscribeResult
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.UserClippingRequestStore
import org.springframework.stereotype.Service

/**
 * 사용자의 카테고리 탐색과 즉시 구독 기능을 제공한다.
 */
@Service
class UserCategoryBrowseService(
    private val categoryStore: CategoryStore,
    private val requestStore: UserClippingRequestStore,
    private val adminUserStore: AdminUserStore
) {

    /**
     * 사용자가 구독 가능한 카테고리 목록을 조회한다.
     */
    fun browse(username: String): List<UserCategoryBrowseItem> {
        val user = resolveUser(username)

        // 활성이면서 공개된 카테고리만 탐색 대상에 포함한다.
        val categories = categoryStore.findPublicOperational()
        // 본인의 승인된 구독에서 카테고리 ID 집합을 추출한다.
        val myRequests = requestStore.listByRequesterUserId(user.id)
            .filter { it.status == UserClippingRequestStatus.APPROVED }
        val myCategoryIds = myRequests.mapNotNull { it.approvedCategoryId }.toSet()

        // 카테고리별 구독자 수는 DB 집계 결과만 조회해 전체 승인 구독 로드를 피한다.
        val subscriberCounts = requestStore.countApprovedGroupByCategoryId()

        // 이름이 같은 활성 카테고리가 여러 개 있어도 사용자 browse에는 대표 항목 하나만 노출한다.
        return categories
            .groupBy(::browseGroupKey)
            .values
            .map { duplicates ->
                duplicates.sortedWith(
                    compareByDescending<Category> { it.id in myCategoryIds }
                        .thenByDescending { subscriberCounts[it.id] ?: 0 }
                        .thenBy { it.createdAt }
                        .thenBy { it.id }
                ).first()
            }
            .map { cat ->
                UserCategoryBrowseItem(
                    id = cat.id,
                    name = cat.name,
                    description = cat.description,
                    slackChannelId = cat.slackChannelId,
                    subscriberCount = subscriberCounts[cat.id] ?: 0,
                    isSubscribed = cat.id in myCategoryIds,
                    deliveryHour = null,
                    maxItems = cat.maxItems
                )
            }
            .sortedWith(
                compareByDescending<UserCategoryBrowseItem> { it.isSubscribed }
                    .thenByDescending { it.subscriberCount }
                    .thenBy { it.name.lowercase() }
            )
    }

    /**
     * 기존 카테고리에 즉시 구독한다 (관리자 승인 불필요).
     */
    fun subscribe(username: String, categoryId: String, slackChannelId: String): UserCategorySubscribeResult {
        val user = resolveUser(username)
        val category = categoryStore.findById(categoryId)
            ?: throw NotFoundException("카테고리를 찾을 수 없습니다")
        // browse 화면에 노출되지 않는 비활성 카테고리는 즉시 구독을 막는다.
        ensureCategoryIsSubscribable(category)

        // 월간 생성 제한: 이번 달 유효 요청이 한도 이상이면 거부한다.
        ensureMonthlyCreationLimit(user.id)
        // 구독 한도 체크: 승인된 구독 + 검토 중 요청 합산 5개 초과 불가
        ensureSubscriptionQuota(user.id)
        ensureNotAlreadySubscribed(user.id, categoryId)
        // 기존 카테고리는 기본 채널 또는 요청자 본인 DM으로만 전달 목적지를 확정한다.
        val effectiveChannelId = resolveBrowseSubscriptionDestination(user, category, slackChannelId)

        // 즉시 승인 상태로 구독 생성한다.
        val request = requestStore.save(
            UserClippingRequest(
                id = "",
                requesterUserId = user.id,
                requestName = "",
                sourceName = "",
                sourceUrl = "",
                slackChannelId = effectiveChannelId,
                personaName = "",
                personaPrompt = "",
                status = UserClippingRequestStatus.APPROVED,
                approvedCategoryId = categoryId
            )
        )

        return UserCategorySubscribeResult(
            requestId = request.id,
            categoryId = categoryId,
            status = "APPROVED"
        )
    }

    /**
     * browse 화면에서 노출된 활성 카테고리만 즉시 구독 대상으로 허용한다.
     */
    private fun ensureCategoryIsSubscribable(category: Category) {
        ensureValid(category.status.isOperational) { "비활성화된 카테고리는 구독할 수 없습니다." }
        // 비공개 카테고리는 browse 즉시 구독 대상에서 제외한다.
        ensureValid(category.isPublic) { "비공개 카테고리는 구독할 수 없습니다." }
    }

    companion object {
        /** 월간 신규 요청 생성 한도 (submitRequest와 동일) */
        private const val MAX_MONTHLY_NEW_REQUESTS = 5
    }

    /**
     * 이번 달 신규 요청 생성 수가 월 한도를 초과하면 요청을 거부한다.
     * REJECTED/WITHDRAWN 상태의 요청은 한도에 포함하지 않는다.
     */
    private fun ensureMonthlyCreationLimit(requesterUserId: String) {
        val kst = java.time.ZoneId.of("Asia/Seoul")
        val startOfMonth = java.time.YearMonth.now(kst)
            .atDay(1).atStartOfDay(kst).toInstant()
        val thisMonthCount = requestStore.countCreatedSinceByRequesterUserId(requesterUserId, startOfMonth)
        ensureValid(thisMonthCount < MAX_MONTHLY_NEW_REQUESTS) {
            "이번 달 신규 요청 한도(${MAX_MONTHLY_NEW_REQUESTS}건)에 도달했습니다. 다음 달에 다시 시도해 주세요."
        }
    }

    /**
     * 즉시 구독도 일반 요청과 동일하게 승인/대기 합산 5개 한도를 따른다.
     */
    private fun ensureSubscriptionQuota(requesterUserId: String) {
        val existingCount = requestStore.countActiveSubscriptionsByRequesterUserId(requesterUserId)
        ensureValid(existingCount < 5) { "구독 한도(5개)에 도달했습니다" }
    }

    /**
     * 현재 모델은 한 카테고리에 하나의 승인 구독만 허용한다.
     */
    private fun ensureNotAlreadySubscribed(
        requesterUserId: String,
        categoryId: String
    ) {
        val alreadySubscribed = requestStore.existsApprovedByRequesterUserIdAndCategoryId(requesterUserId, categoryId)
        ensureValid(!alreadySubscribed) { "이미 구독 중인 카테고리입니다" }
    }

    /**
     * 기존 카테고리의 즉시 구독 목적지를 현재 정책 기준으로 정규화한다.
     * DM 의도(blank, `DM`, `D...`)는 요청자 본인의 DM 채널로 치환한다.
     * 공유 채널 의도는 카테고리 기본 Slack 채널과 정확히 일치할 때만 허용한다.
     */
    private fun resolveBrowseSubscriptionDestination(
        requester: AdminUser,
        category: Category,
        rawChannelId: String
    ): String {
        val normalizedChannelId = rawChannelId.trim()
        // DM 의도는 임의 입력값을 저장하지 않고 요청자 프로필의 실제 DM 채널을 사용한다.
        if (isSlackDirectMessageIntent(normalizedChannelId)) {
            return resolveRequesterSlackDirectMessageChannel(requester)
        }

        // 기존 카테고리의 공유 채널 구독은 카테고리 기본 채널만 허용한다.
        val categoryChannelId = category.slackChannelId?.trim().orEmpty()
        ensureValid(categoryChannelId.isNotBlank() && normalizedChannelId == categoryChannelId) {
            "기존 카테고리는 기본 Slack 채널 또는 본인 DM으로만 구독할 수 있습니다."
        }
        return categoryChannelId
    }

    /**
     * 요청자 프로필에 저장된 실제 DM 채널 ID를 읽어 명시적 목적지로 사용한다.
     */
    private fun resolveRequesterSlackDirectMessageChannel(requester: AdminUser): String =
        requester.slackDmChannelId?.trim()?.takeIf { it.isNotBlank() }
            ?: throw InvalidInputException(
                "Slack DM 채널 ID가 설정되지 않았습니다. 프로필에서 설정해 주세요."
            )

    /**
     * browse 구독 입력에서 DM 의도를 나타내는 값인지 판별한다.
     */
    private fun isSlackDirectMessageIntent(rawChannelId: String): Boolean {
        val normalizedChannelId = rawChannelId.trim()
        return normalizedChannelId.isBlank() ||
            normalizedChannelId.equals("DM", ignoreCase = true) ||
            normalizedChannelId.uppercase().let { it.startsWith("D") || it.startsWith("U") }
    }

    /**
     * browse 목록에서 이름이 같은 카테고리를 하나로 묶기 위한 정규화 키를 만든다.
     */
    private fun browseGroupKey(category: Category): String =
        category.name.trim().lowercase()


    private fun resolveUser(username: String): AdminUser =
        adminUserStore.findByUsername(username)
            ?: throw NotFoundException("User not found")
}
