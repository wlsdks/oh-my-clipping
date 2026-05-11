package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.model.CategoryRule
import com.clipping.mcpserver.model.ReviewDecisionStatus
import com.clipping.mcpserver.model.RssItem
import com.clipping.mcpserver.model.RuntimeSetting
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryRuleStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.ReviewItemAuditStore
import com.clipping.mcpserver.store.ReviewItemDecisionStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.RuntimeSettingStore
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * [AdminReviewQueueService.ensurePolicyReviewDecisions] 가 [ReviewPolicyRuleEvaluator] 와
 * 올바르게 연결돼 자동 EXCLUDE 경로를 탄다는 것을 확인하는 통합 테스트.
 *
 * 검증 관점:
 *  - event_type_blacklist 룰 발동 시 DB 에 EXCLUDE 결정/감사 이력이 `rule:event_type_blacklist`
 *    reason 으로 쓰인다.
 *  - zero_signal 룰 발동 시 `rule:zero_signal` reason 으로 쓰인다.
 *  - 룰이 비해당이면 기존 threshold 기반 판정 흐름이 유지된다 (regression).
 *  - runtime setting `zero_signal_exclude.enabled=false` 면 zero_signal 이 발동하지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminReviewQueueServiceRuleEngineTest {

    @Autowired
    lateinit var adminReviewQueueService: AdminReviewQueueService

    @Autowired
    lateinit var categoryStore: CategoryStore

    @Autowired
    lateinit var categoryRuleStore: CategoryRuleStore

    @Autowired
    lateinit var itemStore: RssItemStore

    @Autowired
    lateinit var summaryStore: BatchSummaryStore

    @Autowired
    lateinit var reviewItemDecisionStore: ReviewItemDecisionStore

    @Autowired
    lateinit var reviewItemAuditStore: ReviewItemAuditStore

    @Autowired
    lateinit var runtimeSettingStore: RuntimeSettingStore

    @BeforeEach
    fun enableZeroSignalByDefault() {
        // 각 테스트가 일관된 상태에서 시작하도록 zero_signal 기본값을 활성으로 보정한다.
        runtimeSettingStore.save(
            RuntimeSetting(
                key = ReviewPolicyRuleEvaluator.ZERO_SIGNAL_KEY,
                value = "true",
            )
        )
    }

    // ── 픽스처 헬퍼 ──

    private fun createCategoryWithRule(
        nameSuffix: String,
        excludeEventTypes: List<String> = emptyList(),
        includeKeywords: List<String> = emptyList(),
    ): Category {
        val cat = categoryStore.save(Category(id = "", name = "RuleEngCat-$nameSuffix-${System.nanoTime()}"))
        categoryRuleStore.upsert(
            CategoryRule(
                categoryId = cat.id,
                includeKeywords = includeKeywords,
                excludeEventTypes = excludeEventTypes,
            )
        )
        return cat
    }

    private fun createSummary(
        category: Category,
        title: String,
        summary: String,
        eventType: String?,
        sentiment: String?,
        importanceScore: Float = 0.7f, // threshold 기반 판정에서 INCLUDE 로 떨어지는 안전 기본값
    ): BatchSummary {
        val item = itemStore.save(
            RssItem(
                id = "",
                title = title,
                content = summary,
                link = "https://example.com/re-${System.nanoTime()}",
                categoryId = category.id
            )
        )
        return summaryStore.save(
            BatchSummary(
                id = "",
                originalTitle = title,
                summary = summary,
                keywords = listOf("kw"),
                importanceScore = importanceScore,
                sourceLink = item.link,
                categoryId = category.id,
                rssItemId = item.id,
                eventType = eventType,
                sentiment = sentiment,
            )
        )
    }

    // ════════════════════════════════════════════
    // 룰 바인딩 검증
    // ════════════════════════════════════════════

    @Test
    fun `event_type_blacklist 에 걸린 기사는 자동 EXCLUDE 되고 audit reason 이 rule prefix 로 기록된다`() {
        val cat = createCategoryWithRule(
            "blacklist",
            excludeEventTypes = listOf("OPINION"),
        )
        val summary = createSummary(
            cat,
            title = "중요한 기사이지만 OPINION 이다",
            summary = "본문은 INCLUDE 기준을 넘는 중요도",
            eventType = "OPINION",
            sentiment = "POSITIVE",
            importanceScore = 0.9f, // 원래는 INCLUDE 로 갈 점수
        )

        adminReviewQueueService.ensurePolicyReviewDecisions(listOf(summary))

        val decision = reviewItemDecisionStore.findBySummaryId(summary.id)
        decision.shouldNotBeNull()
        decision.status shouldBe ReviewDecisionStatus.EXCLUDE
        decision.reviewedBy shouldBe "policy-auto"
        decision.reason shouldBe "rule:event_type_blacklist"

        // audit 이력도 동일한 reason 으로 저장돼야 한다
        val audits = reviewItemAuditStore.listBySummaryId(summary.id, limit = 10)
        audits shouldHaveAtLeastSize 1
        audits.first().reason shouldBe "rule:event_type_blacklist"
        audits.first().toStatus shouldBe ReviewDecisionStatus.EXCLUDE
        audits.first().reviewedBy shouldBe "policy-auto"
    }

    @Test
    fun `zero_signal 조건 만족 시 자동 EXCLUDE 되고 reason 이 rule zero_signal 로 기록된다`() {
        val cat = createCategoryWithRule(
            "zerosignal",
            includeKeywords = listOf("AI", "반도체"),
        )
        val summary = createSummary(
            cat,
            title = "관련 없는 중립 뉴스",
            summary = "전혀 관련 없는 중립 본문",
            eventType = "OTHER",
            sentiment = "NEUTRAL",
            importanceScore = 0.9f, // 중요도 높아도 zero_signal 이 먼저 잡아야 함
        )

        adminReviewQueueService.ensurePolicyReviewDecisions(listOf(summary))

        val decision = reviewItemDecisionStore.findBySummaryId(summary.id)
        decision.shouldNotBeNull()
        decision.status shouldBe ReviewDecisionStatus.EXCLUDE
        decision.reason shouldBe "rule:zero_signal"
    }

    @Test
    fun `룰 미적용 기사는 기존 threshold 기반 판정이 그대로 유지된다`() {
        val cat = createCategoryWithRule(
            "passthrough",
            excludeEventTypes = emptyList(),
            includeKeywords = listOf("AI"),
        )
        // eventType 이 OTHER 지만 제목에 AI 키워드가 매칭되므로 zero_signal 비발동
        val summary = createSummary(
            cat,
            title = "AI 반도체 시장 전망",
            summary = "매칭되는 본문",
            eventType = "OTHER",
            sentiment = "NEUTRAL",
            importanceScore = 0.9f, // INCLUDE threshold 이상 → INCLUDE 제안 (저장되지 않음)
        )

        adminReviewQueueService.ensurePolicyReviewDecisions(listOf(summary))

        // INCLUDE 제안 + autoApproveThreshold 미설정 → 저장되지 않아야 한다 (기존 동작)
        val decision = reviewItemDecisionStore.findBySummaryId(summary.id)
        // decision 이 null 이거나 있더라도 reason 이 rule prefix 가 아니어야 한다
        if (decision != null) {
            (decision.reason?.startsWith("rule:") == true) shouldBe false
        }
    }

    @Test
    fun `zero_signal enabled 가 false 면 zero_signal 룰이 발동하지 않는다`() {
        // setting 을 false 로 덮어쓴다
        runtimeSettingStore.save(
            RuntimeSetting(
                key = ReviewPolicyRuleEvaluator.ZERO_SIGNAL_KEY,
                value = "false",
            )
        )
        val cat = createCategoryWithRule(
            "zerosignal-off",
            includeKeywords = listOf("AI"),
        )
        val summary = createSummary(
            cat,
            title = "관련 없는 중립 뉴스",
            summary = "전혀 관련 없는 중립 본문",
            eventType = "OTHER",
            sentiment = "NEUTRAL",
            importanceScore = 0.9f,
        )

        adminReviewQueueService.ensurePolicyReviewDecisions(listOf(summary))

        // zero_signal 비활성이므로 rule:zero_signal 로 저장되면 안 된다
        val decision = reviewItemDecisionStore.findBySummaryId(summary.id)
        if (decision != null) {
            (decision.reason == "rule:zero_signal") shouldBe false
        }
    }
}
