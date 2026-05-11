package com.ohmyclipping.store

/**
 * 사용자 self-serve 위자드에서 생성한 RSS 소스 소유권을 관리한다.
 */
interface UserOwnedSourceStore {
    fun save(userId: String, sourceId: String)
    fun exists(userId: String, sourceId: String): Boolean

    /** 사용자가 소유한 RSS 소스 ID 목록을 반환한다. 개인정보 export 등에서 사용한다. */
    fun listSourceIds(userId: String): List<String>
}
