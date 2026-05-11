package com.ohmyclipping.store

import com.ohmyclipping.model.BlockedSlackChannel

/**
 * 관리자가 차단한 Slack 채널을 관리하는 저장소.
 * 차단된 채널은 사용자 채널 목록에서 제외된다.
 */
interface BlockedSlackChannelStore {
    /** 차단된 전체 채널 목록을 최신순으로 반환한다. */
    fun findAll(): List<BlockedSlackChannel>

    /** 차단된 채널 ID 집합을 반환한다. 캐시 적용 대상. */
    fun listBlockedChannelIds(): Set<String>

    /** 채널을 차단 목록에 추가한다. */
    fun save(blocked: BlockedSlackChannel): BlockedSlackChannel

    /** 채널 ID로 차단을 해제한다. 삭제 성공 시 true를 반환한다. */
    fun deleteByChannelId(channelId: String): Boolean

    /** 채널 ID가 차단 목록에 존재하는지 확인한다. */
    fun existsByChannelId(channelId: String): Boolean
}
