package io.github.robhannan.backtotv

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Makes the box's remote drive the TV whenever the TV shows its own screens (Home, Live TV, settings): those presses are
 * swallowed here and sent to the TV over the network instead. While the TV shows the box's input, every press goes to the
 * box as usual. Keys not in [KEYS] (volume, power, ...) are never touched, and nothing is swallowed while the TV's state
 * is unknown (off, unreachable, not yet approved), so the box's remote can never go dead.
 *
 * Enabled as an accessibility service (key filtering): Settings > Accessibility on the box, or over adb.
 */
class KeyForwardService : AccessibilityService() {
    private lateinit var tv: TvRemote
    private lateinit var cfg: Config
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) startTv() else tv.stop()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        cfg = Config(this)
        tv = TvRemote(this)
        instance = this
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(screen, filter)
        if (getSystemService(PowerManager::class.java).isInteractive) startTv()
    }

    private fun startTv() {
        if (cfg.forwardKeys && cfg.host.isNotEmpty()) tv.start()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val name = KEYS[event.keyCode] ?: return false
        if (!cfg.forwardKeys || tv.state != TvRemote.State.READY) return false
        val app = tv.tvApp
        if (app.isEmpty() || app == cfg.input) return false // the box's input (or unknown): the box handles it
        // the TV shows its own screen: the press is the TV's. Send on down (and on auto-repeat, for held arrows); swallow both.
        if (event.action == KeyEvent.ACTION_DOWN && !tv.button(name)) return false
        if (event.action == KeyEvent.ACTION_DOWN) Log.d(TvRemote.TAG, "key ${event.keyCode} -> TV $name ($app)")
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screen) }
        tv.stop()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: KeyForwardService? = null

        /** The connection's state, for the settings screen. */
        fun status(): String = instance?.tv?.let { t ->
            when (t.state) {
                TvRemote.State.READY -> "Connected; the TV shows ${t.tvApp.ifEmpty { "?" }}."
                TvRemote.State.WAITING_FOR_APPROVAL -> "Waiting: accept \"Back to TV\" on the TV screen."
                TvRemote.State.CONNECTING -> "Connecting to the TV…"
                TvRemote.State.OFF -> "Not connected${t.lastError.let { if (it.isEmpty()) "" else ": $it" }}."
            }
        } ?: "The key service is off (Accessibility settings)."

        /** Box key -> LG remote button name. Volume, mute and power are left to the remote and the TV. */
        val KEYS = mapOf(
            KeyEvent.KEYCODE_DPAD_UP to "UP",
            KeyEvent.KEYCODE_DPAD_DOWN to "DOWN",
            KeyEvent.KEYCODE_DPAD_LEFT to "LEFT",
            KeyEvent.KEYCODE_DPAD_RIGHT to "RIGHT",
            KeyEvent.KEYCODE_DPAD_CENTER to "ENTER",
            KeyEvent.KEYCODE_ENTER to "ENTER",
            KeyEvent.KEYCODE_NUMPAD_ENTER to "ENTER",
            KeyEvent.KEYCODE_BUTTON_A to "ENTER",
            KeyEvent.KEYCODE_BACK to "BACK",
            KeyEvent.KEYCODE_HOME to "HOME",
            KeyEvent.KEYCODE_MENU to "MENU",
            KeyEvent.KEYCODE_C to "MENU", // this universal remote's Menu button arrives through the Flirc receiver as C
            KeyEvent.KEYCODE_INFO to "INFO",
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "PLAY",
            KeyEvent.KEYCODE_MEDIA_PLAY to "PLAY",
            KeyEvent.KEYCODE_MEDIA_PAUSE to "PAUSE",
            KeyEvent.KEYCODE_MEDIA_STOP to "STOP",
            KeyEvent.KEYCODE_MEDIA_REWIND to "REWIND",
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "FASTFORWARD",
            KeyEvent.KEYCODE_CHANNEL_UP to "CHANNELUP",
            KeyEvent.KEYCODE_CHANNEL_DOWN to "CHANNELDOWN",
            KeyEvent.KEYCODE_PAGE_UP to "CHANNELUP",
            KeyEvent.KEYCODE_PAGE_DOWN to "CHANNELDOWN",
            KeyEvent.KEYCODE_GUIDE to "GUIDE",
            KeyEvent.KEYCODE_0 to "0", KeyEvent.KEYCODE_1 to "1", KeyEvent.KEYCODE_2 to "2", KeyEvent.KEYCODE_3 to "3",
            KeyEvent.KEYCODE_4 to "4", KeyEvent.KEYCODE_5 to "5", KeyEvent.KEYCODE_6 to "6", KeyEvent.KEYCODE_7 to "7",
            KeyEvent.KEYCODE_8 to "8", KeyEvent.KEYCODE_9 to "9",
        )
    }
}
