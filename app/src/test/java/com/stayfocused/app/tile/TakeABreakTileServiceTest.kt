package com.stayfocused.app.tile

import android.content.Context
import android.service.quicksettings.Tile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.BreakSessionEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TakeABreakTileServiceTest {

    private lateinit var service: TakeABreakTileService
    private lateinit var database: StayFocusedDatabase
    private lateinit var mockTile: Tile
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        service = Robolectric.buildService(TakeABreakTileService::class.java).create().get()
        service.database = database
        service.showToasts = false
        service.ioDispatcher = testDispatcher
        service.serviceScope = CoroutineScope(SupervisorJob() + testDispatcher)

        mockTile = mockk(relaxed = true)
        service.qsTileProvider = { mockTile }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRefreshTileStateWhenBreakIsInactive() = runTest(testDispatcher) {
        service.refreshTileState().join()

        verify { mockTile.state = Tile.STATE_INACTIVE }
        verify { mockTile.label = "Take a Break" }
        verify { mockTile.updateTile() }
    }

    @Test
    fun testRefreshTileStateWhenBreakIsActive() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val breakSession = BreakSessionEntity(
            startTime = now,
            endTime = now + 5 * 60 * 1000L,
            durationMinutes = 5,
            isActive = true
        )
        database.breakSessionDao().upsertBreak(breakSession)

        service.refreshTileState().join()

        verify { mockTile.state = Tile.STATE_ACTIVE }
        verify { mockTile.updateTile() }
    }

    @Test
    fun testOnClickTogglesBreakWhenInactive() = runTest(testDispatcher) {
        // Initially inactive
        assertNull(database.breakSessionDao().getActiveBreakSync())

        service.handleClick().join()

        val activeBreak = database.breakSessionDao().getActiveBreakSync()
        assertNotNull(activeBreak)
        assertEquals(5, activeBreak?.durationMinutes)
    }

    @Test
    fun testOnClickCancelsBreakWhenActive() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val breakSession = BreakSessionEntity(
            startTime = now,
            endTime = now + 5 * 60 * 1000L,
            durationMinutes = 5,
            isActive = true
        )
        database.breakSessionDao().upsertBreak(breakSession)

        service.handleClick().join()

        val activeBreak = database.breakSessionDao().getActiveBreakSync()
        assertNull(activeBreak)
    }

    @Test
    fun testOnClickDoesNotStartBreakDuringStrictMode() = runTest(testDispatcher) {
        // Must insert parent FocusProfileEntity first to satisfy Foreign Key constraint
        val profile = FocusProfileEntity(
            id = 1L,
            name = "Deep Work",
            isActive = true,
            isStrictMode = true
        )
        database.focusProfileDao().upsertProfile(profile)

        val now = System.currentTimeMillis()
        val strictSession = StrictSessionEntity(
            profileId = 1L,
            startTime = now,
            targetEndTime = now + 60 * 60 * 1000L,
            delayedUnlockDurationMs = 24 * 60 * 60 * 1000L,
            isActive = true
        )
        database.strictSessionDao().insertSession(strictSession)

        service.handleClick().join()

        val activeBreak = database.breakSessionDao().getActiveBreakSync()
        assertNull("Breaks cannot be started while Strict Mode is active", activeBreak)
    }
}
