package com.ohmyclipping.repository

import com.ohmyclipping.entity.BudgetSettingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface BudgetSettingRepository : JpaRepository<BudgetSettingEntity, String>
