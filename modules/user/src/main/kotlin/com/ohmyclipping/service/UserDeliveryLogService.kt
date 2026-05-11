package com.ohmyclipping.service

import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.service.dto.user.UserDeliveryLogItemView
import com.ohmyclipping.service.dto.user.UserDeliveryLogListView
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.DeliveryLogStore
import com.ohmyclipping.store.UserClippingRequestStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

/**
 * 사용자 발송 이력 조회 서비스.
 * 인증된 사용자의 승인된 구독 카테고리에 대한 발송 이력을 조회한다.
 */
@Service
class UserDeliveryLogService(
    private val adminUserStore: AdminUserStore,
    private val userClippingRequestStore: UserClippingRequestStore,
    private val deliveryLogStore: DeliveryLogStore
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val seoulZone = ZoneId.of("Asia/Seoul")

    /**
     * 로그인 사용자의 승인된 구독 카테고리에 대한 발송 이력을 조회한다.
     *
     * @param requesterUsername 로그인 사용자 이름 (authentication.name)
     * @param days 조회 기간 일수 (기본 7일, 최대 90일)
     * @return 발송 이력 목록 뷰
     */
    fun getDeliveryLogs(requesterUsername: String, days: Int): UserDeliveryLogListView {
        // 사용자 확인 및 USER 역할 검증
        val user = adminUserStore.findByUsername(requesterUsername.trim().lowercase())
            ?: throw NotFoundException("사용자를 찾을 수 없습니다: $requesterUsername")
        if (user.role != AccountRole.USER) {
            throw NotFoundException("사용자를 찾을 수 없습니다: $requesterUsername")
        }

        // 승인된 구독에서 카테고리 ID를 추출한다.
        val approvedCategoryIds = userClippingRequestStore
            .listByRequesterUserId(user.id)
            .filter { it.isApproved() }
            .mapNotNull { it.approvedCategoryId?.takeIf(String::isNotBlank) }
            .distinct()

        if (approvedCategoryIds.isEmpty()) {
            return UserDeliveryLogListView(deliveries = emptyList())
        }

        // 조회 기간을 서울 시간 기준으로 계산한다.
        val clampedDays = days.coerceIn(1, 90)
        val today = LocalDate.now(seoulZone)
        val from = today.minusDays(clampedDays.toLong() - 1)

        // 발송 이력을 조회하여 뷰로 변환한다.
        val entries = deliveryLogStore.findByCategoryIds(approvedCategoryIds, from, today)

        val items = entries.map { entry ->
            UserDeliveryLogItemView(
                date = entry.date,
                categoryName = entry.categoryName,
                itemCount = entry.itemCount,
                status = entry.status,
                deliveredAt = entry.deliveredAt
            )
        }

        return UserDeliveryLogListView(deliveries = items)
    }
}
