package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stayfocused.app.data.local.dao.AppLimitDao
import com.stayfocused.app.data.local.dao.BlockedDomainDao
import com.stayfocused.app.data.local.dao.BlockedKeywordDao
import com.stayfocused.app.data.local.dao.BlockedUrlPathDao
import com.stayfocused.app.data.local.dao.BreakSessionDao
import com.stayfocused.app.data.local.dao.BrowsingHistoryDao
import com.stayfocused.app.data.local.dao.FailsafeLogDao
import com.stayfocused.app.data.local.dao.FocusProfileDao
import com.stayfocused.app.data.local.dao.HourlyTelemetryDao
import com.stayfocused.app.data.local.dao.PomodoroDao
import com.stayfocused.app.data.local.dao.RecoveryCodeDao
import com.stayfocused.app.data.local.dao.ShortFormRuleDao
import com.stayfocused.app.data.local.dao.SoundStemDao
import com.stayfocused.app.data.local.dao.StrictScheduleDao
import com.stayfocused.app.data.local.dao.StrictSessionDao
import com.stayfocused.app.data.local.dao.SuppressedNotificationDao
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import com.stayfocused.app.data.local.entities.BlockedKeywordEntity
import com.stayfocused.app.data.local.entities.BlockedUrlPathEntity
import com.stayfocused.app.data.local.entities.BreakSessionEntity
import com.stayfocused.app.data.local.entities.BrowsingHistoryEntity
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.GeofenceProfileEntity
import com.stayfocused.app.data.local.entities.HourlyAppTelemetryEntity
import com.stayfocused.app.data.local.entities.HourlyDeviceTelemetryEntity
import com.stayfocused.app.data.local.entities.NotificationBlockRuleEntity
import com.stayfocused.app.data.local.entities.PomodoroSessionEntity
import com.stayfocused.app.data.local.entities.PomodoroSettingsEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedDomainEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedPackageEntity
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity
import com.stayfocused.app.data.local.entities.ShortFormRuleEntity
import com.stayfocused.app.data.local.entities.SoundStemEntity
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
 * - Version 6: Added monotonic timing and checkpoint columns to `strict_sessions` table (ADR 011).
 * - Version 7: Added `short_form_rules` table for granular short-form video feed blocking (ADR 016).
 * - Version 8: Added `browsing_history`, `blocked_keywords`, and `blocked_url_paths` tables for URL & keyword blocking (ADR 017).
 * - Version 9: Added session cooldown, monotonic elapsed timing, and prerequisite goal app columns to `app_limits` (ADR 018).
 * - Version 10: Added `hourly_device_telemetry` and `hourly_app_telemetry` tables for 24-hour usage & pickup telemetry (ADR 019).
 * - Version 11: Added `pomodoro_settings`, `pomodoro_session`, and `sound_stems` tables for Pomodoro & Soundscape (ADR 020).
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
        FailsafeLogEntity::class,
        ShortFormRuleEntity::class,
        BrowsingHistoryEntity::class,
        BlockedKeywordEntity::class,
        BlockedUrlPathEntity::class,
        HourlyDeviceTelemetryEntity::class,
        HourlyAppTelemetryEntity::class,
        PomodoroSettingsEntity::class,
        PomodoroSessionEntity::class,
        SoundStemEntity::class
    ],
    version = 11,
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
    abstract fun shortFormRuleDao(): ShortFormRuleDao
    abstract fun browsingHistoryDao(): BrowsingHistoryDao
    abstract fun blockedKeywordDao(): BlockedKeywordDao
    abstract fun blockedUrlPathDao(): BlockedUrlPathDao
    abstract fun hourlyTelemetryDao(): HourlyTelemetryDao
    abstract fun pomodoroDao(): PomodoroDao
    abstract fun soundStemDao(): SoundStemDao

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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `short_form_rules` (" +
                        "`id` TEXT NOT NULL, " +
                        "`packageName` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`isBlocked` INTEGER NOT NULL, " +
                        "`dailyScrollLimit` INTEGER NOT NULL, " +
                        "`currentDayScrolls` INTEGER NOT NULL, " +
                        "`lastEvictionTimestamp` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                // Seed default short-form feed targets
                db.execSQL("INSERT OR IGNORE INTO `short_form_rules` (`id`, `packageName`, `displayName`, `isBlocked`, `dailyScrollLimit`, `currentDayScrolls`, `lastEvictionTimestamp`) VALUES ('youtube_shorts', 'com.google.android.youtube', 'YouTube Shorts', 0, 0, 0, 0)")
                db.execSQL("INSERT OR IGNORE INTO `short_form_rules` (`id`, `packageName`, `displayName`, `isBlocked`, `dailyScrollLimit`, `currentDayScrolls`, `lastEvictionTimestamp`) VALUES ('instagram_reels', 'com.instagram.android', 'Instagram Reels', 0, 0, 0, 0)")
                db.execSQL("INSERT OR IGNORE INTO `short_form_rules` (`id`, `packageName`, `displayName`, `isBlocked`, `dailyScrollLimit`, `currentDayScrolls`, `lastEvictionTimestamp`) VALUES ('instagram_stories', 'com.instagram.android', 'Instagram Stories', 0, 0, 0, 0)")
                db.execSQL("INSERT OR IGNORE INTO `short_form_rules` (`id`, `packageName`, `displayName`, `isBlocked`, `dailyScrollLimit`, `currentDayScrolls`, `lastEvictionTimestamp`) VALUES ('snapchat_spotlight', 'com.snapchat.android', 'Snapchat Spotlight', 0, 0, 0, 0)")
                db.execSQL("INSERT OR IGNORE INTO `short_form_rules` (`id`, `packageName`, `displayName`, `isBlocked`, `dailyScrollLimit`, `currentDayScrolls`, `lastEvictionTimestamp`) VALUES ('facebook_reels', 'com.facebook.katana', 'Facebook Reels', 0, 0, 0, 0)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `browsing_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestampMs` INTEGER NOT NULL, " +
                        "`browserPackage` TEXT NOT NULL, " +
                        "`domain` TEXT NOT NULL, " +
                        "`fullUrl` TEXT NOT NULL, " +
                        "`dwellTimeMs` INTEGER NOT NULL, " +
                        "`wasBlocked` INTEGER NOT NULL, " +
                        "`blockReason` TEXT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_browsing_history_timestampMs` ON `browsing_history` (`timestampMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_browsing_history_domain_timestampMs` ON `browsing_history` (`domain`, `timestampMs`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `blocked_keywords` (" +
                        "`keyword` TEXT NOT NULL, " +
                        "`isBlocked` INTEGER NOT NULL DEFAULT 1, " +
                        "`category` TEXT NOT NULL DEFAULT 'custom', " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`keyword`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `blocked_url_paths` (" +
                        "`pathPattern` TEXT NOT NULL, " +
                        "`isBlocked` INTEGER NOT NULL DEFAULT 1, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`pathPattern`))"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `sessionAllowanceMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `cooldownDurationMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `cooldownStartElapsedRealtime` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `cooldownActiveUntilWallMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `cooldownBootCount` INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `goalPackageName` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `goalDurationMinutes` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hourly_device_telemetry` (" +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`hourOfDay` INTEGER NOT NULL, " +
                        "`pickupCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`totalScreenTimeMs` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`epochDay`, `hourOfDay`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hourly_device_telemetry_epochDay` ON `hourly_device_telemetry` (`epochDay`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hourly_app_telemetry` (" +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`hourOfDay` INTEGER NOT NULL, " +
                        "`packageName` TEXT COLLATE NOCASE NOT NULL, " +
                        "`foregroundMs` INTEGER NOT NULL DEFAULT 0, " +
                        "`shortsBlockedCount` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`epochDay`, `hourOfDay`, `packageName`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hourly_app_telemetry_epochDay_hourOfDay` ON `hourly_app_telemetry` (`epochDay`, `hourOfDay`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pomodoro_settings` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`workMinutes` INTEGER NOT NULL, " +
                        "`shortBreakMinutes` INTEGER NOT NULL, " +
                        "`longBreakMinutes` INTEGER NOT NULL, " +
                        "`longBreakInterval` INTEGER NOT NULL, " +
                        "`autoStartBreaks` INTEGER NOT NULL, " +
                        "`autoStartWork` INTEGER NOT NULL, " +
                        "`isDndEnabled` INTEGER NOT NULL, " +
                        "`linkedProfileId` INTEGER, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`linkedProfileId`) REFERENCES `focus_profiles`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pomodoro_settings_linkedProfileId` ON `pomodoro_settings` (`linkedProfileId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pomodoro_session` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`phaseName` TEXT NOT NULL, " +
                        "`currentCycle` INTEGER NOT NULL, " +
                        "`targetElapsedRealtimeMs` INTEGER NOT NULL, " +
                        "`targetWallTimeMs` INTEGER NOT NULL, " +
                        "`totalDurationMs` INTEGER NOT NULL, " +
                        "`remainingMs` INTEGER NOT NULL, " +
                        "`bootCount` INTEGER NOT NULL, " +
                        "`isRunning` INTEGER NOT NULL, " +
                        "`originalDndFilter` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sound_stems` (" +
                        "`stemId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`assetPath` TEXT NOT NULL, " +
                        "`volume` REAL NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`stemId`))"
                )

                // Seed default settings and 6 ambient sound stems
                db.execSQL("INSERT OR IGNORE INTO `pomodoro_settings` (`id`, `workMinutes`, `shortBreakMinutes`, `longBreakMinutes`, `longBreakInterval`, `autoStartBreaks`, `autoStartWork`, `isDndEnabled`, `linkedProfileId`) VALUES (1, 25, 5, 15, 4, 0, 0, 0, NULL)")
                db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('rain', 'Rain & Thunder', 'sounds/rain.ogg', 0.0, 0)")
                db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('waves', 'Ocean Waves', 'sounds/waves.ogg', 0.0, 0)")
                db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('forest', 'Forest Wind', 'sounds/forest.ogg', 0.0, 0)")
                db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('whitenoise', 'White Noise', 'sounds/whitenoise.ogg', 0.0, 0)")
                db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('brownnoise', 'Brown Noise', 'sounds/brownnoise.ogg', 0.0, 0)")
                db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('campfire', 'Campfire', 'sounds/campfire.ogg', 0.0, 0)")
            }
        }

        val DEFAULT_SHORT_FORM_RULES = listOf(
            ShortFormRuleEntity("youtube_shorts", "com.google.android.youtube", "YouTube Shorts", false, 0, 0, 0L),
            ShortFormRuleEntity("instagram_reels", "com.instagram.android", "Instagram Reels", false, 0, 0, 0L),
            ShortFormRuleEntity("instagram_stories", "com.instagram.android", "Instagram Stories", false, 0, 0, 0L),
            ShortFormRuleEntity("snapchat_spotlight", "com.snapchat.android", "Snapchat Spotlight", false, 0, 0, 0L),
            ShortFormRuleEntity("facebook_reels", "com.facebook.katana", "Facebook Reels", false, 0, 0, 0L)
        )

        @Volatile
        private var INSTANCE: StayFocusedDatabase? = null

        fun getInstance(context: Context): StayFocusedDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext ?: context,
                    StayFocusedDatabase::class.java,
                    "stay_focused_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL("INSERT OR IGNORE INTO `short_form_rules` (`id`, `packageName`, `displayName`, `isBlocked`, `dailyScrollLimit`, `currentDayScrolls`, `lastEvictionTimestamp`) VALUES ('youtube_shorts', 'com.google.android.youtube', 'YouTube Shorts', 0, 0, 0, 0)")
                            db.execSQL("INSERT OR IGNORE INTO `short_form_rules` (`id`, `packageName`, `displayName`, `isBlocked`, `dailyScrollLimit`, `currentDayScrolls`, `lastEvictionTimestamp`) VALUES ('instagram_reels', 'com.instagram.android', 'Instagram Reels', 0, 0, 0, 0)")
                            db.execSQL("INSERT OR IGNORE INTO `short_form_rules` (`id`, `packageName`, `displayName`, `isBlocked`, `dailyScrollLimit`, `currentDayScrolls`, `lastEvictionTimestamp`) VALUES ('instagram_stories', 'com.instagram.android', 'Instagram Stories', 0, 0, 0, 0)")
                            db.execSQL("INSERT OR IGNORE INTO `short_form_rules` (`id`, `packageName`, `displayName`, `isBlocked`, `dailyScrollLimit`, `currentDayScrolls`, `lastEvictionTimestamp`) VALUES ('snapchat_spotlight', 'com.snapchat.android', 'Snapchat Spotlight', 0, 0, 0, 0)")
                            db.execSQL("INSERT OR IGNORE INTO `short_form_rules` (`id`, `packageName`, `displayName`, `isBlocked`, `dailyScrollLimit`, `currentDayScrolls`, `lastEvictionTimestamp`) VALUES ('facebook_reels', 'com.facebook.katana', 'Facebook Reels', 0, 0, 0, 0)")

                            db.execSQL("INSERT OR IGNORE INTO `pomodoro_settings` (`id`, `workMinutes`, `shortBreakMinutes`, `longBreakMinutes`, `longBreakInterval`, `autoStartBreaks`, `autoStartWork`, `isDndEnabled`, `linkedProfileId`) VALUES (1, 25, 5, 15, 4, 0, 0, 0, NULL)")
                            db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('rain', 'Rain & Thunder', 'sounds/rain.ogg', 0.0, 0)")
                            db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('waves', 'Ocean Waves', 'sounds/waves.ogg', 0.0, 0)")
                            db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('forest', 'Forest Wind', 'sounds/forest.ogg', 0.0, 0)")
                            db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('whitenoise', 'White Noise', 'sounds/whitenoise.ogg', 0.0, 0)")
                            db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('brownnoise', 'Brown Noise', 'sounds/brownnoise.ogg', 0.0, 0)")
                            db.execSQL("INSERT OR IGNORE INTO `sound_stems` (`stemId`, `title`, `assetPath`, `volume`, `isEnabled`) VALUES ('campfire', 'Campfire', 'sounds/campfire.ogg', 0.0, 0)")
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetInstanceForTesting() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}

