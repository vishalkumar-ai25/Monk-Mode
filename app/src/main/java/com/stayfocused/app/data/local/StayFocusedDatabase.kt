package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.stayfocused.app.data.local.dao.AppLimitDao
import com.stayfocused.app.data.local.dao.BlockedDomainDao
import com.stayfocused.app.data.local.dao.FocusProfileDao
import com.stayfocused.app.data.local.dao.RecoveryCodeDao
import com.stayfocused.app.data.local.dao.StrictSessionDao
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.GeofenceProfileEntity
import com.stayfocused.app.data.local.entities.NotificationBlockRuleEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedDomainEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedPackageEntity
import com.stayfocused.app.data.local.dao.BreakSessionDao
import com.stayfocused.app.data.local.dao.SuppressedNotificationDao
import com.stayfocused.app.data.local.entities.BreakSessionEntity
import com.stayfocused.app.data.local.entities.SuppressedNotificationEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.data.local.entities.UnlockEventEntity
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity

@Database(
    entities = [
        AppLimitEntity::class,
        BlockedDomainEntity::class,
        FocusProfileEntity::class,
        ProfileBlockedPackageEntity::class,
        ProfileBlockedDomainEntity::class,
        StrictSessionEntity::class,
        RecoveryCodeEntity::class,
        UnlockEventEntity::class,
        GeofenceProfileEntity::class,
        NotificationBlockRuleEntity::class,
        SuppressedNotificationEntity::class,
        BreakSessionEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class StayFocusedDatabase : RoomDatabase() {

    abstract fun appLimitDao(): AppLimitDao
    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun focusProfileDao(): FocusProfileDao
    abstract fun strictSessionDao(): StrictSessionDao
    abstract fun recoveryCodeDao(): RecoveryCodeDao
    abstract fun suppressedNotificationDao(): SuppressedNotificationDao
    abstract fun breakSessionDao(): BreakSessionDao

    companion object {
        @Volatile
        private var INSTANCE: StayFocusedDatabase? = null

        fun getInstance(context: Context): StayFocusedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StayFocusedDatabase::class.java,
                    "stay_focused_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
