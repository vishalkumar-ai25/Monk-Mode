package com.stayfocused.app.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.stayfocused.app.domain.model.BlockReason

class BlockOverlayManager(
    private val context: Context,
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
    private val permissionChecker: (Context) -> Boolean = { Settings.canDrawOverlays(it) }
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var activeReason by mutableStateOf<BlockReason?>(null)
    private var activeOnReturnHome: (() -> Unit)? = null

    val isShowing: Boolean
        get() = overlayView != null

    val currentReason: BlockReason?
        get() = activeReason

    fun canDrawOverlays(): Boolean = permissionChecker(context)

    fun showOverlay(
        reason: BlockReason,
        onReturnHome: () -> Unit = { navigateToHome() }
    ) {
        if (!canDrawOverlays()) {
            return
        }

        runOnMainThread {
            activeReason = reason
            activeOnReturnHome = onReturnHome

            if (overlayView != null) {
                // Already showing: Compose reactivity will update the UI automatically
                return@runOnMainThread
            }

            val owner = OverlayLifecycleOwner()
            lifecycleOwner = owner

            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    activeReason?.let { currentBlockReason ->
                        BlockOverlayContent(
                            reason = currentBlockReason,
                            onReturnHome = {
                                triggerReturnHome()
                            }
                        )
                    }
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                windowManager.addView(composeView, params)
                overlayView = composeView
            } catch (e: Exception) {
                owner.destroy()
                lifecycleOwner = null
                overlayView = null
            }
        }
    }

    fun triggerReturnHome() {
        val callback = activeOnReturnHome
        hideOverlay()
        callback?.invoke()
    }

    fun hideOverlay() {
        runOnMainThread {
            overlayView?.let { view ->
                try {
                    view.disposeComposition()
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    // Ignored if already removed
                } finally {
                    lifecycleOwner?.destroy()
                    lifecycleOwner = null
                    overlayView = null
                    activeReason = null
                    activeOnReturnHome = null
                }
            }
        }
    }

    private fun navigateToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(homeIntent)
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}
