package com.clipping.mcpserver.store

/**
 * 사용자 self-serve 위자드에서 생성한 페르소나 소유권을 관리한다.
 */
interface UserOwnedPersonaStore {
    fun save(userId: String, personaId: String)
    fun exists(userId: String, personaId: String): Boolean
    fun listPersonaIds(userId: String): List<String>
    fun delete(userId: String, personaId: String)
}
