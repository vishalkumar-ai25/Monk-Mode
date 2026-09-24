package com.stayfocused.app.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.strict.GracePeriodManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootCompletedReceiverTest {

    @Test
    fun testOnReceiveBootCompletedActivatesGracePeriod() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = BootCompletedReceiver()

        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, bootIntent)

        assertTrue(
            "Boot grace period should be active after receiving BOOT_COMPLETED",
            GracePeriodManager.isDefaultGracePeriodActive()
        )
    }
}
