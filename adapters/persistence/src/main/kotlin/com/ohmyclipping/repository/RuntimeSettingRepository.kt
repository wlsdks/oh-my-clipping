package com.ohmyclipping.repository

import com.ohmyclipping.entity.RuntimeSettingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RuntimeSettingRepository : JpaRepository<RuntimeSettingEntity, String>
