package io.github.robhannan.backtotv

import android.graphics.Color
import android.service.dreams.DreamService
import android.view.View

/**
 * The box's screensaver slot, used to start the TV's own screensaver instead. When the box's screensaver timer runs out while the
 * TV shows the box's input, this asks the TV (LG's network remote) to start LG's screensaver over the picture, then ends at once so
 * the box is back as it was underneath. The next press on the box's remote dismisses the TV's screensaver (KeyForwardService);
 * the Magic Remote dismisses it as usual. While the TV shows its own screens, the TV's screensaver is the TV's business, so this
 * does nothing.
 *
 * Choose it as the box's screensaver: Settings > Device Preferences > Screen saver, or over adb (README).
 */
class SaverDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(View(this).apply { setBackgroundColor(Color.BLACK) })
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val tv = KeyForwardService.instance?.remote
        val input = Config(this).input
        if (tv != null && tv.state == TvRemote.State.READY && tv.tvApp == input) tv.startSaver()
        // the TV's screensaver is the screensaver now; the box goes back to what it showed
        window?.decorView?.postDelayed({ finish() }, 300)
    }
}
