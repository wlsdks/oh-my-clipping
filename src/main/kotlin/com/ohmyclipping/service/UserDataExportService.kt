package com.ohmyclipping.service

import com.ohmyclipping.config.RedisRateLimitService
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.BookmarkedArticle
import com.ohmyclipping.model.DeliveryLog
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.model.UserDeliverySchedule
import com.ohmyclipping.model.UserEvent
import com.ohmyclipping.service.dto.admin.AccountExportSection
import com.ohmyclipping.service.dto.admin.BookmarkExportEntry
import com.ohmyclipping.service.dto.admin.DeliveryLogExportEntry
import com.ohmyclipping.service.dto.admin.DeliveryScheduleExportEntry
import com.ohmyclipping.service.dto.admin.PersonalDataExport
import com.ohmyclipping.service.dto.admin.PersonalDataExportLimits
import com.ohmyclipping.service.dto.admin.PreferencesExportSection
import com.ohmyclipping.service.dto.admin.SubscriptionExportEntry
import com.ohmyclipping.service.dto.admin.SummaryFeedbackExportEntry
import com.ohmyclipping.service.dto.admin.UserEventExportEntry
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.BookmarkedArticleStore
import com.ohmyclipping.store.DeliveryLogStore
import com.ohmyclipping.store.DepartmentStore
import com.ohmyclipping.store.SummaryFeedbackStore
import com.ohmyclipping.store.TeamStore
import com.ohmyclipping.store.UserClippingRequestStore
import com.ohmyclipping.store.UserDeliveryScheduleStore
import com.ohmyclipping.store.UserEventStore
import com.ohmyclipping.store.UserOwnedCategoryStore
import com.ohmyclipping.store.UserOwnedPersonaStore
import com.ohmyclipping.store.UserOwnedSourceStore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

/**
 * 사용자 본인의 개인정보를 JSON/CSV 로 내려받도록 지원하는 서비스.
 *
 * 개인정보보호법 제35조(개인정보의 열람)에 따라 본인이 요청한 데이터만,
 * 민감 필드를 whitelist 방식으로 제외한 뒤 제공한다. 다른 사용자의 개인정보가
 * 결과에 섞이지 않도록 모든 조회는 `username` 기반으로 엄격히 제한된다.
 *
 * 일일 export 횟수는 [PersonalDataExportLimits.MAX_EXPORTS_PER_DAY]회로 제한되며,
 * 모든 요청은 감사 로그(`PERSONAL_DATA_EXPORT`)에 기록된다.
 */
