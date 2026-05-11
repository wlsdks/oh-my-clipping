package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.DeliveryPreset
import com.clipping.mcpserver.model.UserDeliverySchedule
import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.UserDeliveryScheduleStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserDeliveryScheduleServiceTest {

    private val scheduleStore = mockk<UserDeliveryScheduleStore>()
    private val adminUserStore = mockk<AdminUserStore>()
    private val service = UserDeliveryScheduleService(scheduleStore, adminUserStore)

    private val testUser = AdminUser(
        id = "user-1",
        username = "testuser",
        passwordHash = "hashed"
    )

    @Nested
    inner class `saveSchedule 메서드` {

        @Test
        fun `존재하지 않는 사용자이면 NotFoundException을 던진다`() {
            every { adminUserStore.findByUsername("unknown") } returns null

            val exception = shouldThrow<NotFoundException> {
                service.saveSchedule("unknown", listOf("MON"), 8, DeliveryPreset.CUSTOM)
            }

            exception.message shouldBe "사용자를 찾을 수 없습니다: unknown"
            verify(exactly = 0) { scheduleStore.upsert(any()) }
        }

        @Test
        fun `발송 시간이 음수이면 InvalidInputException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser

            val exception = shouldThrow<InvalidInputException> {
                service.saveSchedule("testuser", listOf("MON"), -1, DeliveryPreset.CUSTOM)
            }

            exception.message shouldBe "발송 시간은 8, 12, 18시만 선택할 수 있습니다."
            verify(exactly = 0) { scheduleStore.upsert(any()) }
        }

        @Test
        fun `발송 시간이 24 이상이면 InvalidInputException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser

            val exception = shouldThrow<InvalidInputException> {
                service.saveSchedule("testuser", listOf("MON"), 24, DeliveryPreset.CUSTOM)
            }

            exception.message shouldBe "발송 시간은 8, 12, 18시만 선택할 수 있습니다."
            verify(exactly = 0) { scheduleStore.upsert(any()) }
        }

        @Test
        fun `WEEKDAY 프리셋이면 월~금 요일이 자동 설정된다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            val savedSlot = slot<UserDeliverySchedule>()
            every { scheduleStore.upsert(capture(savedSlot)) } just Runs
            every { scheduleStore.findByUserId("user-1") } returns null

            val result = service.saveSchedule("testuser", emptyList(), 8, DeliveryPreset.WEEKDAYS)

            savedSlot.captured.deliveryDays shouldBe listOf("MON", "TUE", "WED", "THU", "FRI")
            savedSlot.captured.deliveryHour shouldBe 8
            savedSlot.captured.preset shouldBe DeliveryPreset.WEEKDAYS
            result.deliveryDays shouldBe listOf("MON", "TUE", "WED", "THU", "FRI")
        }

        @Test
        fun `EVERYDAY 프리셋이면 월~일 전체 요일이 설정된다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            val savedSlot = slot<UserDeliverySchedule>()
            every { scheduleStore.upsert(capture(savedSlot)) } just Runs
            every { scheduleStore.findByUserId("user-1") } returns null

            val result = service.saveSchedule("testuser", emptyList(), 8, DeliveryPreset.EVERYDAY)

            savedSlot.captured.deliveryDays shouldBe
                listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
            savedSlot.captured.preset shouldBe DeliveryPreset.EVERYDAY
        }

        @Test
        fun `CUSTOM 프리셋에 빈 요일 목록이면 InvalidInputException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser

            val exception = shouldThrow<InvalidInputException> {
                service.saveSchedule("testuser", emptyList(), 8, DeliveryPreset.CUSTOM)
            }

            exception.message shouldBe "직접 선택 시 최소 1개 요일을 선택해야 합니다."
            verify(exactly = 0) { scheduleStore.upsert(any()) }
        }

        @Test
        fun `CUSTOM 프리셋에 유효하지 않은 요일만 있으면 InvalidInputException을 던진다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser

            val exception = shouldThrow<InvalidInputException> {
                service.saveSchedule("testuser", listOf("INVALID", "XYZ"), 8, DeliveryPreset.CUSTOM)
            }

            exception.message shouldBe "유효하지 않은 요일이 포함되어 있습니다: INVALID,XYZ"
            verify(exactly = 0) { scheduleStore.upsert(any()) }
        }

        @Test
        fun `CUSTOM 프리셋에 유효하지 않은 요일이 섞여 있으면 전체를 거부한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser

            val exception = shouldThrow<InvalidInputException> {
                service.saveSchedule("testuser", listOf("MON", "bad"), 8, DeliveryPreset.CUSTOM)
            }

            exception.message shouldBe "유효하지 않은 요일이 포함되어 있습니다: BAD"
            verify(exactly = 0) { scheduleStore.upsert(any()) }
        }

        @Test
        fun `CUSTOM 프리셋에 유효한 요일이면 정상 저장된다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            val savedSlot = slot<UserDeliverySchedule>()
            every { scheduleStore.upsert(capture(savedSlot)) } just Runs
            every { scheduleStore.findByUserId("user-1") } returns null

            val result = service.saveSchedule("testuser", listOf("mon", "WED", "fri"), 12, DeliveryPreset.CUSTOM)

            // 소문자 입력도 대문자로 변환
            savedSlot.captured.deliveryDays shouldBe listOf("MON", "WED", "FRI")
            savedSlot.captured.deliveryHour shouldBe 12
            savedSlot.captured.preset shouldBe DeliveryPreset.CUSTOM
        }

        @Test
        fun `CUSTOM 프리셋은 중복 요일을 제거하고 저장한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            val savedSlot = slot<UserDeliverySchedule>()
            every { scheduleStore.upsert(capture(savedSlot)) } just Runs
            every { scheduleStore.findByUserId("user-1") } returns null

            service.saveSchedule("testuser", listOf("mon", "MON", "wed"), 12, DeliveryPreset.CUSTOM)

            savedSlot.captured.deliveryDays shouldBe listOf("MON", "WED")
        }

        @Test
        fun `허용되지 않은 시간 0시와 23시는 거부된다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser

            // 0시 거부
            shouldThrow<InvalidInputException> {
                service.saveSchedule("testuser", listOf("MON"), 0, DeliveryPreset.CUSTOM)
            }
            verify(exactly = 0) { scheduleStore.upsert(any()) }

            // 23시 거부
            shouldThrow<InvalidInputException> {
                service.saveSchedule("testuser", listOf("MON"), 23, DeliveryPreset.CUSTOM)
            }
            verify(exactly = 0) { scheduleStore.upsert(any()) }
        }

        @Test
        fun `허용된 시간 8, 12, 18시는 정상 동작한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { scheduleStore.upsert(any()) } just Runs
            every { scheduleStore.findByUserId("user-1") } returns null

            service.saveSchedule("testuser", listOf("MON"), 8, DeliveryPreset.CUSTOM)
            service.saveSchedule("testuser", listOf("MON"), 12, DeliveryPreset.CUSTOM)
            service.saveSchedule("testuser", listOf("MON"), 18, DeliveryPreset.CUSTOM)
            verify(exactly = 3) { scheduleStore.upsert(any()) }
        }
    }

    @Nested
    inner class `getSchedule 메서드` {

        @Test
        fun `존재하지 않는 사용자이면 NotFoundException을 던진다`() {
            every { adminUserStore.findByUsername("unknown") } returns null

            val exception = shouldThrow<NotFoundException> {
                service.getSchedule("unknown")
            }

            exception.message shouldBe "사용자를 찾을 수 없습니다: unknown"
        }

        @Test
        fun `저장된 스케줄이 없으면 기본값을 반환한다`() {
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { scheduleStore.findByUserId("user-1") } returns null

            val result = service.getSchedule("testuser")

            result.userId shouldBe "user-1"
            result.deliveryDays shouldBe listOf("MON", "TUE", "WED", "THU", "FRI")
            result.deliveryHour shouldBe 8
            result.preset shouldBe DeliveryPreset.WEEKDAYS
        }

        @Test
        fun `저장된 스케줄이 있으면 해당 값을 반환한다`() {
            val saved = UserDeliverySchedule(
                userId = "user-1",
                deliveryDays = listOf("SAT", "SUN"),
                deliveryHour = 12,
                preset = DeliveryPreset.CUSTOM
            )
            every { adminUserStore.findByUsername("testuser") } returns testUser
            every { scheduleStore.findByUserId("user-1") } returns saved

            val result = service.getSchedule("testuser")

            result.deliveryDays shouldBe listOf("SAT", "SUN")
            result.deliveryHour shouldBe 12
            result.preset shouldBe DeliveryPreset.CUSTOM
        }
    }
}
