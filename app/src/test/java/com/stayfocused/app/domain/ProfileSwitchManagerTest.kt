package com.stayfocused.app.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.domain.model.ProfileSwitchDecision
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileSwitchManagerTest {

    private lateinit var context: Context
    private lateinit var db: StayFocusedDatabase
    private lateinit var manager: ProfileSwitchManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = ProfileSwitchManager(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSwitchProfileAtomicallySucceedsWhenNoStrictMode() = runBlocking {
        val p1Id = db.focusProfileDao().upsertProfile(FocusProfileEntity(name = "Work", isActive = true))
        val p2Id = db.focusProfileDao().upsertProfile(FocusProfileEntity(name = "Personal", isActive = false))

        val target = db.focusProfileDao().getAllProfilesSync().first { it.id == p2Id }
        val result = manager.switchProfileAtomically(target)

        assertTrue(result is ProfileSwitchDecision.ImmediateSwitch)
        assertEquals(target, (result as ProfileSwitchDecision.ImmediateSwitch).targetProfile)

        // Verify database state was updated atomically
        val active = db.focusProfileDao().getActiveProfileSync()
        assertEquals(p2Id, active?.id)
    }

    @Test
    fun testSwitchProfileAtomicallyBlockedWhenStrictModeActive() = runBlocking {
        val p1Id = db.focusProfileDao().upsertProfile(FocusProfileEntity(name = "Deep Work", isActive = true))
        val p2Id = db.focusProfileDao().upsertProfile(FocusProfileEntity(name = "Casual", isActive = false))

        // Activate Strict Mode
        db.strictSessionDao().insertSession(
            StrictSessionEntity(
                id = 1,
                profileId = p1Id,
                startTime = System.currentTimeMillis() - 1000L,
                targetEndTime = System.currentTimeMillis() + 3600_000L,
                isActive = true
            )
        )

        val target = db.focusProfileDao().getAllProfilesSync().first { it.id == p2Id }
        val result = manager.switchProfileAtomically(target)

        assertTrue(result is ProfileSwitchDecision.BlockedByStrictMode)
        assertEquals(target, (result as ProfileSwitchDecision.BlockedByStrictMode).targetProfile)

        // Verify database profile was NOT switched
        val active = db.focusProfileDao().getActiveProfileSync()
        assertEquals(p1Id, active?.id)
    }

    @Test
    fun testSwitchProfileAtomicallyReturnsAlreadyActive() = runBlocking {
        val p1Id = db.focusProfileDao().upsertProfile(FocusProfileEntity(name = "Work", isActive = true))
        val current = db.focusProfileDao().getAllProfilesSync().first { it.id == p1Id }

        val result = manager.switchProfileAtomically(current)
        assertTrue(result is ProfileSwitchDecision.AlreadyActive)
        assertEquals(current, (result as ProfileSwitchDecision.AlreadyActive).profile)
    }
}
