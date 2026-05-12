package com.ohmyclipping.service.digest

import com.ohmyclipping.model.CategoryRule
import com.ohmyclipping.model.DeliveryPreset
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.time.ZoneId
import java.time.ZonedDateTime

class DeliveryScheduleMatcherTest {

    // -- isDeliveryDue: 발송 우선순위 ---------------------------------------------------

    @TestFactory
    fun `isDeliveryDue 우선순위 매트릭스`(): List<DynamicTest> {
        data class Case(
            val name: String,
            val categoryRule: CategoryRule?,
            val dueCategoryIds: Set<String>,
            val globalCronDue: Boolean,
            val expected: Boolean,
        )

        val matchingPresetRule = CategoryRule(
            categoryId = "cat-1",
            deliveryPreset = DeliveryPreset.WEEKDAYS,
            deliveryDays = listOf("MON"),
            deliveryHour = 9,
        )
        val nonMatchingPresetRule = matchingPresetRule.copy(deliveryHour = 18)
        val unsetPresetRule = CategoryRule(categoryId = "cat-1") // deliveryPreset = null

        val cases = listOf(
            Case(
                name = "preset 매치 → 발송",
                categoryRule = matchingPresetRule,
                dueCategoryIds = emptySet(),
                globalCronDue = false,
                expected = true,
            ),
            Case(
                name = "preset 있지만 시각 불일치 → preset 만 따른다 (글로벌 무시)",
                categoryRule = nonMatchingPresetRule,
                dueCategoryIds = setOf("cat-1"),
                globalCronDue = true,
                expected = false,
            ),
            Case(
                name = "preset 없음 + dueCategoryIds 포함 → 발송",
                categoryRule = unsetPresetRule,
                dueCategoryIds = setOf("cat-1"),
                globalCronDue = false,
                expected = true,
            ),
            Case(
                name = "categoryRule 자체가 null + dueCategoryIds 포함 → 발송",
                categoryRule = null,
                dueCategoryIds = setOf("cat-1"),
                globalCronDue = false,
                expected = true,
            ),
            Case(
                name = "preset 없음 + dueCategoryIds 미포함 + 글로벌 cron 매치 → 발송",
                categoryRule = unsetPresetRule,
                dueCategoryIds = emptySet(),
                globalCronDue = true,
                expected = true,
            ),
            Case(
                name = "어떤 조건도 매치 안 함 → 미발송",
                categoryRule = unsetPresetRule,
                dueCategoryIds = emptySet(),
                globalCronDue = false,
                expected = false,
            ),
            Case(
                name = "categoryRule null + 글로벌 cron 매치 → 발송",
                categoryRule = null,
                dueCategoryIds = emptySet(),
                globalCronDue = true,
                expected = true,
            ),
        )

        return cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                DeliveryScheduleMatcher.isDeliveryDue(
                    categoryId = "cat-1",
                    categoryRule = case.categoryRule,
                    dayOfWeek = "MON",
                    currentHour = 9,
                    dueCategoryIds = case.dueCategoryIds,
                    globalCronDue = case.globalCronDue,
                ) shouldBe case.expected
            }
        }
    }

    // -- matchesCategoryRule: 카테고리 preset 매칭 분기 ---------------------------------

    @TestFactory
    fun `matchesCategoryRule 매트릭스`(): List<DynamicTest> {
        data class Case(
            val name: String,
            val deliveryDays: List<String>?,
            val deliveryHour: Int?,
            val dayOfWeek: String,
            val currentHour: Int,
            val expected: Boolean,
        )

        val cases = listOf(
            Case("요일과 시각 모두 일치", listOf("MON", "TUE"), 9, "MON", 9, true),
            Case("요일은 일치, 시각 불일치", listOf("MON"), 9, "MON", 10, false),
            Case("시각은 일치, 요일 불일치", listOf("MON"), 9, "WED", 9, false),
            Case("deliveryDays null → 미매치", null, 9, "MON", 9, false),
            Case("deliveryHour null → 미매치", listOf("MON"), null, "MON", 9, false),
            Case("deliveryDays empty → 미매치", emptyList(), 9, "MON", 9, false),
            Case("자정 0시 매치", listOf("MON"), 0, "MON", 0, true),
            Case("23시 매치", listOf("MON"), 23, "MON", 23, true),
        )

        return cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                val rule = CategoryRule(
                    categoryId = "cat-1",
                    deliveryPreset = DeliveryPreset.CUSTOM,
                    deliveryDays = case.deliveryDays,
                    deliveryHour = case.deliveryHour,
                )
                DeliveryScheduleMatcher.matchesCategoryRule(
                    rule = rule,
                    dayOfWeek = case.dayOfWeek,
                    currentHour = case.currentHour,
                ) shouldBe case.expected
            }
        }
    }

    // -- matchesGlobalCron: cron 표현식 매칭 -------------------------------------------

    private val kst: ZoneId = ZoneId.of("Asia/Seoul")

    /** 2026-05-12 (TUE) 의 특정 시각에 stub 한 현재 시각을 생성한다. */
    private fun kstAt(hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 5, 12, hour, minute, 0, 0, kst)

    @Test
    fun `cron 표현식이 현재 시각과 매치되면 true`() {
        // 매일 9시 정각
        DeliveryScheduleMatcher.matchesGlobalCron("0 0 9 * * *", kstAt(9, 30)) shouldBe true
    }

    @Test
    fun `cron 표현식이 다른 시각이면 false`() {
        DeliveryScheduleMatcher.matchesGlobalCron("0 0 9 * * *", kstAt(10)) shouldBe false
    }

    @Test
    fun `빈 cron 표현식은 false`() {
        DeliveryScheduleMatcher.matchesGlobalCron("", kstAt(9)) shouldBe false
        DeliveryScheduleMatcher.matchesGlobalCron("   ", kstAt(9)) shouldBe false
    }

    @Test
    fun `dash sentinel 은 비활성으로 보고 false`() {
        DeliveryScheduleMatcher.matchesGlobalCron("-", kstAt(9)) shouldBe false
        DeliveryScheduleMatcher.matchesGlobalCron("  -  ", kstAt(9)) shouldBe false
    }

    @Test
    fun `잘못된 cron 표현식은 예외 없이 false`() {
        DeliveryScheduleMatcher.matchesGlobalCron("not a cron", kstAt(9)) shouldBe false
        DeliveryScheduleMatcher.matchesGlobalCron("* * * * *", kstAt(9)) shouldBe false // 5-field POSIX cron 은 Spring 비지원
    }

    @Test
    fun `cron next 가 다음날로 넘어가면 오늘은 미매치`() {
        // 매일 23시 정각, 현재가 0시면 next 는 오늘 23시. → 오늘은 hour 0, next.hour 23, 불일치.
        DeliveryScheduleMatcher.matchesGlobalCron("0 0 23 * * *", kstAt(0, 30)) shouldBe false
    }

    @Test
    fun `요일별 cron 매트릭스`() {
        // 평일 9시 (MON-FRI). 2026-05-12 는 TUE → 매치
        DeliveryScheduleMatcher.matchesGlobalCron("0 0 9 * * MON-FRI", kstAt(9)) shouldBe true
        // 같은 시각이지만 주말 cron → 미매치
        DeliveryScheduleMatcher.matchesGlobalCron("0 0 9 * * SAT,SUN", kstAt(9)) shouldBe false
    }

    // -- isValidCronExpression -------------------------------------------------

    @Test
    fun `유효한 cron 은 valid`() {
        DeliveryScheduleMatcher.isValidCronExpression("0 0 9 * * *") shouldBe true
    }

    @Test
    fun `빈 문자열과 dash 는 invalid`() {
        DeliveryScheduleMatcher.isValidCronExpression("") shouldBe false
        DeliveryScheduleMatcher.isValidCronExpression("-") shouldBe false
        DeliveryScheduleMatcher.isValidCronExpression("  ") shouldBe false
    }

    @Test
    fun `잘못된 cron 표현식은 invalid`() {
        DeliveryScheduleMatcher.isValidCronExpression("not-a-cron") shouldBe false
    }
}
