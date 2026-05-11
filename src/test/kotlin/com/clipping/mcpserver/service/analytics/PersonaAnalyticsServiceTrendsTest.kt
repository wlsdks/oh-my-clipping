package com.clipping.mcpserver.service.analytics

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.store.analytics.dto.WeeklyPersonaSnapshot
import com.clipping.mcpserver.service.analytics.dto.WeeklyTrendsResponse
import com.clipping.mcpserver.service.analytics.time.AnalyticsTime
import com.clipping.mcpserver.store.PersonaAnalyticsStore
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * PersonaAnalyticsService.getWeeklyTrends 단위 테스트.
 *
 * ReadModel 을 직접 목킹해 Service 레이어의 입력 검증과 위임 로직만 검증한다.
 * buildWeeklyTrends 의 내부 로직(주차 채우기 등)은 ReadModel 레벨 테스트에서 다룬다.
 */
class PersonaAnalyticsServiceTrendsTest {

    private lateinit var readModel: PersonaAnalyticsReadModel
    private lateinit var analyticsStore: PersonaAnalyticsStore
    private lateinit var service: PersonaAnalyticsService

    @BeforeEach
    fun setUp() {
        analyticsStore = mockk(relaxed = true)
        readModel = mockk()
        service = PersonaAnalyticsService(readModel, analyticsStore)
    }

    @Nested
    inner class `getWeeklyTrends 정상 경로` {

        @Test
        fun `getWeeklyTrends 12주 요청 시 weeks 배열 12개`() {
            // 12개의 주 목록을 가진 응답 목킹
            val now = LocalDate.now(AnalyticsTime.KST)
            val fromWeek = now.with(DayOfWeek.MONDAY).minusWeeks(11)
            val weekList = (0 until 12).map { i -> fromWeek.plusWeeks(i.toLong()).toString() }
            val expected = WeeklyTrendsResponse(weeks = weekList, series = emptyList())

            every { readModel.buildWeeklyTrends(12) } returns expected

            val result = service.getWeeklyTrends(12)

            assertThat(result.weeks).hasSize(12)
            assertThat(result.weeks).isEqualTo(weekList)
        }

        @Test
        fun `빈 스냅샷은 빈 series 반환`() {
            val now = LocalDate.now(AnalyticsTime.KST)
            val fromWeek = now.with(DayOfWeek.MONDAY).minusWeeks(3)
            val weekList = (0 until 4).map { i -> fromWeek.plusWeeks(i.toLong()).toString() }
            val expected = WeeklyTrendsResponse(weeks = weekList, series = emptyList())

            every { readModel.buildWeeklyTrends(4) } returns expected

            val result = service.getWeeklyTrends(4)

            assertThat(result.series).isEmpty()
            assertThat(result.weeks).hasSize(4)
        }

        @Test
        fun `스냅샷 없는 주차는 0으로 채움`() {
            // 2주 요청, 1개 페르소나가 첫 번째 주에만 스냅샷 보유
            val now = LocalDate.now(AnalyticsTime.KST)
            val fromWeek = now.with(DayOfWeek.MONDAY).minusWeeks(1)
            val weekList = listOf(fromWeek.toString(), fromWeek.plusWeeks(1).toString())

            val seriesWithGap = listOf(
                com.clipping.mcpserver.service.analytics.dto.PersonaTrendSeries(
                    personaId = "p1",
                    personaName = "테크 에디터",
                    isPreset = true,
                    activeSubs = listOf(10, 0),   // 두 번째 주는 스냅샷 없어서 0
                    engagedUsers = listOf(5, 0),
                    deliveredCount = listOf(20, 0)
                )
            )
            val expected = WeeklyTrendsResponse(weeks = weekList, series = seriesWithGap)

            every { readModel.buildWeeklyTrends(2) } returns expected

            val result = service.getWeeklyTrends(2)

            assertThat(result.series).hasSize(1)
            val series = result.series[0]
            assertThat(series.activeSubs[0]).isEqualTo(10)
            assertThat(series.activeSubs[1]).isEqualTo(0)  // 갭은 0으로 채워짐
            assertThat(series.deliveredCount[1]).isEqualTo(0)
        }
    }

    @Nested
    inner class `getWeeklyTrends 범위 검증` {

        @Test
        fun `weeks 0 요청 시 InvalidInputException 발생`() {
            assertThatThrownBy { service.getWeeklyTrends(0) }
                .isInstanceOf(InvalidInputException::class.java)
                .hasMessageContaining("weeks")
        }

        @Test
        fun `weeks 53 요청 시 InvalidInputException 발생`() {
            assertThatThrownBy { service.getWeeklyTrends(53) }
                .isInstanceOf(InvalidInputException::class.java)
                .hasMessageContaining("weeks")
        }

        @Test
        fun `weeks 1 경계값은 정상 처리`() {
            val weekList = listOf(LocalDate.now(AnalyticsTime.KST).with(DayOfWeek.MONDAY).toString())
            val expected = WeeklyTrendsResponse(weeks = weekList, series = emptyList())

            every { readModel.buildWeeklyTrends(1) } returns expected

            val result = service.getWeeklyTrends(1)

            assertThat(result.weeks).hasSize(1)
        }

        @Test
        fun `weeks 52 경계값은 정상 처리`() {
            val now = LocalDate.now(AnalyticsTime.KST)
            val fromWeek = now.with(DayOfWeek.MONDAY).minusWeeks(51)
            val weekList = (0 until 52).map { i -> fromWeek.plusWeeks(i.toLong()).toString() }
            val expected = WeeklyTrendsResponse(weeks = weekList, series = emptyList())

            every { readModel.buildWeeklyTrends(52) } returns expected

            val result = service.getWeeklyTrends(52)

            assertThat(result.weeks).hasSize(52)
        }
    }

    @Nested
    inner class `캐시 상수 검증` {

        @Test
        fun `CACHE_TRENDS 상수는 persona-trends 로 정의`() {
            assertThat(PersonaAnalyticsService.CACHE_TRENDS).isEqualTo("persona-trends")
        }
    }
}
