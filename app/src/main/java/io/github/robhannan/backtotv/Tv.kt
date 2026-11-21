package io.github.robhannan.backtotv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The TV's CleanYourTV MCP server: plain JSON-RPC 2.0 over HTTP, no session and no event stream
 * (POST /pair once for a token, then POST /mcp with "Authorization: Bearer <token>").
 * Blocking: call from App.io only.
 */
object Tv {
    class Reply(val ok: Boolean, val text: String)

    private fun post(url: String, body: JSONObject, token: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 3000
            c.readTimeout = 6000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            if (token != null) c.setRequestProperty("authorization", "Bearer $token")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }

    /** Pairs with the code shown on the TV (CleanYourTV manager > Features > AI agents (MCP) > Connect an agent). */
    fun pair(cfg: Config, code: String, name: String): Reply = guard {
        val (status, text) = post(cfg.pairUrl, JSONObject().put("code", code.trim()).put("name", name), null)
        val token = runCatching { JSONObject(text).optString("token") }.getOrDefault("")
        if (status == 200 && token.isNotEmpty()) {
            cfg.token = token
            cfg.lastOk = System.currentTimeMillis()
            Reply(true, "Paired with the TV.")
        } else {
            Reply(false, "The TV refused the code (HTTP $status): ${errorOf(text)}")
        }
    }

    /** Calls one of the TV's tools and returns its text (or why it failed). */
    fun call(cfg: Config, tool: String, args: JSONObject = JSONObject()): Reply = guard {
        val rpc = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "tools/call")
            .put("params", JSONObject().put("name", tool).put("arguments", args))
        val (status, text) = post(cfg.mcpUrl, rpc, cfg.token)
        when (status) {
            200 -> {
                val o = JSONObject(text)
                o.optJSONObject("error")?.let { return@guard Reply(false, it.optString("message")) }
                val result = o.optJSONObject("result") ?: return@guard Reply(false, "no result")
                val content = result.optJSONArray("content")
                val out = (0 until (content?.length() ?: 0)).joinToString("\n") { content!!.getJSONObject(it).optString("text") }
                if (!result.optBoolean("isError")) cfg.lastOk = System.currentTimeMillis()
                Reply(!result.optBoolean("isError"), out)
            }
            401 -> Reply(false, "The TV no longer knows this box: pair again (HTTP 401).")
            403 -> Reply(false, "This box is blocked on the TV (HTTP 403).")
            else -> Reply(false, "HTTP $status: ${errorOf(text)}")
        }
    }

    /** Sends the TV to its Home, but only while the box's input is still on screen. */
    fun backToTvHome(cfg: Config): Reply {
        val now = call(cfg, "get_current_app")
        if (!now.ok) return now
        val id = runCatching { JSONObject(now.text).optString("id") }.getOrDefault("")
        if (id != cfg.input) return Reply(true, "The TV shows ${id.ifEmpty { "something else" }}, so it was left alone.")
        val nav = call(cfg, "navigate", JSONObject().put("to", "home"))
        return if (nav.ok) Reply(true, "The TV is back on its Home.") else nav
    }

    private fun errorOf(text: String) = runCatching { JSONObject(text).optString("error") }.getOrNull()?.ifEmpty { null } ?: text.take(200)

    private inline fun guard(block: () -> Reply): Reply = try {
        block()
    } catch (e: Exception) {
        Reply(false, "Could not reach the TV: ${e.javaClass.simpleName} ${e.message ?: ""}".trim())
    }
}
