package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.AccountRole
import com.ohmyclipping.model.AdminUser
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.store.AdminUserStore
import com.ohmyclipping.store.UserClippingRequestStore
import com.ohmyclipping.store.UserOwnedCategoryStore
import com.ohmyclipping.store.UserOwnedPersonaStore
import com.ohmyclipping.store.UserOwnedSourceStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class UserSetupOwnershipServiceTest {

    private val adminUserStore = mockk<AdminUserStore>()
    private val requestStore = mockk<UserClippingRequestStore>()
    private val userOwnedPersonaStore = mockk<UserOwnedPersonaStore>()
    private val userOwnedCategoryStore = mockk<UserOwnedCategoryStore>()
    private val userOwnedSourceStore = mockk<UserOwnedSourceStore>()

    private val personaStore = mockk<com.ohmyclipping.store.PersonaStore>(relaxed = true)

    private val service = UserSetupOwnershipService(
        adminUserStore = adminUserStore,
        requestStore = requestStore,
        userOwnedPersonaStore = userOwnedPersonaStore,
        userOwnedCategoryStore = userOwnedCategoryStore,
        userOwnedSourceStore = userOwnedSourceStore,
        personaStore = personaStore
    )

    private fun makeUser(
        id: String = "user-1",
        username: String = "testuser",
        role: AccountRole = AccountRole.USER
    ) = AdminUser(
        id = id,
        username = username,
        passwordHash = "hashed",
        role = role
    )

    private fun makeRequest(
        id: String = "req-1",
        requesterUserId: String = "user-1",
        status: UserClippingRequestStatus = UserClippingRequestStatus.APPROVED,
        approvedPersonaId: String? = null,
        approvedCategoryId: String? = null,
        approvedSourceId: String? = null
    ) = UserClippingRequest(
        id = id,
        requesterUserId = requesterUserId,
        requestName = "테스트 요청",
        sourceName = "테스트 소스",
        sourceUrl = "https://example.com/rss",
        slackChannelId = "C123",
        personaName = "페르소나",
        personaPrompt = "프롬프트",
        status = status,
        approvedPersonaId = approvedPersonaId,
        approvedCategoryId = approvedCategoryId,
        approvedSourceId = approvedSourceId
    )

    @Nested
    inner class `requireUserRequester 메서드` {

        @Test
        fun `USER 역할의 계정이면 정상 반환한다`() {
            val user = makeUser(role = AccountRole.USER)
            every { adminUserStore.findByUsername("testuser") } returns user

            val result = service.requireUserRequester("testuser", "setup")
            result shouldBe user
        }

        @Test
        fun `ADMIN 역할의 계정이면 InvalidInputException을 던진다`() {
            val admin = makeUser(role = AccountRole.ADMIN)
            every { adminUserStore.findByUsername("adminuser") } returns admin

            shouldThrow<InvalidInputException> {
                service.requireUserRequester("adminuser", "setup")
            }
        }

        @Test
        fun `존재하지 않는 username이면 NotFoundException을 던진다`() {
            every { adminUserStore.findByUsername("nobody") } returns null

            shouldThrow<NotFoundException> {
                service.requireUserRequester("nobody", "setup")
            }
        }

        @Test
        fun `username 양쪽 공백과 대문자를 정리해서 조회한다`() {
            val user = makeUser(username = "testuser", role = AccountRole.USER)
            every { adminUserStore.findByUsername("testuser") } returns user

            val result = service.requireUserRequester("  TestUser  ", "setup")
            result shouldBe user
        }
    }

    @Nested
    inner class `ensureOwnsPersona 메서드` {

        @Test
        fun `직접 매핑이 있으면 예외 없이 통과한다`() {
            every { userOwnedPersonaStore.exists("user-1", "persona-1") } returns true

            // 예외가 발생하지 않아야 한다
            service.ensureOwnsPersona("user-1", "persona-1")
        }

        @Test
        fun `직접 매핑이 없지만 승인된 요청으로 연결되면 통과한다`() {
            every { userOwnedPersonaStore.exists("user-1", "persona-2") } returns false
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(
                makeRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedPersonaId = "persona-2"
                )
            )

            service.ensureOwnsPersona("user-1", "persona-2")
        }

        @Test
        fun `직접 매핑도 없고 승인된 요청도 없으면 NotFoundException을 던진다`() {
            every { userOwnedPersonaStore.exists("user-1", "persona-x") } returns false
            every { requestStore.listByRequesterUserId("user-1") } returns emptyList()

            val ex = shouldThrow<NotFoundException> {
                service.ensureOwnsPersona("user-1", "persona-x")
            }
            ex.message shouldBe "Persona not found: persona-x"
        }

        @Test
        fun `PENDING 상태의 요청은 소유권으로 인정하지 않는다`() {
            every { userOwnedPersonaStore.exists("user-1", "persona-p") } returns false
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(
                makeRequest(
                    status = UserClippingRequestStatus.PENDING,
                    approvedPersonaId = "persona-p"
                )
            )

            shouldThrow<NotFoundException> {
                service.ensureOwnsPersona("user-1", "persona-p")
            }
        }
    }

    @Nested
    inner class `ensureOwnsCategory 메서드` {

        @Test
        fun `직접 매핑이 있으면 통과한다`() {
            every { userOwnedCategoryStore.exists("user-1", "cat-1") } returns true

            service.ensureOwnsCategory("user-1", "cat-1")
        }

        @Test
        fun `승인된 요청의 approvedCategoryId로 연결되면 통과한다`() {
            every { userOwnedCategoryStore.exists("user-1", "cat-2") } returns false
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(
                makeRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedCategoryId = "cat-2"
                )
            )

            service.ensureOwnsCategory("user-1", "cat-2")
        }

        @Test
        fun `둘 다 없으면 NotFoundException을 던진다`() {
            every { userOwnedCategoryStore.exists("user-1", "cat-x") } returns false
            every { requestStore.listByRequesterUserId("user-1") } returns emptyList()

            val ex = shouldThrow<NotFoundException> {
                service.ensureOwnsCategory("user-1", "cat-x")
            }
            ex.message shouldBe "Category not found: cat-x"
        }
    }

    @Nested
    inner class `ensureOwnsSource 메서드` {

        @Test
        fun `직접 매핑이 있으면 통과한다`() {
            every { userOwnedSourceStore.exists("user-1", "src-1") } returns true

            service.ensureOwnsSource("user-1", "src-1")
        }

        @Test
        fun `승인된 요청의 approvedSourceId로 연결되면 통과한다`() {
            every { userOwnedSourceStore.exists("user-1", "src-2") } returns false
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(
                makeRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedSourceId = "src-2"
                )
            )

            service.ensureOwnsSource("user-1", "src-2")
        }

        @Test
        fun `둘 다 없으면 NotFoundException을 던진다`() {
            every { userOwnedSourceStore.exists("user-1", "src-x") } returns false
            every { requestStore.listByRequesterUserId("user-1") } returns emptyList()

            val ex = shouldThrow<NotFoundException> {
                service.ensureOwnsSource("user-1", "src-x")
            }
            ex.message shouldBe "Source not found: src-x"
        }
    }

    @Nested
    inner class `listOwnedPersonaIds 메서드` {

        @Test
        fun `직접 매핑과 승인된 요청의 페르소나를 합쳐서 반환한다`() {
            every { userOwnedPersonaStore.listPersonaIds("user-1") } returns listOf("p-1", "p-2")
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(
                makeRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedPersonaId = "p-3"
                ),
                makeRequest(
                    id = "req-2",
                    status = UserClippingRequestStatus.APPROVED,
                    approvedPersonaId = "p-2"  // 중복
                )
            )

            val result = service.listOwnedPersonaIds("user-1")

            // p-2는 중복이므로 3개만 반환 (linkedSetOf 사용)
            result shouldContainExactly listOf("p-1", "p-2", "p-3")
        }

        @Test
        fun `직접 매핑도 승인 요청도 없으면 빈 리스트를 반환한다`() {
            every { userOwnedPersonaStore.listPersonaIds("user-1") } returns emptyList()
            every { requestStore.listByRequesterUserId("user-1") } returns emptyList()

            service.listOwnedPersonaIds("user-1").shouldBeEmpty()
        }

        @Test
        fun `승인된 요청의 approvedPersonaId가 null이면 무시한다`() {
            every { userOwnedPersonaStore.listPersonaIds("user-1") } returns listOf("p-1")
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(
                makeRequest(
                    status = UserClippingRequestStatus.APPROVED,
                    approvedPersonaId = null
                )
            )

            val result = service.listOwnedPersonaIds("user-1")
            result shouldContainExactly listOf("p-1")
        }

        @Test
        fun `REJECTED 상태의 요청은 결과에 포함하지 않는다`() {
            every { userOwnedPersonaStore.listPersonaIds("user-1") } returns emptyList()
            every { requestStore.listByRequesterUserId("user-1") } returns listOf(
                makeRequest(
                    status = UserClippingRequestStatus.REJECTED,
                    approvedPersonaId = "p-rejected"
                )
            )

            service.listOwnedPersonaIds("user-1").shouldBeEmpty()
        }
    }

    @Nested
    inner class `registerOwnedResources 메서드` {

        @Test
        fun `모든 리소스 ID가 존재하면 세 개 모두 저장한다`() {
            every { userOwnedCategoryStore.save("user-1", "cat-1") } returns Unit
            every { userOwnedPersonaStore.save("user-1", "persona-1") } returns Unit
            every { userOwnedSourceStore.save("user-1", "src-1") } returns Unit

            service.registerOwnedResources(
                userId = "user-1",
                categoryId = "cat-1",
                personaId = "persona-1",
                sourceId = "src-1"
            )

            verify(exactly = 1) { userOwnedCategoryStore.save("user-1", "cat-1") }
            verify(exactly = 1) { userOwnedPersonaStore.save("user-1", "persona-1") }
            verify(exactly = 1) { userOwnedSourceStore.save("user-1", "src-1") }
        }

        @Test
        fun `null인 리소스 ID는 저장하지 않는다`() {
            service.registerOwnedResources(
                userId = "user-1",
                categoryId = null,
                personaId = null,
                sourceId = null
            )

            verify(exactly = 0) { userOwnedCategoryStore.save(any(), any()) }
            verify(exactly = 0) { userOwnedPersonaStore.save(any(), any()) }
            verify(exactly = 0) { userOwnedSourceStore.save(any(), any()) }
        }

        @Test
        fun `빈 문자열인 리소스 ID는 저장하지 않는다`() {
            service.registerOwnedResources(
                userId = "user-1",
                categoryId = "",
                personaId = "  ",
                sourceId = ""
            )

            verify(exactly = 0) { userOwnedCategoryStore.save(any(), any()) }
            verify(exactly = 0) { userOwnedPersonaStore.save(any(), any()) }
            verify(exactly = 0) { userOwnedSourceStore.save(any(), any()) }
        }

        @Test
        fun `일부만 null이면 존재하는 것만 저장한다`() {
            every { userOwnedPersonaStore.save("user-1", "persona-1") } returns Unit

            service.registerOwnedResources(
                userId = "user-1",
                categoryId = null,
                personaId = "persona-1",
                sourceId = null
            )

            verify(exactly = 0) { userOwnedCategoryStore.save(any(), any()) }
            verify(exactly = 1) { userOwnedPersonaStore.save("user-1", "persona-1") }
            verify(exactly = 0) { userOwnedSourceStore.save(any(), any()) }
        }
    }
}
