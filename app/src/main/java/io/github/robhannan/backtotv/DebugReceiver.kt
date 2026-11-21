package io.github.robhannan.backtotv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

/**
 * For trying SSAP requests from a computer; only the shell can send it (android.permission.DUMP):
 *   adb shell am broadcast -n io.github.robhannan.backtotv/.DebugReceiver -a io.github.robhannan.backtotv.SSAP \
 *     --es uri ssap://com.webos.service.tvpower/power/turnOnScreenSaver [--es payload '{}']
 * The reply is logged under the BackToTv tag.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.getStringExtra("uri") ?: return
        val payload = runCatching { JSONObject(intent.getStringExtra("payload") ?: "{}") }.getOrDefault(JSONObject())
        val tv = KeyForwardService.instance?.remote
        if (tv == null) {
            Log.w(TvRemote.TAG, "ssap $uri: the key service is not running")
            return
        }
        val done = goAsync()
        tv.request(uri, payload) { reply ->
            Log.i(TvRemote.TAG, "ssap $uri -> $reply")
            done.finish()
        }
    }
}
