package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.service.dto.UserBriefingResult
import com.clipping.mcpserver.service.dto.UserBriefingSectionResult
import com.clipping.mcpserver.service.dto.UserSubscriptionQueryItem
import com.clipping.mcpserver.service.port.ClippingQueryPort
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.UserDeliveryScheduleStore
import com.clipping.mcpserver.store.UserOwnedCategoryStore
import org.springframework.stereotype.Service

/**
 * 사용자 구독 메타와 내 브리핑 조회 정책을 담당한다.
 * inbound adapter가 store를 직접 조합하지 않도록 소유권/스케줄 폴백/카테고리 필터링을 이곳에 모은다.
 */
@Service
class UserSubscriptionQueryService(
    private val userOwnedCategoryStore: UserOwnedCategoryStore,
    private val categoryStore: CategoryStore,
    private val categoryRuleStore: CategoryRuleStore,
    private val userDeliveryScheduleStore: UserDeliveryScheduleStore,
    private val clippingQueryPort: ClippingQueryPort,
) {

    /**
     * 사용자가 소유한 카테고리와 발송 스케줄 메타를 반환한다.
     *
     * @param userId 조회 주체 사용자 ID. 빈 문자열은 허용하지 않는다.
     */
    fun listMySubscriptions(userId: String): List<UserSubscriptionQueryItem> {
        val normalizedUserId = userId.takeIf { it.isNotBlank() }
            ?: throw InvalidInputException("Caller user id is required")
        val ownedIds = userOwnedCategoryStore.listCategoryIds(normalizedUserId).toSet()
        if (ownedIds.isEmpty()) return emptyList()

        val globalSchedule = userDeliveryScheduleStore.findByUserId(normalizedUserId)
        val globalDays = globalSchedule?.deliveryDays ?: DEFAULT_DAYS
        val globalHour = globalSchedule?.deliveryHour ?: DEFAULT_HOUR

        return categoryStore.listByIds(ownedIds)
            .map { category ->
                val rule = categoryRuleStore.findByCategoryId(category.id)
                val ruleDays = rule?.deliveryDays
                val ruleHour = rule?.deliveryHour
                val (days, source) = when {
                    !ruleDays.isNullOrEmpty() -> ruleDays to "category"
                    globalSchedule != null -> globalDays to "global"
                    else -> DEFAULT_DAYS to "default"
                }
                UserSubscriptionQueryItem(
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryDescription = category.description,
                    deliveryDays = days,
                    deliveryHour = ruleHour ?: globalHour,
                    deliveryDaysSource = source,
                )
            }
    }

    /**
     * 사용자가 소유한 카테고리별 최근 요약을 한 번에 조회한다.
     *
     * @param userId 조회 주체 사용자 ID
     * @param sinceDays 최근 N일 범위. 1..30만 허용한다.
     * @param perCategoryLimit 카테고리당 최대 요약 수. 1..10만 허용한다.
     */
    fun getMyBriefing(userId: String, sinceDays: Int, perCategoryLimit: Int): UserBriefingResult {
        val normalizedUserId = userId.takeIf { it.isNotBlank() }
            ?: throw InvalidInputException("Caller user id is required")
        if (sinceDays !in 1..30) throw InvalidInputException("sinceDays must be between 1 and 30")
        if (perCategoryLimit !in 1..10) throw InvalidInputException("perCategoryLimit must be between 1 and 10")

        val ownedIds = userOwnedCategoryStore.listCategoryIds(normalizedUserId).toSet()
        if (ownedIds.isEmpty()) {
            return UserBriefingResult(
                sinceDays = sinceDays,
                perCategoryLimit = perCategoryLimit,
                sections = emptyList(),
                emptyNote = "구독 중인 카테고리가 없습니다",
            )
        }

        val ownedCategories = categoryStore.listByIds(ownedIds)
        val perCategory = clippingQueryPort.listRecentForCategories(
            categoryIds = ownedCategories.map { it.id },
            sinceDays = sinceDays,
            limitPerCategory = perCategoryLimit,
        )
        val sections = ownedCategories.map { category ->
            UserBriefingSectionResult(
                categoryId = category.id,
                categoryName = category.name,
                summaries = perCategory[category.id].orEmpty(),
            )
        }
        return UserBriefingResult(
            sinceDays = sinceDays,
            perCategoryLimit = perCategoryLimit,
            sections = sections,
            emptyNote = null,
        )
    }

    private companion object {
        val DEFAULT_DAYS = listOf("MON", "TUE", "WED", "THU", "FRI")
        const val DEFAULT_HOUR = 8
    }
}
