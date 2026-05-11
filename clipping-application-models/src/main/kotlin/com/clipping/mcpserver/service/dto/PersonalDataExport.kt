package com.clipping.mcpserver.service.dto

import java.time.Instant

/**
 * 개인정보 열람권(개인정보보호법 제35조) 대응 export DTO.
 *
 * 사용자가 요청 시점의 본인 개인정보를 JSON/CSV 로 내려받을 때의 응답 루트 구조.
 * 민감 필드(`password_hash`, `totp_secret`, API 키, 암호화 토큰 등)는 **절대 포함하지 않는다.**
 * 서비스 레이어는 whitelist 방식으로 노출 필드를 결정한다.
 *
 * @property exportedAt export 생성 시각 (UTC)
 * @property userId     내부 사용자 UUID (본인 식별용)
 * @property username   로그인 ID (마스킹 없이 그대로 노출 — 본인 확인 가치)
 * @property legalBasis 법적 근거 안내 문구 (예: "개인정보보호법 제35조")
 * @property account        계정 관련 개인정보 (마스킹된 이메일/부서/Slack 멤버 ID 등)
 * @property preferences    사용자 설정 스냅샷 (발송 스케줄, 소유한 카테고리/페르소나/소스)
 * @property subscriptions  사용자 구독 요청 이력 (PENDING/APPROVED/REJECTED/WITHDRAWN)
 * @property bookmarks      북마크한 기사 스냅샷
 * @property recentEvents   최근 행동 이벤트 (상한 [PersonalDataExportLimits.MAX_EVENTS] 건)
 * @property deliveryLogs   본인 슬랙 채널로 발송된 기록
 */
data class PersonalDataExport(
    val exportedAt: Instant,
    val userId: String,
    val username: String,
    val legalBasis: String,
    val account: AccountExportSection,
    val preferences: PreferencesExportSection,
    val subscriptions: List<SubscriptionExportEntry>,
    val bookmarks: List<BookmarkExportEntry>,
    val recentEvents: List<UserEventExportEntry>,
    val deliveryLogs: List<DeliveryLogExportEntry>,
    /**
     * 사용자 요약 피드백 이력. V129 스펙 §2.6 에서 개보법 35조 gap 해소를 위해 추가됐다.
     * 상한은 [PersonalDataExportLimits.MAX_EVENTS] 와 동일하게 적용한다.
     */
    val feedback: List<SummaryFeedbackExportEntry>
)

/**
 * 계정 관련 whitelist 필드. 비밀번호 해시·암호화 비밀은 절대 포함하지 않는다.
 *
 * V129 에서 FK 전환 필드([departmentId] / [teamId] / [departmentName] / [teamName])
 * 와 승인/활성 상태 필드([approvalNote], [approvedAt], [mustChangePassword],
 * [isActive], [updatedAt]) 가 추가됐다. 레거시 [department] / [team] 자유 텍스트는
 * 이름 캐시로 6개월 유지된다.
 *
 * organizationId 는 본 export 에 포함되지 않는다 — admin_users 에 해당 컬럼이 존재하지 않는다.
 *
 * @property maskedEmail       이메일 주소 마스킹 결과 (예: `j***@example.com`). 원본 이메일 미보존 시 null.
 * @property displayName       표시 이름
 * @property department        레거시 부서 이름 캐시 (자유 텍스트, V129 이후 FK 동기화 값)
 * @property departmentId      부서 FK (V129 신규)
 * @property departmentName    부서 FK JOIN 결과 이름 (V129 신규)
 * @property team              레거시 팀 이름 캐시
 * @property teamId            팀 FK (V129 신규)
 * @property teamName          팀 FK JOIN 결과 이름 (V129 신규)
 * @property role              권한 (`USER` / `ADMIN`)
 * @property approvalStatus    가입 승인 상태
 * @property approvalNote      관리자 메모 (V129 신규 — 본인 관련)
 * @property approvedAt        승인 시각 (V129 신규)
 * @property mustChangePassword 비밀번호 재설정 요구 플래그 (V129 신규)
 * @property isActive          계정 활성 여부 (V129 신규)
 * @property slackMemberId     연결된 슬랙 멤버 ID
 * @property slackDmChannelId  슬랙 DM 채널 ID (멤버 ID와 별도로 저장)
 * @property lastLoginAt       마지막 로그인 시각
 * @property createdAt         가입 시각
 * @property updatedAt         마지막 수정 시각 (V129 신규)
 */
