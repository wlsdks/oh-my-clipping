package com.ohmyclipping.store

import com.ohmyclipping.model.RetentionPolicy

interface RetentionPolicyStore {
    fun findByCategoryId(categoryId: String): RetentionPolicy?
    fun saveOrUpdate(policy: RetentionPolicy): RetentionPolicy
}
