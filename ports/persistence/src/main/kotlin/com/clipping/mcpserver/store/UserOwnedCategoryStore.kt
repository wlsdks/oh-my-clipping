package com.clipping.mcpserver.store

/**
 * 사용자 self-serve 위자드에서 생성한 카테고리 소유권을 관리한다.
 */
interface UserOwnedCategoryStore {
    fun save(userId: String, categoryId: String)
    fun exists(userId: String, categoryId: String): Boolean

    /** 사용자가 소유한 카테고리 ID 목록을 반환한다. 개인정보 export 등에서 사용한다. */
    fun listCategoryIds(userId: String): List<String>
}
