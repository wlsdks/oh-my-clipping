package com.ohmyclipping.service.digest

import com.ohmyclipping.config.AppProperties
import com.ohmyclipping.service.dto.clipping.DigestItemResult
import com.ohmyclipping.model.Organization
import com.ohmyclipping.service.digest.BadgedArticle
import com.ohmyclipping.service.digest.DigestDocument
import com.ohmyclipping.service.digest.DigestDocumentBuilder
import com.ohmyclipping.service.digest.DigestDocumentItem
import com.ohmyclipping.service.digest.DigestMode
import com.ohmyclipping.service.digest.DigestSectionResult
import com.ohmyclipping.service.digest.DigestSummaryFormattingPolicy
import com.ohmyclipping.service.digest.EscalationCopy
import com.ohmyclipping.service.digest.resolveSectionLabel
import com.ohmyclipping.service.digest.toDigestOrganizations
import com.ohmyclipping.support.GraphemeTruncator
import com.ohmyclipping.support.SlackEscapeUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.net.URLEncoder
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.util.Locale

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

private val log = KotlinLogging.logger {}

/**
 * 다이제스트 스냅샷을 Slack 메시지 형식(Block Kit / fallback 텍스트)으로 렌더링한다.
 *
 * 책임 분리: 후보 선정과 Slack 전송은 담당하지 않는다. 입력 `DigestItemResult` 목록을
 * 가지고 Slack 이 받는 출력 문자열/블록 구조만 만든다.
 *
 * 외부 의존성은 `AppProperties` 하나뿐이며, URL escape, 링크 tracking, 이모지 중복 제거,
 * 문단 분할 등 출력 경계에서 필요한 모든 유틸을 내부에 유지한다.
 *
 * 이 클래스는 Spring Bean 이 아니다 — `DigestService` 가 자신이 받은 의존성으로 직접 인스턴스화한다.
 * 렌더러가 별도 Bean 이 되면 기존 DigestService 단위 테스트의 생성자 시그니처가 깨진다.
 */
