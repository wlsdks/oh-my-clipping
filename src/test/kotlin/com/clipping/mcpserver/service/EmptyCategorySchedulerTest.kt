package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.OpsNotificationEvent

import com.clipping.mcpserver.service.notification.OperationsNotificationService
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.UserClippingRequestStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class EmptyCategorySchedulerTest {

    private val categoryStore = mockk<CategoryStore>(relaxed = true)
    private val requestStore = mockk<UserClippingRequestStore>()
    private val jdbc = mockk<JdbcTemplate>()
    private val notificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }
    private val scheduler = EmptyCategoryScheduler(
        categoryStore = categoryStore,
        requestStore = requestStore,
        jdbc = jdbc,
        notificationService = notificationService,
        metrics = metrics
    )

    @Test
    fun `구독자와 최근 발송이 없는 카테고리는 비활성화한다`() {
        val category = Category(id = "category-1", name = "비활성 후보", isActive = true)
        every { requestStore.countApprovedGroupByCategoryId() } returns emptyMap()
        every { jdbc.queryForList(any<String>(), eq(String::class.java)) } returns emptyList()
        every { categoryStore.findOperational() } returns listOf(category)
        every { jdbc.queryForObject(any<String>(), eq(Int::class.java), eq("category-1"), any()) } returns 0
        scheduler.deactivateEmptyCategories()

        verify(exactly = 1) { categoryStore.update(match { it.id == "category-1" && !it.isActive }) }
        verify(exactly = 1) { notificationService.sendOps(eq(OpsNotificationEvent.EMPTY_CATEGORY_CLEANUP), match { it.contains("자동 비활성화") }, any()) }
        verify(exactly = 0) { requestStore.listAll(any()) }
    }

    @Test
    fun `최근 발송 이력이 있으면 비활성화하지 않는다`() {
        val category = Category(id = "category-2", name = "유지 대상", isActive = true)
        every { requestStore.countApprovedGroupByCategoryId() } returns emptyMap()
        every { jdbc.queryForList(any<String>(), eq(String::class.java)) } returns emptyList()
        every { categoryStore.findOperational() } returns listOf(category)
        every { jdbc.queryForObject(any<String>(), eq(Int::class.java), eq("category-2"), any()) } returns 1

        scheduler.deactivateEmptyCategories()

        verify(exactly = 0) { categoryStore.update(any()) }
        verify(exactly = 0) { notificationService.sendOps(any(), any(), any()) }
        verify(exactly = 0) { requestStore.listAll(any()) }
    }

    @Test
    fun `승인 구독자가 있는 카테고리는 발송 이력 조회 없이 유지한다`() {
        val category = Category(id = "category-3", name = "구독 중", isActive = true)
        every { requestStore.countApprovedGroupByCategoryId() } returns mapOf("category-3" to 12)
        every { jdbc.queryForList(any<String>(), eq(String::class.java)) } returns emptyList()
        every { categoryStore.findOperational() } returns listOf(category)

        scheduler.deactivateEmptyCategories()

        verify(exactly = 0) { jdbc.queryForObject(any<String>(), eq(Int::class.java), eq("category-3"), any()) }
        verify(exactly = 0) { categoryStore.update(any()) }
        verify(exactly = 0) { requestStore.listAll(any()) }
    }
}
