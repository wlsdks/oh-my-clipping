package com.ohmyclipping.service

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.*
import com.ohmyclipping.service.dto.clipping.*
import com.ohmyclipping.service.SlackMessageSender.SendResult
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.UserClippingRequestStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

class UserAccountApprovalServiceTest {

    private val adminUserStore = mockk<AdminUserStore>()
    private val userClippingRequestStore = mockk<UserClippingRequestStore>()
    private val auditLogStore = mockk<AuditLogStore>(relaxed = true)
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val slackMessageSender = mockk<SlackMessageSender>()
    private val operationsNotificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val categoryStore = mockk<CategoryStore>()
    /** Principal → actorId passthrough: 테스트에서 `verify { auditLogStore.log(actorId = "admin", ...) }` 가 동작하도록 한다. */
    private val auditActorResolver = mockk<AuditActorResolver>().apply {
        every { resolve(any()) } answers {
            val arg = firstArg<String?>()
            ResolvedActor(id = arg, name = arg ?: "system")
        }
    }
    private val departmentTreeService = mockk<DepartmentTreeService>(relaxed = true)
    private lateinit var service: UserAccountApprovalService

    private val adminAccount = AdminUser(
        id = "admin-1",
        username = "admin",
        passwordHash = "hashed",
        role = AccountRole.ADMIN,
        approvalStatus = AccountApprovalStatus.APPROVED,
        isActive = true
    )

    private val userAccount = AdminUser(
        id = "user-1",
        username = "testuser",
        passwordHash = "hashed",
        role = AccountRole.USER,
        approvalStatus = AccountApprovalStatus.APPROVED,
        isActive = true
    )

    private fun makeApprovedRequest(id: String = "req-1") = UserClippingRequest(
        id = id,
        requesterUserId = userAccount.id,
        requestName = "뉴스 구독",
        sourceName = "Source",
        sourceUrl = "https://example.com/feed",
        slackChannelId = "C123",
        personaName = "분석가",
        personaPrompt = "요약합니다",
        status = UserClippingRequestStatus.APPROVED,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        service = UserAccountApprovalService(
            adminUserStore,
            userClippingRequestStore,
            auditLogStore,
            BCryptPasswordEncoder(),
            runtimeSettingService,
            slackMessageSender,
            operationsNotificationService,
            categoryStore,
            auditActorResolver,
            departmentTreeService
        )
    }

    @Nested
    inner class `승인자 표시명 배치 조회` {
        @Test
        fun `빈 ID 목록이면 빈 맵을 반환한다`() {
            val result = service.getApproverDisplayNames(emptyList())
            result shouldBe emptyMap()
            verify(exactly = 0) { adminUserStore.findByIds(any()) }
        }

        @Test
        fun `존재하는 ID만 결과에 포함된다`() {
            every { adminUserStore.findByIds(listOf("admin-1", "admin-999")) } returns listOf(adminAccount)
            val result = service.getApproverDisplayNames(listOf("admin-1", "admin-999"))
            result shouldBe mapOf("admin-1" to null)
        }
    }

    @Nested
    inner class `구독 수 조회` {
        @Test
        fun `SQL 집계 결과를 그대로 반환한다`() {
            every { userClippingRequestStore.countApprovedGroupByRequester() } returns mapOf(
                "user-1" to 3, "user-2" to 1
            )
            val result = service.getSubscriptionCountByUser()
            result shouldBe mapOf("user-1" to 3, "user-2" to 1)
        }

        @Test
        fun `승인 구독이 없으면 빈 맵을 반환한다`() {
            every { userClippingRequestStore.countApprovedGroupByRequester() } returns emptyMap()
            val result = service.getSubscriptionCountByUser()
            result shouldBe emptyMap()
        }
    }

