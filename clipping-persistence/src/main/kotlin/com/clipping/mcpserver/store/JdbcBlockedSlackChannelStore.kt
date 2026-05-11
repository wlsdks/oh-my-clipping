package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.BlockedSlackChannel
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * blocked_slack_channels 테이블에 대한 JDBC 저장소 구현.
 * 차단 목록 변경 시 "blocked-channels" 캐시를 즉시 무효화한다.
 */
@Repository
class JdbcBlockedSlackChannelStore(private val jdbc: JdbcTemplate) : BlockedSlackChannelStore {

    private val rowMapper = RowMapper<BlockedSlackChannel> { rs, _ ->
        BlockedSlackChannel(
            id = rs.getString("id"),
            channelId = rs.getString("channel_id"),
            channelName = rs.getString("channel_name"),
            isPrivate = rs.getBoolean("is_private"),
            blockedByUserId = rs.getString("blocked_by_user_id"),
            blockedAt = rs.getTimestamp("blocked_at").toInstant(),
            reason = rs.getString("reason")
        )
    }

    override fun findAll(): List<BlockedSlackChannel> =
        jdbc.query("SELECT * FROM blocked_slack_channels ORDER BY blocked_at DESC", rowMapper)

    @Cacheable(cacheNames = ["blocked-channels"])
    override fun listBlockedChannelIds(): Set<String> =
        jdbc.queryForList("SELECT channel_id FROM blocked_slack_channels", String::class.java).toSet()

    @CacheEvict(cacheNames = ["blocked-channels"], allEntries = true)
    override fun save(blocked: BlockedSlackChannel): BlockedSlackChannel {
        val id = blocked.id.ifBlank { UUID.randomUUID().toString() }
        val saved = blocked.copy(id = id)
        jdbc.update(
            """INSERT INTO blocked_slack_channels (id, channel_id, channel_name, is_private, blocked_by_user_id, blocked_at, reason)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            saved.id, saved.channelId, saved.channelName, saved.isPrivate, saved.blockedByUserId,
            java.sql.Timestamp.from(saved.blockedAt), saved.reason
        )
        return saved
    }

    @CacheEvict(cacheNames = ["blocked-channels"], allEntries = true)
    override fun deleteByChannelId(channelId: String): Boolean =
        jdbc.update("DELETE FROM blocked_slack_channels WHERE channel_id = ?", channelId) > 0

    override fun existsByChannelId(channelId: String): Boolean =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM blocked_slack_channels WHERE channel_id = ?",
            Int::class.java, channelId
        )?.let { it > 0 } ?: false
}
