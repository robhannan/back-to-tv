package io.github.robhannan.backtotv

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView

/**
 * Settings: the TV's address, pairing with the code CleanYourTV shows, the box's input on the TV, and which launcher OK
 * opens on the box. Everything can also be passed as extras, so it can be set up over adb:
 *
 *   adb shell am start -n io.github.robhannan.backtotv/.SetupActivity --es host 192.168.1.20 --es code 123456 \
 *     [--es input com.webos.app.hdmi1] [--es fallback me.efesser.flauncher/.MainActivity] [--ez idleSaver true] [--ei idleMinutes 5]
 */
class SetupActivity : Activity() {
    private lateinit var cfg: Config
    private lateinit var status: TextView
    private lateinit var launcherButton: Button
    private val keyStatus by lazy { text("", 18f, GREY) }
    private val tick = object : Runnable {
        override fun run() {
            keyStatus.text = "Remote keys: " + KeyForwardService.status()
            keyStatus.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Config(this)
        if (savedInstanceState == null) applyExtras(intent) // not again when rebuilt (recreate keeps the first intent)

        val host = field("TV address (CleanYourTV, port 8802)", cfg.host, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val code = field("Pairing code (TV: CleanYourTV > Features > AI agents (MCP) > Connect an agent)", "", InputType.TYPE_CLASS_TEXT)
        val input = field("The box's input on the TV", cfg.input, InputType.TYPE_CLASS_TEXT)
        status = text(state(), 18f, GREY)

        val pair = Button(this).apply {
            text = "Pair with the TV"
            setOnClickListener {
                cfg.host = host.text.toString()
                cfg.input = input.text.toString()
                pairWith(code.text.toString())
            }
        }
        val test = Button(this).apply {
            text = "Test: send the TV to its Home now"
            setOnClickListener {
                cfg.host = host.text.toString()
                cfg.input = input.text.toString()
                status.text = "Asking the TV…"
                App.io.execute { val r = Tv.call(cfg, "navigate", org.json.JSONObject().put("to", "home")); runOnUiThread { status.text = r.text } }
            }
        }
        launcherButton = Button(this).apply {
            setOnClickListener {
                val all = cfg.otherLaunchers()
                if (all.isNotEmpty()) {
                    val i = (all.indexOf(cfg.fallbackLauncher()) + 1) % all.size
                    cfg.fallback = all[i].flattenToString()
                }
                showLauncher()
            }
        }
        showLauncher()
        val keys = Button(this).apply {
            setOnClickListener {
                cfg.forwardKeys = !cfg.forwardKeys
                showKeys(this)
            }
        }
        showKeys(keys)
        val idle = Button(this).apply { setOnClickListener { cfg.idleSaver = !cfg.idleSaver; showIdle(this) } }
        val idleMins = Button(this).apply {
            setOnClickListener {
                val steps = listOf(5, 10, 15, 30, 60)
                cfg.idleMinutes = steps[(steps.indexOf(cfg.idleMinutes) + 1) % steps.size]
                showIdle(idle); text = "Idle time: ${cfg.idleMinutes} minutes (press to change)"
            }
            text = "Idle time: ${cfg.idleMinutes} minutes (press to change)"
        }
        showIdle(idle)
        val help = text(
            "To make this the box's Home (so closing an app lands here), run once from a computer:\n" +
                "adb shell cmd package set-home-activity $packageName/.HomeActivity\n" +
                "To undo: adb shell cmd package set-home-activity <your launcher>, or uninstall this app.",
            16f, GREY,
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(column(text("Back to TV: settings", 30f), host, code, input, pair, test, launcherButton, keys, keyStatus, idle, idleMins, status, help))
        })

        status.text = state()
    }

    // settings passed from adb (see the class comment); applied before the screen is built, so it shows them
    private fun applyExtras(i: Intent) {
        i.getStringExtra("host")?.let { cfg.host = it }
        i.getStringExtra("input")?.let { cfg.input = it }
        i.getStringExtra("fallback")?.let { cfg.fallback = it }
        if (i.hasExtra("idleSaver")) cfg.idleSaver = i.getBooleanExtra("idleSaver", false)
        if (i.hasExtra("idleMinutes")) cfg.idleMinutes = i.getIntExtra("idleMinutes", 5)
        if (i.hasExtra("forwardKeys")) cfg.forwardKeys = i.getBooleanExtra("forwardKeys", true)
        i.getStringExtra("code")?.let { code -> window.decorView.post { pairWith(code) } }
    }

    // started again while open (adb): apply the new extras, then rebuild the screen so it shows them
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyExtras(intent)
        recreate()
    }

    private fun pairWith(code: String) {
        if (cfg.host.isEmpty() || code.isBlank()) {
            status.text = "Enter the TV address and the code shown on the TV."
            return
        }
        status.text = "Pairing…"
        val name = "${Build.MODEL} (Back to TV)"
        App.io.execute { val r = Tv.pair(cfg, code, name); runOnUiThread { status.text = r.text + "\n" + state() } }
    }

    override fun onResume() {
        super.onResume()
        tick.run()
    }

    override fun onPause() {
        keyStatus.removeCallbacks(tick)
        super.onPause()
    }

    private fun showIdle(b: Button) {
        b.text = "TV screensaver after ${cfg.idleMinutes} min with no key and no sound, even in apps that keep the screen on: " +
            "${if (cfg.idleSaver) "ON" else "OFF"} (press to change)"
    }

    private fun showKeys(b: Button) {
        b.text = "This box's remote drives the TV on the TV's own screens: ${if (cfg.forwardKeys) "ON" else "OFF"} (press to change)"
    }

    private fun showLauncher() {
        launcherButton.text = "OK on the box opens: ${cfg.label(cfg.fallbackLauncher())} (press to change)"
    }

    private fun state() = if (cfg.paired) "Paired with ${cfg.host}; the TV is sent Home while ${cfg.input} is on screen." else "Not paired yet."

    private fun field(hint: String, value: String, type: Int) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = type
        setSingleLine()
        textSize = 20f
        setTextColor(0xFFFFFFFF.toInt())
        setHintTextColor(GREY)
    }
}
