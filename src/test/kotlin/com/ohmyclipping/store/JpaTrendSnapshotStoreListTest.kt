package com.ohmyclipping.store

import com.ohmyclipping.model.TrendPeriodType
import com.ohmyclipping.model.TrendRegionType
import com.ohmyclipping.model.TrendSnapshot
import com.ohmyclipping.model.TrendSnapshotStatus
import com.ohmyclipping.repository.TrendSnapshotRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

/**
 * Trend snapshot list bad-row coercion tests.
 */
class JpaTrendSnapshotStoreListTest {

    private val repository = mockk<TrendSnapshotRepository>(relaxed = true)
    private val jdbc = mockk<JdbcTemplate>()
    private val store = JpaTrendSnapshotStore(repository, jdbc)

    @Test
    fun `list는 enum 또는 필수 날짜가 깨진 row를 제외한다`() {
        every {
            jdbc.query(any<String>(), any<RowMapper<TrendSnapshot?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<TrendSnapshot?>>()
            listOfNotNull(
                mapper.mapRow(row(id = "bad-period", periodType = "YEARLY"), 0),
                mapper.mapRow(row(id = "bad-date", snapshotFrom = null), 1),
                mapper.mapRow(row(id = "bad-status", status = "ARCHIVED"), 2),
                mapper.mapRow(row(id = "ok"), 3),
            )
        }

        val result = store.list(limit = 10)

        result shouldHaveSize 1
        result.first().id shouldBe "ok"
        result.first().periodType shouldBe TrendPeriodType.WEEKLY
        result.first().regionType shouldBe TrendRegionType.ALL
        result.first().status shouldBe TrendSnapshotStatus.DRAFT
    }

    @Test
    fun `list는 nullable display 필드를 기본값으로 보정한다`() {
        every {
            jdbc.query(any<String>(), any<RowMapper<TrendSnapshot?>>(), *anyVararg())
        } answers {
            val mapper = secondArg<RowMapper<TrendSnapshot?>>()
            listOfNotNull(
                mapper.mapRow(
                    row(
                        id = "nullable-display",
                        categoryName = null,
                        title = null,
                        summary = null,
                        templateType = null,
                        createdAt = null,
                        updatedAt = null,
                    ),
                    0
                )
            )
        }

        val result = store.list(limit = 10).first()

        result.categoryName shouldBe ""
        result.title shouldBe ""
        result.summary shouldBe ""
        result.templateType shouldBe "DETAILED"
        result.createdAt shouldBe Instant.EPOCH
        result.updatedAt shouldBe Instant.EPOCH
    }

    private fun row(
        id: String = "snapshot-1",
        periodType: String? = TrendPeriodType.WEEKLY.name,
        snapshotFrom: LocalDate? = LocalDate.of(2026, 4, 1),
        snapshotTo: LocalDate? = LocalDate.of(2026, 4, 7),
        regionType: String? = TrendRegionType.ALL.name,
        status: String? = TrendSnapshotStatus.DRAFT.name,
        categoryName: String? = "카테고리",
        title: String? = "제목",
        summary: String? = "요약",
        templateType: String? = "DETAILED",
        createdAt: Instant? = Instant.parse("2026-04-08T00:00:00Z"),
        updatedAt: Instant? = Instant.parse("2026-04-08T01:00:00Z"),
    ): ResultSet =
        mockk {
            every { getString("id") } returns id
            every { getString("period_type") } returns periodType
            every { getDate("snapshot_from") } returns snapshotFrom?.let(Date::valueOf)
            every { getDate("snapshot_to") } returns snapshotTo?.let(Date::valueOf)
            every { getString("category_id") } returns null
            every { getString("category_name") } returns categoryName
            every { getString("region_type") } returns regionType
            every { getString("title") } returns title
            every { getString("summary") } returns summary
            every { getString("key_signals") } returns null
            every { getString("action_items") } returns null
            every { getInt("source_count") } returns 0
            every { getInt("item_count") } returns 0
            every { getString("status") } returns status
            every { getString("template_type") } returns templateType
            every { getString("generated_by") } returns null
            every { getTimestamp("published_at") } returns null
            every { getTimestamp("created_at") } returns createdAt?.let(Timestamp::from)
            every { getTimestamp("updated_at") } returns updatedAt?.let(Timestamp::from)
        }
}
