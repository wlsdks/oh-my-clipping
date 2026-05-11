package com.ohmyclipping.service

import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.service.dto.user.UserMonthlyStat
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.DeliveryLogStore
import com.ohmyclipping.store.StatsStore
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * 사용자 포털 통계 조회/CSV 리포트 생성을 담당한다.
 *
 * `itemsSent` 지표는 기존 `clipping_stats`의 카테고리 수준 카운터가 아니라
 * `delivery_log`에서 SENT 상태로 확정된 item_count 합계를 사용한다.
 * "내가 실제로 받은 뉴스"가 사용자 포털의 관점에 더 정확하기 때문이다.
 */
@Service
class UserStatsReportService(
    private val userClippingRequestService: UserClippingRequestService,
    private val statsStore: StatsStore,
    private val categoryStore: CategoryStore,
    private val deliveryLogStore: DeliveryLogStore
) {

    /**
     * 로그인 사용자에게 승인된 카테고리만 대상으로 월간 통계를 조회한다.
     *
     * 반환 행의 `itemsSent`는 `delivery_log.item_count`(SENT)의 `(카테고리, 날짜)` 합이다.
     * stats 행이 없지만 발송은 성공한 `(카테고리, 날짜)` 조합이 있으면 합성 행으로 추가한다.
     */
    fun getOwnMonthlyStats(requesterUsername: String, yearMonth: YearMonth): List<UserMonthlyStat> {
        // 사용자 요청 중 승인된 카테고리 ID만 통계 대상에 포함한다.
        val approvedCategoryIds = resolveApprovedCategoryIds(requesterUsername)
        if (approvedCategoryIds.isEmpty()) return emptyList()

        // 카테고리 삭제 상황에서도 CSV/화면 렌더링이 깨지지 않게 대체명을 부여한다.
        val categoryNameById = approvedCategoryIds.associateWith { categoryId ->
            categoryStore.findById(categoryId)?.name ?: "삭제된 카테고리"
        }

        // 실제 발송 성공 건수를 (카테고리, 일자) 단위로 미리 집계해둔다.
        val deliveredByCategoryDate = deliveryLogStore.sumDeliveredItemsByCategoryDate(
            categoryIds = approvedCategoryIds,
            from = yearMonth.atDay(1),
            to = yearMonth.atEndOfMonth()
        )

        val seenKeys = mutableSetOf<Pair<String, LocalDate>>()
        val rows = mutableListOf<UserMonthlyStat>()

        // 1) clipping_stats 행 기준 응답 생성 — itemsSent만 delivery_log 집계로 교체한다.
        approvedCategoryIds.forEach { categoryId ->
            val categoryName = categoryNameById[categoryId] ?: "삭제된 카테고리"
            statsStore.findMonthly(categoryId, yearMonth).forEach { stat ->
                val key = categoryId to stat.statDate
                seenKeys += key
                rows += UserMonthlyStat(
                    id = stat.id,
                    categoryId = stat.categoryId,
                    categoryName = categoryName,
                    statDate = stat.statDate,
                    itemsCollected = stat.itemsCollected,
                    itemsSummarized = stat.itemsSummarized,
                    itemsSent = deliveredByCategoryDate[key] ?: 0,
                    topKeywords = stat.topKeywords,
                    avgImportanceScore = stat.avgImportanceScore
                )
            }
        }

        // 2) stats 행은 없지만 발송 성공 기록은 있는 (카테고리, 일자) 조합을 합성 행으로 보완한다.
        deliveredByCategoryDate.forEach { (key, delivered) ->
            if (key in seenKeys) return@forEach
            val (categoryId, statDate) = key
            val categoryName = categoryNameById[categoryId] ?: "삭제된 카테고리"
            rows += UserMonthlyStat(
                id = "delivered-$categoryId-$statDate",
                categoryId = categoryId,
                categoryName = categoryName,
                statDate = statDate,
                itemsCollected = 0,
                itemsSummarized = 0,
                itemsSent = delivered,
                topKeywords = emptyList(),
                avgImportanceScore = 0f
            )
        }

        return rows.sortedWith(
            // 최신 일자 우선, 같은 날짜면 카테고리명 오름차순으로 정렬한다.
            compareByDescending<UserMonthlyStat> { it.statDate }
                .thenBy { it.categoryName }
        )
    }

    /**
     * 로그인 사용자의 월간 통계를 CSV 문자열로 생성한다.
     */
    fun exportOwnMonthlyStatsCsv(requesterUsername: String, yearMonth: YearMonth): ByteArray {
        // 조회 결과를 CSV 행으로 직렬화한다.
        val rows = getOwnMonthlyStats(requesterUsername, yearMonth)
        val csvBody = buildString {
            appendLine(
                "statDate,categoryName,categoryId,itemsCollected," +
                    "itemsSummarized,itemsSent,avgImportanceScore,topKeywords"
            )
            rows.forEach { row ->
                appendCsvLine(
                    listOf(
                        row.statDate.toString(),
                        row.categoryName,
                        row.categoryId,
                        row.itemsCollected.toString(),
                        row.itemsSummarized.toString(),
                        row.itemsSent.toString(),
                        String.format(Locale.US, "%.3f", row.avgImportanceScore),
                        row.topKeywords.joinToString("|")
                    )
                )
            }
        }
        // 엑셀 한글 깨짐을 막기 위해 UTF-8 BOM을 앞에 붙인다.
        return ("\uFEFF" + csvBody).toByteArray(StandardCharsets.UTF_8)
    }

    private fun resolveApprovedCategoryIds(requesterUsername: String): List<String> =
        // 승인 완료된 요청에서만 카테고리 ID를 추출해 중복을 제거한다.
        userClippingRequestService.listOwnRequests(requesterUsername)
            .asSequence()
            .filter { it.status == UserClippingRequestStatus.APPROVED }
            .mapNotNull { it.approvedCategoryId?.takeIf(String::isNotBlank) }
            .distinct()
            .toList()

    private fun String.escapeCsv(): String {
        // 스프레드시트 수식 인젝션을 방지하기 위한 선처리를 먼저 적용한다.
        val hardened = protectSpreadsheetFormula()
        // CSV 특수문자가 있으면 큰따옴표 escaping 규칙으로 감싼다.
        val needsQuote = hardened.contains(',') ||
            hardened.contains('"') ||
            hardened.contains('\n') ||
            hardened.contains('\r')
        if (!needsQuote) return hardened
        return '"' + hardened.replace("\"", "\"\"") + '"'
    }

    /**
     * CSV를 스프레드시트에서 열 때 수식으로 해석될 수 있는 접두 문자를 무해화한다.
     */
    private fun String.protectSpreadsheetFormula(): String {
        val firstMeaningful = trimStart().firstOrNull() ?: return this
        return if (firstMeaningful in setOf('=', '+', '-', '@')) {
            "'$this"
        } else {
            this
        }
    }

    private fun StringBuilder.appendCsvLine(values: List<String>) {
        // 각 셀을 escape한 뒤 쉼표로 결합해 한 줄을 추가한다.
        append(values.joinToString(",") { it.escapeCsv() })
        append('\n')
    }
}
