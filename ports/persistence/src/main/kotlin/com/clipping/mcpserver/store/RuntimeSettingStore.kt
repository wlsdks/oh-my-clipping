package com.clipping.mcpserver.store

import com.clipping.mcpserver.model.RuntimeSetting

interface RuntimeSettingStore {
    fun list(): List<RuntimeSetting>
    fun findByKey(key: String): RuntimeSetting?
    fun save(setting: RuntimeSetting): RuntimeSetting
    fun saveAll(settings: List<RuntimeSetting>): List<RuntimeSetting>
    fun deleteAll(): Int
}
