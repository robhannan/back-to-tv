package io.github.robhannan.backtotv

import android.app.Activity
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
 *     [--es input com.webos.app.hdmi1] [--es fallback me.efesser.flauncher/.MainActivity]
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
        val help = text(
            "To make this the box's Home (so closing an app lands here), run once from a computer:\n" +
                "adb shell cmd package set-home-activity $packageName/.HomeActivity\n" +
                "To undo: adb shell cmd package set-home-activity <your launcher>, or uninstall this app.",
            16f, GREY,
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(column(text("Back to TV: settings", 30f), host, code, input, pair, test, launcherButton, keys, keyStatus, status, help))
        })

        // set up from adb extras
        intent.getStringExtra("host")?.let { cfg.host = it; host.setText(it) }
        intent.getStringExtra("input")?.let { cfg.input = it; input.setText(cfg.input) }
        intent.getStringExtra("fallback")?.let { cfg.fallback = it; showLauncher() }
        intent.getStringExtra("code")?.let { pairWith(it) }
        status.text = state()
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
