# Back to TV

A tiny home screen for an Android TV box (built for an NVIDIA SHIELD) that sits behind an LG TV running
CleanYourTV. When you back out of an app on the box, Android returns to its home screen,
which is this app, and it sends the TV back to **its** Home instead of leaving you on the box's launcher.

```
Netflix on the box ──Back──► Back to TV (box's Home) ──HTTP──► CleanYourTV on the TV ──► TV Home
```

## How it decides

- **App closed** (Home appears while the box has been awake for 15 s or more): asks the TV what it's showing, and if it's
  still the box's input (`com.webos.app.hdmi1` by default) it sends the TV Home. If you've already switched the TV to
  something else, it leaves it alone.
- **Box just woke** (within 15 s): does nothing to the TV. That's the TV turning on, you choosing the box's input, or
  CleanYourTV starting an app on the box.
- **Using the box directly:** the screen shows "OK: apps on this box". OK opens your usual launcher (FLauncher, Google
  TV's, ...). It closes as soon as an app starts from it, so backing out of that app also comes back here and sends the
  TV Home. Pressing Home on the box's own remote does too.

After first making it the box's Home, close the old launcher once (`adb shell am force-stop <launcher package>`):
until then Android keeps the old launcher's home window on top, and Back from an app returns to it.

It talks to the TV through CleanYourTV's MCP server (Features > AI agents (MCP) on the TV), with its own pairing token,
kept in the app's private storage. It uses the TV's `get_current_app` and `navigate` tools only. The token is refreshed by
use; if the TV drops it (HTTP 401), pair again.

## The box's remote drives the TV

CEC is slow, so the box usually has its own remote (here a universal IR remote through a Flirc receiver). With the
"Back to TV remote keys" accessibility service on, that remote also drives the TV's own screens:

- **TV shows the box's input:** every press goes to the box as usual.
- **TV shows anything else** (Home, Live TV, settings): arrows, OK, Back, Home, Menu, media keys, channel and number
  keys are swallowed on the box and sent to the TV with LG's network remote protocol (SSAP on `wss://<tv>:3001`; this
  firmware closes the plain port 3000). Nothing is pressed twice, because the TV never sees the box's remote itself.
- Volume, mute and power are never touched. While the TV is off, unreachable or not yet approved, every press goes to
  the box, so its remote can't go dead.

The TV pushes every change of the on-screen app over the same connection, so there's no polling. The connection only
runs while the box's screen is on. The first time, the TV asks to allow "LG Remote App" (LG's signed manifest from
[lgtv2](https://github.com/hobbyquaker/lgtv2), MIT, see `LICENSE-lgtv2`); the key it then gives is kept. The TV's
self-signed certificate is pinned the first time it is seen.

Turn off the box's **One Touch Play** (HDMI-CEC "Switch TV input"), or the box's first press while the TV shows its
own Home wakes it and pulls the TV to its input:

```sh
adb shell settings put global hdmi_one_touch_play_enabled 0
```

Enable the service (keeping any others already on):

```sh
adb shell settings put secure enabled_accessibility_services "$(adb shell settings get secure enabled_accessibility_services):io.github.robhannan.backtotv/.KeyForwardService"
adb shell settings put secure accessibility_enabled 1
```

## The TV's screensaver over the box

LG's screensaver covers the TV's own screens, but not a still picture from the box. "TV screensaver (Back to TV)" goes in
the box's screensaver slot instead: when the box's screensaver timer runs out while the TV shows the box's input, it starts
LG's screensaver on the TV (`ssap://system.launcher/launch` of `com.webos.app.screensaver`; the TV refuses
`tvpower/turnOnScreenSaver` from the network) and ends at once, leaving the box as it was underneath. The next press on the
box's remote only dismisses the TV's screensaver; the Magic Remote dismisses it as usual. Android never starts a screensaver while
an app keeps the screen on, and many TV apps (YouTube-style ones especially) do that even when idle.

**Idle screensaver (option, off by default):** the key service keeps its own idle timer instead. While the TV shows the
box's input, after N minutes (5 by default) with no key press on the box and **no sound playing**, it starts the TV's
screensaver, whatever the app. Sound playing means a video or music is on, so it waits. A video playing with the box's
own sound muted counts as idle. Switching the TV to the box's input counts as activity. Turn it on in Back to TV's settings,
or `am start -n io.github.robhannan.backtotv/.SetupActivity --ez idleSaver true --ei idleMinutes 5`.

```sh
adb shell settings put secure screensaver_components io.github.robhannan.backtotv/.SaverDream
adb shell settings put secure screensaver_enabled 1
adb shell settings put system screen_off_timeout 300000   # minutes of idle before it starts, here 5
```

**Dismissing it.** A screensaver started this way isn't the TV's own (the TV's power daemon only starts one when no app
objects, and the HDMI input objects while it has a picture), so the TV's remotes don't dismiss it by themselves. Two parts
make it behave like a normal screensaver:

- **The box's remote:** the first press closes it and does nothing else. Back to TV remembers that it started the
  screensaver across restarts; if LG's network remote won't close it, CleanYourTV's `close_app` tool does.
- **The Magic Remote and LG's IR remote:** `tv/saver-dismiss.js` runs on the TV (root; Homebrew Channel starts it at
  boot) and closes the screensaver on the first key. It reads the remotes' input devices without grabbing them.

```sh
TV_HOST=<tv-ip> [TV_KEY=~/.ssh/key] tv/install.sh        # install and start; tv/install.sh remove takes it off
```

After a Magic Remote dismissal the box doesn't know the screensaver is gone, so the box's remote's next press is spent on
closing nothing.

For trying other SSAP requests, `DebugReceiver` takes them from adb only (the sender needs `android.permission.DUMP`):

```sh
adb shell am broadcast -n io.github.robhannan.backtotv/.DebugReceiver -a io.github.robhannan.backtotv.SSAP \
  --es uri ssap://com.webos.service.tvpower/power/getPowerState
adb logcat -s BackToTv
```

## Build and install

Needs JDK 17+ and the Android SDK (compileSdk 36).

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Set up

1. On the TV: CleanYourTV manager > Features > AI agents (MCP) > Connect an agent. Note the code.
2. Pair (or open "Back to TV" on the box and fill in the form):
   ```sh
   adb shell am start -n io.github.robhannan.backtotv/.SetupActivity --es host <tv-ip> --es code <code>
   ```
3. Make it the box's Home:
   ```sh
   adb shell cmd package set-home-activity io.github.robhannan.backtotv/.HomeActivity
   ```

To undo, set your old launcher back (for example `me.efesser.flauncher/.MainActivity`) or uninstall the app.