class DigestRenderer(
    private val appProperties: AppProperties
) {

    companion object {
        /** Slack Block Kit section text 한 블록당 최대 3,000자(Slack API 한계) */
        private const val SLACK_SECTION_MAX_CHARS = 3000

        /** 다이제스트 카드 제목 렌더 시 허용하는 최대 grapheme 수 */
        private const val ITEM_TITLE_MAX_GRAPHEMES = 120

        /** Slack 링크 label 렌더에 사용할 불안전 URL 대체 문구 */
        private const val UNSAFE_URL_FALLBACK_LABEL = "원문 링크 사용 불가"
    }

    // -- 공개 렌더링 진입점 --

    private fun buildDocument(
        categoryName: String,
        totalCandidates: Int,
        items: List<DigestItemResult>,
        keywordMaxCount: Int,
        userRequestedMaxItems: Int,
    ): DigestDocument =
        DigestDocumentBuilder.build(
            categoryName = categoryName,
            totalCandidates = totalCandidates,
            requestedMaxItems = userRequestedMaxItems,
            keywordLimit = keywordMaxCount,
            items = items.map { it.toDigestDocumentItem() },
        )

    private fun DigestItemResult.toDigestDocumentItem(): DigestDocumentItem =
        DigestDocumentItem(
            id = summaryId,
            title = title,
            summary = summary,
            keywords = keywords,
            importanceScore = importanceScore.toDouble(),
            whyImportant = whyImportant,
            sourceLink = sourceLink,
            createdAt = runCatching { Instant.parse(createdAt) }.getOrNull(),
            isFallback = isFallback,
        )

    /**
     * 플레인 텍스트 다이제스트를 렌더링한다.
     * DigestResult.digestText 에 담기는 값으로, Slack 에 보내지 않는 경로(sendToSlack=false) 에서도 재사용된다.
     */
    fun buildDigestText(
        categoryName: String,
        totalCandidates: Int,
        items: List<DigestItemResult>,
        maxMessageChars: Int,
        itemSummaryMaxChars: Int,
        keywordMaxCount: Int,
        userRequestedMaxItems: Int
    ): String = buildDigestText(
        document = buildDocument(categoryName, totalCandidates, items, keywordMaxCount, userRequestedMaxItems),
        maxMessageChars = maxMessageChars,
        itemSummaryMaxChars = itemSummaryMaxChars,
    )

    private fun buildDigestText(
        document: DigestDocument,
        maxMessageChars: Int,
        itemSummaryMaxChars: Int,
    ): String {
        // 카테고리명은 사용자가 임의로 짓기 때문에 mrkdwn 포맷 문자를 포함할 수 있다
        val escapedCategory = escapeForSection(document.categoryName)
        if (document.items.isEmpty()) {
            return "*$escapedCategory* 다이제스트\n- 전송할 요약이 없습니다."
        }

        val topKeywords = document.topKeywords.joinToString(", ")

        val header = buildString {
            append("*")
            append(escapedCategory)
            append("* Clipping 다이제스트 (")
            append(document.selectedCount)
            append("/")
            append(document.totalCandidates)
            append(")")
            if (topKeywords.isNotBlank()) {
                append("\n핵심 키워드: ")
                append(escapeForSection(topKeywords))
            }
        }

        val singleItem = document.items.size == 1
        val body = document.items.mapIndexed { index, item ->
            buildString {
                append("\n")
                if (!singleItem) {
                    append(index + 1)
                    append(". ")
                }
                append("*")
                append(escapeForSection(item.title))
                append("*\n")
                // 출처 라벨은 scheme 검증을 통과한 링크에서만 추출한다 (javascript:/data: 등 노출 방지)
                val safeLinkForLabel = safeSourceLinkOrNull(item.sourceLink)
                if (safeLinkForLabel != null) {
                    append("   - ")
                    append("출처: ")
                    append(escapeForSection(sourceLabel(safeLinkForLabel)))
                    append("\n")
                }
                append(formatSummaryForPlain(item.summary, maxChars = itemSummaryMaxChars).prependIndent("   "))
                append("\n")
                // 원본 link는 scheme 화이트리스트 통과한 경우에만 tracking URL로 감싼다
                val safeSourceLink = safeSourceLinkOrNull(item.sourceLink)
                if (safeSourceLink != null) {
                    append("   - <")
                    append(buildTrackingUrl(item.id, safeSourceLink))
                    append("|원문 보기>")
                } else {
                    append("   - _${UNSAFE_URL_FALLBACK_LABEL}_")
                }
            }
        }.joinToString("")

        // 품질 필터로 인해 선정 수가 요청 수보다 적을 때 사용자에게 이유를 안내하는 푸터를 추가한다
        val thinDayFooter = if (document.isThinDay) {
            "\n\n📊 오늘은 품질 기준을 넘은 기사가 ${document.selectedCount}건이라 ${document.selectedCount}건만 보내드려요. (설정: ${document.requestedMaxItems}건)"
        } else {
            ""
        }
        val full = header + body + thinDayFooter
        // grapheme cluster 기반 truncate로 한글 조합/이모지 ZWJ sequence 중간 잘림 방지
        return GraphemeTruncator.truncateByGrapheme(full, maxMessageChars, ellipsis = "")
    }

    /**
     * Slack Block Kit 블록 배열을 생성한다.
     * 헤더 / 키워드 / 기사별 카드 / 푸터(thin-day 안내 + 대시보드 링크) 순으로 조립하며
     * Slack 의 50블록 제한을 넘지 않도록 방어한다.
     */
    fun buildDigestBlocks(
        categoryName: String,
        categoryId: String,
        totalCandidates: Int,
        items: List<DigestItemResult>,
        itemSummaryMaxChars: Int,
        keywordMaxCount: Int,
        userRequestedMaxItems: Int
    ): List<Map<String, Any?>> = buildDigestBlocks(
        document = buildDocument(categoryName, totalCandidates, items, keywordMaxCount, userRequestedMaxItems),
        itemSummaryMaxChars = itemSummaryMaxChars,
    )

    private fun buildDigestBlocks(
        document: DigestDocument,
        itemSummaryMaxChars: Int,
    ): List<Map<String, Any?>> {
        val blocks = mutableListOf<Map<String, Any?>>()
        val safeItemSummaryMaxChars = itemSummaryMaxChars.coerceAtLeast(220)
        val topKeywords = document.topKeywords.joinToString(", ")
        val dateLabel = formatKoreanDigestDate(ZonedDateTime.now(KST).toLocalDate())

        // 헤더·컨텍스트·핵심 키워드·divider 를 한 번에 붙인다
        blocks += buildDigestHeaderBlocks(document.categoryName, dateLabel, document.selectedCount, topKeywords)

        // AI 요약 폴백이 있으면 전용 안내 섹션을 추가한다
        if (document.hasFallbackItems) {
            blocks += buildFallbackNoticeBlocks()
        }

        // 기사별 블록(제목/요약/원문 버튼/피드백)을 순서대로 덧붙인다
        document.items.forEachIndexed { index, item ->
            blocks += buildItemBlocks(
                index = index,
                item = item,
                safeItemSummaryMaxChars = safeItemSummaryMaxChars,
                isLast = index == document.items.lastIndex
            )
        }

        // 푸터(thin-day 안내 + 대시보드 링크) 를 붙인다
        // 품질 필터로 인해 선정 수가 요청 수보다 적을 때(items.size < userRequestedMaxItems) 이유를 안내한다
        blocks += buildDigestFooterBlocks(
            selectedCount = document.selectedCount,
            userRequestedMaxItems = document.requestedMaxItems
        )

        // Slack Block Kit 메시지당 최대 50블록 제한을 초과하지 않도록 방어한다
        if (blocks.size > 50) {
            log.warn { "Digest blocks (${blocks.size}) exceed Slack 50-block limit, truncating" }
            return blocks.take(50)
        }
        return blocks
    }

    /**
     * Slack 이 blocks 를 렌더링하지 못할 때 fallback 으로 사용할 평문 텍스트.
     * Block Kit 출력과 같은 정보(제목/요약/원문/키워드/선정이유)를 평문 bullet 으로 제공한다.
     */
    fun buildSlackDigestFallbackText(
        categoryName: String,
        totalCandidates: Int,
        items: List<DigestItemResult>,
        itemSummaryMaxChars: Int,
        keywordMaxCount: Int,
        userRequestedMaxItems: Int
    ): String = buildSlackDigestFallbackText(
        document = buildDocument(categoryName, totalCandidates, items, keywordMaxCount, userRequestedMaxItems),
        itemSummaryMaxChars = itemSummaryMaxChars,
    )

    private fun buildSlackDigestFallbackText(
        document: DigestDocument,
        itemSummaryMaxChars: Int,
    ): String {
        val escapedCategory = escapeForSection(document.categoryName)
        if (document.items.isEmpty()) {
            return "*$escapedCategory* 다이제스트\n- 전송할 요약이 없습니다."
        }

        val topKeywords = document.topKeywords.joinToString(", ")
            .let { if (it.isNotBlank()) "\n핵심 키워드: ${escapeForSection(it)}" else "" }

        val parts = document.items.mapIndexed { index, item ->
            buildString {
                append(index + 1)
                append(". ")
                append(escapeForSection(item.title))
                append("\n   - ")
                append(formatSummaryForPlain(item.summary, maxChars = itemSummaryMaxChars))
                // 원문 링크는 화이트리스트 통과 시에만 출력
                val safeSourceLink = safeSourceLinkOrNull(item.sourceLink)
                if (safeSourceLink != null) {
                    append("\n   - 원문: ")
                    append(buildTrackingUrl(item.id, safeSourceLink))
                }
                if (item.keywords.isNotEmpty()) {
                    append("\n   - 키워드: ")
                    append(escapeForSection(item.keywords.joinToString(", ")))
                }
                if (item.whyImportant.isNotBlank()) {
                    append("\n   - 선정 이유: ")
                    append(escapeForSection(item.whyImportant))
                }
            }
        }

        return buildString {
            append("*")
            append(escapedCategory)
            append("* 다이제스트 (")
            append(document.selectedCount)
            append("/")
            append(document.totalCandidates)
            append(")")
            append(topKeywords)
            append("\n")
            append(parts.joinToString("\n\n"))
            // 품질 필터로 인해 선정 수가 요청 수보다 적을 때 사용자에게 이유를 안내하는 푸터를 추가한다
            if (document.isThinDay) {
                appendLine()
                appendLine()
                append("📊 오늘은 품질 기준을 넘은 기사가 ${document.selectedCount}건이라 ${document.selectedCount}건만 보내드려요. (설정: ${document.requestedMaxItems}건)")
            }
            // 웹 대시보드 링크
            val dashboardUrl = appProperties.baseUrl.trimEnd('/')
            appendLine()
            appendLine()
            append("Clipping에서 더 자세히 보기 → $dashboardUrl/user/news-report")
        }
    }

    // -- 요약 가공 / 이모지 중복 제거 --

    /**
     * LLM 출력에서 발생하는 섹션 이모지 중복/연속 붙임을 정규화한다.
     * 다이제스트 카드의 첫 글자 이모지가 겹쳐 보이지 않도록 출력 경계에서 1회 적용한다.
     */
    fun sanitizeSummaryForDisplay(text: String): String =
        DigestSummaryFormattingPolicy.sanitizeSummaryForDisplay(text)

    /**
     * 본문 앞쪽의 이모지·장식 문자를 모두 제거한다. 한글/영문/숫자/기본 문장부호가 처음 나타날 때까지 truncate.
     * 페르소나 prompt 의 섹션 이모지(📌 등) + LLM 이 임의로 추가한 장식 이모지(🍃, 💼, 🌟 …) 를 모두 흡수.
     */
    fun stripLeadingDecoration(text: String): String {
        return DigestSummaryFormattingPolicy.stripLeadingDecoration(text)
    }

    /**
     * Slack 단일 기사 카드에 들어갈 요약 텍스트를 만든다.
     * 최대 3개 paragraph 을 라벨 이모지(📌/🔍/💡) 와 함께 붙이고, 섹션 간 추가 공백을 강제한다.
     */
    fun summarizeForSlackText(summary: String, maxChars: Int): String {
        val summaryParts = buildSummaryParts(summary, maxChars)
        if (summaryParts.isEmpty()) {
            return "*요약*\n요약을 생성하지 못했습니다."
        }

        // 섹션 이모지(📌/🔍/💡) 사이는 `\n\n` 한 줄 공백으로 구분한다.
        // 기존 `\n\n\u200B\n\n` 은 데스크톱에서는 적절했으나 모바일에서 과도한 빈 줄로 보였다.
        return summaryParts.joinToString("\n\n") { part ->
            "${part.title} ${part.content}"
        }
    }

    /**
     * 원본 기사 URL을 클릭 추적 리다이렉트 URL로 감싼다.
     * Slack 버튼은 절대 URL이 필요하므로 APP_BASE_URL을 기반으로 생성한다.
     *
     * Path-based 경로 `/api/track/click/slack/{sid}` 를 사용해 source="slack" 태그가
     * URL 복사/붙여넣기/북마크에도 유지되도록 한다.
     * (기존 `/api/track/click?sid=...` 경로는 backward compat 로 컨트롤러에 남아있음.)
     */
    fun buildTrackingUrl(summaryId: String, originalUrl: String): String {
        val encodedUrl = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8)
        // summaryId가 path segment에 안전하게 포함되도록 인코딩
        val encodedSid = URLEncoder.encode(summaryId, StandardCharsets.UTF_8)
        val base = appProperties.baseUrl.trimEnd('/')
        return "$base/api/track/click/slack/$encodedSid?url=$encodedUrl"
    }

    // -- 템플릿 렌더링 지원 (커스텀 Block Kit 템플릿에서 사용) --

    /** `computeTopKeywords` 를 외부(템플릿 컨텍스트 빌더)에서 재사용할 수 있게 노출한다. */
    fun computeTopKeywordsForTemplate(items: List<DigestItemResult>, max: Int): List<String> =
        DigestDocumentBuilder.computeTopKeywords(items.map { it.toDigestDocumentItem() }, max)

    /** 출처 호스트 라벨을 외부에서 재사용하기 위해 노출한다. */
    fun sourceLabelOf(sourceLink: String): String = sourceLabel(sourceLink)

    /** safeSourceLinkOrNull 을 외부에서 재사용하기 위해 노출한다. */
    fun safeSourceLinkOrNullOf(sourceLink: String): String? = safeSourceLinkOrNull(sourceLink)

    /** 섹션 텍스트 이스케이프를 외부에서 재사용하기 위해 노출한다. */
    fun escapeForSectionOf(text: String): String = escapeForSection(text)

    /** 현재 KST 기준 한국어 날짜 라벨을 반환한다. */
    fun currentKoreanDateLabel(): String = formatKoreanDigestDate(ZonedDateTime.now(KST).toLocalDate())

    /** 제목 grapheme 최대 길이 상수를 외부에 노출한다 (템플릿 컨텍스트용). */
    val itemTitleMaxGraphemes: Int get() = ITEM_TITLE_MAX_GRAPHEMES

    // -- Account-based digest 렌더링 --

    /**
     * 단일 기사 섹션 블록을 mrkdwn JSON 문자열로 렌더링한다.
     *
     * badged=true 일 때 ⭐ 를 제목 뒤 suffix 로 붙인다.
     * ⭐ 를 line head 에 두지 않는 것이 핵심 — stripLeadingDecoration(AGENTS.md §1.3.1) 회귀 방지.
     *
     * @param title 기사 제목
     * @param summary 기사 요약 본문
     * @param badged true 이면 제목 뒤에 ⭐ suffix 를 붙인다
     * @return Slack Block Kit section 블록 JSON 문자열
     */
    fun renderAccountSectionBlock(title: String, summary: String, badged: Boolean): String {
        // ⭐ 는 반드시 제목 텍스트 뒤에 붙여 line head 에 오지 않게 한다
        val titleLine = if (badged) "*${escapeJsonStr(title)}* ⭐" else "*${escapeJsonStr(title)}*"
        val summaryLine = escapeJsonStr(summary)
        val body = "$titleLine\\n$summaryLine"
        return """{"type":"section","text":{"type":"mrkdwn","text":"$body"}}"""
    }

    /**
     * 섹션 리스트를 받아 Slack Block Kit JSON 배열 문자열로 렌더링한다.
     *
     * 섹션 순서: header → 기사 블록들(또는 empty context block) → [divider] → 다음 섹션 ...
     * → DUAL 모드 첫 사용 시 legend context block 1회 추가.
     *
     * @param sections 섹션 결과 목록
     * @param mode 다이제스트 구성 모드
     * @param keywords 토픽 키워드 목록
     * @param orgs 관련 조직 목록
     * @param dualLegendShown 이미 legend 를 본 적 있으면 true (재노출 방지)
     * @param emptyCopies 섹션 kind → EscalationCopy. 섹션이 비었을 때 context block footer 로 노출
     * @return Block Kit JSON 배열 문자열 (파싱 가능한 valid JSON)
     */
    fun renderAccountBasedDigest(
        sections: List<DigestSectionResult>,
        mode: DigestMode,
        keywords: List<String>,
        orgs: List<Organization>,
        dualLegendShown: Boolean,
        emptyCopies: Map<String, EscalationCopy> = emptyMap()
    ): String {
        val blocks = mutableListOf<String>()
        val digestOrgs = orgs.toDigestOrganizations()

        sections.forEachIndexed { i, section ->
            // 첫 번째 이후의 섹션 앞에 divider 를 삽입해 시각적 구분을 명확히 한다
            if (i > 0) blocks.add(accountDividerBlock())

            // DUAL_SECTION 은 섹션 kind 를 기반으로 헤더 텍스트를 결정한다
            val dualKind = if (mode == DigestMode.DUAL_SECTION) section.kind else null
            val headerText = resolveSectionLabel(mode, keywords, digestOrgs, dualKind)
            blocks.add(accountHeaderBlock(headerText))

            if (section.articles.isEmpty()) {
                // 빈 섹션 → escalation copy 가 있으면 context block footer 로 노출
                val copy = emptyCopies[section.kind]
                if (copy != null) {
                    blocks.add(accountContextBlock(copy.text))
                }
            } else {
                section.articles.forEach { ba ->
                    blocks.add(renderAccountSectionBlock(ba.article.title, ba.article.summary, ba.badged))
                }
            }
        }

        // DUAL 모드에서 ⭐ 의미 안내
        //  - 처음 3회까지: 전체 legend 텍스트 (caller 가 dualLegendShown=false 전달)
        //  - 이후: footer link 만 — caller 가 dualLegendShown=true 전달
        // footer link 는 DUAL 모드면 항상 포함돼 참고 위치를 상실하지 않도록 보장.
        if (mode == DigestMode.DUAL_SECTION) {
            if (!dualLegendShown) {
                blocks.add(accountContextBlock("⭐ = 주제와 기업이 모두 일치하는 뉴스"))
            }
            val legendUrl = "${appProperties.baseUrl.trimEnd('/')}/help/legend"
            blocks.add(accountContextBlock("<$legendUrl|⭐ 표기 자세히 보기>"))
        }

        return blocks.joinToString(",", prefix = "[", postfix = "]")
    }

    private fun accountHeaderBlock(text: String): String =
        """{"type":"header","text":{"type":"plain_text","text":"${escapeJsonStr(text)}"}}"""

    private fun accountDividerBlock(): String = """{"type":"divider"}"""

    private fun accountContextBlock(text: String): String =
        """{"type":"context","elements":[{"type":"mrkdwn","text":"${escapeJsonStr(text)}"}]}"""

    /**
     * JSON 문자열 리터럴 내부에서 안전하게 포함될 수 있도록 최소 escape 를 적용한다.
     * 기존 Block Kit 빌더는 Jackson Map 기반이므로 별도 escape 유틸이 없어 여기서 자체 구현한다.
     */
    private fun escapeJsonStr(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    // -- 내부 블록 빌더 --

    /**
     * 다이제스트 상단 블록(헤더/컨텍스트/핵심 키워드/divider)을 만든다.
     * 헤더는 plain_text 라 mrkdwn escape 불필요. 단 `<>&`는 Slack 이 entity 로 디코드하므로 HTML escape 적용.
     */
    private fun buildDigestHeaderBlocks(
        categoryName: String,
        dateLabel: String,
        itemCount: Int,
        topKeywords: String
    ): List<Map<String, Any?>> {
        val headerBlocks = mutableListOf<Map<String, Any?>>()
        headerBlocks.add(
            mapOf(
                "type" to "header",
                "text" to mapOf(
                    "type" to "plain_text",
                    "text" to SlackEscapeUtil.escapeHtml("$categoryName 다이제스트 — $dateLabel"),
                    "emoji" to true
                )
            )
        )
        headerBlocks.add(
            mapOf(
                "type" to "context",
                "elements" to listOf(
                    mapOf(
                        "type" to "mrkdwn",
                        "text" to "${escapeForSection(dateLabel)} · ${itemCount}건의 뉴스"
                    )
                )
            )
        )
        if (topKeywords.isNotBlank()) {
            val keywordTags = topKeywords
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            headerBlocks.add(
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "mrkdwn",
                        "text" to enforceSlackSectionLimit("*핵심 키워드*\n${formatKeywordTags(keywordTags)}")
                    )
                )
            )
        }
        headerBlocks.add(mapOf("type" to "divider"))
        return headerBlocks
    }

    /** AI 요약 폴백이 있는 경우 사용자에게 안내 문구와 divider 를 삽입한다 */
    private fun buildFallbackNoticeBlocks(): List<Map<String, Any?>> = listOf(
        mapOf(
            "type" to "section",
            "text" to mapOf(
                "type" to "mrkdwn",
                "text" to "\uD83D\uDD04 AI 요약이 일시적으로 불가하여 일부 기사를 원문 요약으로 대체합니다"
            )
        ),
        mapOf("type" to "divider")
    )

    /**
     * 한 기사에 대응하는 블록 묶음(제목/요약/원문 버튼/피드백 버튼)을 만든다.
     * 마지막 기사가 아니면 뒤에 divider 를 덧붙인다.
     */
    private fun buildItemBlocks(
        index: Int,
        item: DigestDocumentItem,
        safeItemSummaryMaxChars: Int,
        isLast: Boolean
    ): List<Map<String, Any?>> {
        val itemBlocks = mutableListOf<Map<String, Any?>>()
        // 제목은 grapheme 기준으로 먼저 자른 뒤 mrkdwn 이스케이프를 적용해야 한글/이모지 중간 깨짐을 막는다
        val rawTitle = item.title.trim().ifBlank { "뉴스 항목" }
        val truncatedTitle = GraphemeTruncator.truncateByGrapheme(rawTitle, ITEM_TITLE_MAX_GRAPHEMES)
        val escapedTitle = escapeForSection(truncatedTitle)
        val headerText = "*${index + 1}. ${escapedTitle}*"
        itemBlocks.add(
            mapOf(
                "type" to "section",
                "text" to mapOf(
                    "type" to "mrkdwn",
                    "text" to enforceSlackSectionLimit(headerText)
                )
            )
        )
        // 요약/원문 링크/피드백 버튼은 각자 전용 헬퍼에서 만든다
        itemBlocks += buildItemSummaryBlock(item, safeItemSummaryMaxChars)
        itemBlocks += buildItemSourceLinkBlock(item)
        itemBlocks += buildItemFeedbackBlock(item)

        if (!isLast) {
            itemBlocks.add(mapOf("type" to "divider"))
        }
        return itemBlocks
    }

    /** 기사 요약 섹션 블록을 만든다. 요약이 비어 있으면 대체 문구를 사용한다. */
    private fun buildItemSummaryBlock(
        item: DigestDocumentItem,
        safeItemSummaryMaxChars: Int
    ): Map<String, Any?> {
        val summaryParts = buildSummaryParts(
            summary = item.summary,
            maxChars = safeItemSummaryMaxChars
        )
        if (summaryParts.isEmpty()) {
            return mapOf(
                "type" to "section",
                "text" to mapOf(
                    "type" to "mrkdwn",
                    "text" to "*요약*\n요약을 생성하지 못했습니다."
                )
            )
        }
        // 요약은 buildSummaryParts 내부에서 이미 escape를 적용하므로 여기서는 최종 길이만 enforce
        val summaryText = summarizeForSlackText(item.summary, safeItemSummaryMaxChars)
        return mapOf(
            "type" to "section",
            "text" to mapOf("type" to "mrkdwn", "text" to enforceSlackSectionLimit(summaryText))
        )
    }

    /**
     * 원문 링크 블록을 만든다.
     * 원본 URL이 http/https가 아니면 button의 url 필드를 생략한다 (Slack button url은 scheme 검증 필수).
     */
    private fun buildItemSourceLinkBlock(item: DigestDocumentItem): Map<String, Any?> {
        val safeSourceLink = safeSourceLinkOrNull(item.sourceLink)
        if (safeSourceLink != null) {
            return mapOf(
                "type" to "actions",
                "elements" to listOf(
                    mapOf(
                        "type" to "button",
                        "text" to mapOf("type" to "plain_text", "text" to "원문 보기", "emoji" to true),
                        "url" to buildTrackingUrl(item.id, safeSourceLink),
                        "action_id" to "open_source_${item.id}"
                    )
                )
            )
        }
        // URL scheme이 안전하지 않으면 버튼 대신 안내 문구만 남긴다
        log.warn { "Skipping unsafe sourceLink for summaryId=${item.id}" }
        return mapOf(
            "type" to "section",
            "text" to mapOf(
                "type" to "mrkdwn",
                "text" to "_${UNSAFE_URL_FALLBACK_LABEL}_"
            )
        )
    }

    /** 좋아요/별로 피드백 버튼 두 개가 나란히 붙는 actions 블록을 만든다. */
    private fun buildItemFeedbackBlock(item: DigestDocumentItem): Map<String, Any?> = mapOf(
        "type" to "actions",
        "block_id" to "feedback_${item.id}",
        "elements" to listOf(
            mapOf(
                "type" to "button",
                "text" to mapOf("type" to "plain_text", "text" to "\uD83D\uDC4D 좋아요", "emoji" to true),
                "value" to item.id,
                "action_id" to "feedback_like:${item.id}"
            ),
            mapOf(
                "type" to "button",
                "text" to mapOf("type" to "plain_text", "text" to "\uD83D\uDC4E 별로", "emoji" to true),
                "value" to item.id,
                "action_id" to "feedback_dislike:${item.id}"
            )
        )
    )

    /**
     * 다이제스트 하단 푸터 블록을 만든다.
     * thin-day 안내(선정 수가 요청보다 적을 때)와 Clipping 대시보드 링크를 포함한다.
     */
    private fun buildDigestFooterBlocks(
        selectedCount: Int,
        userRequestedMaxItems: Int
    ): List<Map<String, Any?>> {
        val footerBlocks = mutableListOf<Map<String, Any?>>()
        // 선정 수가 요청 수보다 적으면 품질 안내 섹션을 덧붙인다
        if (selectedCount < userRequestedMaxItems) {
            footerBlocks.add(
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "mrkdwn",
                        "text" to "📊 오늘은 품질 기준을 넘은 기사가 ${selectedCount}건이라 ${selectedCount}건만 보내드려요. (설정: ${userRequestedMaxItems}건)"
                    )
                )
            )
        }
        // 다이제스트 메시지 하단에 웹 대시보드 링크 푸터를 추가한다
        val dashboardUrl = appProperties.baseUrl.trimEnd('/')
        footerBlocks.add(mapOf("type" to "divider"))
        footerBlocks.add(
            mapOf(
                "type" to "context",
                "elements" to listOf(
                    mapOf(
                        "type" to "mrkdwn",
                        "text" to "<$dashboardUrl/user/news-report|Clipping에서 더 자세히 보기> \u2192 키워드 분석, 리포트, 경쟁사 동향을 확인하세요"
                    )
                )
            )
        )
        return footerBlocks
    }

    // -- 문단/문장 분할 유틸 --

    private fun buildSummaryParts(summary: String, maxChars: Int) =
        DigestSummaryFormattingPolicy.buildSummaryParts(summary, maxChars)
            .map { it.copy(content = escapeForSection(it.content)) }

    private fun formatSummaryForPlain(summary: String, maxChars: Int): String {
        val paragraphs = DigestSummaryFormattingPolicy.buildDigestParagraphs(summary, maxChars)
        if (paragraphs.isEmpty()) return "- 요약 정보가 없습니다."

        val sections = mutableListOf<String>()
        val summaryParts = buildSummaryParts(summary, maxChars)
        if (summaryParts.isNotEmpty()) {
            summaryParts.forEach { part ->
                sections += "- ${part.title}: ${part.content}"
            }
            return sections.joinToString("\n\n")
        }

        sections += "- 핵심 내용: ${paragraphs.first()}"
        if (paragraphs.size >= 2) {
            sections += "- 세부 맥락: ${paragraphs[1]}"
        }
        if (paragraphs.size >= 3) {
            sections += "- 실무 시사점: ${paragraphs.drop(2).joinToString(" ")}"
        }
        return sections.joinToString("\n\n")
    }

    // -- 키워드 / 라벨 / 이스케이프 --

    private fun formatKeywordTags(keywords: List<String>): String =
        keywords.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(6)
            // 키워드는 inline code(``` ` ```)로 감싸기 전에 HTML entity만 escape하고,
            // 내부 백틱은 제거해 code span이 깨지지 않게 한다 (mrkdwn escape는 code 내부에서 불필요)
            .map { SlackEscapeUtil.escapeHtml(it).replace("`", "") }
            .joinToString(" ") { "`$it`" }

    private fun sourceLabel(sourceLink: String): String = try {
        val host = URI(sourceLink).host ?: return sourceLink
        host.removePrefix("www.")
    } catch (_: URISyntaxException) {
        sourceLink
    }

    private fun formatKoreanDigestDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)", Locale.KOREAN))

    /**
     * mrkdwn section text 렌더 직전에 호출하는 보호막.
     * HTML entity(`&<>`)와 mrkdwn 포맷(`*_~\``) 이스케이프를 모두 적용한다.
     */
    private fun escapeForSection(text: String): String =
        SlackEscapeUtil.escapeForMrkdwnSection(text)

    /**
     * Slack 한 블록이 3,000자를 넘지 않도록 grapheme 기준으로 안전 truncate한다.
     * char length로 판단해 대부분의 경우 O(1)에 통과하고, 초과 시에만 BreakIterator를 쓴다.
     */
    private fun enforceSlackSectionLimit(text: String): String {
        if (text.length <= SLACK_SECTION_MAX_CHARS) return text
        // ellipsis 포함 총 길이를 SLACK_SECTION_MAX_CHARS 이하로 맞춘다
        return GraphemeTruncator.truncateByGrapheme(text, SLACK_SECTION_MAX_CHARS - 1)
    }

    /**
     * 원본 링크 URL이 http/https가 아니면 null을 반환해 호출부에서 link 렌더를 생략하게 한다.
     * buildTrackingUrl은 내부 baseUrl을 쓰므로 안전하지만, 원본 검증은 출력 경계에서 추가 수행한다.
     */
    private fun safeSourceLinkOrNull(sourceLink: String): String? =
        if (SlackEscapeUtil.isSafeUrl(sourceLink)) sourceLink else null
}
