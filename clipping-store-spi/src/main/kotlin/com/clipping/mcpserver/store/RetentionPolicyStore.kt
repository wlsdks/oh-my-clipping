package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.RetentionPolicy

interface RetentionPolicyStore {
    fun findByCategoryId(categoryId: String): RetentionPolicy?
    fun saveOrUpdate(policy: RetentionPolicy): RetentionPolicy
}
