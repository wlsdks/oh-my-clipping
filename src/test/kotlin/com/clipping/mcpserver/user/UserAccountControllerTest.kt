package com.clipping.mcpserver.user

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.RateLimitExceededException
import com.clipping.mcpserver.service.UserAccountApprovalService
import com.clipping.mcpserver.service.UserDataExportService
import com.clipping.mcpserver.user.dto.SelfWithdrawRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class UserAccountControllerTest {

    private val approvalService = mockk<UserAccountApprovalService>(relaxed = true)
    private val exportService = mockk<UserDataExportService>()
    private val controller = UserAccountController(approvalService, exportService)

    private fun auth(name: String) = UsernamePasswordAuthenticationToken(name, "credentials")

    @Nested
    inner class `개인정보 export` {

        @Test
        fun `json 포맷 기본값으로 JSON 본문과 첨부 헤더를 반환한다`() {
            every {
                exportService.exportWithRateLimit("alice", UserDataExportService.FORMAT_JSON)
            } returns "{\"ok\":true}".toByteArray()

            val response = controller.exportPersonalData(auth("alice"), format = "json")

            response.statusCode shouldBe HttpStatus.OK
            response.headers.contentType shouldBe MediaType.APPLICATION_JSON
            response.headers[HttpHeaders.CONTENT_DISPOSITION]?.first()?.let {
                it shouldStartWith "attachment; filename=\"personal_data_"
                it shouldContain ".json\""
            }
            verify(exactly = 1) { exportService.exportWithRateLimit("alice", UserDataExportService.FORMAT_JSON) }
        }

        @Test
        fun `csv 포맷은 text csv 본문과 csv 파일명을 반환한다`() {
            every {
                exportService.exportWithRateLimit("alice", UserDataExportService.FORMAT_CSV)
            } returns "section,field,value\n".toByteArray()

            val response = controller.exportPersonalData(auth("alice"), format = "CSV")

            response.statusCode shouldBe HttpStatus.OK
            response.headers.contentType?.toString() shouldContain "text/csv"
            response.headers[HttpHeaders.CONTENT_DISPOSITION]?.first()?.let {
                it shouldContain ".csv\""
            }
            verify(exactly = 1) { exportService.exportWithRateLimit("alice", UserDataExportService.FORMAT_CSV) }
        }

        @Test
        fun `알 수 없는 포맷은 InvalidInputException 을 던진다`() {
            shouldThrow<InvalidInputException> {
                controller.exportPersonalData(auth("alice"), format = "xml")
            }
            verify(exactly = 0) { exportService.exportWithRateLimit(any(), any()) }
        }

        @Test
        fun `rate limit 초과 시 RateLimitExceededException 이 전파된다`() {
            every { exportService.exportWithRateLimit("alice", UserDataExportService.FORMAT_JSON) } throws
                RateLimitExceededException(message = "오늘은 3회까지 받을 수 있어요. 내일 다시 시도해 주세요.")

            val exception = shouldThrow<RateLimitExceededException> {
                controller.exportPersonalData(auth("alice"), format = "json")
            }
            exception.message shouldContain "3회"
            verify(exactly = 1) { exportService.exportWithRateLimit("alice", UserDataExportService.FORMAT_JSON) }
        }
    }

    @Nested
    inner class `셀프 탈퇴 위임` {

        @Test
        fun `selfWithdraw 는 approvalService 에 위임한다`() {
            controller.selfWithdraw(
                authentication = auth("alice"),
                request = SelfWithdrawRequest(password = "pw")
            )
            verify(exactly = 1) { approvalService.selfWithdraw(username = "alice", rawPassword = "pw") }
        }
    }
}
