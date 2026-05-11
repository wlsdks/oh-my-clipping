package com.ohmyclipping.store

import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.DeliveryPreset
import com.ohmyclipping.model.UserDeliverySchedule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcUserDeliveryScheduleStoreTest {

    @Autowired lateinit var adminUserStore: AdminUserStore
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var userDeliveryScheduleStore: UserDeliveryScheduleStore

    private lateinit var userId: String

    @BeforeEach
    fun setup() {
        userId = adminUserStore.save(
            AdminUser(
                id = "",
                username = "schedule-${System.nanoTime()}@example.com",
                passwordHash = "hashed",
                role = AccountRole.USER
            )
        ).id
    }

    @Test
    fun `upsert는 신규 스케줄을 저장한다`() {
        userDeliveryScheduleStore.upsert(
            UserDeliverySchedule(
                userId = userId,
                deliveryDays = listOf("MON", "WED"),
                deliveryHour = 7,
                preset = DeliveryPreset.CUSTOM
            )
        )

        val saved = userDeliveryScheduleStore.findByUserId(userId)
            ?: error("saved schedule not found")

        saved.deliveryDays shouldBe listOf("MON", "WED")
        saved.deliveryHour shouldBe 7
        saved.preset shouldBe DeliveryPreset.CUSTOM
    }

    @Test
    fun `upsert는 기존 스케줄을 갱신한다`() {
        userDeliveryScheduleStore.upsert(
            UserDeliverySchedule(
                userId = userId,
                deliveryDays = listOf("MON", "WED"),
                deliveryHour = 7,
                preset = DeliveryPreset.CUSTOM
            )
        )

        userDeliveryScheduleStore.upsert(
            UserDeliverySchedule(
                userId = userId,
                deliveryDays = listOf("TUE", "THU", "SAT"),
                deliveryHour = 11,
                preset = DeliveryPreset.CUSTOM
            )
        )

        val saved = userDeliveryScheduleStore.findByUserId(userId)
            ?: error("updated schedule not found")

        saved.deliveryDays shouldBe listOf("TUE", "THU", "SAT")
        saved.deliveryHour shouldBe 11
        saved.preset shouldBe DeliveryPreset.CUSTOM
    }

    @Test
    fun `findSchedulesDueNow는 오늘 이전 스케줄만 발송 대상으로 반환한다`() {
        val dueUserId = createUserWithSchedule("due-${System.nanoTime()}@example.com", 8)
        val sameDayUserId = createUserWithSchedule("same-day-${System.nanoTime()}@example.com", 8)

        // 스케줄러가 집계해야 하는 기존 스케줄은 전날로 돌려둔다.
        updateTimestamp(dueUserId, Instant.now().minusSeconds(86_400))
        // 오늘 수정된 스케줄은 다음날부터 적용되어야 하므로 제외한다.
        updateTimestamp(sameDayUserId, Instant.now())

        val dueSchedules = userDeliveryScheduleStore.findSchedulesDueNow("MON", 8)

        dueSchedules.map { it.userId }.contains(dueUserId) shouldBe true
        dueSchedules.map { it.userId }.contains(sameDayUserId) shouldBe false
    }

    @Test
    fun `findAllUserIds는 오늘 이전에 저장된 스케줄 사용자만 반환한다`() {
        val oldUserId = createUserWithSchedule("old-${System.nanoTime()}@example.com", 6)
        val recentUserId = createUserWithSchedule("recent-${System.nanoTime()}@example.com", 6)

        updateTimestamp(oldUserId, Instant.now().minusSeconds(86_400))
        updateTimestamp(recentUserId, Instant.now())

        val userIds = userDeliveryScheduleStore.findAllUserIds()

        userIds.contains(oldUserId) shouldBe true
        userIds.contains(recentUserId) shouldBe false
    }

    /**
     * 테스트용 사용자를 생성하고 기본 스케줄을 저장한다.
     */
    private fun createUserWithSchedule(username: String, hour: Int): String {
        val savedUser = adminUserStore.save(
            AdminUser(
                id = "",
                username = username,
                passwordHash = "hashed",
                role = AccountRole.USER
            )
        )
        userDeliveryScheduleStore.upsert(
            UserDeliverySchedule(
                userId = savedUser.id,
                deliveryDays = listOf("MON", "WED"),
                deliveryHour = hour,
                preset = DeliveryPreset.CUSTOM
            )
        )
        return savedUser.id
    }

    /**
     * 스케줄의 updated_at 값을 직접 조정해 스케줄러 적용 조건을 검증한다.
     */
    private fun updateTimestamp(userId: String, instant: Instant) {
        jdbc.update(
            "UPDATE user_delivery_schedules SET updated_at = ? WHERE user_id = ?",
            Timestamp.from(instant),
            userId
        )
    }
}
