package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedDomainEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedPackageEntity
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
    fun testSwitchToProfileAtomicallyActivatesTargetAndDeactivatesOthers() = runBlocking {
        val dao = db.focusProfileDao()

        val id1 = dao.upsertProfile(FocusProfileEntity(name = "Work", isActive = true))
        val id2 = dao.upsertProfile(FocusProfileEntity(name = "Personal", isActive = false))
        val id3 = dao.upsertProfile(FocusProfileEntity(name = "Study", isActive = false))

        assertEquals(id1, dao.getActiveProfileSync()?.id)

        // Switch to profile 2
        dao.switchToProfile(id2)

        val active = dao.getActiveProfileSync()
        assertNotNull(active)
        assertEquals(id2, active?.id)
        assertEquals("Personal", active?.name)

        val all = dao.getAllProfilesSync()
        assertEquals(3, all.size)
        assertFalse(all.first { it.id == id1 }.isActive)
        assertTrue(all.first { it.id == id2 }.isActive)
        assertFalse(all.first { it.id == id3 }.isActive)
    }

    @Test
    fun testDeactivateAllProfilesSetsAllToInactive() = runBlocking {
        val dao = db.focusProfileDao()

        val id1 = dao.upsertProfile(FocusProfileEntity(name = "Work", isActive = true))
        val id2 = dao.upsertProfile(FocusProfileEntity(name = "Study", isActive = false))

        dao.deactivateAllProfiles()

        assertNull(dao.getActiveProfileSync())
        val all = dao.getAllProfilesSync()
        assertTrue(all.none { it.isActive })
    }

    @Test
    fun testGetAllProfilesWithRulesSyncReturnsRelations() = runBlocking {
        val dao = db.focusProfileDao()

        val profileId = dao.upsertProfile(FocusProfileEntity(name = "Deep Focus", isActive = true))
        dao.insertBlockedPackages(listOf(ProfileBlockedPackageEntity(profileId, "com.instagram.android")))
        dao.insertBlockedDomains(listOf(ProfileBlockedDomainEntity(profileId, "reddit.com")))

        val profilesWithRules = dao.getAllProfilesWithRulesSync()
        assertEquals(1, profilesWithRules.size)
        assertEquals("Deep Focus", profilesWithRules[0].profile.name)
        assertEquals(1, profilesWithRules[0].blockedPackages.size)
        assertEquals("com.instagram.android", profilesWithRules[0].blockedPackages[0].packageName)
        assertEquals(1, profilesWithRules[0].blockedDomains.size)
        assertEquals("reddit.com", profilesWithRules[0].blockedDomains[0].domain)
    }
}
