package com.stayfocused.app.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.strict.GracePeriodManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootCompletedReceiverTest {

    private lateinit var context: Context
    private lateinit var receiver: BootCompletedReceiver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        receiver = BootCompletedReceiver()
        GracePeriodManager.clearGracePeriod()
    }

    @Test
    fun testOnReceiveBootCompletedActivatesGracePeriodWhenNoStrictActive() {
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, bootIntent)

        Thread.sleep(200)
        ShadowLooper.idleMainLooper()

        assertTrue(
            "Boot grace period should be active after receiving BOOT_COMPLETED without strict session",
            GracePeriodManager.isDefaultGracePeriodActive()
        )
    }

    @Test
    fun testOnReceivePackageReplacedDoesNotActivateGracePeriod() {
        val replacedIntent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)
        receiver.onReceive(context, replacedIntent)

        Thread.sleep(200)
        ShadowLooper.idleMainLooper()

        assertFalse(
            "Package replaced must not grant boot grace period",
            GracePeriodManager.isDefaultGracePeriodActive()
        )
    }

    @Test
    fun testOnReceiveBootCompletedDeniesGracePeriodWhenStrictActive() {
        val db = StayFocusedDatabase.getInstance(context)
        runBlocking {
            db.focusProfileDao().upsertProfile(
                FocusProfileEntity(
                    id = 1L,
                    name = "Work Focus",
                    isActive = true,
                    isStrictMode = true
                )
            )
            db.strictSessionDao().insertSession(
                StrictSessionEntity(
                    id = 1L,
                    profileId = 1L,
                    startTime = System.currentTimeMillis() - 1000L,
                    targetEndTime = System.currentTimeMillis() + 3600_000L,
                    isActive = true,
                    startElapsedRealtime = android.os.SystemClock.elapsedRealtime()
                )
            )
        }

        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, bootIntent)

        Thread.sleep(200)
        ShadowLooper.idleMainLooper()

        assertFalse(
            "Boot grace period must be denied when strict session is active at boot",
            GracePeriodManager.isDefaultGracePeriodActive()
        )
    }
}
