package com.stayfocused.app.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.stayfocused.app.breaks.BreakDecisionEngine
import com.stayfocused.app.data.local.StayFocusedDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Quick Settings (QS) Tile allowing users to quickly start or stop
 * an authorized 5-minute break directly from their notification shade.
 */
open class TakeABreakTileService : TileService() {

    companion object {
        private const val TAG = "TakeABreakTile"
        const val DEFAULT_BREAK_MINUTES = 5
    }

    var breakEngine: BreakDecisionEngine = BreakDecisionEngine()
    var database: StayFocusedDatabase? = null
    var showToasts: Boolean = true
    var qsTileProvider: () -> Tile? = { qsTile }
    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    var serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        if (database == null) {
            database = StayFocusedDatabase.getInstance(this)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    fun refreshTileState(): kotlinx.coroutines.Job {
        val tile = qsTileProvider() ?: return kotlinx.coroutines.Job()
        val db = database ?: return kotlinx.coroutines.Job()

        return serviceScope.launch {
            val (isStrict, activeBreak) = withContext(ioDispatcher) {
                val strict = db.strictSessionDao().getActiveStrictSessionSync()?.isActive == true
                val session = db.breakSessionDao().getActiveBreakSync()
                strict to session
            }

            val now = System.currentTimeMillis()
            val isBreakActive = breakEngine.isBreakActive(now, activeBreak, isStrict)

            when {
                isStrict -> {
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.label = "Break (Locked)"
                    setTileSubtitle(tile, "Strict Mode")
                }
                isBreakActive -> {
                    val remainingSec = breakEngine.calculateRemainingSeconds(now, activeBreak)
                    val remainingMin = (remainingSec + 59) / 60
                    tile.state = Tile.STATE_ACTIVE
                    tile.label = "Break Active"
                    setTileSubtitle(tile, "${remainingMin}m remaining")
                }
                else -> {
                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "Take a Break"
                    setTileSubtitle(tile, "${DEFAULT_BREAK_MINUTES} min")
                }
            }
            tile.updateTile()
        }
    }

    private fun setTileSubtitle(tile: Tile, subtitleText: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            tile.subtitle = subtitleText
        }
    }

    override fun onClick() {
        super.onClick()
        handleClick()
    }

    fun handleClick(): kotlinx.coroutines.Job {
        val db = database ?: return kotlinx.coroutines.Job()

        return serviceScope.launch {
            val (isStrict, activeBreak) = withContext(ioDispatcher) {
                val strict = db.strictSessionDao().getActiveStrictSessionSync()?.isActive == true
                val session = db.breakSessionDao().getActiveBreakSync()
                strict to session
            }

            val now = System.currentTimeMillis()
            val isBreakActive = breakEngine.isBreakActive(now, activeBreak, isStrict)

            if (isStrict) {
                if (showToasts) {
                    Toast.makeText(this@TakeABreakTileService, "Breaks are disabled during active Strict Mode", Toast.LENGTH_SHORT).show()
                }
                refreshTileState()
                return@launch
            }

            if (isBreakActive) {
                // End break early
                withContext(ioDispatcher) {
                    db.breakSessionDao().deactivateBreak()
                }
                if (showToasts) {
                    Toast.makeText(this@TakeABreakTileService, "Break ended. Focus restored!", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Start a new 5-minute break
                val newSession = breakEngine.createBreakSession(now, DEFAULT_BREAK_MINUTES)
                withContext(ioDispatcher) {
                    db.breakSessionDao().upsertBreak(newSession)
                }
                if (showToasts) {
                    Toast.makeText(this@TakeABreakTileService, "$DEFAULT_BREAK_MINUTES-minute break started. Enjoy!", Toast.LENGTH_SHORT).show()
                }
            }
            refreshTileState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
