package io.github.robhannan.backtotv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.widget.TextView

/**
 * The box's home screen. Backing out of an app returns here, and that sends the TV to its own Home (CleanYourTV's
 * launcher), as long as the box's input is still on screen. Right after the box wakes it does nothing to the TV, because
 * then Home means "the box was switched on", not "an app was closed".
 *
 * On the box itself it is a plain screen: OK opens the box's usual launcher (FLauncher or Google TV's), Menu opens
 * the settings.
 */
class HomeActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var cfg: Config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Config(this)
        status = text("", 22f, GREY).apply { gravity = Gravity.CENTER }
        val hint = text("OK: apps on this box      Menu: settings", 18f, GREY).apply { gravity = Gravity.CENTER }
        val title = text(getString(R.string.app_name), 34f).apply { gravity = Gravity.CENTER }
        setContentView(column(title, status, hint).apply { gravity = Gravity.CENTER; setBackgroundColor(BG) })
    }

    override fun onResume() {
        super.onResume()
        if (!cfg.paired) {
            status.text = "Not paired with the TV yet: press Menu to set up."
            return
        }
        val sinceWake = SystemClock.elapsedRealtime() - App.lastWake
        if (sinceWake < Config.WAKE_GRACE_MS) {
            // the box was just switched on: leave the TV where it is, but keep the pairing in use now and then
            status.text = ""
            if (System.currentTimeMillis() - cfg.lastOk > Config.KEEPALIVE_MS) App.io.execute { Tv.call(cfg, "get_current_app") }
            return
        }
        status.text = "Sending the TV to its Home…"
        App.io.execute {
            val r = Tv.backToTvHome(cfg)
            runOnUiThread { status.text = r.text }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
            openLauncher(); true
        }
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_BUTTON_Y -> {
            startActivity(Intent(this, SetupActivity::class.java)); true
        }
        KeyEvent.KEYCODE_BACK -> true // this is Home: there is nothing to go back to
        else -> super.onKeyDown(keyCode, event)
    }

    private fun openLauncher() {
        val c = cfg.fallbackLauncher()
        if (c == null) {
            status.text = "No other launcher is installed: press Menu for settings."
            return
        }
        // NO_HISTORY: the launcher closes as soon as an app opens from it, so backing out of any app lands here (and on the
        // TV's Home), never on a launcher left open underneath. Started without CATEGORY_HOME, so Android does not treat it
        // as a home screen either.
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .setComponent(c)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
        )
    }
}