data class AccountExportSection(
    val maskedEmail: String?,
    val displayName: String?,
    val department: String?,
    val departmentId: String?,
    val departmentName: String?,
    val team: String?,
    val teamId: String?,
    val teamName: String?,
    val role: String,
    val approvalStatus: String,
    val approvalNote: String?,
    val approvedAt: Instant?,
    val mustChangePassword: Boolean,
    val isActive: Boolean,
    val slackMemberId: String?,
    val slackDmChannelId: String?,
    val lastLoginAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * 사용자가 설정한 발송 스케줄과 소유 관계 목록.
 *
 * @property deliverySchedule    사용자 글로벌 발송 스케줄 (없으면 null)
 * @property ownedCategoryIds    사용자가 셀프서브로 생성한 카테고리 ID 목록
 * @property ownedPersonaIds     사용자가 셀프서브로 생성한 페르소나 ID 목록
 * @property ownedSourceIds      사용자가 셀프서브로 생성한 RSS 소스 ID 목록
 */
data class PreferencesExportSection(
    val deliverySchedule: DeliveryScheduleExportEntry?,
    val ownedCategoryIds: List<String>,
    val ownedPersonaIds: List<String>,
    val ownedSourceIds: List<String>
)

/**
 * 발송 스케줄 export 항목.
 */
data class DeliveryScheduleExportEntry(
    val deliveryDays: List<String>,
    val deliveryHour: Int,
    val preset: String,
    val updatedAt: Instant
)

/**
 * 사용자 구독 요청 export 항목. 내부 태그(`[baseRequestId=...]` 등)는 sanitize 된다.
 */
data class SubscriptionExportEntry(
    val id: String,
    val requestName: String,
    val sourceName: String,
    val sourceUrl: String,
    val slackChannelId: String,
    val personaName: String,
    val status: String,
    val requestNote: String?,
    val reviewNote: String?,
    val approvedCategoryId: String?,
    val createdAt: Instant,
    val reviewedAt: Instant?
)

/**
 * 북마크 export 항목. 대용량 본문은 포함하지 않고 식별자와 제목·링크 위주로 최소화한다.
 */
data class BookmarkExportEntry(
    val summaryId: String,
    val originalTitle: String,
    val translatedTitle: String?,
    val sourceLink: String,
    val categoryId: String,
    val articleCreatedAt: Instant,
    val bookmarkedAt: Instant
)

/**
 * 사용자 행동 이벤트 export 항목. eventData JSON 은 그대로 노출한다 (사용자 본인 데이터).
 */
data class UserEventExportEntry(
    val eventType: String,
    val pagePath: String?,
    val summaryId: String?,
    val eventData: String?,
    val createdAt: Instant
)

/**
 * 요약 피드백 export 항목. V129 스펙 §2.6 추가.
 * 사용자 본인이 남긴 LIKE / NEUTRAL / DISLIKE 기록을 담는다.
 */
data class SummaryFeedbackExportEntry(
    val summaryId: String,
    val feedbackType: String,
    val createdAt: Instant
)

/**
 * 발송 이력 export 항목. 본인 슬랙 채널로 발송된 기록만 포함된다.
 */
data class DeliveryLogExportEntry(
    val categoryId: String,
    val channelId: String,
    val deliveryDate: String,
    val deliveryHour: Int,
    val status: String,
    val itemCount: Int,
    val createdAt: Instant
)

/**
 * export 규모 상한 정책. 프로덕션 성능 보호를 위해 각 섹션에 상한을 둔다.
 */
object PersonalDataExportLimits {
    /** 최근 이벤트 최대 수. 사용자 행동 이력이 폭주해도 export 크기를 제한한다. */
    const val MAX_EVENTS: Int = 500

    /** 발송 이력 최대 수. 장기 구독자라도 export 크기를 합리적으로 유지한다. */
    const val MAX_DELIVERY_LOGS: Int = 1000

    /** 북마크 최대 수. 평균적인 사용자를 충분히 커버한다. */
    const val MAX_BOOKMARKS: Int = 2000

    /** 일일 export 허용 횟수. 초과 시 다음날 재시도를 안내한다. */
    const val MAX_EXPORTS_PER_DAY: Int = 3
}
