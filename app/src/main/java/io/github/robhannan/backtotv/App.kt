package io.github.robhannan.backtotv

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Remembers when the box last woke up. Home appearing right after a wake means the box was just switched on (the TV turned
 * on, someone chose its input, or CleanYourTV is about to start an app on it), not that an app was closed, so the TV is left
 * alone then.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // A process started by the wake itself never sees the broadcast, so starting counts as waking.
        lastWake = SystemClock.elapsedRealtime()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                lastWake = SystemClock.elapsedRealtime()
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    companion object {
        @Volatile
        var lastWake = 0L

        /** One background thread for every call to the TV, so they never overlap. */
        val io: ExecutorService = Executors.newSingleThreadExecutor()
    }
}
