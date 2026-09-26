package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.stayfocused.app.data.local.dao.FailsafeLogDao
import com.stayfocused.app.data.local.dao.SuppressedNotificationDao
import com.stayfocused.app.data.local.entities.BreakSessionEntity
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import com.stayfocused.app.data.local.entities.SuppressedNotificationEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.data.local.entities.UnlockEventEntity
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity

/**
 * StayFocused Room Database.
 *
 * Schema history:
 * - Version 1: Initial schema.
 * - Version 2: Baseline MVP schema with app limits, blocked domains, recovery codes, and strict sessions.
 * - Version 3: Added `reason` column to `break_sessions` table (Phase 9 friction reason tracking).
 * - Version 4: Added `failsafe_logs` table (Phase 15 anti-tamper failsafe audit logging).
 */
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
        BreakSessionEntity::class,
        FailsafeLogEntity::class
    ],
    version = 4,
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
    abstract fun failsafeLogDao(): FailsafeLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema identical between v1 and v2
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `break_sessions` ADD COLUMN `reason` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `failsafe_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`eventType` TEXT NOT NULL, " +
                        "`details` TEXT NOT NULL, " +
                        "`success` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        @Volatile
        private var INSTANCE: StayFocusedDatabase? = null

        fun getInstance(context: Context): StayFocusedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StayFocusedDatabase::class.java,
                    "stay_focused_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_2_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