    @Nested
    inner class `일괄 승인 실패 코드` {
        @Test
        fun `존재하지 않는 유저는 NOT_FOUND 코드로 실패한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            // findByIds에서 결과가 없음 → NOT_FOUND
            every { adminUserStore.findByIds(listOf("no-exist")) } returns emptyList()

            val result = service.bulkApproveUserAccounts(
                ids = listOf("no-exist"), reviewerUsername = "admin", reviewNote = null
            )
            result.succeeded shouldBe emptyList()
            result.failed.size shouldBe 1
            result.failed[0].code shouldBe "NOT_FOUND"
        }

        @Test
        fun `이미 승인된 유저는 ALREADY_PROCESSED 코드로 실패한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            // findByIds에서 이미 APPROVED 상태인 유저 반환
            every { adminUserStore.findByIds(listOf("user-1")) } returns listOf(userAccount)

            val result = service.bulkApproveUserAccounts(
                ids = listOf("user-1"), reviewerUsername = "admin", reviewNote = null
            )
            result.succeeded shouldBe emptyList()
            result.failed.size shouldBe 1
            result.failed[0].code shouldBe "ALREADY_PROCESSED"
        }

        @Test
        fun `ADMIN 계정은 INVALID_ROLE 코드로 실패한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            // findByIds에서 ADMIN 역할 계정 반환
            every { adminUserStore.findByIds(listOf("admin-1")) } returns listOf(adminAccount)

            val result = service.bulkApproveUserAccounts(
                ids = listOf("admin-1"), reviewerUsername = "admin", reviewNote = null
            )
            result.succeeded shouldBe emptyList()
            result.failed.size shouldBe 1
            result.failed[0].code shouldBe "INVALID_ROLE"
        }
    }

    @Nested
    inner class `탈퇴 처리` {

        @Test
        fun `정상 탈퇴 - 계정 비활성화 및 구독 WITHDRAWN 전환`() {
            // 관리자 조회
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            // 탈퇴 대상 사용자 조회
            every { adminUserStore.findById("user-1") } returns userAccount

            // APPROVED 구독 2건
            val req1 = makeApprovedRequest("req-1")
            val req2 = makeApprovedRequest("req-2")
            every { userClippingRequestStore.listByRequesterUserId("user-1") } returns listOf(req1, req2)

            // 구독 상태 일괄 업데이트 모킹
            every {
                userClippingRequestStore.updateStatusBulk(
                    ids = listOf("req-1", "req-2"),
                    status = UserClippingRequestStatus.WITHDRAWN,
                    reviewNote = "탈퇴 처리로 인한 자동 해제",
                    reviewedByUserId = adminAccount.id
                )
            } returns 2

            // 계정 업데이트 캡처
            val userSlot = slot<AdminUser>()
            every { adminUserStore.update(capture(userSlot)) } answers { firstArg() }

            val result = service.withdrawUserAccount("user-1", "admin", "퇴사 처리")

            // 구독 일괄 WITHDRAWN 전환이 호출되었는지 검증
            verify(exactly = 1) {
                userClippingRequestStore.updateStatusBulk(
                    ids = listOf("req-1", "req-2"),
                    status = UserClippingRequestStatus.WITHDRAWN,
                    reviewNote = "탈퇴 처리로 인한 자동 해제",
                    reviewedByUserId = adminAccount.id
                )
            }

            // 계정이 비활성화되고 REJECTED 상태인지 검증
            result.isActive shouldBe false
            result.approvalStatus shouldBe AccountApprovalStatus.REJECTED
            result.approvalNote shouldBe "퇴사 처리"
            result.approvedByUserId shouldBe adminAccount.id
        }

        @Test
        fun `탈퇴 메모 없으면 기본값 사용`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { adminUserStore.findById("user-1") } returns userAccount
            every { userClippingRequestStore.listByRequesterUserId("user-1") } returns emptyList()

            val userSlot = slot<AdminUser>()
            every { adminUserStore.update(capture(userSlot)) } answers { firstArg() }

            val result = service.withdrawUserAccount("user-1", "admin", null)

            result.approvalNote shouldBe "탈퇴 처리"
        }

        @Test
        fun `존재하지 않는 사용자 탈퇴 시 NotFoundException 발생`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { adminUserStore.findById("nonexistent") } returns null

            shouldThrow<NotFoundException> {
                service.withdrawUserAccount("nonexistent", "admin", "탈퇴")
            }
        }

        @Test
        fun `USER가 아닌 ADMIN 계정 탈퇴 시 예외 발생`() {
            val anotherAdmin = adminAccount.copy(id = "admin-2", username = "admin2")
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { adminUserStore.findById("admin-2") } returns anotherAdmin

            shouldThrow<Exception> {
                service.withdrawUserAccount("admin-2", "admin", "탈퇴")
            }
        }

        @Test
        fun `ADMIN이 아닌 USER가 탈퇴 처리 시도하면 예외 발생`() {
            val regularUser = AdminUser(
                id = "user-2",
                username = "regular",
                passwordHash = "hashed",
                role = AccountRole.USER,
                approvalStatus = AccountApprovalStatus.APPROVED
            )
            every { adminUserStore.findByUsername("regular") } returns regularUser

            shouldThrow<Exception> {
                service.withdrawUserAccount("user-1", "regular", "탈퇴")
            }
        }
    }

    @Nested
    inner class `Slack 멤버 ID 업데이트` {

        @Test
        fun `유효한 Slack 멤버 ID를 저장한다`() {
            every { adminUserStore.findByUsername("testuser") } returns userAccount
            val userSlot = slot<AdminUser>()
            every { adminUserStore.update(capture(userSlot)) } answers { firstArg() }
            // DM 채널 자동 획득 모킹
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-test"
            every { runtimeSettingService.current() } returns runtime
            every { slackMessageSender.openDmChannel("xoxb-test", "U01AB2CD3EF") } returns "D0123456789"

            service.updateSlackMemberId("testuser", "U01AB2CD3EF")

            userSlot.captured.slackMemberId shouldBe "U01AB2CD3EF"
            userSlot.captured.slackDmChannelId shouldBe "D0123456789"
        }

        @Test
        fun `빈 Slack 멤버 ID는 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.updateSlackMemberId("testuser", "   ")
            }.message shouldBe "Slack 멤버 ID를 입력해 주세요."
        }

        @Test
        fun `형식이 잘못된 Slack 멤버 ID는 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.updateSlackMemberId("testuser", "INVALID123")
            }.message shouldBe "U로 시작하는 멤버 ID를 입력해 주세요 (예: U01AB2CD3EF)."
        }

        @Test
        fun `소문자 입력을 대문자로 정규화해 저장한다`() {
            every { adminUserStore.findByUsername("testuser") } returns userAccount
            val userSlot = slot<AdminUser>()
            every { adminUserStore.update(capture(userSlot)) } answers { firstArg() }
            // DM 채널 획득 실패 시에도 멤버 ID는 저장된다
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns ""
            every { runtimeSettingService.current() } returns runtime

            service.updateSlackMemberId("testuser", "u01ab2cd3ef")

            userSlot.captured.slackMemberId shouldBe "U01AB2CD3EF"
        }

        @Test
        fun `최소 길이(8자) 멤버 ID를 허용한다`() {
            every { adminUserStore.findByUsername("testuser") } returns userAccount
            val userSlot = slot<AdminUser>()
            every { adminUserStore.update(capture(userSlot)) } answers { firstArg() }
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns ""
            every { runtimeSettingService.current() } returns runtime

            // U + 7자 = 8자 (최소)
            service.updateSlackMemberId("testuser", "U0123456")

            userSlot.captured.slackMemberId shouldBe "U0123456"
        }

        @Test
        fun `이미 설정된 Slack ID를 새 값으로 덮어쓴다`() {
            val userWithSlack = userAccount.copy(slackMemberId = "U00000OLD")
            every { adminUserStore.findByUsername("testuser") } returns userWithSlack
            val userSlot = slot<AdminUser>()
            every { adminUserStore.update(capture(userSlot)) } answers { firstArg() }
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-test"
            every { runtimeSettingService.current() } returns runtime
            every { slackMessageSender.openDmChannel("xoxb-test", "U11111NEW") } returns "D9876543210"

            service.updateSlackMemberId("testuser", "U11111NEW")

            userSlot.captured.slackMemberId shouldBe "U11111NEW"
            userSlot.captured.slackDmChannelId shouldBe "D9876543210"
        }

        @Test
        fun `DM 채널 획득 실패 시에도 멤버 ID는 저장된다`() {
            every { adminUserStore.findByUsername("testuser") } returns userAccount
            val userSlot = slot<AdminUser>()
            every { adminUserStore.update(capture(userSlot)) } answers { firstArg() }
            // DM 채널 획득이 예외를 던지는 경우
            val runtime = mockk<RuntimeSettingService.RuntimeSettings>()
            every { runtime.slackBotToken } returns "xoxb-test"
            every { runtimeSettingService.current() } returns runtime
            every { slackMessageSender.openDmChannel(any(), any()) } throws RuntimeException("API error")

            service.updateSlackMemberId("testuser", "U01AB2CD3EF")

            userSlot.captured.slackMemberId shouldBe "U01AB2CD3EF"
            // conversations.open 실패 시 멤버 ID를 DM 채널로 fallback 사용
            // (chat.postMessage가 U... 멤버 ID를 채널로 받아 자동 DM 전송하므로 발송에는 문제 없음)
            userSlot.captured.slackDmChannelId shouldBe "U01AB2CD3EF"
        }
    }

    @Nested
    inner class `가입 승인 반려 DM` {

        private val pendingUserWithDm = AdminUser(
            id = "user-dm-1",
            username = "dmuser",
            passwordHash = "hashed",
            role = AccountRole.USER,
            approvalStatus = AccountApprovalStatus.PENDING,
            isActive = false,
            slackDmChannelId = "D456"
        )

        @Test
        fun `승인 시 DM 채널 있으면 축하 메시지 발송`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { adminUserStore.findById("user-dm-1") } returns pendingUserWithDm
            every { adminUserStore.update(any()) } answers { firstArg() }
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SendResult(ts = "", channelId = "C-any", ok = false)

            service.approveUserAccount("user-dm-1", "admin", null)

            verify(exactly = 1) {
                slackMessageSender.sendMessage(
                    channelId = "D456",
                    text = match { it.contains("가입이 승인되었습니다") },
                    any(),
                    any()
                )
            }
        }

        @Test
        fun `반려 시 DM 채널 있으면 반려 사유 포함 발송`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { adminUserStore.findById("user-dm-1") } returns pendingUserWithDm
            every { adminUserStore.update(any()) } answers { firstArg() }
            every { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) } returns SendResult(ts = "", channelId = "C-any", ok = false)

            service.rejectUserAccount("user-dm-1", "admin", "서비스 대상이 아닙니다")

            verify(exactly = 1) {
                slackMessageSender.sendMessage(
                    channelId = "D456",
                    text = match { it.contains("반려") && it.contains("서비스 대상이 아닙니다") },
                    any(),
                    any()
                )
            }
        }

        @Test
        fun `DM 채널 없으면 발송 건너뜀`() {
            val pendingUserNoDm = pendingUserWithDm.copy(
                id = "user-nodm-1",
                slackDmChannelId = null
            )
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { adminUserStore.findById("user-nodm-1") } returns pendingUserNoDm
            every { adminUserStore.update(any()) } answers { firstArg() }

            service.approveUserAccount("user-nodm-1", "admin", null)

            verify(exactly = 0) { slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `트랜잭션 중에는 가입 승인 DM을 커밋 이후로 지연한다`() {
            every { adminUserStore.findByUsername("admin") } returns adminAccount
            every { adminUserStore.findById("user-dm-1") } returns pendingUserWithDm
            every { adminUserStore.update(any()) } answers { firstArg() }
            every {
                slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any())
            } returns SendResult(ts = "", channelId = "C-any", ok = false)

            TransactionSynchronizationManager.initSynchronization()
            try {
                service.approveUserAccount("user-dm-1", "admin", null)

                verify(exactly = 0) {
                    slackMessageSender.sendMessage(any(), any(), any(), any(), any(), any(), any())
                }
                TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
                verify(exactly = 1) {
                    slackMessageSender.sendMessage(
                        channelId = "D456",
                        text = match { it.contains("가입이 승인되었습니다") },
                        any(),
                        any()
                    )
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }
    }

    @Nested
    inner class `listUserAccounts 페르소나 필터` {

        private val userA = userAccount.copy(id = "user-A")
        private val userB = userAccount.copy(id = "user-B")
        private val userC = userAccount.copy(id = "user-C")
        private val allUsers = listOf(userA, userB, userC)

        private val personaCategory = Category(
            id = "cat-1",
            name = "분석가 뉴스",
            personaId = "persona-xyz"
        )

        @Test
        fun `personaId null이면 기존 동작과 동일하게 전체 목록을 반환한다`() {
            every { adminUserStore.listByRole(AccountRole.USER, AccountApprovalStatus.APPROVED) } returns allUsers

            val result = service.listUserAccounts(AccountApprovalStatus.APPROVED, personaId = null)

            result shouldBe allUsers
            verify(exactly = 0) { categoryStore.findActiveByPersonaId(any()) }
            verify(exactly = 0) { userClippingRequestStore.listAll(any()) }
        }

        @Test
        fun `limit이 있고 personaId가 없으면 DB 제한 조회 경로를 사용한다`() {
            every {
                adminUserStore.listByRole(AccountRole.USER, AccountApprovalStatus.APPROVED, 2)
            } returns listOf(userA, userB)

            val result = service.listUserAccounts(AccountApprovalStatus.APPROVED, personaId = null, limit = 2)

            result shouldBe listOf(userA, userB)
            verify(exactly = 1) {
                adminUserStore.listByRole(AccountRole.USER, AccountApprovalStatus.APPROVED, 2)
            }
            verify(exactly = 0) { adminUserStore.listByRole(AccountRole.USER, AccountApprovalStatus.APPROVED) }
            verify(exactly = 0) { categoryStore.findActiveByPersonaId(any()) }
        }

        @Test
        fun `personaId 지정 시 해당 페르소나 카테고리를 구독한 사용자만 남긴다`() {
            every { adminUserStore.listByRole(AccountRole.USER, null) } returns allUsers
            every { categoryStore.findActiveByPersonaId("persona-xyz") } returns listOf(personaCategory)
            every {
                userClippingRequestStore.findApprovedRequesterIdsByCategoryIds(setOf("cat-1"))
            } returns setOf("user-A", "user-C")

            val result = service.listUserAccounts(status = null, personaId = "persona-xyz")

            result shouldBe listOf(userA, userC)
            verify(exactly = 0) { userClippingRequestStore.listAll(any()) }
        }

        @Test
        fun `personaId에 연결된 활성 카테고리가 없으면 빈 리스트를 반환한다`() {
            every { adminUserStore.listByRole(AccountRole.USER, null) } returns allUsers
            every { categoryStore.findActiveByPersonaId("persona-empty") } returns emptyList()

            val result = service.listUserAccounts(status = null, personaId = "persona-empty")

            result shouldBe emptyList()
            verify(exactly = 0) { userClippingRequestStore.listAll(any()) }
        }

        @Test
        fun `personaId가 공백이면 필터로 취급하지 않는다`() {
            every { adminUserStore.listByRole(AccountRole.USER, null) } returns allUsers

            val result = service.listUserAccounts(status = null, personaId = "   ")

            result shouldBe allUsers
            verify(exactly = 0) { categoryStore.findActiveByPersonaId(any()) }
        }
    }

    @Nested
    inner class `updateSelfProfile — V129 FK` {

        private val oldDepartment = com.ohmyclipping.model.Department(
            id = "dept-old",
            name = "영업팀",
            nameNormalized = "영업팀"
        )
        private val newDepartment = com.ohmyclipping.model.Department(
            id = "dept-new",
            name = "마케팅팀",
            nameNormalized = "마케팅팀"
        )
        private val newTeam = com.ohmyclipping.model.Team(
            id = "team-new",
            departmentId = "dept-new",
            name = "퍼포먼스",
            nameNormalized = "퍼포먼스"
        )
        private val baseUser = userAccount.copy(
            id = "user-42",
            username = "profile-user",
            department = "영업팀",
            team = null,
            departmentId = "dept-old",
            teamId = null
        )

        @Test
        fun `departmentId 와 teamId 를 저장하고 legacy 이름 캐시를 동기화한다`() {
            every { adminUserStore.findByUsername("profile-user") } returns baseUser
            every { departmentTreeService.resolveUserAssignment("dept-new", "team-new") } returns
                (newDepartment to newTeam)
            val slotUser = slot<AdminUser>()
            every { adminUserStore.update(capture(slotUser)) } answers { firstArg() }

            val updated = service.updateSelfProfile(
                username = "profile-user",
                departmentId = "dept-new",
                teamId = "team-new"
            )

            slotUser.captured.departmentId shouldBe "dept-new"
            slotUser.captured.teamId shouldBe "team-new"
            slotUser.captured.department shouldBe "마케팅팀"
            slotUser.captured.team shouldBe "퍼포먼스"
            updated.departmentId shouldBe "dept-new"
            updated.teamId shouldBe "team-new"
            verify(exactly = 1) { auditLogStore.log(
                actorId = baseUser.id,
                actorName = "profile-user",
                action = "PROFILE_UPDATED",
                targetType = "USER",
                targetId = baseUser.id,
                targetName = baseUser.username,
                detail = any()
            ) }
        }

        @Test
        fun `null 필드는 기존 FK 값을 유지한다`() {
            // 기존 팀이 있던 사용자가 departmentId 만 교체한다.
            every { adminUserStore.findByUsername("profile-user") } returns baseUser.copy(
                teamId = "team-existing",
                team = "existing"
            )
            every { departmentTreeService.resolveUserAssignment("dept-new", "team-existing") } returns
                (newDepartment to com.ohmyclipping.model.Team(
                    id = "team-existing",
                    departmentId = "dept-new",
                    name = "existing",
                    nameNormalized = "existing"
                ))
            val slotUser = slot<AdminUser>()
            every { adminUserStore.update(capture(slotUser)) } answers { firstArg() }

            service.updateSelfProfile(username = "profile-user", departmentId = "dept-new", teamId = null)

            slotUser.captured.departmentId shouldBe "dept-new"
            slotUser.captured.teamId shouldBe "team-existing"
        }

        @Test
        fun `빈 문자열 departmentId 는 부서와 팀을 모두 null 로 초기화한다`() {
            every { adminUserStore.findByUsername("profile-user") } returns baseUser.copy(teamId = "t1")
            every { departmentTreeService.resolveUserAssignment(null, null) } returns (null to null)
            val slotUser = slot<AdminUser>()
            every { adminUserStore.update(capture(slotUser)) } answers { firstArg() }

            service.updateSelfProfile(username = "profile-user", departmentId = "", teamId = "anything")

            slotUser.captured.departmentId shouldBe null
            slotUser.captured.teamId shouldBe null
            slotUser.captured.department shouldBe null
            slotUser.captured.team shouldBe null
        }

        @Test
        fun `변경 사항이 없으면 store 를 호출하지 않는다`() {
            every { adminUserStore.findByUsername("profile-user") } returns baseUser

            val result = service.updateSelfProfile(username = "profile-user", departmentId = null, teamId = null)

            result shouldBe baseUser
            verify(exactly = 0) { adminUserStore.update(any()) }
        }

        @Test
        fun `존재하지 않는 사용자면 NotFoundException`() {
            every { adminUserStore.findByUsername("ghost") } returns null

            shouldThrow<NotFoundException> {
                service.updateSelfProfile(username = "ghost", departmentId = "dept", teamId = null)
            }
        }
    }

    @Nested
    inner class `changePassword — 본인 비밀번호 변경` {

        private val encoder = BCryptPasswordEncoder()
        private val currentHash = encoder.encode("OldPass123")
        private val baseUser = userAccount.copy(
            id = "user-42",
            username = "me@example.com",
            passwordHash = currentHash
        )

        @BeforeEach
        fun stubResolver() {
            // clearAllMocks 가 class-level auditActorResolver init 을 지우므로 매 테스트에서 재스텁.
            every { auditActorResolver.resolve(any()) } answers {
                val arg = firstArg<String?>()
                ResolvedActor(id = arg, name = arg ?: "system")
            }
        }

        @Test
        fun `정상 변경 — 새 해시 + must_change_password false 로 저장 + 감사 로그`() {
            every { adminUserStore.findByUsername("me@example.com") } returns baseUser
            every { adminUserStore.updatePasswordHashAndFlags(any(), any(), any()) } returns Unit

            service.changePassword("me@example.com", "OldPass123", "NewPass456")

            verify(exactly = 1) {
                adminUserStore.updatePasswordHashAndFlags(
                    userId = "user-42",
                    passwordHash = any(),
                    mustChangePassword = false
                )
            }
            verify(exactly = 1) {
                auditLogStore.log(
                    actorId = "me@example.com",
                    actorName = any(),
                    action = "PASSWORD_CHANGED_BY_SELF",
                    targetType = "USER",
                    targetId = "user-42",
                    targetName = "me@example.com",
                    detail = any()
                )
            }
        }

        @Test
        fun `현재 비밀번호 불일치면 InvalidInputException`() {
            every { adminUserStore.findByUsername("me@example.com") } returns baseUser

            shouldThrow<InvalidInputException> {
                service.changePassword("me@example.com", "WrongPass1", "NewPass456")
            }
            verify(exactly = 0) { adminUserStore.updatePasswordHashAndFlags(any(), any(), any()) }
        }

        @Test
        fun `존재하지 않는 사용자면 NotFoundException`() {
            every { adminUserStore.findByUsername("ghost@example.com") } returns null

            shouldThrow<NotFoundException> {
                service.changePassword("ghost@example.com", "OldPass123", "NewPass456")
            }
        }

        @Test
        fun `새 비밀번호가 8자 미만이면 InvalidInputException`() {
            every { adminUserStore.findByUsername("me@example.com") } returns baseUser

            shouldThrow<InvalidInputException> {
                service.changePassword("me@example.com", "OldPass123", "Short1")
            }
            verify(exactly = 0) { adminUserStore.updatePasswordHashAndFlags(any(), any(), any()) }
        }

        @Test
        fun `새 비밀번호에 숫자가 없으면 InvalidInputException`() {
            every { adminUserStore.findByUsername("me@example.com") } returns baseUser

            shouldThrow<InvalidInputException> {
                service.changePassword("me@example.com", "OldPass123", "NoDigitPass")
            }
        }

        @Test
        fun `새 비밀번호에 영문이 없으면 InvalidInputException`() {
            every { adminUserStore.findByUsername("me@example.com") } returns baseUser

            shouldThrow<InvalidInputException> {
                service.changePassword("me@example.com", "OldPass123", "12345678")
            }
        }

        @Test
        fun `새 비밀번호가 현재 비밀번호와 같으면 InvalidInputException`() {
            every { adminUserStore.findByUsername("me@example.com") } returns baseUser

            shouldThrow<InvalidInputException> {
                service.changePassword("me@example.com", "OldPass123", "OldPass123")
            }
            verify(exactly = 0) { adminUserStore.updatePasswordHashAndFlags(any(), any(), any()) }
        }
    }
}