@Service
class UserDataExportService(
    private val adminUserStore: AdminUserStore,
    private val userClippingRequestStore: UserClippingRequestStore,
    private val userOwnedCategoryStore: UserOwnedCategoryStore,
    private val userOwnedPersonaStore: UserOwnedPersonaStore,
    private val userOwnedSourceStore: UserOwnedSourceStore,
    private val userDeliveryScheduleStore: UserDeliveryScheduleStore,
    private val userEventStore: UserEventStore,
    private val bookmarkedArticleStore: BookmarkedArticleStore,
    private val deliveryLogStore: DeliveryLogStore,
    private val auditLogStore: AuditLogStore,
    private val rateLimitService: RedisRateLimitService,
    private val departmentStore: DepartmentStore,
    private val teamStore: TeamStore,
    private val summaryFeedbackStore: SummaryFeedbackStore
) {

    companion object {
        const val FORMAT_JSON: String = "json"
        const val FORMAT_CSV: String = "csv"

        /** 법적 근거 안내 문구. 프론트에도 노출되는 고정 텍스트. */
        private const val LEGAL_BASIS: String =
            "개인정보보호법 제35조(개인정보의 열람) 및 제38조(권리행사의 방법 및 절차)에 따른 본인 열람 요청"

        /** 일일 rate limit 윈도우. 25시간으로 넉넉히 잡아 타임존 경계 문제를 완화한다. */
        private const val RATE_LIMIT_WINDOW_SECONDS: Long = 25 * 60 * 60

        /** 내부 메타 태그 제거용 정규식. `[key=value]` 형태를 모두 제거한다. */
        private val INTERNAL_TAG_PATTERN = Regex("""\[[^\[\]]+?]""")

        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        // CSV 헤더 상수. 순서를 고정해 사용자/외부 도구가 파싱하기 쉽게 한다.
        private val CSV_HEADERS: List<String> = listOf(
            "section", "field", "value"
        )

        private val SUBSCRIPTION_HEADERS: List<String> = listOf(
            "id", "requestName", "sourceName", "sourceUrl", "slackChannelId",
            "personaName", "status", "requestNote", "reviewNote",
            "approvedCategoryId", "createdAt", "reviewedAt"
        )

        private val BOOKMARK_HEADERS: List<String> = listOf(
            "summaryId", "originalTitle", "translatedTitle", "sourceLink",
            "categoryId", "articleCreatedAt", "bookmarkedAt"
        )

        private val EVENT_HEADERS: List<String> = listOf(
            "eventType", "pagePath", "summaryId", "eventData", "createdAt"
        )

        private val DELIVERY_HEADERS: List<String> = listOf(
            "categoryId", "channelId", "deliveryDate", "deliveryHour",
            "status", "itemCount", "createdAt"
        )

        private val FEEDBACK_HEADERS: List<String> = listOf(
            "summaryId", "feedbackType", "createdAt"
        )
    }

    /** JSON 직렬화 시 공용 설정을 재사용하기 위한 ObjectMapper 싱글턴. */
    private val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .setSerializationInclusion(JsonInclude.Include.ALWAYS)

    /**
     * 사용자 이름으로 개인정보를 수집해 JSON 문자열로 반환한다.
     *
     * @param username 인증 주체의 사용자명 (Authentication principal)
     * @throws NotFoundException        사용자 존재하지 않음
     * @throws RateLimitExceededException 일일 허용 횟수 초과
     */
    fun exportAsJson(username: String): ByteArray {
        // rate limit 소진 전에 사용자 존재 여부를 우선 검증한다.
        val export = gatherPersonalData(username)
        // 감사 로그는 실제 export 성공 이후에 기록한다 (실패한 조회는 감사 대상 아님).
        writeAuditLog(username, export.userId, FORMAT_JSON)
        return objectMapper.writeValueAsBytes(export)
    }

    /**
     * 사용자 이름으로 개인정보를 수집해 CSV(UTF-8 BOM 포함) 바이트로 반환한다.
     *
     * 섹션별로 블록을 나누고, 각 블록은 자체 헤더 행으로 시작한다.
     * 엑셀 호환을 위해 선두에 UTF-8 BOM 을 붙인다.
     */
    fun exportAsCsv(username: String): ByteArray {
        val export = gatherPersonalData(username)
        writeAuditLog(username, export.userId, FORMAT_CSV)
        val csv = buildCsv(export)
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray(Charsets.UTF_8)
    }

    /**
     * 사용자명 기준으로 export rate limit 을 확인한 뒤 요청 포맷에 맞는 본문을 생성한다.
     * 컨트롤러가 사용자 저장소를 직접 참조하지 않도록 사용자 조회와 제한 소진을 한 경계에 둔다.
     *
     * @param username 인증 주체의 사용자명
     * @param format `json` 또는 `csv`
     */
    fun exportWithRateLimit(username: String, format: String): ByteArray {
        val user = adminUserStore.findByUsername(username)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다.")
        consumeDailyRateLimit(user.id)
        return if (format == FORMAT_CSV) {
            exportAsCsv(username)
        } else {
            exportAsJson(username)
        }
    }

    /**
     * 일일 rate limit 을 소진한다. 초과 시 [RateLimitExceededException] 을 던진다.
     * 컨트롤러에서 본 처리 전에 직접 호출한다.
     */
    fun consumeDailyRateLimit(userId: String, today: LocalDate = LocalDate.now(ZoneOffset.UTC)) {
        // rate limit 키는 userId 기준으로 일자 단위 버킷을 사용한다.
        val key = "rl:data-export:$userId:${today.format(DATE_FORMATTER)}"
        val limited = rateLimitService.isRateLimited(
            key = key,
            maxRequests = PersonalDataExportLimits.MAX_EXPORTS_PER_DAY,
            windowSeconds = RATE_LIMIT_WINDOW_SECONDS
        )
        if (limited) {
            throw RateLimitExceededException(
                message = "오늘은 ${PersonalDataExportLimits.MAX_EXPORTS_PER_DAY}회까지 받을 수 있어요. 내일 다시 시도해 주세요.",
                retryAfterSeconds = RATE_LIMIT_WINDOW_SECONDS
            )
        }
    }

    /**
     * 사용자 username 을 기준으로 8개 스토어에서 개인정보를 수집해 DTO 로 반환한다.
     *
     * 민감 필드(password_hash, totp_secret 등)는 whitelist 방식으로 **노출 대상에서 명시적으로 제외**한다.
     * 다른 사용자의 데이터가 섞이지 않도록 모든 쿼리는 [AdminUser.id] 또는 본인 채널 ID 로 제한된다.
     */
    fun gatherPersonalData(username: String): PersonalDataExport {
        // 사용자 존재 여부 및 본인 식별자(id) 를 먼저 확보한다.
        val user = adminUserStore.findByUsername(username)
            ?: throw NotFoundException("사용자를 찾을 수 없습니다.")

        val account = buildAccountSection(user)
        val preferences = buildPreferencesSection(user.id)
        val subscriptions = loadSubscriptions(user.id)
        val bookmarks = loadBookmarks(user.id)
        val recentEvents = loadRecentEvents(user.id)
        val deliveryLogs = loadDeliveryLogs(subscriptions)
        val feedback = loadFeedback(user.id)

        return PersonalDataExport(
            exportedAt = Instant.now(),
            userId = user.id,
            username = user.username,
            legalBasis = LEGAL_BASIS,
            account = account,
            preferences = preferences,
            subscriptions = subscriptions,
            bookmarks = bookmarks,
            recentEvents = recentEvents,
            deliveryLogs = deliveryLogs,
            feedback = feedback
        )
    }

    /**
     * 계정 섹션 whitelist 매핑. 비밀번호 해시 등은 어디에서도 접근하지 않는다.
     *
     * V129: FK 컬럼은 DepartmentStore / TeamStore 로 이름을 한 번 더 조회해
     * 레거시 name 캐시가 drift 된 경우에도 정확한 JOIN 값을 노출한다.
     */
    private fun buildAccountSection(user: AdminUser): AccountExportSection {
        // FK 가 있을 때만 부서/팀을 조회한다. 없으면 JOIN 결과 이름은 null.
        val departmentName = user.departmentId?.let { departmentStore.findById(it)?.name }
        val teamName = user.teamId?.let { teamStore.findById(it)?.name }
        return AccountExportSection(
            // 현재 스키마에는 별도 email 컬럼이 없으므로 username 에 이메일이 들어왔을 때만 마스킹한다.
            maskedEmail = maskEmail(user.username),
            displayName = user.displayName,
            department = user.department,
            departmentId = user.departmentId,
            departmentName = departmentName,
            team = user.team,
            teamId = user.teamId,
            teamName = teamName,
            role = user.role.name,
            approvalStatus = user.approvalStatus.name,
            approvalNote = user.approvalNote,
            approvedAt = user.approvedAt,
            mustChangePassword = user.mustChangePassword,
            isActive = user.isActive,
            slackMemberId = user.slackMemberId,
            slackDmChannelId = user.slackDmChannelId,
            lastLoginAt = user.lastLoginAt,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }

    /**
     * 사용자 설정(스케줄 + 소유 관계) 섹션을 수집한다.
     */
    private fun buildPreferencesSection(userId: String): PreferencesExportSection {
        val schedule = userDeliveryScheduleStore.findByUserId(userId)?.let(::toScheduleEntry)
        return PreferencesExportSection(
            deliverySchedule = schedule,
            ownedCategoryIds = userOwnedCategoryStore.listCategoryIds(userId),
            ownedPersonaIds = userOwnedPersonaStore.listPersonaIds(userId),
            ownedSourceIds = userOwnedSourceStore.listSourceIds(userId)
        )
    }

    /**
     * 구독 요청 목록을 최신순으로 반환한다. 내부 태그는 sanitize 된다.
     */
    private fun loadSubscriptions(userId: String): List<SubscriptionExportEntry> =
        userClippingRequestStore.listByRequesterUserId(userId)
            .sortedByDescending { it.createdAt }
            .map(::toSubscriptionEntry)

    /**
     * 북마크를 상한까지만 포함해 반환한다. 대용량 본문은 export 대상에서 제외한다.
     */
    private fun loadBookmarks(userId: String): List<BookmarkExportEntry> =
        bookmarkedArticleStore.listAllForUser(userId)
            .take(PersonalDataExportLimits.MAX_BOOKMARKS)
            .map(::toBookmarkEntry)

    /**
     * 최근 사용자 이벤트를 상한까지 반환한다. 기간은 모든 시점을 허용하되 건수로 제한한다.
     */
    private fun loadRecentEvents(userId: String): List<UserEventExportEntry> {
        // Instant.EPOCH 부터 현재까지 → 전체 기간이지만 limit 으로 상한을 둔다.
        val now = Instant.now()
        return userEventStore.findByUserAndDateRange(
            userId = userId,
            from = Instant.EPOCH,
            to = now,
            limit = PersonalDataExportLimits.MAX_EVENTS
        ).map(::toEventEntry)
    }

    /**
     * 사용자가 남긴 요약 피드백을 상한까지 반환한다.
     * 개보법 35조 대응으로 V129 스펙 §2.6 에 추가됐다. 상한은 MAX_EVENTS 와 동일하게 둔다.
     */
    private fun loadFeedback(userId: String): List<SummaryFeedbackExportEntry> =
        summaryFeedbackStore
            .findByUserId(userId = userId, limit = PersonalDataExportLimits.MAX_EVENTS)
            .map { feedback ->
                SummaryFeedbackExportEntry(
                    summaryId = feedback.summaryId,
                    feedbackType = feedback.feedbackType,
                    createdAt = feedback.createdAt
                )
            }

    /**
     * 본인 구독에 연결된 슬랙 채널의 발송 이력만 수집한다. 타 사용자 채널이 섞이지 않도록
     * 구독 요청의 `slackChannelId` 집합으로 제한한다.
     */
    private fun loadDeliveryLogs(subscriptions: List<SubscriptionExportEntry>): List<DeliveryLogExportEntry> {
        // 사용자가 한 번이라도 사용했던 슬랙 채널 ID 집합을 만든다.
        val channelIds = subscriptions
            .map { it.slackChannelId }
            .filter { it.isNotBlank() }
            .toSet()
        if (channelIds.isEmpty()) return emptyList()
        return deliveryLogStore.findByChannelIds(channelIds)
            .take(PersonalDataExportLimits.MAX_DELIVERY_LOGS)
            .map(::toDeliveryLogEntry)
    }

    private fun toScheduleEntry(schedule: UserDeliverySchedule): DeliveryScheduleExportEntry =
        DeliveryScheduleExportEntry(
            deliveryDays = schedule.deliveryDays,
            deliveryHour = schedule.deliveryHour,
            preset = schedule.preset.name,
            updatedAt = schedule.updatedAt
        )

    private fun toSubscriptionEntry(request: UserClippingRequest): SubscriptionExportEntry =
        SubscriptionExportEntry(
            id = request.id,
            requestName = request.requestName,
            sourceName = request.sourceName,
            sourceUrl = request.sourceUrl,
            slackChannelId = request.slackChannelId,
            personaName = request.personaName,
            status = request.status.name,
            requestNote = sanitizeInternalTags(request.requestNote),
            reviewNote = sanitizeInternalTags(request.reviewNote),
            approvedCategoryId = request.approvedCategoryId,
            createdAt = request.createdAt,
            reviewedAt = request.reviewedAt
        )

    private fun toBookmarkEntry(bookmark: BookmarkedArticle): BookmarkExportEntry =
        BookmarkExportEntry(
            summaryId = bookmark.summaryId,
            originalTitle = bookmark.originalTitle,
            translatedTitle = bookmark.translatedTitle,
            sourceLink = bookmark.sourceLink,
            categoryId = bookmark.categoryId,
            articleCreatedAt = bookmark.articleCreatedAt,
            bookmarkedAt = bookmark.bookmarkedAt
        )

    private fun toEventEntry(event: UserEvent): UserEventExportEntry =
        UserEventExportEntry(
            eventType = event.eventType,
            pagePath = event.pagePath,
            summaryId = event.summaryId,
            eventData = event.eventData,
            createdAt = event.createdAt
        )

    private fun toDeliveryLogEntry(logRow: DeliveryLog): DeliveryLogExportEntry =
        DeliveryLogExportEntry(
            categoryId = logRow.categoryId,
            channelId = logRow.channelId,
            deliveryDate = logRow.deliveryDate.format(DATE_FORMATTER),
            deliveryHour = logRow.deliveryHour,
            status = logRow.status,
            itemCount = logRow.itemCount,
            createdAt = logRow.createdAt
        )

    /**
     * 이메일을 `j***@example.com` 형식으로 마스킹한다. `@` 미포함 문자열은 그대로 반환한다.
     * 로그인 ID 가 이메일이 아닐 수도 있으므로 원문을 유지한다.
     */
    internal fun maskEmail(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val atIndex = value.indexOf('@')
        // `@` 없는 로그인 ID(예: 내부 username) 는 별도 마스킹 없이 그대로 노출한다.
        if (atIndex <= 0) return value
        val local = value.substring(0, atIndex)
        val domain = value.substring(atIndex)
        val firstChar = local.first()
        return "$firstChar***$domain"
    }

    /**
     * requestNote/reviewNote 에 삽입될 수 있는 내부 메타 태그(`[key=value]`, `[설정 변경]` 등)를 제거한다.
     */
    internal fun sanitizeInternalTags(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val cleaned = INTERNAL_TAG_PATTERN.replace(text, "").trim()
        return cleaned.ifBlank { null }
    }

    /**
     * export 성공 이력을 감사 로그에 기록한다.
     */
    private fun writeAuditLog(username: String, userId: String, format: String) {
        auditLogStore.log(
            actorId = userId,
            actorName = username,
            action = "PERSONAL_DATA_EXPORT",
            targetType = "USER_DATA",
            targetId = userId,
            targetName = username,
            detail = "format=$format"
        )
    }

    /**
     * CSV 본문을 조립한다. 섹션별 블록을 공백 라인으로 구분하며, 필드 값은 RFC4180 을 따른다.
     */
    private fun buildCsv(export: PersonalDataExport): String {
        val builder = StringBuilder()

        // 메타 섹션: 식별자, 생성 시각, 법적 근거.
        builder.appendLine(CSV_HEADERS.joinToString(","))
        builder.appendLine(csvRow("meta", "exportedAt", export.exportedAt.toString()))
        builder.appendLine(csvRow("meta", "userId", export.userId))
        builder.appendLine(csvRow("meta", "username", export.username))
        builder.appendLine(csvRow("meta", "legalBasis", export.legalBasis))
        builder.appendLine()

        // 계정 섹션: 각 필드를 키-값 행으로 나열한다.
        // V129 에서 FK/상태 필드를 추가했으며 순서는 JSON export 와 동일하게 유지한다.
        builder.appendLine(CSV_HEADERS.joinToString(","))
        listOf(
            "maskedEmail" to export.account.maskedEmail,
            "displayName" to export.account.displayName,
            "department" to export.account.department,
            "departmentId" to export.account.departmentId,
            "departmentName" to export.account.departmentName,
            "team" to export.account.team,
            "teamId" to export.account.teamId,
            "teamName" to export.account.teamName,
            "role" to export.account.role,
            "approvalStatus" to export.account.approvalStatus,
            "approvalNote" to export.account.approvalNote,
            "approvedAt" to export.account.approvedAt?.toString(),
            "mustChangePassword" to export.account.mustChangePassword.toString(),
            "isActive" to export.account.isActive.toString(),
            "slackMemberId" to export.account.slackMemberId,
            "slackDmChannelId" to export.account.slackDmChannelId,
            "lastLoginAt" to export.account.lastLoginAt?.toString(),
            "createdAt" to export.account.createdAt.toString(),
            "updatedAt" to export.account.updatedAt.toString()
        ).forEach { (field, value) -> builder.appendLine(csvRow("account", field, value)) }
        builder.appendLine()

        // preferences 섹션: 스케줄과 소유 관계 ID.
        builder.appendLine(CSV_HEADERS.joinToString(","))
        val schedule = export.preferences.deliverySchedule
        builder.appendLine(csvRow("preferences", "deliveryDays", schedule?.deliveryDays?.joinToString("|")))
        builder.appendLine(csvRow("preferences", "deliveryHour", schedule?.deliveryHour?.toString()))
        builder.appendLine(csvRow("preferences", "preset", schedule?.preset))
        builder.appendLine(csvRow("preferences", "ownedCategoryIds", export.preferences.ownedCategoryIds.joinToString("|")))
        builder.appendLine(csvRow("preferences", "ownedPersonaIds", export.preferences.ownedPersonaIds.joinToString("|")))
        builder.appendLine(csvRow("preferences", "ownedSourceIds", export.preferences.ownedSourceIds.joinToString("|")))
        builder.appendLine()

        // subscriptions 섹션: 표 형태 테이블로 작성.
        appendTableSection(builder, "subscriptions", SUBSCRIPTION_HEADERS, export.subscriptions) { entry ->
            listOf(
                entry.id,
                entry.requestName,
                entry.sourceName,
                entry.sourceUrl,
                entry.slackChannelId,
                entry.personaName,
                entry.status,
                entry.requestNote,
                entry.reviewNote,
                entry.approvedCategoryId,
                entry.createdAt.toString(),
                entry.reviewedAt?.toString()
            )
        }

        appendTableSection(builder, "bookmarks", BOOKMARK_HEADERS, export.bookmarks) { entry ->
            listOf(
                entry.summaryId,
                entry.originalTitle,
                entry.translatedTitle,
                entry.sourceLink,
                entry.categoryId,
                entry.articleCreatedAt.toString(),
                entry.bookmarkedAt.toString()
            )
        }

        appendTableSection(builder, "recentEvents", EVENT_HEADERS, export.recentEvents) { entry ->
            listOf(
                entry.eventType,
                entry.pagePath,
                entry.summaryId,
                entry.eventData,
                entry.createdAt.toString()
            )
        }

        appendTableSection(builder, "deliveryLogs", DELIVERY_HEADERS, export.deliveryLogs) { entry ->
            listOf(
                entry.categoryId,
                entry.channelId,
                entry.deliveryDate,
                entry.deliveryHour.toString(),
                entry.status,
                entry.itemCount.toString(),
                entry.createdAt.toString()
            )
        }

        // V129: 요약 피드백 섹션 (개보법 35조 대응).
        appendTableSection(builder, "feedback", FEEDBACK_HEADERS, export.feedback) { entry ->
            listOf(entry.summaryId, entry.feedbackType, entry.createdAt.toString())
        }

        return builder.toString()
    }

    /**
     * 섹션 테이블을 CSV 블록으로 직렬화한다. 빈 리스트는 제목만 남겨 사용자가 비어 있음을 알 수 있게 한다.
     */
    private fun <T> appendTableSection(
        builder: StringBuilder,
        sectionName: String,
        headers: List<String>,
        rows: List<T>,
        toColumns: (T) -> List<String?>
    ) {
        builder.appendLine("# section: $sectionName")
        builder.appendLine(headers.joinToString(","))
        rows.forEach { row ->
            builder.appendLine(toColumns(row).joinToString(",") { csvEscape(it) })
        }
        if (rows.isEmpty()) {
            // 공란 안내: 데이터가 없음을 표시.
            builder.appendLine("# (empty)")
        }
        builder.appendLine()
    }

    /**
     * `section,field,value` 형식의 단일 row 를 생성한다.
     */
    private fun csvRow(section: String, field: String, value: String?): String =
        listOf(section, field, value).joinToString(",") { csvEscape(it) }

    /**
     * RFC4180 호환 CSV escape. 쉼표/따옴표/개행 포함 시 큰따옴표로 감싸고 따옴표를 중복 처리한다.
     */
    private fun csvEscape(value: String?): String {
        if (value == null) return ""
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuote) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
