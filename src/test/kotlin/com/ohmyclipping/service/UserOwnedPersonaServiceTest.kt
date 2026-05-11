package com.ohmyclipping.service

import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.Persona
import com.ohmyclipping.store.PersonaStore
import com.ohmyclipping.store.UserOwnedPersonaStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class UserOwnedPersonaServiceTest {

    private val adminPersonaService = mockk<AdminPersonaService>()
    private val personaStore = mockk<PersonaStore>()
    private val userOwnedPersonaStore = mockk<UserOwnedPersonaStore>()
    private val userSetupOwnershipService = mockk<UserSetupOwnershipService>()
    private val service = UserOwnedPersonaService(
        adminPersonaService, personaStore, userOwnedPersonaStore, userSetupOwnershipService
    )

    private val testUser = AdminUser(
        id = "user-1",
        username = "alice",
        passwordHash = "hash",
        role = AccountRole.USER
    )

    private val testPersona = Persona(
        id = "p1",
        name = "테스트 페르소나",
        systemPrompt = "You are a test assistant",
        createdAt = Instant.parse("2026-01-15T10:00:00Z")
    )

    private val testPersona2 = Persona(
        id = "p2",
        name = "두 번째 페르소나",
        systemPrompt = "Second persona",
        createdAt = Instant.parse("2026-01-16T10:00:00Z")
    )

    @Nested
    inner class `목록 조회` {

        @Test
        fun `소유한 페르소나 목록을 최신순으로 반환한다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.listOwnedPersonaIds("user-1") } returns listOf("p1", "p2")
            every { personaStore.findById("p1") } returns testPersona
            every { personaStore.findById("p2") } returns testPersona2

            val result = service.listOwnPersonas("alice")

            result shouldHaveSize 2
            // createdAt 기준 내림차순이므로 p2가 먼저
            result[0].persona.id shouldBe "p2"
            result[1].persona.id shouldBe "p1"
        }

        @Test
        fun `소유한 페르소나가 없으면 빈 리스트를 반환한다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.listOwnedPersonaIds("user-1") } returns emptyList()

            val result = service.listOwnPersonas("alice")

            result shouldHaveSize 0
        }

        @Test
        fun `personaStore에서 찾을 수 없는 ID는 건너뛴다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.listOwnedPersonaIds("user-1") } returns listOf("p1", "deleted-id")
            every { personaStore.findById("p1") } returns testPersona
            every { personaStore.findById("deleted-id") } returns null

            val result = service.listOwnPersonas("alice")

            result shouldHaveSize 1
            result[0].persona.id shouldBe "p1"
        }
    }

    @Nested
    inner class `단건 조회` {

        @Test
        fun `소유한 페르소나를 정상 조회한다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsPersona("user-1", "p1") } just runs
            every { personaStore.findById("p1") } returns testPersona

            val result = service.getOwnPersona("alice", "p1")

            result.persona.id shouldBe "p1"
            result.persona.name shouldBe "테스트 페르소나"
        }

        @Test
        fun `소유하지 않은 페르소나 조회 시 NotFoundException이 발생한다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsPersona("user-1", "other-p") } throws
                NotFoundException("Persona not found: other-p")

            shouldThrow<NotFoundException> {
                service.getOwnPersona("alice", "other-p")
            }.message shouldContain "other-p"
        }

        @Test
        fun `소유 확인 통과 후 personaStore에 없으면 NotFoundException이 발생한다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsPersona("user-1", "p-gone") } just runs
            every { personaStore.findById("p-gone") } returns null

            shouldThrow<NotFoundException> {
                service.getOwnPersona("alice", "p-gone")
            }.message shouldContain "p-gone"
        }
    }

    @Nested
    inner class `페르소나 생성` {

        @Test
        fun `페르소나를 생성하고 owner 매핑을 저장한다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every {
                adminPersonaService.createPersona(
                    name = "새 페르소나",
                    description = "설명",
                    systemPrompt = "prompt",
                    summaryStyle = null,
                    targetAudience = null,
                    maxItems = 5,
                    language = "ko"
                )
            } returns testPersona.copy(id = "new-p", name = "새 페르소나")
            every { userOwnedPersonaStore.save("user-1", "new-p") } just runs

            val result = service.createOwnPersona(
                requesterUsername = "alice",
                name = "새 페르소나",
                description = "설명",
                systemPrompt = "prompt",
                summaryStyle = null,
                targetAudience = null,
                maxItems = 5,
                language = "ko"
            )

            result.id shouldBe "new-p"
            verify(exactly = 1) { userOwnedPersonaStore.save("user-1", "new-p") }
        }
    }

    @Nested
    inner class `페르소나 수정` {

        @Test
        fun `본인 소유 페르소나를 수정한다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsPersona("user-1", "p1") } just runs
            every { userSetupOwnershipService.isApprovedPersonaLocked("user-1", "p1") } returns false
            val updated = testPersona.copy(name = "수정됨")
            every {
                adminPersonaService.updatePersona(
                    id = "p1",
                    name = "수정됨",
                    description = null,
                    systemPrompt = null,
                    summaryStyle = null,
                    targetAudience = null,
                    maxItems = null,
                    language = null,
                    isActive = null
                )
            } returns updated

            val result = service.updateOwnPersona(
                requesterUsername = "alice",
                personaId = "p1",
                name = "수정됨",
                description = null,
                systemPrompt = null,
                summaryStyle = null,
                targetAudience = null,
                maxItems = null,
                language = null,
                isActive = null
            )

            result.name shouldBe "수정됨"
        }

        @Test
        fun `승인된 구독에 연결된 페르소나는 직접 수정할 수 없다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsPersona("user-1", "p1") } just runs
            every { userSetupOwnershipService.isApprovedPersonaLocked("user-1", "p1") } returns true

            shouldThrow<InvalidInputException> {
                service.updateOwnPersona(
                    requesterUsername = "alice",
                    personaId = "p1",
                    name = "수정됨",
                    description = null,
                    systemPrompt = null,
                    summaryStyle = null,
                    targetAudience = null,
                    maxItems = null,
                    language = null,
                    isActive = null
                )
            }.message shouldContain "운영 검토 요청"

            verify(exactly = 0) { adminPersonaService.updatePersona(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `타인 소유 페르소나 수정 시 NotFoundException`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsPersona("user-1", "other") } throws
                NotFoundException("Persona not found: other")

            shouldThrow<NotFoundException> {
                service.updateOwnPersona(
                    requesterUsername = "alice",
                    personaId = "other",
                    name = "해킹",
                    description = null,
                    systemPrompt = null,
                    summaryStyle = null,
                    targetAudience = null,
                    maxItems = null,
                    language = null,
                    isActive = null
                )
            }
        }
    }

    @Nested
    inner class `페르소나 삭제` {

        @Test
        fun `본인 소유 페르소나를 삭제하고 매핑도 제거한다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsPersona("user-1", "p1") } just runs
            every { userSetupOwnershipService.isApprovedPersonaLocked("user-1", "p1") } returns false
            every { adminPersonaService.deletePersona("p1") } just runs
            every { userOwnedPersonaStore.delete("user-1", "p1") } just runs

            service.deleteOwnPersona("alice", "p1")

            verify(exactly = 1) { adminPersonaService.deletePersona("p1") }
            verify(exactly = 1) { userOwnedPersonaStore.delete("user-1", "p1") }
        }

        @Test
        fun `승인된 구독에 연결된 페르소나는 직접 삭제할 수 없다`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsPersona("user-1", "p1") } just runs
            every { userSetupOwnershipService.isApprovedPersonaLocked("user-1", "p1") } returns true

            shouldThrow<InvalidInputException> {
                service.deleteOwnPersona("alice", "p1")
            }.message shouldContain "운영 검토 요청"

            verify(exactly = 0) { adminPersonaService.deletePersona(any()) }
            verify(exactly = 0) { userOwnedPersonaStore.delete(any(), any()) }
        }

        @Test
        fun `타인 소유 페르소나 삭제 시 NotFoundException`() {
            every { userSetupOwnershipService.requireUserRequester("alice", any()) } returns testUser
            every { userSetupOwnershipService.ensureOwnsPersona("user-1", "foreign") } throws
                NotFoundException("Persona not found: foreign")

            shouldThrow<NotFoundException> {
                service.deleteOwnPersona("alice", "foreign")
            }

            verify(exactly = 0) { adminPersonaService.deletePersona(any()) }
        }
    }
}
