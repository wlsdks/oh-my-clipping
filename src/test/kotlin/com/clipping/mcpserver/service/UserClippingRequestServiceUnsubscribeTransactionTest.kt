package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.AccountRole
import com.clipping.mcpserver.model.AdminUser
import com.clipping.mcpserver.model.UserClippingRequest
import com.clipping.mcpserver.model.UserClippingRequestStatus
import com.clipping.mcpserver.store.AdminUserStore
import com.clipping.mcpserver.store.AuditLogStore
import com.clipping.mcpserver.store.UserClippingRequestStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * 구독 해제 시 상태 변경과 감사 로그 기록이 같은 트랜잭션으로 묶이는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserClippingRequestServiceUnsubscribeTransactionTest {

    @Autowired
    lateinit var service: UserClippingRequestService

    @Autowired
    lateinit var adminUserStore: AdminUserStore

    @Autowired
    lateinit var requestStore: UserClippingRequestStore

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @MockitoBean
    lateinit var auditLogStore: AuditLogStore

    private lateinit var user: AdminUser
    private lateinit var request: UserClippingRequest

    @BeforeEach
    fun setup() {
        val suffix = System.nanoTime()
        user = adminUserStore.save(
            AdminUser(
                id = "",
                username = "u$suffix@t.io",
                passwordHash = "pw",
                role = AccountRole.USER
            )
        )
        request = requestStore.save(
            UserClippingRequest(
                id = "",
                requesterUserId = user.id,
                requestName = "unsubscribe-tx-$suffix",
                sourceName = "unsubscribe-source-$suffix",
                sourceUrl = "https://example.com/unsubscribe-$suffix.xml",
                slackChannelId = "C_TX_UNSUBSCRIBE",
                personaName = "실무 요약",
                personaPrompt = "핵심만 정리",
                status = UserClippingRequestStatus.APPROVED
            )
        )
    }

    @AfterEach
    fun cleanup() {
        if (this::request.isInitialized) {
            jdbc.update("DELETE FROM clipping_user_requests WHERE id = ?", request.id)
        }
        if (this::user.isInitialized) {
            jdbc.update("DELETE FROM admin_users WHERE id = ?", user.id)
        }
    }

    @Test
    fun `unsubscribe should rollback request status when audit log fails`() {
        doThrow(IllegalStateException("audit failure"))
            .`when`(auditLogStore)
            .log(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String::class.java),
                nullable(String::class.java),
                nullable(String::class.java)
            )

        assertThrows(IllegalStateException::class.java) {
            service.unsubscribeRequest(request.id, user.username)
        }

        assertEquals(
            UserClippingRequestStatus.APPROVED,
            requestStore.findById(request.id)?.status
        )
    }
}
