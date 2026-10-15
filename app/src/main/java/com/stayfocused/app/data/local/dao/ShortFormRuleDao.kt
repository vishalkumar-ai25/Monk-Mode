package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.ShortFormRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortFormRuleDao {
    @Query("SELECT * FROM short_form_rules")
    fun getAllRulesFlow(): Flow<List<ShortFormRuleEntity>>

    @Query("SELECT * FROM short_form_rules")
    suspend fun getAllRules(): List<ShortFormRuleEntity>

    @Query("SELECT * FROM short_form_rules WHERE packageName = :packageName")
    suspend fun getRulesForPackage(packageName: String): List<ShortFormRuleEntity>

    @Query("SELECT * FROM short_form_rules WHERE id = :id")
    suspend fun getRuleById(id: String): ShortFormRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(rule: ShortFormRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultRules(rules: List<ShortFormRuleEntity>)

    @Query("UPDATE short_form_rules SET currentDayScrolls = currentDayScrolls + 1, lastEvictionTimestamp = :timestamp WHERE id = :id")
    suspend fun incrementScrollCount(id: String, timestamp: Long)

    @Query("UPDATE short_form_rules SET currentDayScrolls = 0")
    suspend fun resetAllDailyScrolls()

    @Query("UPDATE short_form_rules SET isBlocked = :isBlocked WHERE id = :id")
    suspend fun setBlocked(id: String, isBlocked: Boolean)

    @Query("UPDATE short_form_rules SET dailyScrollLimit = :limit WHERE id = :id")
    suspend fun setDailyScrollLimit(id: String, limit: Int)
}
