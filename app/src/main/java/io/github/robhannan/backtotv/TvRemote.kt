package io.github.robhannan.backtotv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * LG's own network remote protocol (SSAP, the one LG's phone apps use) on wss://<tv>:3001 (this firmware closes the
 * plain ws:// port 3000). The TV's certificate is self-signed: it is trusted the first time and pinned after that
 * (Config.lgCert), like SSH does with a new host.
 * - register with the signed manifest in assets/lg-pairing.json; the first time, the TV asks on screen and then gives a
 *   client key that is kept for next time;
 * - subscribe to the foreground app, so [tvApp] always says what the TV shows, without polling;
 * - open the pointer input socket and send remote buttons ("type:button\nname:UP\n\n").
 *
 * Runs only while [start]ed (the key service starts it while the box's screen is on).
 */
class TvRemote(private val context: Context) {
    enum class State { OFF, CONNECTING, WAITING_FOR_APPROVAL, READY }

    @Volatile var state = State.OFF
        private set

    /** The app on the TV's screen (com.webos.app.hdmi1, com.webos.app.home, ...); empty while unknown. */
    @Volatile var tvApp = ""
        private set

    @Volatile var lastError = ""
        private set

    private val cfg = Config(context)
    /** Trust on first use: accept the TV's self-signed certificate once, then only that one. */
    private val pin = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException("server only")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val leaf = chain.firstOrNull() ?: throw CertificateException("no certificate")
            val sha = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString("") { "%02x".format(it) }
            val known = cfg.lgCert
            if (known.isEmpty()) cfg.lgCert = sha
            else if (known != sha) throw CertificateException("the TV's certificate changed; clear it in Back to TV's settings if that is expected")
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    private val http = OkHttpClient.Builder()
        .sslSocketFactory(SSLContext.getInstance("TLS").apply { init(null, arrayOf(pin), null) }.socketFactory, pin)
        .hostnameVerifier { host, _ -> host == cfg.host.substringBefore(':') }
        .connectTimeout(3, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val main = Handler(Looper.getMainLooper())
    private var mainWs: WebSocket? = null
    private var pointer: WebSocket? = null
    private var running = false
    private var backoff = 1_000L
    @Volatile private var generation = 0

    fun start() {
        if (running) return
        running = true
        backoff = 1_000L
        connect()
    }

    fun stop() {
        running = false
        generation++
        main.removeCallbacksAndMessages(null)
        pointer?.close(1000, null)
        mainWs?.close(1000, null)
        pointer = null
        mainWs = null
        state = State.OFF
        tvApp = ""
    }

    /** Sends one remote button (LG's names: UP, DOWN, LEFT, RIGHT, ENTER, BACK, HOME, MENU, ...). False if not connected. */
    fun button(name: String): Boolean = pointer?.send("type:button\nname:$name\n\n") ?: false

    private fun connect() {
        if (!running) return
        val host = cfg.host.substringBefore(':')
        if (host.isEmpty()) {
            lastError = "no TV address"
            return
        }
        val gen = ++generation
        state = State.CONNECTING
        mainWs = http.newWebSocket(Request.Builder().url("wss://$host:3001").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen != generation) return
                val reg = JSONObject(context.assets.open("lg-pairing.json").bufferedReader().use { it.readText() })
                if (cfg.lgKey.isNotEmpty()) reg.put("client-key", cfg.lgKey)
                webSocket.send(JSONObject().put("type", "register").put("id", "register").put("payload", reg).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != generation) return
                val m = runCatching { JSONObject(text) }.getOrNull() ?: return
                val payload = m.optJSONObject("payload")
                when {
                    m.optString("type") == "error" -> {
                        lastError = m.optString("error")
                        Log.w(TAG, "TV error on ${m.optString("id")}: $lastError")
                        // a key the TV no longer accepts: forget it, so the next try asks on screen again
                        if (m.optString("id") == "register") cfg.lgKey = ""
                    }
                    m.optString("id") == "register" && payload?.optString("pairingType") == "PROMPT" ->
                        state = State.WAITING_FOR_APPROVAL
                    m.optString("type") == "registered" -> {
                        payload?.optString("client-key")?.takeIf { it.isNotEmpty() }?.let { cfg.lgKey = it }
                        backoff = 1_000L
                        webSocket.send(
                            JSONObject().put("type", "subscribe").put("id", "fg")
                                .put("uri", "ssap://com.webos.applicationManager/getForegroundAppInfo").toString(),
                        )
                        webSocket.send(
                            JSONObject().put("type", "request").put("id", "pointer")
                                .put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket").toString(),
                        )
                    }
                    m.optString("id") == "fg" && payload != null && payload.has("appId") -> {
                        tvApp = payload.optString("appId")
                        Log.d(TAG, "TV shows $tvApp")
                    }
                    m.optString("id") == "pointer" && payload != null -> openPointer(payload.optString("socketPath"), gen)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(gen, t.message ?: t.javaClass.simpleName)

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(gen, "closed $code $reason")
        })
    }

    private fun openPointer(path: String, gen: Int) {
        if (path.isEmpty()) return
        pointer = http.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen == generation) state = State.READY
                Log.d(TAG, "pointer socket open")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(gen, "pointer: ${t.message}")
        })
    }

    private fun lost(gen: Int, why: String) {
        if (gen != generation) return
        lastError = why
        Log.d(TAG, "TV connection lost: $why")
        pointer?.cancel()
        mainWs?.cancel()
        pointer = null
        mainWs = null
        tvApp = ""
        state = State.OFF
        if (!running) return
        // the TV is off or asleep most of the time this happens: retry, slower and slower, up to once a minute
        val delay = backoff
        backoff = (backoff * 2).coerceAtMost(60_000L)
        generation++
        main.postDelayed({ connect() }, delay)
    }

    companion object {
        const val TAG = "BackToTv"
    }
}
