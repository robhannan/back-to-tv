# Back to TV

A tiny home screen for an Android TV box (built for an NVIDIA SHIELD) that sits behind an LG TV running
[CleanYourTV](https://github.com/cleanyourtv). When you back out of an app on the box, Android returns to its home screen,
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
