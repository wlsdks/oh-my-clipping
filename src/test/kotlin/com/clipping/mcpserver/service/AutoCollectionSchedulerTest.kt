package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.pipeline.PipelineLogService

import com.clipping.mcpserver.service.collection.NaverNewsSearchPort
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.model.DeliveryPreset
import com.clipping.mcpserver.model.RssSource
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.service.collection.NaverNewsCollectionService
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.RssSourceStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class AutoCollectionSchedulerTest {

    private val categoryStore = mockk<CategoryStore>()
    private val sourceStore = mockk<RssSourceStore>()
    private val categoryRuleStore = mockk<CategoryRuleStore>()
    private val asyncClipJobService = mockk<AsyncClipJobService>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val naverNewsSearchPort = mockk<NaverNewsSearchPort> {
        every { isConfigured() } returns false
    }
    private val naverNewsCollectionService = mockk<NaverNewsCollectionService>(relaxed = true)
    private val metrics = mockk<ClippingMetrics>(relaxed = true) {
        every { recordSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }
    private val pipelineLogService = mockk<PipelineLogService>(relaxed = true)
    private val itemSummarizationService = mockk<ItemSummarizationService>(relaxed = true)

    private val scheduler = AutoCollectionScheduler(
        categoryStore = categoryStore,
        sourceStore = sourceStore,
        categoryRuleStore = categoryRuleStore,
        asyncClipJobService = asyncClipJobService,
        runtimeSettingService = runtimeSettingService,
        naverNewsSearchPort = naverNewsSearchPort,
        naverNewsCollectionService = naverNewsCollectionService,
        metrics = metrics,
        pipelineLogService = pipelineLogService,
        itemSummarizationService = itemSummarizationService,
    )

    private fun normalRuntime() = mockk<RuntimeSettingService.RuntimeSettings> {
        every { maintenanceMode } returns false
    }

    private fun maintenanceRuntime() = mockk<RuntimeSettingService.RuntimeSettings> {
        every { maintenanceMode } returns true
    }

    /** 요일과 시각을 제어할 수 있도록 오버라이드한 스케줄러를 생성한다. */
    private fun schedulerWithDayAndHour(day: String, hour: Int = 10) = object : AutoCollectionScheduler(
        categoryStore = categoryStore,
        sourceStore = sourceStore,
        categoryRuleStore = categoryRuleStore,
        asyncClipJobService = asyncClipJobService,
        runtimeSettingService = runtimeSettingService,
        naverNewsSearchPort = naverNewsSearchPort,
        naverNewsCollectionService = naverNewsCollectionService,
        metrics = metrics,
        pipelineLogService = pipelineLogService,
        itemSummarizationService = itemSummarizationService,
    ) {
        override fun currentDayOfWeek(): String = day
        override fun currentHour(): Int = hour
    }

    /** 요일 필터링을 제어할 수 있도록 currentDayOfWeek를 오버라이드한 스케줄러를 생성한다. */
    private fun schedulerWithDay(day: String) = schedulerWithDayAndHour(day)

    @Nested
    inner class `수집 스케줄링` {

        @Test
        fun `활성 카테고리에 대해 수집 작업을 큐에 등록한다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            val category = Category(id = "cat-1", name = "테스트 카테고리", isActive = true)
            every { categoryStore.findOperational() } returns listOf(category)
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null
            every { sourceStore.listApproved("cat-1") } returns listOf(mockk<RssSource>())

            // 평일(WED)에 실행 — 기본값(WEEKDAYS)이므로 수집 대상
            val weekdayScheduler = schedulerWithDay("WED")
            weekdayScheduler.collectActiveCategories()

            verify(exactly = 1) { asyncClipJobService.enqueueCollect("cat-1", null) }
        }

        @Test
        fun `비활성 카테고리는 건너뛴다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            every { categoryStore.findOperational() } returns emptyList()

            val weekdayScheduler = schedulerWithDay("MON")
            weekdayScheduler.collectActiveCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }

        @Test
        fun `승인된 소스가 없는 카테고리는 건너뛴다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            val category = Category(id = "cat-1", name = "소스 없음", isActive = true)
            every { categoryStore.findOperational() } returns listOf(category)
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null
            every { sourceStore.listApproved("cat-1") } returns emptyList()

            val weekdayScheduler = schedulerWithDay("MON")
            weekdayScheduler.collectActiveCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }

        @Test
        fun `점검 모드에서는 수집을 건너뛴다`() {
            every { runtimeSettingService.current() } returns maintenanceRuntime()

            scheduler.collectActiveCategories()

            verify(exactly = 0) { categoryStore.findOperational() }
            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }

        @Test
        fun `한 카테고리 실패해도 나머지는 계속 처리한다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            val cat1 = Category(id = "cat-1", name = "실패", isActive = true)
            val cat2 = Category(id = "cat-2", name = "성공", isActive = true)
            every { categoryStore.findOperational() } returns listOf(cat1, cat2)
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null
            every { categoryRuleStore.findByCategoryId("cat-2") } returns null
            every { sourceStore.listApproved("cat-1") } returns listOf(mockk())
            every { sourceStore.listApproved("cat-2") } returns listOf(mockk())
            every { asyncClipJobService.enqueueCollect("cat-1", null) } throws RuntimeException("enqueue failed")
            every { asyncClipJobService.enqueueCollect("cat-2", null) } returns mockk()

            val weekdayScheduler = schedulerWithDay("TUE")
            weekdayScheduler.collectActiveCategories()

            verify(exactly = 1) { asyncClipJobService.enqueueCollect("cat-1", null) }
            verify(exactly = 1) { asyncClipJobService.enqueueCollect("cat-2", null) }
        }

        @Test
        fun `주말에는 기본값(WEEKDAYS) 카테고리를 건너뛴다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            val category = Category(id = "cat-1", name = "평일 전용", isActive = true)
            every { categoryStore.findOperational() } returns listOf(category)
            // deliveryPreset 미설정 → 기본값(WEEKDAYS)
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null

            val satScheduler = schedulerWithDay("SAT")
            satScheduler.collectActiveCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }

        @Test
        fun `주말에도 EVERYDAY 카테고리는 수집한다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            val category = Category(id = "cat-1", name = "매일 수집", isActive = true)
            every { categoryStore.findOperational() } returns listOf(category)
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.EVERYDAY
            )
            every { sourceStore.listApproved("cat-1") } returns listOf(mockk())

            val sunScheduler = schedulerWithDay("SUN")
            sunScheduler.collectActiveCategories()

            verify(exactly = 1) { asyncClipJobService.enqueueCollect("cat-1", null) }
        }
    }

    @Nested
    inner class `요약 스케줄링` {

        @Test
        fun `활성 카테고리에 대해 요약 작업을 큐에 등록한다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            val category = Category(id = "cat-1", name = "테스트", isActive = true)
            every { categoryStore.findOperational() } returns listOf(category)
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null

            val weekdayScheduler = schedulerWithDay("WED")
            weekdayScheduler.summarizeActiveCategories()

            verify(exactly = 1) { asyncClipJobService.enqueueSummarize("cat-1") }
        }

        @Test
        fun `비활성 카테고리는 건너뛴다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            every { categoryStore.findOperational() } returns emptyList()

            val weekdayScheduler = schedulerWithDay("MON")
            weekdayScheduler.summarizeActiveCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueSummarize(any()) }
        }

        @Test
        fun `점검 모드에서는 요약을 건너뛴다`() {
            every { runtimeSettingService.current() } returns maintenanceRuntime()

            scheduler.summarizeActiveCategories()

            verify(exactly = 0) { categoryStore.findOperational() }
            verify(exactly = 0) { asyncClipJobService.enqueueSummarize(any()) }
        }

        @Test
        fun `한 카테고리 실패해도 나머지는 계속 처리한다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            val cat1 = Category(id = "cat-1", name = "실패", isActive = true)
            val cat2 = Category(id = "cat-2", name = "성공", isActive = true)
            every { categoryStore.findOperational() } returns listOf(cat1, cat2)
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null
            every { categoryRuleStore.findByCategoryId("cat-2") } returns null
            every { asyncClipJobService.enqueueSummarize("cat-1") } throws RuntimeException("summarize failed")
            every { asyncClipJobService.enqueueSummarize("cat-2") } returns mockk()

            val weekdayScheduler = schedulerWithDay("TUE")
            weekdayScheduler.summarizeActiveCategories()

            verify(exactly = 1) { asyncClipJobService.enqueueSummarize("cat-1") }
            verify(exactly = 1) { asyncClipJobService.enqueueSummarize("cat-2") }
        }

        @Test
        fun `주말에는 기본값(WEEKDAYS) 카테고리의 요약을 건너뛴다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            val category = Category(id = "cat-1", name = "평일 전용", isActive = true)
            every { categoryStore.findOperational() } returns listOf(category)
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null

            val satScheduler = schedulerWithDay("SAT")
            satScheduler.summarizeActiveCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueSummarize(any()) }
        }

        @Test
        fun `주말에도 EVERYDAY 카테고리는 요약한다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            val category = Category(id = "cat-1", name = "매일 요약", isActive = true)
            every { categoryStore.findOperational() } returns listOf(category)
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.EVERYDAY
            )

            val sunScheduler = schedulerWithDay("SUN")
            sunScheduler.summarizeActiveCategories()

            verify(exactly = 1) { asyncClipJobService.enqueueSummarize("cat-1") }
        }
    }

    @Nested
    inner class `오늘 수집 여부 판단` {

        @Test
        fun `WEEKDAYS 프리셋 - 평일에는 수집한다`() {
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.WEEKDAYS
            )

            scheduler.shouldCollectToday("cat-1", "MON") shouldBe true
            scheduler.shouldCollectToday("cat-1", "FRI") shouldBe true
        }

        @Test
        fun `WEEKDAYS 프리셋 - 주말에는 건너뛴다`() {
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.WEEKDAYS
            )

            scheduler.shouldCollectToday("cat-1", "SAT") shouldBe false
            scheduler.shouldCollectToday("cat-1", "SUN") shouldBe false
        }

        @Test
        fun `EVERYDAY 프리셋 - 모든 요일에 수집한다`() {
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.EVERYDAY
            )

            scheduler.shouldCollectToday("cat-1", "MON") shouldBe true
            scheduler.shouldCollectToday("cat-1", "SAT") shouldBe true
            scheduler.shouldCollectToday("cat-1", "SUN") shouldBe true
        }

        @Test
        fun `CUSTOM 프리셋 - 설정된 요일에만 수집한다`() {
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf("MON", "WED", "FRI")
            )

            scheduler.shouldCollectToday("cat-1", "MON") shouldBe true
            scheduler.shouldCollectToday("cat-1", "WED") shouldBe true
            scheduler.shouldCollectToday("cat-1", "TUE") shouldBe false
            scheduler.shouldCollectToday("cat-1", "SAT") shouldBe false
        }

        @Test
        fun `CUSTOM 프리셋 - deliveryDays가 null이면 수집하지 않는다`() {
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = null
            )

            scheduler.shouldCollectToday("cat-1", "MON") shouldBe false
        }

        @Test
        fun `프리셋 미설정 - 기본값(WEEKDAYS) 적용으로 평일에만 수집한다`() {
            // rule이 존재하지만 deliveryPreset이 null
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = null
            )

            scheduler.shouldCollectToday("cat-1", "MON") shouldBe true
            scheduler.shouldCollectToday("cat-1", "SAT") shouldBe false
        }

        @Test
        fun `규칙이 없는 카테고리 - 기본값(WEEKDAYS) 적용으로 평일에만 수집한다`() {
            // rule 자체가 없음
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null

            scheduler.shouldCollectToday("cat-1", "FRI") shouldBe true
            scheduler.shouldCollectToday("cat-1", "SUN") shouldBe false
        }
    }

    @Nested
    inner class `적응형 폴링` {
        @Test
        fun `활성 카테고리를 매시간 추가 수집한다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns null

            val adaptiveScheduler = schedulerWithDayAndHour("WED", 10)
            adaptiveScheduler.markCategoryActive("cat-1")
            adaptiveScheduler.collectHighFrequencyCategories()

            verify(exactly = 1) { asyncClipJobService.enqueueCollect("cat-1", null) }
        }

        @Test
        fun `활성 카테고리가 없으면 수집하지 않는다`() {
            every { runtimeSettingService.current() } returns normalRuntime()

            val adaptiveScheduler = schedulerWithDayAndHour("WED", 10)
            adaptiveScheduler.collectHighFrequencyCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }

        @Test
        fun `4시간 주기 수집 시각에는 적응형 수집을 건너뛴다`() {
            every { runtimeSettingService.current() } returns normalRuntime()

            val adaptiveScheduler = schedulerWithDayAndHour("WED", 7)
            adaptiveScheduler.markCategoryActive("cat-1")
            adaptiveScheduler.collectHighFrequencyCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }

        @Test
        fun `주말에는 적응형 수집을 실행하지 않는다`() {
            every { runtimeSettingService.current() } returns normalRuntime()

            val adaptiveScheduler = schedulerWithDayAndHour("SAT", 10)
            adaptiveScheduler.markCategoryActive("cat-1")
            adaptiveScheduler.collectHighFrequencyCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }

        @Test
        fun `점검 모드에서는 적응형 수집을 건너뛴다`() {
            every { runtimeSettingService.current() } returns maintenanceRuntime()

            val adaptiveScheduler = schedulerWithDayAndHour("WED", 10)
            adaptiveScheduler.markCategoryActive("cat-1")
            adaptiveScheduler.collectHighFrequencyCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }

        @Test
        fun `markCategoryInactive로 비활성화한 카테고리는 수집하지 않는다`() {
            every { runtimeSettingService.current() } returns normalRuntime()

            val adaptiveScheduler = schedulerWithDayAndHour("WED", 10)
            adaptiveScheduler.markCategoryActive("cat-1")
            adaptiveScheduler.markCategoryInactive("cat-1")
            adaptiveScheduler.collectHighFrequencyCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }

        @Test
        fun `24시간 이상 경과한 활성 카테고리는 만료 제거된다`() {
            val adaptiveScheduler = schedulerWithDayAndHour("WED", 10)

            adaptiveScheduler.activeCategoryTimestamps["cat-old"] =
                Instant.now().minusSeconds(25 * 3600)
            adaptiveScheduler.activeCategoryTimestamps["cat-new"] =
                Instant.now()

            adaptiveScheduler.evictExpiredActiveCategories()

            adaptiveScheduler.activeCategoryTimestamps.containsKey("cat-old") shouldBe false
            adaptiveScheduler.activeCategoryTimestamps.containsKey("cat-new") shouldBe true
        }

        @Test
        fun `오늘 수집 대상이 아닌 활성 카테고리는 건너뛴다`() {
            every { runtimeSettingService.current() } returns normalRuntime()
            every { categoryRuleStore.findByCategoryId("cat-1") } returns CategoryRule(
                categoryId = "cat-1",
                deliveryPreset = DeliveryPreset.CUSTOM,
                deliveryDays = listOf("MON", "TUE")
            )

            val adaptiveScheduler = schedulerWithDayAndHour("WED", 10)
            adaptiveScheduler.markCategoryActive("cat-1")
            adaptiveScheduler.collectHighFrequencyCategories()

            verify(exactly = 0) { asyncClipJobService.enqueueCollect(any(), any()) }
        }
    }
}
