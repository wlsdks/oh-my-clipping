package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.RuntimeSettingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RuntimeSettingRepository : JpaRepository<RuntimeSettingEntity, String>
