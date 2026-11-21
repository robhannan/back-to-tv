package io.github.robhannan.backtotv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** The settings, kept in the app's private storage (the TV token never leaves it). */
class Config(context: Context) {
    private val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    private val pm = context.packageManager
    private val self = context.packageName

    var host: String
        get() = prefs.getString("host", "") ?: ""
        set(v) = prefs.edit().putString("host", v.trim()).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v).apply()

    /** The TV app of the box's HDMI input: the TV is only sent Home while this is on screen. */
    var input: String
        get() = prefs.getString("input", DEFAULT_INPUT) ?: DEFAULT_INPUT
        set(v) = prefs.edit().putString("input", v.trim().ifEmpty { DEFAULT_INPUT }).apply()

    /** The launcher to open on the box itself ("package/activity"); empty: the first other home app. */
    var fallback: String
        get() = prefs.getString("fallback", "") ?: ""
        set(v) = prefs.edit().putString("fallback", v.trim()).apply()

    /** When the TV last answered (ms since 1970), to keep the pairing from expiring for lack of use. */
    var lastOk: Long
        get() = prefs.getLong("lastOk", 0L)
        set(v) = prefs.edit().putLong("lastOk", v).apply()

    /** The client key the TV gave on LG's network remote protocol, after the person accepted the box on screen. */
    var lgKey: String
        get() = prefs.getString("lgKey", "") ?: ""
        set(v) = prefs.edit().putString("lgKey", v).apply()

    /** SHA-256 of the TV's certificate on port 3001, pinned the first time (the TV's is self-signed). */
    var lgCert: String
        get() = prefs.getString("lgCert", "") ?: ""
        set(v) = prefs.edit().putString("lgCert", v).apply()

    /** Whether the box's remote drives the TV while the TV shows its own screens. */
    var forwardKeys: Boolean
        get() = prefs.getBoolean("forwardKeys", true)
        set(v) = prefs.edit().putBoolean("forwardKeys", v).apply()

    val paired: Boolean get() = host.isNotEmpty() && token.isNotEmpty()

    val mcpUrl: String get() = "http://${host.substringBefore(':')}:${host.substringAfter(':', "8802")}/mcp"
    val pairUrl: String get() = mcpUrl.removeSuffix("/mcp") + "/pair"

    /** Every other home screen app on the box, such as FLauncher or Google TV's own. */
    fun otherLaunchers(): List<ComponentName> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(home, PackageManager.MATCH_ALL)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .filter { it.packageName != self && it.packageName != "com.android.settings" }
            .distinct()
    }

    fun fallbackLauncher(): ComponentName? =
        ComponentName.unflattenFromString(fallback)?.takeIf { c -> otherLaunchers().any { it == c } }
            ?: otherLaunchers().firstOrNull()

    fun label(c: ComponentName?): String = c?.let {
        runCatching { pm.getActivityInfo(it, 0).loadLabel(pm).toString() }.getOrDefault(it.packageName)
    } ?: "none"

    companion object {
        const val DEFAULT_INPUT = "com.webos.app.hdmi1"

        /** Home within this long of a wake is the box switching on, not an app closing. */
        const val WAKE_GRACE_MS = 15_000L
        const val KEEPALIVE_MS = 24L * 60 * 60 * 1000
    }
}
