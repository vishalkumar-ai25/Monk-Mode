package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusProfileSwitchDaoTest {

    private lateinit var context: Context
    private lateinit var db: StayFocusedDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSwitchToProfileDeactivatesPreviousAndActivatesTarget() = runBlocking {
        val dao = db.focusProfileDao()

        val id1 = dao.upsertProfile(FocusProfileEntity(id = 1, name = "Reading", isActive = true))
        val id2 = dao.upsertProfile(FocusProfileEntity(id = 2, name = "Deep Work", isActive = false))

        val initialActive = dao.getActiveProfileSync()
        assertNotNull(initialActive)
        assertEquals(id1, initialActive?.id)

        // Switch to Profile 2
        dao.switchToProfile(id2)

        val updatedActive = dao.getActiveProfileSync()
        assertNotNull(updatedActive)
        assertEquals(id2, updatedActive?.id)

        val allProfiles = dao.getAllProfilesSync()
        val profile1 = allProfiles.first { it.id == id1 }
        val profile2 = allProfiles.first { it.id == id2 }

        assertFalse(profile1.isActive)
        assertTrue(profile2.isActive)
    }

    @Test
    fun testDeactivateAllProfilesSetsAllToInactive() = runBlocking {
        val dao = db.focusProfileDao()

        dao.upsertProfile(FocusProfileEntity(id = 1, name = "Reading", isActive = true))
        dao.upsertProfile(FocusProfileEntity(id = 2, name = "Deep Work", isActive = true))

        dao.deactivateAllProfiles()

        val active = dao.getActiveProfileSync()
        assertNull(active)

        val all = dao.getAllProfilesSync()
        assertTrue(all.all { !it.isActive })
    }
}
