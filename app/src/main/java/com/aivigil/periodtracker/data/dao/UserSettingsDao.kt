package com.aivigil.periodtracker.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.aivigil.periodtracker.data.entity.UserSettings

@Dao
interface UserSettingsDao {

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun observe(): LiveData<UserSettings?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun get(): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: UserSettings)

    @Query("UPDATE user_settings SET cycleLength = :len WHERE id = 1")
    suspend fun updateCycleLength(len: Int)

    @Query("UPDATE user_settings SET periodDuration = :dur WHERE id = 1")
    suspend fun updatePeriodDuration(dur: Int)

    @Query("UPDATE user_settings SET lastPeriodStart = :date WHERE id = 1")
    suspend fun updateLastPeriodStart(date: String)

    @Query("UPDATE user_settings SET conditions = :conditions WHERE id = 1")
    suspend fun updateConditions(conditions: String)

    @Query("DELETE FROM user_settings")
    suspend fun deleteAll()
}