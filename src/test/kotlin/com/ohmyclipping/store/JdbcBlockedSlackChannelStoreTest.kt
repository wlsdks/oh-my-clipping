package com.ohmyclipping.store

import com.ohmyclipping.model.BlockedSlackChannel
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcBlockedSlackChannelStoreTest {

    @Autowired
    private lateinit var store: JdbcBlockedSlackChannelStore

    @Test
    fun `existsByChannelId는 COUNT 결과가 null이어도 false를 반환한다`() {
        val jdbc = mockk<JdbcTemplate>()
        every {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM blocked_slack_channels WHERE channel_id = ?",
                Int::class.java,
                "CNULL"
            )
        } returns null

        JdbcBlockedSlackChannelStore(jdbc).existsByChannelId("CNULL") shouldBe false
    }

    @Nested
    inner class `차단 채널 CRUD` {

        @Test
        fun `채널을 차단하고 조회할 수 있다`() {
            val blocked = BlockedSlackChannel(
                id = "", channelId = "C12345678", channelName = "general",
                blockedByUserId = "admin-1"
            )
            val saved = store.save(blocked)
            saved.channelId shouldBe "C12345678"

            val all = store.findAll()
            all.any { it.channelId == "C12345678" } shouldBe true
        }

        @Test
        fun `차단 채널 ID 목록을 조회할 수 있다`() {
            store.save(BlockedSlackChannel(id = "", channelId = "CBLK111", channelName = "a", blockedByUserId = "admin-1"))
            store.save(BlockedSlackChannel(id = "", channelId = "CBLK222", channelName = "b", blockedByUserId = "admin-1"))

            val ids = store.listBlockedChannelIds()
            ids shouldContain "CBLK111"
            ids shouldContain "CBLK222"
        }

        @Test
        fun `채널 차단을 해제할 수 있다`() {
            store.save(BlockedSlackChannel(id = "", channelId = "CDEL111", channelName = "a", blockedByUserId = "admin-1"))
            store.deleteByChannelId("CDEL111") shouldBe true
            store.existsByChannelId("CDEL111") shouldBe false
        }

        @Test
        fun `존재 여부를 확인할 수 있다`() {
            store.save(BlockedSlackChannel(id = "", channelId = "CEXS111", channelName = "a", blockedByUserId = "admin-1"))
            store.existsByChannelId("CEXS111") shouldBe true
            store.existsByChannelId("C999") shouldBe false
        }

        @Test
        fun `isPrivate 값이 true인 채널을 저장하고 조회할 수 있다`() {
            val blocked = BlockedSlackChannel(
                id = "", channelId = "CPRV999", channelName = "secret",
                blockedByUserId = "admin-1", isPrivate = true
            )
            store.save(blocked)

            val all = store.findAll()
            val found = all.first { it.channelId == "CPRV999" }
            found.isPrivate shouldBe true
        }
    }
}
