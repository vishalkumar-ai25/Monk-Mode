package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stayfocused.app.data.local.dao.AppLimitDao
import com.stayfocused.app.data.local.dao.BlockedDomainDao
import com.stayfocused.app.data.local.dao.BreakSessionDao
import com.stayfocused.app.data.local.dao.FailsafeLogDao
import com.stayfocused.app.data.local.dao.FocusProfileDao
import com.stayfocused.app.data.local.dao.RecoveryCodeDao
import com.stayfocused.app.data.local.dao.StrictScheduleDao
import com.stayfocused.app.data.local.dao.StrictSessionDao
import com.stayfocused.app.data.local.dao.SuppressedNotificationDao
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import com.stayfocused.app.data.local.entities.BreakSessionEntity
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.GeofenceProfileEntity
import com.stayfocused.app.data.local.entities.NotificationBlockRuleEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedDomainEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedPackageEntity
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity
import com.stayfocused.app.data.local.entities.StrictScheduleEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.data.local.entities.SuppressedNotificationEntity
import com.stayfocused.app.data.local.entities.UnlockEventEntity

/**
 * StayFocused Room Database.
 *
 * Schema history:
 * - Version 1: Initial schema.
 * - Version 2: Baseline MVP schema with app limits, blocked domains, recovery codes, and strict sessions.
 * - Version 3: Added `failsafe_logs` table (Phase 15 anti-tamper failsafe audit logging).
 * - Version 4: Added `reason` column to `break_sessions` table (Phase 9 friction-based breaks).
 * - Version 5: Added `strict_schedules` table and updated `strict_sessions` with hardware monotonic baseline and challenge type (Phase 16).
 */
@Database(
    entities = [
        AppLimitEntity::class,
        BlockedDomainEntity::class,
        FocusProfileEntity::class,
        ProfileBlockedPackageEntity::class,
        ProfileBlockedDomainEntity::class,
        StrictSessionEntity::class,
        StrictScheduleEntity::class,
        RecoveryCodeEntity::class,
        UnlockEventEntity::class,
        GeofenceProfileEntity::class,
        NotificationBlockRuleEntity::class,
        SuppressedNotificationEntity::class,
        BreakSessionEntity::class,
        FailsafeLogEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class StayFocusedDatabase : RoomDatabase() {

    abstract fun appLimitDao(): AppLimitDao
    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun focusProfileDao(): FocusProfileDao
    abstract fun strictSessionDao(): StrictSessionDao
    abstract fun strictScheduleDao(): StrictScheduleDao
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `break_sessions` ADD COLUMN `reason` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `strict_schedules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`daysOfWeekMask` INTEGER NOT NULL, " +
                        "`startMinuteOfDay` INTEGER NOT NULL, " +
                        "`endMinuteOfDay` INTEGER NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, " +
                        "`deactivationChallenge` TEXT NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL, " +
                        "`dismissedUntilEpochMs` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`profileId`) REFERENCES `focus_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_strict_schedules_profileId` ON `strict_schedules` (`profileId`)")
                db.execSQL("ALTER TABLE `strict_sessions` ADD COLUMN `startElapsedRealtime` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `strict_sessions` ADD COLUMN `deactivationChallenge` TEXT NOT NULL DEFAULT 'EXPIRATION_ONLY'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `strict_sessions` ADD COLUMN `accumulatedMonotonicMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `strict_sessions` ADD COLUMN `lastElapsedRealtime` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `strict_sessions` ADD COLUMN `lastWallTime` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `strict_sessions` ADD COLUMN `bootCount` INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE `strict_sessions` ADD COLUMN `delayedUnlockStartAccumulatedMs` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `strict_sessions` ADD COLUMN `isScheduled` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE `strict_sessions`
                    SET `accumulatedMonotonicMs` = CASE
                        WHEN `isActive` = 1 AND `targetEndTime` > `startTime` THEN MAX(0, MIN(`targetEndTime` - `startTime`, (strftime('%s', 'now') * 1000) - `startTime`))
                        ELSE 0
                    END,
                    `lastElapsedRealtime` = CASE WHEN `startElapsedRealtime` > 0 THEN `startElapsedRealtime` ELSE 0 END,
                    `lastWallTime` = (strftime('%s', 'now') * 1000),
                    `bootCount` = -1
                    WHERE `isActive` = 1
                    """.trimIndent()
                )
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
