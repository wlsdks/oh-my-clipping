package com.clipping.mcpserver.admin

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.model.DeliveryLog
import com.clipping.mcpserver.service.DeliveryAdminService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class DeliveryAdminControllerWithinTest {

    private val deliveryAdminService = mockk<DeliveryAdminService>()
    private val controller = DeliveryAdminController(deliveryAdminService)

    private val kst: ZoneId = ZoneId.of("Asia/Seoul")

    private fun emptyLogs() = emptyList<DeliveryLog>()

    /** 컨트롤러 호출 시점의 오늘 KST 자정 Instant 를 계산한다. */
    private fun todayKstMidnight(): Instant =
        LocalDate.now(kst).atStartOfDay(kst).toInstant()

    @Nested
    inner class `listLogs within 파라미터` {

        @Test
        fun `within=1d 이면 오늘 KST 자정 Instant 로 서비스에 전달된다`() {
            val expectedSince = todayKstMidnight()

            every {
                deliveryAdminService.findLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = any(),
                    offset = 0,
                    limit = 30
                )
            } returns emptyLogs()
            every {
                deliveryAdminService.countLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = any()
                )
            } returns 0

            controller.listLogs(
                categoryId = null, status = null, from = null, to = null,
                within = "1d", page = 0, size = 30
            )

            // controller 내부에서 Instant.now 를 따로 호출하므로 ±30s 허용 (KST 자정 경계 레이스 안전역 포함)
            verify {
                deliveryAdminService.findLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = match<Instant> {
                        !it.isBefore(expectedSince.minusSeconds(30)) &&
                            !it.isAfter(expectedSince.plusSeconds(30))
                    },
                    offset = 0,
                    limit = 30
                )
            }
        }

        @Test
        fun `within=7d 이면 오늘 KST 자정의 6일 전 Instant 로 서비스에 전달된다 (오늘 포함 7일)`() {
            val expectedSince = todayKstMidnight().minus(6L, ChronoUnit.DAYS)

            every {
                deliveryAdminService.findLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = any(),
                    offset = 0,
                    limit = 30
                )
            } returns emptyLogs()
            every {
                deliveryAdminService.countLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = any()
                )
            } returns 0

            controller.listLogs(
                categoryId = null, status = null, from = null, to = null,
                within = "7d", page = 0, size = 30
            )

            // controller 내부에서 Instant.now 를 따로 호출하므로 ±30s 허용 (KST 자정 경계 레이스 안전역 포함)
            verify {
                deliveryAdminService.findLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = match<Instant> {
                        !it.isBefore(expectedSince.minusSeconds(30)) &&
                            !it.isAfter(expectedSince.plusSeconds(30))
                    },
                    offset = 0,
                    limit = 30
                )
            }
        }

        @Test
        fun `within 파라미터가 없으면 since=null 로 서비스에 전달된다`() {
            every {
                deliveryAdminService.findLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = null,
                    offset = 0,
                    limit = 30
                )
            } returns emptyLogs()
            every {
                deliveryAdminService.countLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = null
                )
            } returns 0

            controller.listLogs(
                categoryId = null, status = null, from = null, to = null,
                within = null, page = 0, size = 30
            )

            verify {
                deliveryAdminService.findLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = null,
                    offset = 0,
                    limit = 30
                )
            }
        }

        @Test
        fun `page가 음수이면 첫 페이지 offset으로 보정한다`() {
            every {
                deliveryAdminService.findLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = null,
                    offset = 0,
                    limit = 30
                )
            } returns emptyLogs()
            every {
                deliveryAdminService.countLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = null
                )
            } returns 0

            val result = controller.listLogs(
                categoryId = null, status = null, from = null, to = null,
                within = null, page = -1, size = 30
            )

            result.page shouldBe 0
            verify {
                deliveryAdminService.findLogs(
                    categoryId = null,
                    status = null,
                    from = null,
                    to = null,
                    since = null,
                    offset = 0,
                    limit = 30
                )
            }
        }

        @Test
        fun `within=abc 이면 InvalidInputException 을 던진다`() {
            shouldThrow<InvalidInputException> {
                controller.listLogs(
                    categoryId = null, status = null, from = null, to = null,
                    within = "abc", page = 0, size = 30
                )
            }.message shouldBe "within 파라미터는 '1d' 또는 '7d' 만 허용합니다"
        }
    }
}
