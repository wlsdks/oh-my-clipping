package com.clipping.mcpserver.repository

import com.clipping.mcpserver.entity.BudgetSettingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface BudgetSettingRepository : JpaRepository<BudgetSettingEntity, String>
